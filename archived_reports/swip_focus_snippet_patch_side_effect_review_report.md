# SWIP Focus Snippet Patch 副作用验证报告

- 生成时间：2026-05-16 22:15 +0800
- 角色：agentD
- 本轮性质：只读副作用验证，不改代码
- 验证对象：`AnswerGenerationPromptEvidenceSupport.java` focus snippet 分布式窗口 patch

## 1. Redline 门禁

命令：`bash scripts/scan-redline.sh special_cases_report.md`

| 指标 | 值 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1863 |
| ALLOWLIST | 242 |
| EXIT_CODE | 0 |

结论：redline 通过，无阻断项。

## 2. 修改范围核对

| 项 | 是否只改允许文件 |
|---|---|
| `AnswerGenerationPromptEvidenceSupport.java` | 是，生产代码仅此文件（+434/-26 行） |
| `AnswerGenerationPayloadOrchestrator.java` | 仅 prompt audit instrumentation（环境变量门控日志），无行为变更 |
| 其他 `src/main/java/**` | 否 |
| `src/test/java/**` | 否 |
| 题集 / runner / eval 阈值 | 否 |
| 清库 / 重建 / 重新导入 | 否 |

结论：本轮 patch 只改了允许文件，未触碰禁止范围。

## 3. SWIP Strict Eval 三轮指标

基线参考：answer grounding patch 稳定区间 **15-16/23**（详见 `docs/quality-progress-and-lessons.md`）。

| 指标 | R1 | R2 | R3 | 趋势 vs 基线 |
|---|---:|---:|---:|---|
| passCount | 16/23 | 17/23 | 15/23 | 维持 15-16/23，R2 达到 17/23 |
| casePassRate | 0.6957 | 0.7391 | 0.6522 | 不低于 0.6522 下限 |
| Recall@5 | 0.9565 | 0.9348 | 0.9348 | R2/R3 略有下降但仍健康 |
| Recall@10 | 0.9565 | 0.9348 | 0.9348 | 同 Recall@5，无额外 top-10 漏召回 |
| citationPrecision | 0.7932 | 0.8319 | 0.8085 | R2 提升明显，R3 回到中位 |
| llmSuccessRate | 0.8696 | 0.9130 | 0.9130 | R2/R3 稳定提升 |
| fallbackRate | 0.1304 | 0.0870 | 0.0870 | R2/R3 下降（从 3 例降到 2 例） |
| avgCitationCoverage | 0.8195 | 0.8621 | 0.8544 | 整体提升 |

三轮 pass 分布：16 → 17 → 15，在已知稳定区间内波动，R2 达到区间上界以上。

## 4. 目标 Case 验证

### 4.1 SWIP-USAGE-BANK-SETTLEMENT-001

| 轮次 | pass | answerOutcome | generationMode | 关键事实覆盖 |
|---|---:|---|---|---|
| R1 | PASS | PARTIAL_ANSWER | LLM | 日结/结算成功/小票 均覆盖 |
| R2 | PASS | PARTIAL_ANSWER | LLM | 日结/结算成功/小票 均覆盖 |
| R3 | PASS | PARTIAL_ANSWER | LLM | 日结/结算成功/小票 均覆盖 |

结论：目标 case 三轮稳定 PASS。修复前为 FAIL（INSUFFICIENT_EVIDENCE），修复后稳定通过。

### 4.2 保护 Case 验证

| Case | R1 | R2 | R3 | 状态 |
|---|---|---|---|---|
| SWIP-INSTALL-IP-SUFFIX-001 | PASS | PASS | PASS | 稳定 |
| SWIP-NEG-UNANSWERABLE-001 | PASS | PASS | PASS | 稳定 |
| SWIP-INSTALL-CERT-NAMING-001 | PASS | PASS | PASS | 稳定 |

结论：三个保护 case 三轮均 PASS，无回归。

## 5. 波动 Case 分析

### 5.1 SWIP-USAGE-REPRINT-001

| 轮次 | pass | 失败原因 |
|---|---|---|
| R1 | PASS | — |
| R2 | PASS | — |
| R3 | FAIL | `answer_missing_term:查询交易明细` |

**历史记录核查**：`swip_bank_settlement_focus_snippet_fix_result_report.md` 第 146 行已明确标注 REPRINT-001 为"既有稳定性报告中已标记为波动 case"。在 RRF retained content revert 稳定性报告中，REPRINT-001 也曾作为回归出现后恢复。

判断：非本轮新增副作用，属于已知 LLM 不稳定波动。

### 5.2 SWIP-INSTALL-APP-LIST-001

