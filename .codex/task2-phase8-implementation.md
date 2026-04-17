# Task 2：Phase 8 实现——模型池与 Agent 绑定配置中心

## 前提条件

**必须在 Task 1（Phase 8 前置收口）完成且 `mvn test` 全部通过后才能执行本任务。**

---

## 任务目标

实现"连接配置 / 模型配置 / Agent 绑定 / 运行时快照"四层模型，让运行时模型切换不再依赖改配置重发版，同时升级 `LlmGateway` 与 `AgentModelRouter` 为快照驱动路由。

**验收命令：**
```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

---

## 一、数据库迁移

### 合并进单基线 `V1__baseline_schema.sql`

路径：`src/main/resources/db/migration/V1__baseline_schema.sql`

```sql
-- LLM Provider 连接配置表
CREATE TABLE IF NOT EXISTS llm_provider_connections (
    id                  BIGSERIAL PRIMARY KEY,
    connection_code     VARCHAR(64)  NOT NULL UNIQUE,
    provider_type       VARCHAR(32)  NOT NULL,   -- openai / anthropic / openai_compatible
    base_url            VARCHAR(512) NOT NULL,
    api_key_ciphertext  TEXT         NOT NULL,
    api_key_mask        VARCHAR(128) NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    remarks             VARCHAR(512),
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  llm_provider_connections                      IS 'LLM Provider 连接配置表';
COMMENT ON COLUMN llm_provider_connections.connection_code      IS '连接编码，供后台和快照引用';
COMMENT ON COLUMN llm_provider_connections.provider_type        IS 'Provider 类型：openai/anthropic/openai_compatible';
COMMENT ON COLUMN llm_provider_connections.api_key_ciphertext   IS '加密存储的 API Key';
COMMENT ON COLUMN llm_provider_connections.api_key_mask         IS '前端脱敏展示值';

-- LLM 模型配置表
CREATE TABLE IF NOT EXISTS llm_model_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    model_code          VARCHAR(64)      NOT NULL UNIQUE,
    connection_id       BIGINT           NOT NULL REFERENCES llm_provider_connections(id),
    model_name          VARCHAR(128)     NOT NULL,
    temperature         NUMERIC(4,2),
    max_tokens          INTEGER,
    timeout_seconds     INTEGER,
    extra_options_json  JSONB,
    enabled             BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  llm_model_profiles             IS 'LLM 模型配置表';
COMMENT ON COLUMN llm_model_profiles.model_code  IS '模型配置编码';
COMMENT ON COLUMN llm_model_profiles.model_name  IS '真实模型名称（如 gpt-4o、claude-3-5-sonnet）';

-- Agent 模型绑定表
CREATE TABLE IF NOT EXISTS agent_model_bindings (
    id                          BIGSERIAL PRIMARY KEY,
    scene                       VARCHAR(32)  NOT NULL,  -- compile / query
    agent_role                  VARCHAR(32)  NOT NULL,  -- writer / reviewer / fixer / answer / rewrite
    primary_model_profile_id    BIGINT       NOT NULL REFERENCES llm_model_profiles(id),
    fallback_model_profile_id   BIGINT                 REFERENCES llm_model_profiles(id),
    route_label                 VARCHAR(128) NOT NULL,
    enabled                     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_binding UNIQUE (scene, agent_role)
);

COMMENT ON TABLE  agent_model_bindings                          IS 'Agent 模型绑定表';
COMMENT ON COLUMN agent_model_bindings.scene                    IS '场景：compile/query';
COMMENT ON COLUMN agent_model_bindings.agent_role               IS '角色：writer/reviewer/fixer/answer/rewrite';
COMMENT ON COLUMN agent_model_bindings.route_label              IS '面向日志和状态的稳定路由标签';

-- 运行时 LLM 快照表
CREATE TABLE IF NOT EXISTS execution_llm_snapshots (
    id                  BIGSERIAL PRIMARY KEY,
    scope_type          VARCHAR(32)  NOT NULL,  -- compile_job / query_request
    scope_id            VARCHAR(64)  NOT NULL,
    scene               VARCHAR(32)  NOT NULL,
    agent_role          VARCHAR(32)  NOT NULL,
    binding_id          BIGINT                 REFERENCES agent_model_bindings(id),
    model_profile_id    BIGINT                 REFERENCES llm_model_profiles(id),
    connection_id       BIGINT                 REFERENCES llm_provider_connections(id),
    route_label         VARCHAR(128),
    provider_type       VARCHAR(32),
    base_url            VARCHAR(512),
    model_name          VARCHAR(128),
    snapshot_version    INTEGER      NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_llm_snapshot UNIQUE (scope_type, scope_id, scene, agent_role)
);

COMMENT ON TABLE  execution_llm_snapshots            IS '运行时 LLM 快照表';
COMMENT ON COLUMN execution_llm_snapshots.scope_type IS '作用域类型：compile_job/query_request';
COMMENT ON COLUMN execution_llm_snapshots.scope_id   IS 'jobId 或 queryId';
COMMENT ON COLUMN execution_llm_snapshots.route_label IS '任务内稳定路由标签';
```

---

## 二、新建 `llm` 包

目录：`src/main/java/com/xbk/lattice/llm/`

### 2.1 Domain 对象（`llm/domain/`）

仿照现有 `ArticleRecord` 的 Java Record 或 Lombok `@Data` 风格，创建：

- `LlmProviderConnection`：对应 `llm_provider_connections` 表字段
- `LlmModelProfile`：对应 `llm_model_profiles` 表字段，包含 `LlmProviderConnection connection`（可选关联）
- `AgentModelBinding`：对应 `agent_model_bindings` 表字段
- `ExecutionLlmSnapshot`：对应 `execution_llm_snapshots` 表字段

### 2.2 Repository（`llm/infra/`）

每个 domain 对应一个 JDBC Repository，参照现有 `ArticleJdbcRepository` 的风格实现：

- `LlmProviderConnectionRepository`（`@Repository @Profile("jdbc")`）
  - `findAll()` → `List<LlmProviderConnection>`
  - `findById(long id)` → `Optional<LlmProviderConnection>`
  - `findByConnectionCode(String code)` → `Optional<LlmProviderConnection>`
  - `save(LlmProviderConnection)` → `LlmProviderConnection`（upsert）
  - `deleteById(long id)`

- `LlmModelProfileRepository`（同上风格）
  - `findAll()` → `List<LlmModelProfile>`
  - `findById(long id)` → `Optional<LlmModelProfile>`
  - `findByModelCode(String code)` → `Optional<LlmModelProfile>`
  - `findByConnectionId(long connectionId)` → `List<LlmModelProfile>`
  - `save(LlmModelProfile)` → `LlmModelProfile`
  - `deleteById(long id)`

- `AgentModelBindingRepository`（同上风格）
  - `findAll()` → `List<AgentModelBinding>`
  - `findBySceneAndAgentRole(String scene, String agentRole)` → `Optional<AgentModelBinding>`
  - `findByScene(String scene)` → `List<AgentModelBinding>`
  - `save(AgentModelBinding)` → `AgentModelBinding`
  - `deleteById(long id)`

- `ExecutionLlmSnapshotRepository`（同上风格）
  - `findByScopeIdAndSceneAndAgentRole(String scopeId, String scene, String agentRole)` → `Optional<ExecutionLlmSnapshot>`
  - `findByScopeId(String scopeId)` → `List<ExecutionLlmSnapshot>`
  - `save(ExecutionLlmSnapshot)` → `ExecutionLlmSnapshot`
  - `deleteByScopeId(String scopeId)`

### 2.3 `ExecutionLlmSnapshotService`（`llm/service/`）

`@Service @Profile("jdbc")`，依赖 `AgentModelBindingRepository`、`LlmModelProfileRepository`、`LlmProviderConnectionRepository`、`ExecutionLlmSnapshotRepository`。

核心方法：

```java
/**
 * 在任务启动时为所有角色冻结 LLM 绑定快照。
 * scopeType 为 "compile_job" 或 "query_request"。
 */
public void freezeSnapshots(String scopeType, String scopeId, String scene);

/**
 * 查询指定角色的快照。
 */
public Optional<ExecutionLlmSnapshot> findSnapshot(String scopeId, String scene, String agentRole);
```

`freezeSnapshots` 实现逻辑：
1. 查询 `agent_model_bindings` 中 `scene` 匹配且 `enabled=true` 的所有绑定
2. 对每条绑定解析 `primary_model_profile_id` 和 `connection_id`
3. 构造 `ExecutionLlmSnapshot` 并 upsert（`UNIQUE` 约束保证幂等）
4. `apiKey` 不复制到快照（`api_key_ciphertext` 只存在 `llm_provider_connections`）

### 2.4 `LlmClientFactory`（`llm/service/`）

`@Service @Profile("jdbc")`，依赖 `LlmProviderConnectionRepository`、`RestClient.Builder`、`ObjectMapper`、`OpenAiChatModel`（`@Autowired` fallback）。

核心方法：

```java
/**
 * 按快照创建或复用 LLM 客户端。
 * providerType 为 "openai" / "anthropic" / "openai_compatible"。
 * connectionId 用于从 llm_provider_connections 读取 apiKey（解密）。
 */
public LlmClient createClient(ExecutionLlmSnapshot snapshot);
```

实现规则：
- `anthropic` 类型 → 使用现有 `AnthropicMessageApiLlmClient`
- `openai` 或 `openai_compatible` 类型 → 使用现有 `ChatModelLlmClient` 或用 `RestClient` 构建
- apiKey 解密：V1 暂不做真实加密，`api_key_ciphertext` 直接存明文（加密在 V2 增强），解密即直接读取
- 按 `connectionId + modelName` 做进程内客户端缓存（`ConcurrentHashMap`）

### 2.5 `LlmConfigAdminService`（`llm/service/`）

`@Service @Profile("jdbc")`，面向 Admin 后台的 CRUD + 脱敏。

提供：
- 连接配置 CRUD，返回时 `apiKeyMask` 展示，不返回 `apiKeyCiphertext`
- 模型配置 CRUD
- 绑定配置 CRUD
- `testConnection(long connectionId)` → 发送一条最短 ping 请求，返回成功/失败（不泄露响应头）

---

## 三、升级 `AgentModelRouter`

**文件：** `src/main/java/com/xbk/lattice/compiler/agent/AgentModelRouter.java`

**现状：** 只是透传 `LlmGateway` 的属性读取，没有真实路由逻辑。

**升级后：** 接受 `ExecutionLlmSnapshotService` 注入，按 `scope + agentRole` 解析快照路由标签；当快照不存在时（如本地开发无配置），fallback 读 `LlmGateway.compileRoute()` 等属性。

新方法签名：

```java
/** 按任务快照解析路由标签，找不到快照时 fallback 到 properties */
public String routeFor(String scopeId, String scene, String agentRole);

/** 兼容旧调用，scene=compile，scopeId=null 时纯用 fallback */
public String routeForWriterAgent();
public String routeForReviewerAgent();
public String routeForFixerAgent();
```

---

## 四、升级 `LlmGateway`

**文件：** `src/main/java/com/xbk/lattice/compiler/service/LlmGateway.java`

**变更点：**

1. **新增构造器参数：** 可选注入 `LlmClientFactory`（`@Autowired(required=false)`）

2. **新增重载方法：**

```java
/**
 * 按快照调用 LLM，优先使用快照 client，找不到时 fallback。
 */
public String compileWithSnapshot(ExecutionLlmSnapshot snapshot, String purpose, String systemPrompt, String userPrompt);
public String reviewWithSnapshot(ExecutionLlmSnapshot snapshot, String purpose, String systemPrompt, String userPrompt);
```

3. **修复缓存键：** 在 `buildCacheKey()` 中加入 `snapshot.getSnapshotVersion()`（无快照时用空串）：

```java
private String buildCacheKey(String modelName, int snapshotVersion, String systemPrompt, String userPrompt) {
    return llmProperties.getCacheKeyPrefix()
        + sha256(modelName + "|" + snapshotVersion + "|" + systemPrompt + "|" + userPrompt);
}
```

4. **估算成本改为从 modelName 匹配：** 把 `estimateCostUsd()` 中的硬编码价格提取为配置常量 map（`"anthropic" -> 3.0/15.0`，`default -> 0.55/2.19`），以便 Phase 9 移入 `LlmModelProfile`。

**不允许删除**原有 `compile()/review()` 方法，保持 fallback 能力。

---

## 五、在 `initialize_job` 节点调用 `freezeSnapshots`

**文件：** `src/main/java/com/xbk/lattice/compiler/graph/node/InitializeJobNode.java`（Task 1 创建）

在 `execute()` 中，获取 `jobId` 后调用：
```java
executionLlmSnapshotService.freezeSnapshots("compile_job", state.getJobId(), "compile");
```

（如果 `ExecutionLlmSnapshotService` 因数据库无配置而找不到绑定，必须优雅降级，不抛异常，日志 warn 即可）

同理，**问答侧 `NormalizeQuestionNode`**（`query/graph/node/`，如果 Task 1 里也创建了的话）中调用：
```java
executionLlmSnapshotService.freezeSnapshots("query_request", state.getQueryId(), "query");
```

---

## 六、Admin API 控制器

目录：`src/main/java/com/xbk/lattice/api/admin/llm/`

### 6.1 `LlmConnectionConfigController`

`@RestController @RequestMapping("/admin/api/llm/connections") @Profile("jdbc")`

| 方法 | 端点 | 说明 |
|---|---|---|
| GET | `/` | 返回所有连接（`apiKey` 脱敏） |
| GET | `/{id}` | 返回单条（`apiKey` 脱敏） |
| POST | `/` | 新建 |
| PUT | `/{id}` | 更新（`apiKey` 为空时不更新密钥字段） |
| DELETE | `/{id}` | 删除 |
| POST | `/{id}/test` | 连通性测试 |

### 6.2 `LlmModelProfileController`

`@RestController @RequestMapping("/admin/api/llm/models") @Profile("jdbc")`

| 方法 | 端点 | 说明 |
|---|---|---|
| GET | `/` | 返回所有模型配置 |
| POST | `/` | 新建 |
| PUT | `/{id}` | 更新 |
| DELETE | `/{id}` | 删除 |

### 6.3 `AgentModelBindingController`

`@RestController @RequestMapping("/admin/api/llm/bindings") @Profile("jdbc")`

| 方法 | 端点 | 说明 |
|---|---|---|
| GET | `/` | 返回所有绑定 |
| POST | `/` | 新建或更新（`scene+agentRole` 唯一） |
| DELETE | `/{id}` | 删除 |

---

## 七、前端 Admin 页面（`src/main/resources/static/admin/`）

### 7.1 `admin.html` 新增三个导航 Tab

在现有 Admin 页面中新增以下 Tab（参照现有页面风格）：
- **连接配置**（`#tab-connections`）
- **模型配置**（`#tab-models`）
- **Agent 绑定**（`#tab-bindings`）

### 7.2 `admin.js` 新增对应 Tab 逻辑

每个 Tab 实现：
- 列表加载（调用对应 GET 接口）
- 新建/编辑表单（inline 或 modal，风格与现有保持一致）
- 删除操作
- 连接配置 Tab 额外提供"测试连通"按钮
- **`apiKey` 输入框**：提示文字"留空则不更新"，不在列表中显示密文

---

## 八、配置项新增（`application.yml`）

在现有配置末尾追加：

```yaml
lattice:
  llm:
    config-source: hybrid        # database / properties / hybrid
    bootstrap-enabled: true      # 数据库无配置时是否 fallback 到本地配置
    admin:
      encrypt-secrets: false     # V1 暂不启用加密，Phase 9 升级
      mask-secrets: true         # Admin API 默认脱敏
```

---

## 九、新增测试

### 9.1 单元测试

- `ExecutionLlmSnapshotServiceTests`：验证 `freezeSnapshots` 在有绑定和无绑定时的行为（无绑定时不抛异常）
- `LlmClientFactoryTests`：验证 anthropic/openai 类型分支
- `AgentModelRouterSnapshotTests`：验证快照存在时返回快照路由标签，不存在时 fallback

### 9.2 集成测试补充

在现有 `AdminGovernanceApiIntegrationTests` 中增加以下 case（或新建 `LlmConfigCenterIntegrationTests`）：
- 新建连接配置后 GET 返回脱敏 `apiKeyMask`，不返回 `apiKeyCiphertext`
- 新建模型配置并关联连接后可正常查询
- 创建绑定后 `freezeSnapshots` 能在 `execution_llm_snapshots` 中写入快照
- 更新绑定后新启动任务使用新快照，已冻结快照不受影响

---

## 设计约束（不允许违反）

1. Admin API 任何接口都**不得**在响应中返回 `apiKeyCiphertext` 字段
2. 日志、步骤摘要、异常信息中**禁止**出现 `apiKey` 明文
3. `freezeSnapshots` 必须是幂等的（重复调用不报错）
4. 快照冻结失败（如无绑定配置）**不阻断**主流程，仅 warn 日志
5. `LlmGateway` 原有 `compile()/review()` 接口**不删除**，保持 properties fallback 能力
6. `agent_model_bindings` 表的 `UNIQUE(scene, agent_role)` 约束必须在迁移脚本中体现

---

## 完成后更新设计文档

**文件：** `.codex/Spring AI Alibaba Graph 完整接入设计方案.md`

将 Phase 8 更新为：

```markdown
- [x] Phase 8：模型池与 Agent 绑定配置中心
  - 验收：`llm_provider_connections / llm_model_profiles / agent_model_bindings / execution_llm_snapshots` 四表已落地；
    Admin 至少支持连接配置、模型配置、Agent 绑定三类对象维护；
    `apiKey` 已实现页面脱敏；`ExecutionLlmSnapshotService.freezeSnapshots()` 在任务启动时冻结快照；
    `AgentModelRouter` 已升级为快照驱动路由；`LlmGateway` 缓存键已加入 `snapshotVersion`；
    `mvn test` 通过。
```
