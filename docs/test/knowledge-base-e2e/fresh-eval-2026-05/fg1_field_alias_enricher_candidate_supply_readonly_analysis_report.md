# FG1 Field-Alias-Enricher / Terminal Unit 候选供给侧 — 只读审计报告

审计时间：2026-06-03
执行人：agentB（治理/链路分析 Agent）
类型：只读分析，无代码修改

---

## 1. 本轮目标

只读审计 FG1 的 field-alias-enricher / terminal unit 候选供给侧，回答核心问题：

**为什么同样是 Fresh Eval 2，上一轮 runtime 里 `late_fee_per_day` 已进入 builder 候选池且存在中文别名，而本轮 runtime 里：**
1. `late_fee_per_day` 完全没进 builder 候选池
2. 118 个 terminal units 的中文 `fieldAliases` 为 0
3. 最终只剩 `type=精密仪器` 一类候选进了 builder

---

## 2. 已知前提事实（不再重判）

| # | 事实 | 来源 |
|---|------|------|
| 1 | FG1 的 `qf=false` 修复已 runtime 生效（`late_fee_per_day` 的 `qf` 从 false→true, `tuQfPassed` 0→4） | `fg1_qf_false_builder_runtime_gate_report.md` |
| 2 | `countFieldLevelTokenMatches()` CJK bigram 修复已 runtime 生效（`精密仪器.type` 的 `ftmc` 0→2） | `fg1_ftmc_zero_builder_runtime_gate_report.md` |
| 3 | 上一轮（qf 修复后）`late_fee_per_day` 在 builder 候选池中（cand#5/#6），中文别名存在 | `fg1_qf_false_builder_runtime_gate_report.md` |
| 4 | 本轮 `late_fee_per_day` 完全未进入 builder 候选池（tuTotal=4，无 late_fee_per_day），中文别名 0 条 | `fg1_ftmc_zero_builder_runtime_gate_report.md` |
| 5 | Field-alias-enricher 在独立审计轮（jobId=`a636416c`）中确认正常运行，中文别名已生成 | `fq4_fg1_field_alias_enricher_runtime_audit_report.md` |
| 6 | 每通道 limit=10 截断在更早轮次已被识别为候选供给侧瓶颈 | `fq4_fg1_terminal_channel_limit_root_cause_analysis_report.md` |
| 7 | FQ4 不在本轮处理范围 | 本轮任务描述 |

---

## 3. 只读审计范围

### 源码层（已读）

| 文件 | 关键内容 |
|------|----------|
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | `doEnrich()` / `isLlmRouteAvailable()` / `shouldEnrich()` guardrails |
| `FactCardGenerationService.java` | `@Autowired(required = false)` 注入 + `materializeTerminalUnits()` 调用点 |
| `ExecutionLlmSnapshotService.java` | `freezeSnapshots()` / `resolveRoute()` / `bootstrapRoute()` 完整链路 |
| `LlmGatewayRouteSupport.java` | `resolveScopedRoute()` 的 snapshot→bootstrap fallback 逻辑 |
| `FactCardTerminalUnitFtsSearchService.java` | `search()` 的 rawLimit 扩展 + rerank + truncate |
| `FactCardTerminalUnitIntentReranker.java` | 字段意图信号 scoring |
| `FactCardTerminalUnitMapper.xml` | `searchLexical` SQL 的 LIKE 评分 + WHERE 条件 |

### 报告层（已读）

| 报告 | 关键信息 |
|------|----------|
| `fg1_qf_false_builder_runtime_gate_report.md` | 上一轮 jobId=`868e0ff9`，late_fee_per_day 在候选池，中文别名存在，tuTotal=7，123 TU |
| `fg1_ftmc_zero_builder_runtime_gate_report.md` | 本轮清库后，late_fee_per_day 不在候选池，中文别名 0 条，tuTotal=4，118 TU |
| `fq4_fg1_field_alias_enricher_runtime_audit_report.md` | jobId=`a636416c`，enricher 正常运行，中文别名已入库 |
| `fq4_fg1_terminal_channel_candidate_supply_runtime_gate_report.md` | jobId=`69a59fd5`，candidate supply 修订未修复，中文别名确认存在 |
| `fq4_fg1_terminal_channel_limit_root_cause_analysis_report.md` | SQL 复算确认目标字段排 14-17/15-17，每通道 limit=10 截断 |

