# Terminal Fix Pre-Commit Cleanup — Cleanup 结果报告

时间：2026-06-04
执行人：agentA（代码执行 Agent）
前置复核：`terminal_fix_pre_commit_quality_review_report.md`

---

## 1. 修改文件列表

| 文件 | 处理项 | 类型 |
|------|--------|------|
| `AnswerFallbackConclusionBuilder.java` | P1: TU_TRACE 日志 `info` → `debug` | 日志级别调整 |
| `AnswerFallbackConclusionBuilder.java` | P2: `hasCjkOverlap` 注释去业务化 | 注释清洁 |
| `FactCardTerminalUnitFtsSearchService.java` | P3: 删除未使用的 `safeLimit` 方法 | 死代码清理 |

---

## 2. 每个 cleanup 项的详细处理

### P1: TU_TRACE 日志降级

**处理方式**：将 `buildTerminalUnitExactConclusionLines` 中 5 处 `log.info` 改为 `log.debug`。

| 行 | 原日志 | 变更 |
|----|--------|------|
| 341 | `log.info("[TU_TRACE] enter fhSize=... tokens=...")` | → `log.debug(...)` |
| 362 | `log.info("[TU_TRACE] cand#... el=... qf=...")` | → `log.debug(...)` |
| 390 | `log.info("[TU_TRACE] result=NONE tuTotal=...")` | → `log.debug(...)` |
| 393 | `log.info("[TU_TRACE] result=SELECTED el=...")` | → `log.debug(...)` |
| 450 | `log.info("[TU_TRACE] additionalCandidates=...")` | → `log.debug(...)` |

**效果**：生产环境默认不输出 terminal unit 诊断日志。需要 runtime gate 时，通过 SLF4J 级别配置临时启用（如 `logging.level.com.xbk.lattice.query.service.AnswerFallbackConclusionBuilder=DEBUG`）。不新增诊断开关字段，不修改功能逻辑。

### P2: hasCjkOverlap 注释去业务化

**处理方式**：删除注释中与当前 eval 场景高度贴近的中文示例短语（"器的逾期"、"逾期"、"逾期日费"），替换为通用描述。

**修改前**：
```java
/**
 * 对 CJK token 做字符级重叠匹配。
 *
 * 当 tokenizer 产生碎片 token（如"器的逾期"）时，完整字符串匹配
 * 可能失败——但 token 中的 CJK bigram（如"逾期"）可能在 haystack
 * 的 fieldAliases（如"逾期日费"）中出现。逐 bigram 检查重叠可
 * 稳健处理碎片 token。
 */
```

**修改后**：
```java
/**
 * 对 CJK token 做字符级 bigram 重叠匹配。
 *
 * 当 tokenizer 将中文片段切分为短 token 时，完整字符串匹配可能失败，
 * 但 token 中的 CJK bigram 可能已在 haystack 中出现。逐 bigram 重叠
 * 检查可稳健处理几乎所有 CJK 碎片匹配场景。
 */
```

**效果**：保留通用算法说明，移除可能与 eval 场景关联的示例词。

### P3: 删除 safeLimit 未使用方法

**处理方式**：完整删除 `FactCardTerminalUnitFtsSearchService.java` 第 138-153 行的 `safeLimit` 方法（含 Javadoc）。

**原因**：`search` 方法已直接计算 `requestedLimit` 与 `rawLimit`，`safeLimit` 不再被任何调用点引用。

---

## 3. 明确声明：未改变 terminal unit 选择逻辑

以下行为**未被修改**：

- 候选选择（ftmc → atmc → fusedScore）— 不变
- qf 判定（`isTerminalHitQueryFocused`）— 不变
- entityContextMatchesQuery — 不变
- 多目标聚合（Phase 2 附加候选收集、去重、上限）— 不变
- raw query context match — 不变
- FTS search / reranker / candidate supply — 不变
- Materializer / Enricher — 不变

P1 仅改变日志级别，P2 仅改变注释文本，P3 仅删除未使用代码。三项修改均不涉及任何运行时分支、选择算法或数据结构变更。

---

## 4. redline 结果

`BLOCKER=0`

---

## 5. mvn test 结果

**995/0/0/0, BUILD SUCCESS**

---

## 6. 是否建议进入 `/code-commit`

**建议进入 `/code-commit`。** 三个 cleanup 项均已处理完毕，redline 与 mvn test 门禁通过，不修改任何 terminal unit 选择逻辑。
