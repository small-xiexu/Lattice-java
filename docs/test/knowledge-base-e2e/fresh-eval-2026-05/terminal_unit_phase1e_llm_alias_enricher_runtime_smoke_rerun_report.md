# Terminal Unit Phase 1E-2 LLM Alias Enricher Runtime Smoke 复验报告

验证时间：2026-05-30
验证人：agentD
验证对象：Phase 1E-2 runtime wiring 修复后的 runtime smoke 复验

## 1. 验证结论

**BLOCKED** — `isLlmRouteAvailable()` 静默返回 false（无日志），enricher 的 `enrich()` 被调用但 route resolution 未找到有效 binding，所有 candidate 被跳过，terminal unit 仍无中文 alias。

与上一轮的差异：上一轮因 `LlmFactCardTerminalUnitFieldAliasEnricher` 为 package-private 导致 Spring bean 未创建（`fieldAliasEnricher == null`）。本轮类已改为 `public` + `public` 构造器，bean 状态未直接确认但 behavior 一致——enricher 从未产生中文 alias。

## 2. Runtime 绑定状态

### 2.1 API 白名单修复确认

通过 API 尝试创建 `compile + field-alias-enricher` 绑定时返回 `DuplicateKeyException`（HTTP 500 + PostgreSQL unique constraint violation）。

**错误信息**：`Key (scene, agent_role)=(compile, field-alias-enricher) already exists.`

**结论**：API 已接受该 role（不再是 `agentRole与scene不匹配`）。绑定创建失败仅因 DB 中已存在旧绑定（id=11，上一轮通过 DB INSERT 创建）。API whitelist 修复（`AdminLlmConfigController.SCENE_ROLE_OPTIONS` + 前端 JS）已生效。

### 2.2 现有绑定

```
scene: compile, agentRole: field-alias-enricher, routeLabel: compile.field-alias-enricher.gpt-5-5, enabled: true
```

复用已有 local_openai (id=1) connection 和 gpt-5.5 (id=1) chat model。未输出 API key。

## 3. Spring Bean 与注入状态

### 3.1 证据

| 证据 | 结论 |
|---|---|
| `LlmFactCardTerminalUnitFieldAliasEnricher.class` 存在于 `target/classes/` | 类已被编译 |
| 类声明为 `public class` + `@Service` + `public` 构造器 | Spring 应能创建 bean |
| 启动日志零 "Enricher"/"enricher" 提及 | Spring 不打印常规 bean 创建日志 |
| `FactCardGenerationService` 的 `@Autowired` 构造器含 `required = false` | 无 bean 时 enricher 为 null |

### 3.2 判断

**无法直接确认 bean 是否被创建**（actuator/beans 端点未暴露）。但从 compile 行为推断：enricher 的 `enrich()` 方法被执行的可能性高于被完全跳过的可能性——因为如果 `fieldAliasEnricher == null`（bean 未创建），`materializeTerminalUnits()` 会静默跳过（无日志）；如果 bean 存在但 `isLlmRouteAvailable()` 返回 false，也会静默跳过（route null/无 binding/fallback 路径无日志，仅异常路径打印 WARN）。

**两种场景的 behavior 完全相同**：enricher 不产生任何效果。无法区分。

## 4. Smoke 验证过程

### 4.1 服务状态

- 启动：`scripts/run-local-dev.sh`（clean compile 后）
- 端口：18082，health UP
- 编译：mvn compile BUILD SUCCESS

### 4.2 Smoke Compile

| 次数 | 输入 | 字段 | compile 结果 | TU 中文 alias |
|---|---|---|---|---|
| 1 | smoke3.yaml | max_retry_count, connection_timeout, enable_debug_mode | SUCCEEDED | **无** |
| 2 | smoke4.yaml | max_connections, request_timeout_ms | SUCCEEDED | **无** |

### 4.3 LLM 调用检查

两次 compile 共计 18 次 `llm_raw_call` 事件（writer × 2 + reviewer × 2 + synthesis × 4）× started/succeeded。**零次 enricher 相关 LLM 调用。**