### 配置/脚本层（已读）

| 文件 | 关键内容 |
|------|----------|
| `scripts/reset-lattice-schema.sh` | `DROP SCHEMA IF EXISTS lattice CASCADE` |
| `src/main/resources/db/schema.sql` | `agent_model_bindings` 表定义，无 INSERT |
| `config/lattice-llm.yml` | `config-source: hybrid`, `bootstrap-enabled: true` |

---

## 4. 关键运行时差异对比

| 维度 | 上一轮（qf 修复后） | 本轮（ftmc 修复后） |
|------|---------------------|---------------------|
| jobId | `868e0ff9-f900-4fd9-84f9-4b5526a207b2` | （ftmc gate 报告未列出，但确认清库后新编译） |
| Schema Reset | **未执行** | **已执行** `bash scripts/reset-lattice-schema.sh` |
| `agent_model_bindings` 状态 | **存在**（含 compile.field-alias-enricher, binding_id=4） | **空表**（被 DROP CASCADE 清除，无种子数据恢复） |
| `execution_llm_snapshots` | **存在**（从 bindings 冻结） | **空**（freezeSnapshots 找不到 bindings） |
| enricher `isLlmRouteAvailable()` | **true**（snapshot 有 bindingId=4, isSnapshotBacked=true） | **false**（bootstrap route: bindingId=null, isSnapshotBacked=false） |
| enricher 实际执行 | **执行**（LLM 调用成功，生成中文别名） | **静默跳过**（doEnrich 在 isLlmRouteAvailable 处 return） |
| 中文 fieldAliases | **存在**（"逾期日费"、"每日逾期费用"等） | **0 条** |
| terminal unit 总数 | 123 | 118（PDF 上传失败 -5） |
| `late_fee_per_day` 在 builder 候选池 | **是**（cand#5/#6, qf=true, ftmc=0） | **否**（tuTotal=4，无 late_fee_per_day） |
| builder tuTotal | 7 | 4 |
| `精密仪器.type` ftmc | 0 | 2（CJK bigram 修复生效） |

---

## 5. Field-Alias-Enricher 审计结论

### 5.1 完整调用链追踪

```
CompileGraph (InitializeJobNode)
  → freezeSnapshotsFailOpen("compile_job", jobId, "compile")
    → ExecutionLlmSnapshotService.freezeSnapshots()
      → 查询 execution_llm_snapshots (空，表刚被 DROP CASCADE 重建)
      → configSource="hybrid"，非 "properties"，继续
      → 查询 agent_model_bindings WHERE scene='compile' AND enabled=true
      → **空结果**（表被 DROP CASCADE 清除，无种子数据）
      → bindings.isEmpty() → compile 非 strict → return List.of()
      → **0 个 snapshot 被冻结**

... (compile continues, fact cards generated) ...

FactCardGenerationService.materializeTerminalUnits(factCardRecord, scopeId=jobId)
  → fieldAliasEnricher != null (LlmFactCardTerminalUnitFieldAliasEnricher @Service 正常注入)
  → fieldAliasEnricher.enrich(terminalUnitRecords, factCardRecord, jobId)
    → doEnrich(records, scopeId=jobId)
      → records=118, 非空 ✓
      → hasAnyCandidate() → shouldEnrich() → YAML 英文字段通过 ✓
      → **isLlmRouteAvailable(jobId)**
        → llmGateway.routeResolutionFor(jobId, "compile", "field-alias-enricher")
          → resolveScopedRoute()
            → resolveRoute("compile_job", jobId, "compile", "field-alias-enricher")
              → 查询 execution_llm_snapshots → **空**
              → compile 非 strict → return Optional.empty()
            → snapshot 为空 → isBootstrapAllowed("compile") = true
            → **回退到 bootstrapRoute("compile", "field-alias-enricher")**
              → agentRole != "reviewer" → 使用 compileModel
              → 返回 LlmRouteResolution(
                  bindingId=null,
                  isSnapshotBacked=false,  ← 关键！
                  modelName=<compile model from YAML>
                )
        → 回到 isLlmRouteAvailable():
            routeResolution.getBindingId() == null  → TRUE
            !routeResolution.isSnapshotBacked()     → TRUE
            → **return false**  ← 根因断点！
      → doEnrich: return records (原始记录，未增强)
```

