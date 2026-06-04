# FG1 Field-Alias-Enricher Bootstrap Guard — 修复结果报告

修复时间：2026-06-03
执行人：agentA（代码执行 Agent）
前置审计：agentB 只读审计报告 `fg1_field_alias_enricher_candidate_supply_readonly_analysis_report.md`

---

## 1. 根因

**断点在 `LlmFactCardTerminalUnitFieldAliasEnricher.isLlmRouteAvailable()` 第 189 行。**

compile 清库后 `agent_model_bindings` 被清空，`freezeSnapshots()` 无法冻结 snapshot。`LlmGatewayRouteSupport.resolveScopedRoute()` 在 compile 场景合法回退到 bootstrap route——这是正确的 fallback 行为。

但 enricher 额外要求：
```java
if (routeResolution.getBindingId() == null && !routeResolution.isSnapshotBacked()) {
    return false;
}
```

bootstrap route 天然是 `bindingId=null && isSnapshotBacked=false`，因此 enricher 在 `isLlmRouteAvailable()` 处静默返回 `false`，不执行别名增强逻辑。

**这是 enricher 内部 guard 与 LlmGateway bootstrap fallback 策略不一致的问题，不是 binding 配置错误，不是 LLM 调用失败。**

---

## 2. 为什么这不是 binding 配置问题

`LlmGatewayRouteSupport.resolveScopedRoute()` 在 snapshot 缺失时主动回退到 bootstrap route——这是设计行为。bootstrap route 携带有效的 `modelName`、`baseUrl`、`apiKey`（来自 YAML 配置），足以完成 LLM 调用。Writer/Reviewer/Fixer 均直接使用 bootstrap route，不受此 guard 影响。

问题不在"为什么没有 binding"，而在"enricher 不应该在 LlmGateway 认为路由可用的前提下再额外拒绝 bootstrap route"。

---

## 3. 为什么这不是 builder 问题

FG1 的 `late_fee_per_day` 终端 unit 未进入 builder 候选池——不是因为 builder 排序错误，而是因为 enricher 未生成中文别名，导致该字段在 DB LIKE 评分中失去所有 CJK 贡献（`field_aliases_json` 和 `fts_text` 均无中文内容），排名跌出 `rawLimit=24` 窗口。builder 不可能选择一个它看不到的候选。

因果链：
```
清库 → bindings 空 → freezeSnapshots() 空 → resolveRoute() 回退 bootstrap
  → isLlmRouteAvailable() 拒绝 bootstrap → enricher 静默跳过
    → 中文别名 0 → DB LIKE 评分极低 → 排名跌出 rawLimit 窗口
      → 未进入 Java reranker / RRF / builder → builder 候选池无 late_fee_per_day
```

---

## 4. 修复内容

**文件**：`src/main/java/com/xbk/lattice/compiler/service/LlmFactCardTerminalUnitFieldAliasEnricher.java`

**修改方法**：`isLlmRouteAvailable()`（一个方法）

**变更**：删除第 189-191 行的过严 guard：
```java
// 删除以下 3 行：
if (routeResolution.getBindingId() == null && !routeResolution.isSnapshotBacked()) {
    return false;
}
```

**保留**：第 192-195 行的 modelName 有效性检查：
```java
String modelName = routeResolution.getModelName();
return hasText(modelName)
        && !"fallback".equalsIgnoreCase(modelName.trim())
        && !"unknown".equalsIgnoreCase(modelName.trim());
```

**修复逻辑**：
- 接受"合法且 modelName 有效"的 bootstrap fallback 路由
- 继续保留对无效 modelName（fallback/unknown/blank）的拒绝
- 不改变 scoped snapshot route 的既有优先级
- 不改变 compile/query/deep_research 的整体路由策略

---

## 5. 门禁验证

| 门禁 | 结果 |
|---|---|
| redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 6. 各 role/path 影响审计

| Role | 调用路径 | 是否受影响 | 说明 |
|---|---|---|---|
| Writer | `LlmGateway.generateTextWithScope()` → `resolveScopedRoute()` | **不受影响** | 不经过 `isLlmRouteAvailable()` |
| Reviewer | `LlmGateway.generateTextWithScope()` → `resolveScopedRoute()` | **不受影响** | 同上 |
| Fixer | `LlmGateway.generateTextWithScope()` → `resolveScopedRoute()` | **不受影响** | 同上 |
| field-alias-enricher (snapshot) | `doEnrich()` → `isLlmRouteAvailable()` | **不变** | snapshot route 天然满足条件，不受此变更影响 |
| field-alias-enricher (bootstrap) | `doEnrich()` → `isLlmRouteAvailable()` | **从拒绝变为通过** | 修复目标 |
| deep_research | `LlmGateway.generateText()` | **不受影响** | deep_research 在自己的 routeResolutionFor 调用中处理，不经过 enricher |

---

## 7. 修复范围说明

- 只修改了 `isLlmRouteAvailable()` 一个方法
- 未修改 `ExecutionLlmSnapshotService`
- 未修改 `LlmGatewayRouteSupport`
- 未修改 `AnswerFallbackConclusionBuilder`
- 未修改 retrieval/reranker/candidate supply
- 未修改 tests、scripts、prompt、config、题集
- 未扩大至 FQ4

---

## 8. 下一步

**只交给 agentD 做 FG1-only runtime gate**：

1. 清库（`bash scripts/reset-lattice-schema.sh`）
2. 上传 Fresh Eval 2 资料并编译
3. 确认 terminal unit 的 `field_aliases_json` 包含中文别名
4. 确认 FG1 的 `late_fee_per_day` 进入 builder 候选池
5. 仅当以上确认后，再跑 FG1 query 验证

**禁止** agentD 在 FG1 验证通过前跑完整 Public Eval。

---

## 9. 明确声明

- [x] 只修改了 `LlmFactCardTerminalUnitFieldAliasEnricher.java` 一个文件
- [x] 只修改了 `isLlmRouteAvailable()` 一个方法
- [x] 未修改 `ExecutionLlmSnapshotService`
- [x] 未修改 `LlmGatewayRouteSupport`
- [x] 未修改 `AnswerFallbackConclusionBuilder`
- [x] 未修改 retrieval/reranker/candidate supply
- [x] 未修改 tests、scripts、prompt、config、题集
- [x] 未扩大至 FQ4
- [x] 未引入业务词/字段名特判
- [x] redline `BLOCKER=0`
- [x] mvn test `995/0/0/0, BUILD SUCCESS`
- [x] 未提交 commit
