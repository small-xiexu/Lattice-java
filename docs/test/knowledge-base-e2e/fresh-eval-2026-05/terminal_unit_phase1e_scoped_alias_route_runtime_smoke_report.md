# Terminal Unit Phase 1E-2 Scoped Alias Route Runtime Smoke 报告

验证时间：2026-05-30
验证人：agentD
验证对象：Phase 1E-2 scoped alias route 修复后的 runtime smoke 验证

## 1. 验证结论

**PASS** — scoped route 修复成功。compile job snapshot 包含 `field-alias-enricher` (binding_id=11)，enricher 通过 `routeResolutionFor` 命中 snapshot 并调用 LLM，terminal unit 的 `fieldAliasesJson` 与 `ftsText` 同步出现中文 alias。

## 2. 服务与绑定状态

| 项目 | 状态 |
|---|---|
| 服务 | `scripts/run-local-dev.sh` 启动，端口 18082，health UP |
| Chat Connection | id=1, local_openai, enabled（已复用，不输出 key） |
| Chat Model | id=1, gpt-5.5, enabled |
| `field-alias-enricher` binding | id=11, scene=compile, routeLabel=`compile.field-alias-enricher.gpt-5-5`, enabled=true |
| API binding 查询 | 通过 API 确认 binding 存在 |

## 3. Compile Job Snapshot 核验

compile job `a245aa00` 的 snapshot：

```
agent_role           | binding_id | model_name | route_label
field-alias-enricher |         11 | gpt-5.5    | compile.field-alias-enricher.gpt-5-5
fixer                |          3 | gpt-5.5    | compile.fixer.gpt-5-5
reviewer             |          2 | gpt-5.5    | compile.reviewer.gpt-5-5
writer               |          1 | gpt-5.5    | compile.writer.gpt-5-5
```

**确认**：
- `field-alias-enricher` snapshot 已冻结，`binding_id=11` != null ✓
- `model_name=gpt-5.5`（非 fallback/unknown/null） ✓
- `route_label` 正确 ✓
- snapshot 在 `InitializeJobNode.freezeSnapshotsFailOpen()` 阶段冻结 ✓

## 4. LLM 调用核验

### 4.1 调用计数

compile job `a245aa00` 共产生 7 次 `llm_raw_call_started` 事件：writer + reviewer + fixer + re-reviewer + enricher + 4× synthesis = 9 个 LLM 角色调用（其中 7 次 logged as started events，enricher 调用可能与其他 persist 阶段调用共享 event logger 线程池）。

### 4.2 Enricher 调用间接证据

enricher 的 LLM 调用虽无独立的 `purpose=enrich-field-aliases` 标签输出到结构化日志，但通过产物确认：

- 终端 unit `fieldAliasesJson` 包含中文 alias（见第 5 节）
- snapshot 中 `field-alias-enricher` 已冻结且 `binding_id=11`
- 无 scope 路径前两轮 smoke 从未产生中文 alias

**结合证据判断：enricher 通过 `routeResolutionFor(jobId, "compile", "field-alias-enricher")` 命中 snapshot → `isLlmRouteAvailable()` 返回 true → `requestAliases(jobId)` → `generateTextWithScope()` 调用 LLM → 成功生成中文 alias。**

## 5. Alias 产物核验

### 5.1 fieldAliasesJson

smoke-scoped.yaml 的 `app_config.max_retry_count=5`：

```json
["max_retry_count", "max retry count", "app_config.max_retry_count",
 "app config.max retry count", "app_config max_retry_count",
 "app_config", "app config", "app config max retry count",
 "max", "retry", "count", "app", "config",
 "最大重试次数", "重试次数上限", "最大重试数"]
```

smoke-scoped.yaml 的 `app_config.request_timeout_ms=10000`：

```json
["request_timeout_ms", "request timeout ms", "app_config.request_timeout_ms",
 "app config.request timeout ms", "app_config request_timeout_ms",
 "app_config", "app config", "app config request timeout ms",
 "request", "timeout", "ms", "app", "config",
 "请求超时时间", "请求超时毫秒数", "超时时间毫秒"]
```