| 轮次 | pass | 失败原因 |
|---|---|---|
| R1 | FAIL | `answer_missing_term:资和信/易百/杉德/得仕卡/苏州市民卡` |
| R2 | PASS | — |
| R3 | FAIL | `answer_missing_term:SWIP APP Store/SWIP网关/资和信/易百/杉德/得仕卡/苏州市民卡` |

**历史记录核查**：同上报告第 146 行标记为波动 case。该 case 要求列出全部 7 个 APP 名称，LLM 在各轮中漏掉的子集不完全相同（R1 漏 5 个，R3 漏 7 个），是典型的枚举完整性波动，非 focus snippet 逻辑引入。

判断：非本轮新增副作用，属于已知 LLM 枚举完整性波动。

## 6. 非目标 Case 副作用抽样

### 6.1 generationMode 变化

| Case | R1 | R2 | R3 | 变化 |
|---|---|---|---|---|
| SWIP-USAGE-BANK-REFUND-001 | FALLBACK | LLM | LLM | FALLBACK→LLM，改善 |
| SWIP-INSTALL-SWIP-POS-JSON-001 | FALLBACK | FALLBACK | FALLBACK | 稳定（DETERMINISTIC_EXACT_LOOKUP） |
| SWIP-NEG-UNANSWERABLE-002 | FALLBACK | FALLBACK | FALLBACK | 稳定（CITATION_QUALITY_INSUFFICIENT） |

结论：无新增 fallback 退化。BANK-REFUND-001 在 R2/R3 从 FALLBACK 恢复到 LLM，是正向变化。

### 6.2 fallbackRate 变化

R1=0.1304（3 例）→ R2/R3=0.0870（2 例）。下降，非上升。

### 6.3 citationCoverage 抽样

| Case | R1 | R2 | R3 | 趋势 |
|---|---:|---:|---:|---|
| SWIP-INSTALL-LOGS-001 | 0.25 | 1.0 | 1.0 | 大幅改善 |
| SWIP-USAGE-BANK-REFUND-001 | 0.7273 | 0.6667 | 1.0 | 改善 |
| SWIP-FAQ-PRINT-PAPER-001 | 0.75 | 0.6667 | 0.6667 | 轻微下降 |
| SWIP-INSTALL-APP-LIST-001 | 1.0 | 0.8889 | 1.0 | 波动 |

结论：无系统性 citationCoverage 下降。LOGS-001 从 0.25 提升到 1.0，改善显著。

### 6.4 answer 引入无关 evidence 检查

三轮失败 case 的 `failed_reasons` 均为 `answer_missing_term`（答案缺项），未出现 `answer_hallucination` 或 irrelevant evidence 类新失败类型。无证据表明 focus snippet 分布式窗口引入了无关 evidence。

## 7. promptLength 增大分析

### 7.1 目标 case 影响

BANK-SETTLEMENT promptLength：13093 → 23815（增加 82%，约 +10722 字符）。

增加原因：分布式窗口从同一 hit 内选择了覆盖"日结"、"结算成功"、"小票"三个不同焦点的局部窗口，每个窗口最多 560 字符 × 最多 3 个窗口 ≈ 1680 字符上限。实际增加超过 10000 字符，说明问题聚焦证据 section 外（如 SOURCE/ARTICLE EVIDENCE）的 content 也因为 hit 内 focus snippet 选择改善而不再被过早截断。

### 7.2 非目标 case 影响

分布式窗口仅在 `shouldUseDistributedPromptFocusSnippets` 返回 true 时触发，触发条件为 flow/enumeration/status/compound-exact-lookup/status-signal/sequence-signal/multi-focus-separator 问题，且明确排除 path/exact-identifier 问题。

- path 问题（如 IP-SUFFIX-001、CERT-NAMING-001）：不触发分布式窗口，promptLength 不受影响。
- exact identifier 问题（如 KEY-FILE-001）：不触发。
- 简单事实问题（如 GOAL-001、TERMINATE-STUCK-001）：通常只触发 1-2 条 snippet，不受分布式窗口影响。

判断：promptLength 增大主要集中在目标类问题（多焦点/流程/枚举/状态），对 path/exact-identifier 和简单事实问题无影响。增大幅度在可接受范围内，且换来了关键事实覆盖的提升。

## 8. 代码审查：AnswerGenerationPromptEvidenceSupport.java diff

### 8.1 是否存在业务特判

搜索项（源码中已确认）：

| 搜索词 | 命中 |
|---|---|
| `SWIP` | 0 |
| `银行` / `BANK` | 0 |
| `日结` / `结算成功` / `小票` | 0 |
| `文档名` / `caseId` | 0 |
| 具体 APP 名 / 卡组织名 | 0 |