### 5.2 根因定位

**断点在 `LlmFactCardTerminalUnitFieldAliasEnricher.isLlmRouteAvailable()` 第 189 行：**

```java
if (routeResolution.getBindingId() == null && !routeResolution.isSnapshotBacked()) {
    return false;
}
```

这个 guard 要求路由解析结果必须满足以下至少一项：
- `bindingId != null`（来自数据库 agent_model_bindings）
- `isSnapshotBacked == true`（来自 execution_llm_snapshots）

但 `bootstrapRoute()` 返回的路由两项都不满足：
- `bindingId = null`（bootstrap 没有数据库绑定 ID）
- `isSnapshotBacked = false`（bootstrap 不是从 snapshot 来的）

**而 `LlmGatewayRouteSupport.resolveScopedRoute()` 认为 bootstrap 路由是合法的 fallback**——它在 snapshot 缺失时主动回退到 bootstrap。但 enricher 自己的 `isLlmRouteAvailable()` 又拒绝了 bootstrap 路由。

这是 **enricher 内部 guard 与 LlmGateway 的 bootstrap fallback 策略不一致**，不是 LLM 调用失败、不是 binding 配置错误、不是 enricher 没被调用。

### 5.3 为什么 Writer/Reviewer/Fixer 不受影响

Writer、Reviewer、Fixer 在 `LlmGateway` 中调用 `generateText()` / `generateTextWithScope()` 时，不经过 `isLlmRouteAvailable()` 这个额外 guard。它们直接使用 `resolveScopedRoute()` 返回的 bootstrap 路由，bootstrap 路由中的 `modelName`、`baseUrl`、`apiKey` 来自 YAML 配置，足够完成 LLM 调用。

只有 enricher 在 `doEnrich()` 中额外调用了 `isLlmRouteAvailable()` 并拒绝了 bootstrap 路由。

### 5.4 `shouldEnrich()` 候选筛选不是根因

`shouldEnrich()` 筛选规则（fieldLabel/terminalKey 不含 CJK + fieldAliasesJson 不含 CJK + 含 Latin）对于 YAML 英文字段是正确的——这些字段确实需要 LLM 生成中文别名。问题不在"筛掉了不该筛的"，而在"筛出来的候选没有被 enricher 处理"。

---

## 6. Terminal Unit 候选供给侧审计结论

### 6.1 中文别名缺失对 DB 评分的影响

`FactCardTerminalUnitMapper.searchLexical` 的 SQL 评分公式中，每个 LIKE token 对 `field_aliases_json` 贡献 3.0 分，对 `fts_text` 贡献 2.0 分：

```sql
+ case when lower(unit.field_aliases_json::text) like #{likeToken} then 3.0 else 0 end
+ case when lower(unit.fts_text) like #{likeToken} then 2.0 else 0 end
```

FG1 查询的 CJK LIKE token 包括 `"逾期"`、`"罚金"`、`"精密"` 等。在没有中文别名的情况下：