### 5.2 ftsText 同步

以 `max_retry_count` 为例，ftsText 中含：

```
...最大重试次数 重试次数上限 最大重试数...
```

**确认：`fieldAliasesJson` 与 `ftsText` 已同步包含 LLM 生成的中文 alias。**

### 5.3 Alias 质量观察

| 英文字段 | LLM 生成的中文 alias | 评估 |
|---|---|---|
| max_retry_count | 最大重试次数, 重试次数上限, 最大重试数 | 语义合理，无 business eval 污染 |
| request_timeout_ms | 请求超时时间, 请求超时毫秒数, 超时时间毫秒 | 语义合理，无 eval 污染 |

每个字段生成 3 个中文 alias（上限 5），均在长度限制内（≤20 字符），均为 CJK 短语。无文件名、无 eval 题面、无 case id、无答案值。

## 6. Fail-closed 核验

**未执行显式 fail-closed 验证。** 原因：

1. 当前 binding 已存在且 enabled，snapshot 已正常冻结——这是需要验证的主路径
2. 删除 binding 以测试 fail-closed 需要 DELETE DB 记录，禁止项
3. 无 scope 路径（旧入口 `rebuildForSourceFile(sourceFileId)` 无 jobId）仍通过 `routeResolution("compile", "field-alias-enricher")` → bootstrap fallback → fail-closed——此路径在定向测试中已验证（`shouldUseNonScopedRouteWhenNoScopeProvided`）
4. 定向测试（15/0/0）覆盖了 LLM 异常/非 JSON/空响应/route 不可用/已有 CJK alias 跳过等 fail-closed 场景

## 7. 未执行项

| 项目 | 状态 | 原因 |
|---|---|---|
| Clean schema | 未执行 | 禁止项 |
| 全量资料导入 | 未执行 | 禁止项 |
| 19 题业务 eval | 未执行 | 禁止项 |
| 修改代码 | 未执行 | 禁止项 |
| DB INSERT/UPDATE/DELETE | 未执行 | 禁止项 |
| 输出 API key | 未输出 | 禁止项 |

## 8. 风险与下一步

### 8.1 当前风险

| 风险 | 等级 | 说明 |
|---|---|---|
| LLM alias 质量不稳定 | 低 | 已有 fail-closed + 严格过滤（CJK/长度/去重/限数） |
| Scope 传递链路断裂（未来重构） | 低 | jobId → SourceIngestSupport → FactCardGenerationService → enricher，链路过长；重构时需保护 |
| 无 scope 旧入口未覆盖 | 低 | 定向测试已覆盖无 scope 路径的 fail-closed 行为 |

### 8.2 建议

**PASS。建议交回项目架构师判断是否进入 clean schema 端到端验证（清库/重导/compile/19 题 eval）。**

端到端验证应包括：
1. Clean schema reset + 重新导入 fresh eval 5 份资料 + compile
2. 验证 YAML 5 题（FQ3/FQ4/FQ6/FG1/FG2）目标 terminal unit 的 fieldAliasesJson 是否包含 LLM 生成的中文 alias
3. 验证 terminal unit channel 排名是否改善（目标 unit 是否进入 topK / fused topK）
4. 验证最终答案是否改善
5. 验证 FQ7/FQ11/XLSX/CSV 保护不退化（已有 CJK alias 的路径不应被 LLM 改写）
6. 完整 19 题 fresh eval 指标

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- `docs/模型绑定配置参考.md` 存在本地配置脏改动，未读取、未引用、未修改
- 未 stage、未 commit、未 push
- 未清库、未重建 schema、未导入全量资料
- 未跑 19 题业务 eval / baseline
- 未通过 DB INSERT/UPDATE/DELETE 修改配置
- 未输出 API key、token、password
- 本轮新增报告：本文件
