# Terminal Unit Phase 1E-2 LLM Alias Enricher Runtime Smoke 报告

验证时间：2026-05-29
验证人：agentD
验证对象：Phase 1E-2 LLM Alias Enricher 最小 runtime binding smoke

## 1. 验证结论

**BLOCKED** — `LlmFactCardTerminalUnitFieldAliasEnricher` 的 `@Service` bean 未被 Spring 创建，enricher 从未被调用，compile 生成的 terminal unit 无中文 alias。需将类改为 `public` 或通过 `@Bean` 显式注册后再验证。

## 2. Runtime 绑定状态

### 2.1 已有连接与模型

| 资源 | ID | 标识 | 状态 |
|---|---|---|---|
| Chat Connection | 1 | local_openai (openai_compatible) | enabled |
| Embedding Connection | 2 | zhipu_embedding (openai_compatible) | enabled |
| Chat Model | 1 | gpt-5.5 | enabled |
| Embedding Model | 2 | embedding-3 | enabled |

### 2.2 field-alias-enricher role 绑定

API 创建被 `AdminLlmConfigController.SCENE_ROLE_OPTIONS` 白名单拒绝（"agentRole与scene不匹配"）。通过直接 DB INSERT 绕过：

```sql
INSERT INTO lattice.agent_model_bindings
  (scene, agent_role, primary_model_profile_id, route_label, enabled, created_by, updated_by)
VALUES ('compile', 'field-alias-enricher', 1, 'compile.field-alias-enricher.gpt-5-5', true, 'admin', 'admin');
```

绑定创建成功：id=11，route_label=`compile.field-alias-enricher.gpt-5-5`，enabled=true。重启后绑定持久存在。

已复用已有 enabled connection（local_openai, id=1）和 chat 模型（gpt-5.5, id=1），未输出 key。

## 3. Smoke 验证过程

### 3.1 服务状态

- 服务正常启动（`scripts/run-local-dev.sh`，端口 18082，health UP）
- 重启后绑定仍存在

### 3.2 最小 Compile Smoke

两次 compile 测试：

| 次数 | 输入文件 | 字段 | compile 结果 | Terminal unit 生成 |
|---|---|---|---|---|
| 1 | smoke-test-config.yaml | max_retry_count, connection_timeout, enable_debug_mode | SUCCEEDED, persistedCount=1 | 3 个 terminal unit |
| 2 | smoke2.yaml | max_connections, request_timeout_ms | SUCCEEDED, persistedCount=1 | 2 个 terminal unit |

### 3.3 LLM 触发结果

- **LLM enricher 未被调用**：服务日志中零条 enricher 相关输出
- **route resolution 未触发**：`"Skip terminal unit field alias enrichment because LLM route is unavailable"` 警告未出现
- **LLM 调用未发生**：无 `llm_raw_call_started`/`llm_raw_call_succeeded` for field-alias-enricher

### 3.4 Terminal Unit Alias 产物

smoke-test-config.yaml 的 `max_retry_count=3` 生成的 fieldAliases：

```json
["max_retry_count", "max retry count", "system_config.max_retry_count",
 "system config.max retry count", "system_config max_retry_count",
 "system_config", "system config", "system config max retry count",
 "max", "retry", "count", "system", "config"]
```

**全部为英文/ASCII**。零中文字符。与 Phase 1D-1（无 LLM enricher）产生的 alias 完全一致。

## 4. 结果核验

### 4.1 阻断点定位

```
compile 触发
  → FactCardGenerationService.materializeTerminalUnits()
    → fieldAliasEnricher == null          ← 阻断点：bean 未被 Spring 创建
    → skip enrich()                       ← null check 跳过
    → upsertAll()                         ← 原始 alias 持久化
```

### 4.2 根因分析

`LlmFactCardTerminalUnitFieldAliasEnricher` 类的声明：

```java
@Service
@Slf4j
class LlmFactCardTerminalUnitFieldAliasEnricher implements FactCardTerminalUnitFieldAliasEnricher {
```

类为 **package-private**（无 `public` 修饰符）。虽然 Spring 理论上支持 package-private 的 `@Service` 类，但在当前 Spring Boot 3.5.1 + 组件扫描配置下，该 bean 未被创建。证据：
- 服务日志零提及 "Enricher"/"enricher"/"LlmFactCard"
- 两次 compile 均无 enricher 日志输出（失败/成功均无）
- Terminal unit alias 产物与 Phase 1D-1（无 enricher）完全一致

### 4.3 无法验证项

因 bean 未被创建，以下项目无法在 runtime 验证：
- `isLlmRouteAvailable()` 的 route resolution 逻辑
- `requestAliases()` 的 LLM 调用与 JSON 解析
- `mergeAliases()` 的 alias 合并与 `rebuildFtsText` 同步更新
- fail-closed 路径（LLM 异常/非 JSON/空响应/已有 CJK alias 跳过）

上述场景在定向测试中已通过 12 个测试覆盖（25/0/0）。

### 4.4 Fail-Closed 行为（间接确认）

虽然 LLM 路径未触发，但现有编译路径的 fail-closed 行为符合预期：
- enricher bean 不存在 → `fieldAliasEnricher` 为 null → null check 跳过 → 原 records 不变
- 无任何编译失败、异常或 alias 损坏

## 5. 未执行项

| 项目 | 状态 | 原因 |
|---|---|---|
| clean schema | 未执行 | 本轮只做 runtime smoke |
| 全量资料导入 | 未执行 | 同上 |
| 19 题业务 eval | 未执行 | 同上 |
| 修改代码 | 未执行 | 禁止项 |
| 修改模型绑定配置参考.md | 未执行 | 禁止项 |
| stage/commit/push | 未执行 | 禁止项 |
| 输出 API key | 未输出 | 禁止项 |

## 6. 风险与下一步

### 6.1 最小修复建议

**将 `LlmFactCardTerminalUnitFieldAliasEnricher` 类改为 `public`，或通过 `@Bean` 方法显式注册。**

选项 A（推荐）：在类声明前加 `public`：
```java
// Before:
@Service
class LlmFactCardTerminalUnitFieldAliasEnricher ...

// After:
@Service
public class LlmFactCardTerminalUnitFieldAliasEnricher ...
```

选项 B：在 `@Configuration` 类中增加 `@Bean` 方法显式创建。

**修复后需要重新 runtime smoke 验证**：确认 bean 被创建、route resolution 成功、LLM 被调用、alias 被合并。

### 6.2 其他发现

`AdminLlmConfigController.SCENE_ROLE_OPTIONS` 硬编码了允许的 agent role 列表，不包含 `field-alias-enricher`。这导致无法通过 API 正常创建绑定（只能通过 DB 绕过）。建议在正式 rollout 时将 `field-alias-enricher` 加入白名单。

### 6.3 建议流程

1. agentA 将 `LlmFactCardTerminalUnitFieldAliasEnricher` 改为 `public`
2. agentA 可选：将 `field-alias-enricher` 加入 `SCENE_ROLE_OPTIONS`
3. agentD 重新执行 runtime smoke
4. smoke 通过后进入 clean schema 端到端验证

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- `docs/模型绑定配置参考.md` 存在本地配置脏改动，未读取、未引用、未修改
- DB 写入仅为临时测试绑定（agent_model_bindings 1 行 INSERT），停止服务后不影响生产
- 未 stage、未 commit、未 push
- 未清库、未重建 schema、未导入全量资料
- 未跑业务 eval
- 未输出 API key、token、password
- 本轮新增报告：本文件