| 字段 | `field_aliases_json` 匹配 "逾期" | `fts_text` 匹配 "逾期" | 其他 LIKE 匹配 |
|------|----------------------------------|----------------------|----------------|
| `late_fee_per_day = 5` | **0**（无中文别名） | **0**（fts_text 无中文） | display_text/value_text 不匹配 CJK |
| `equipment_types[1].type = 精密仪器` | N/A | N/A | display_text="精密仪器" **直接匹配** "精密" |

`late_fee_per_day` 在 DB 评分中失去所有 CJK LIKE 贡献，而 `精密仪器.type` 通过 value/display_text 的自然中文内容获得高 LIKE 评分。这导致 `late_fee_per_day` 的 DB 排名进一步下降，在本轮的 rawLimit=24 窗口内无法进入，被截断在 Java 侧之前。

### 6.2 候选供给侧不是独立根因

上一轮的 channel limit 分析报告中目标字段排 14-17 位，那是**有中文别名的情况下**。本轮中文别名为 0，`late_fee_per_day` 的 DB 评分更低，排名更靠后。

候选供给侧（DB limit 截断）是**下游症状**，不是独立根因。根因在上游：enricher 未生成中文别名 → DB 评分过低 → 被截断。

**如果 enricher 正常运行并生成中文别名，`late_fee_per_day` 至少能恢复到上一轮的排名（15-17），并在 rawLimit=24 窗口内进入 Java 侧。** 候选供给侧的 limit 截断问题仍然存在（15/17 > 10），但那是另一个需要独立处理的问题，不影响本轮核心判断。

---

## 7. 对"为什么 `late_fee_per_day` 没进 builder 池"的最终判断

### 结论类型：**A**

**根因主要在 field-alias-enricher 未生效（被 `isLlmRouteAvailable()` 拒绝 bootstrap 路由），builder 不应再继续修改。**

### 完整因果链

```
reset-lattice-schema.sh 清库
  → agent_model_bindings 被清除
    → freezeSnapshots() 无法冻结快照
      → resolveRoute() 回退到 bootstrapRoute()
        → bootstrapRoute() 返回 bindingId=null, isSnapshotBacked=false
          → isLlmRouteAvailable() 拒绝此路由
            → enricher 静默跳过，0 中文别名
              → late_fee_per_day 的 field_aliases_json / fts_text 无中文
                → DB LIKE 评分极低（CJK token 无法匹配英文内容）
                  → DB 排名跌出 rawLimit=24 窗口
                    → 被截断，未进入 Java reranker / RRF / builder
                      → builder 候选池无 late_fee_per_day
```

### 为什么上一轮正常

上一轮（jobId=`868e0ff9`）**未执行 schema reset**，`agent_model_bindings` 表中存在 `compile.field-alias-enricher` 绑定（binding_id=4，enabled=true）。`freezeSnapshots()` 从绑定创建了快照，`resolveRoute()` 找到了 `isSnapshotBacked=true` 的快照，`isLlmRouteAvailable()` 通过。

---

## 8. 明确排除项

| 假设 | 结论 | 证据 |
|------|------|------|
| enricher 未被调用 | **排除** | `FactCardGenerationService.materializeTerminalUnits()` 中 `fieldAliasEnricher != null`，`doEnrich()` 被调用，在 `isLlmRouteAvailable()` 处 return |
| enricher 的 `@Autowired(required = false)` 导致注入为 null | **排除** | `LlmFactCardTerminalUnitFieldAliasEnricher` 标注 `@Service`，构造器依赖均存在，bean 正常创建 |
| LLM 调用失败 | **排除** | 根本没走到 `requestAliases()`——在 `isLlmRouteAvailable()` 就返回了 |
| `shouldEnrich()` 筛掉了所有候选 | **排除** | YAML 英文字段（late_fee_per_day, type, approval_required 等）满足 shouldEnrich 条件（无 CJK + 有 Latin） |
| `freezeSnapshots()` 抛出异常 | **排除** | compile 场景的 freezeSnapshots 是 fail-open，空 bindings 返回空列表，不抛异常 |
| configSource="properties" 导致跳过 JDBC | **排除** | 默认值为 `"hybrid"`，会查询 JDBC bindings |
| bootstrapEnabled=false | **排除** | 默认值为 `true` |
| PDF 上传失败影响 FG1 | **排除** | YAML 文件（equipment-borrowing-policy.yaml）上传成功，FG1 只依赖此 YAML；PDF 失败只影响 terminal unit 总数（123→118），与中文别名无关 |
| 候选供给侧 limit=10 是独立根因 | **排除为独立根因** | limit 截断在本轮是下游症状——中文别名缺失导致 DB 评分更低，目标字段甚至可能未进入 rawLimit=24 |
| builder 排序逻辑问题 | **排除** | 目标候选根本没进入 builder 候选池，排序规则无论怎么改都选不到 |