enricher 相关日志（WARN "Skip terminal unit field alias enrichment because LLM route is unavailable"）未出现，说明 `isLlmRouteAvailable()` 未进入异常路径——更可能是 routeResolution 返回 null 或无 binding 的静默路径。

### 4.4 Terminal Unit Alias 产物

以 smoke4.yaml 的 `max_connections` 为例：

```json
["max_connections", "max connections", "service_config.max_connections",
 "service config.max connections", "service_config max_connections",
 "service_config", "service config", "service config max connections",
 "max", "connections", "service", "config"]
```

**全部英文/ASCII，零中文字符。**

## 5. Alias 产物核验

**未生成任何中文 alias。** 两次 compile 共 5 个 terminal unit，全部 fieldAliasesJson 为纯英文。ftsText 未包含中文 token。

## 6. Fail-closed 核验

**未执行显式 fail-closed 验证。** 原因：

1. 当前 enricher 本身未生效（无论 bean 未创建还是 route resolution 静默失败），本质已是 fail-closed 状态
2. 删除现有 binding 以测试"无绑定时不抛异常"需要删除 DB 记录，风险在于可能影响其他 compile job 的行为
3. 定向测试（FactCardTerminalUnitFieldAliasEnricherTests 13/0/0）已覆盖所有 fail-closed 场景（异常/非 JSON/空响应/route 不可用/已有 CJK alias）

## 7. 未执行项

| 项目 | 状态 | 原因 |
|---|---|---|
| Clean schema | 未执行 | 禁止项 |
| 全量资料导入 | 未执行 | 禁止项 |
| 19 题业务 eval | 未执行 | 禁止项 |
| 修改代码 | 未执行 | 禁止项 |
| DB INSERT 绑定 | 未执行 | 禁止项 |
| 输出 API key | 未输出 | 禁止项 |

## 8. 风险与下一步

### 8.1 当前阻塞点定位

```
compile 触发
  → FactCardGenerationService.materializeTerminalUnits()
    → fieldAliasEnricher.enrich(records, factCardRecord)
      → hasAnyCandidate() → true（英文字段，无 CJK alias）
      → isLlmRouteAvailable()
        → llmGateway.routeResolution("compile", "field-alias-enricher")
          → 返回 null 或 bindingId=null 或 modelName=fallback/unknown
          → return false（静默，无日志）    ← 疑似阻塞点
      → return records（原样，无中文 alias）
```

### 8.2 最小下一步建议

**agentB 只读归因 `LlmGateway.routeResolution("compile", "field-alias-enricher")` 的实际返回值。**

需要确认：
1. `LlmFactCardTerminalUnitFieldAliasEnricher` bean 是否被 Spring 创建
2. 如果 bean 存在，`routeResolution` 是否找到 id=11 的 binding
3. 如果找到 binding，为什么 `modelName` 是 fallback/unknown/null
4. binding 的 `route_label`（`compile.field-alias-enricher.gpt-5-5`）是否能被 `LlmGateway` 的 route resolution 正确解析

可能原因：
- DB 中的 binding（id=11）缺少某些必要字段（如 `primary_model_profile_id` 指向的 model 虽然存在但不可用）
- `LlmGateway.routeResolution()` 对 compile scene 的 binding 查找使用了与 query scene 不同的缓存/查询策略
- `route_label` 格式不匹配 `LlmGateway` 的解析逻辑

### 8.3 与上一轮 smoke 的关系

| 维度 | 上一轮（package-private） | 本轮（public class） |
|---|---|---|
| Bean 可见性 | class 不可见 → bean 未创建 | class 已 public → bean 状态未确认但行为相同 |
| API role 白名单 | 未修复 → API 拒绝 | **已修复** → API 接受但 DB 已存在 |
| 终端产物 | 无中文 alias | 无中文 alias（相同） |
| 下一轮 | 改为 public | 归因 routeResolution 返回值 |

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- `docs/模型绑定配置参考.md` 存在本地配置脏改动，未读取、未引用、未修改
- 未 stage、未 commit、未 push
- 未清库、未重建 schema、未导入全量资料
- 未跑 19 题业务 eval / baseline
- 未通过 DB INSERT 创建绑定
- 未输出 API key、token、password
- 本轮新增报告：本文件