所有问题类型判断均使用通用语义规则：
- `looksLikeFlowQuestion` / `looksLikeEnumerationQuestion` / `looksLikeStatusQuestion` / `looksLikeCompoundExactLookupQuestion`
- `querySemanticRules.containsAnyStatusSignal` / `containsAnySequenceSignal` / `containsAnyMultiFocusSeparator`
- `looksLikePathQuestion` / `containsRequestedExactPathIdentifier`

结构化信号均为通用规则：`containsFlowSignal`、`containsFlowTransitionSignal`、`containsStatusSignal`、`startsWithDirectStructuredFactAssignment`。

结论：无业务特判，符合 AGENTS.md 红线规则。

### 8.2 触发条件是否过宽

`shouldUseDistributedPromptFocusSnippets` 的触发路径：

```
question != null && !isBlank
  AND NOT (looksLikePathQuestion || containsRequestedExactPathIdentifier)
  AND (
    looksLikeFlowQuestion
    || looksLikeEnumerationQuestion
    || looksLikeStatusQuestion
    || looksLikeCompoundExactLookupQuestion
    || querySemanticRules.containsAnyStatusSignal
    || querySemanticRules.containsAnySequenceSignal
    || querySemanticRules.containsAnyMultiFocusSeparator
  )
```

保护机制：
- path 问题（IP-SUFFIX、CERT-NAMING 等）显式排除，避免相邻未请求路径列表进入 prompt。
- exact identifier 问题显式排除，避免文件名/路径查找被多余窗口干扰。
- 分布式窗口 limit=3，不会无限扩张。
- 窗口内去重（`selectNonOverlappingPromptFocusWindows`）防止重复窗口。
- 窗口大小上限 560 字符，单窗口不会过度膨胀。

判断：触发条件合理保守，保护了 path/exact-identifier 问题不受影响。未观察到触发过宽导致的副作用。

### 8.3 是否影响 path / exact identifier 问题

三轮中 path/exact-identifier 类 case 表现：
- IP-SUFFIX-001 (path): 三轮 PASS，citationCoverage=1.0
- CERT-NAMING-001 (exact identifier): 三轮 PASS，citationCoverage=1.0
- KEY-FILE-001 (exact identifier): 三轮 PASS，citationCoverage=1.0
- SWIP-POS-JSON-001 (exact identifier): 三轮 PASS（FALLBACK 为 DETERMINISTIC_EXACT_LOOKUP，预期行为）

结论：path/exact-identifier 问题未受影响。

### 8.4 是否建议缩小触发条件

当前触发条件已包含合理的排除逻辑（path/exact-identifier），且保护 case 均稳定。不建议进一步缩小，因为：
- 缩小可能让 BANK-SETTLEMENT 类多焦点问题重新丢失关键事实。
- 当前三轮结果无任何由分布式窗口直接导致的新增稳定回归。

## 9. 结论

### 三选一：**可保留**

| 判断维度 | 结论 |
|---|---|
| redline BLOCKER | 0，通过 |
| 修改范围 | 仅允许文件，未触碰禁止范围 |
| SWIP 三轮指标 | 15-16-17/23，维持并略超基线区间 |
| BANK-SETTLEMENT | 三轮稳定 PASS |
| 保护 case | IP-SUFFIX / NEG-UNANSWERABLE / CERT-NAMING 三轮稳定 PASS |
| 新增稳定回归 | 无。REPRINT-001 / APP-LIST-001 波动为已知历史行为 |
| fallbackRate | 未增加（0.1304 → 0.0870） |
| citationCoverage | 未系统性下降，LOGS-001 显著改善 |
| promptLength 增大 | 集中在目标问题类型，path/exact-identifier 不受影响，可接受 |
| 业务特判 | 无，全部使用通用语义规则 |
| 触发条件 | 合理保守，保护了 path/exact-identifier |
| 本轮是否修改代码 | **否** |

### 不保留建议

不适用。当前 patch 无需要回退或缩小的理由。

## 10. 补充说明

- 本报告未修改任何源码、测试、配置、脚本、题集。
- redline 扫描已刷新 `special_cases_report.md`（仅 redline 自动刷新，符合允许范围）。
- 本轮未补跑额外 SWIP eval，所有数据来自既有 `.codex/run/swip-bank-settlement-focus-snippet-full-20260516-213317/` 目录。
- 建议下一步：继续提交 answer grounding patch，然后对仍稳定失败的 `SWIP-INSTALL-LOGS-001` 做只读 prompt audit 归因。