---

## 9. 补证需求

以下链路已完全闭合，不需要额外补证：

- [x] enricher 调用链完整追踪（从 InitializeJobNode → freezeSnapshots → doEnrich → isLlmRouteAvailable）
- [x] bootstrap 路由与 enricher guard 的不兼容性已确认（源码级别）
- [x] schema reset 与 binding 清除的因果关系已确认
- [x] 中文别名缺失 → DB 评分降低 → 候选池缺失的因果链已建立
- [x] 与上一轮的差异已解释（schema reset 有无）

---

## 10. 下一轮唯一最小动作建议

### 交给：agentA（代码修复）

### 最小动作：

**修改 `LlmFactCardTerminalUnitFieldAliasEnricher.isLlmRouteAvailable()` 第 189 行的 guard 条件，使其接受合法的 bootstrap fallback 路由。**

当前代码（`LlmFactCardTerminalUnitFieldAliasEnricher.java:189`）：
```java
if (routeResolution.getBindingId() == null && !routeResolution.isSnapshotBacked()) {
    return false;
}
```

问题：bootstrap 路由总是 `bindingId=null && isSnapshotBacked=false`，即使 modelName 有效也会被拒绝。

最小修复方向（仅一个变量）：
- 当 `routeResolution` 的 `modelName` 非空、非 "fallback"、非 "unknown" 时，即使来自 bootstrap fallback 也应视为路由可用
- 或者：将 `isSnapshotBacked()` 检查替换为对 `modelName` 有效性的检查（与下面已有的 modelName 检查合并）
- 现有的 modelName 检查（第 192-195 行）已经能过滤掉无效路由，前面的 `bindingId == null && !isSnapshotBacked` 是冗余且过度严格的

### 修改范围限定：

- **只修改 `LlmFactCardTerminalUnitFieldAliasEnricher.java` 一个文件**
- **只修改 `isLlmRouteAvailable()` 一个方法**
- **不改 `ExecutionLlmSnapshotService`、`LlmGatewayRouteSupport`、`FactCardGenerationService`**
- **不改 `AnswerFallbackConclusionBuilder`**
- **不改数据库、schema、绑定、配置**

### 禁止事项：

- 禁止改为"总是返回 true"——仍需保留 modelName 有效性检查
- 禁止修改 `freezeSnapshots()` 或 `bootstrapRoute()` 的行为
- 禁止在 schema reset 后自动插入 seed bindings（那是另一个独立问题）
- 禁止扩大修到 Writer/Reviewer/Fixer 路径
- 禁止继续修改 builder 排序或候选供给侧 limit

### 验证要求（交给 agentD）：

1. 执行 `reset-lattice-schema.sh` 清库
2. 上传 Fresh Eval 2 资料并编译
3. 确认 terminal unit 的 `field_aliases_json` 包含中文别名
4. 确认 FG1 的 `late_fee_per_day` 进入 builder 候选池
5. 仅当以上确认后，再跑 FG1 query 验证最终答案

---

## 11. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 未扩大到 FQ4 或完整 Public Eval
- [x] 未读取 hidden eval
- [x] 所有结论基于源码只读分析 + 报告交叉验证
- [x] 本报告为只读归因，不是修复执行
