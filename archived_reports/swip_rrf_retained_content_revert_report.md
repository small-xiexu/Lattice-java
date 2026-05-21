# SWIP RRF Retained Content Revert Report

## 1. 回退边界

- 本轮目标：只回退 RRF retained content 选择改动，不做新修复。
- 生产代码范围：仅检查并回退 `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`。
- 回退结果：`RrfFusionService.java` 已回到本轮 RRF 修复前状态，当前对 Git 无 diff。
- 本轮新增文件：本报告 `swip_rrf_retained_content_revert_report.md`。
- 运行 redline 按指定命令刷新了 `special_cases_report.md`。
- 未提交代码。

## 2. RrfFusionService 回退确认

| 项 | 结果 |
|---|---|
| 是否只回退 `RrfFusionService.java` 生产代码 | 是 |
| `mergeHits(...)` 是否恢复为 `articleHitMap.putIfAbsent(hitKey, hit)` | 是 |
| 是否移除 retained content replacement margin 常量 | 是 |
| 是否移除 retained content 评分 / 替换 helper | 是 |
| 是否移除 retained content 相关 `Locale` import | 是 |
| `git diff -- RrfFusionService.java` 是否为空 | 是 |
| retained helper 关键词残留检索 | 无残留 |

确认命令：

```bash
git diff -- src/main/java/com/xbk/lattice/query/service/RrfFusionService.java
rg -n "RETAINED|Retained|retained|shouldReplaceRetained|scoreRetained|normalizeForRetained|Locale" \
  src/main/java/com/xbk/lattice/query/service/RrfFusionService.java
```

## 3. Redline

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

| 类型 | 数量 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1830 |
| ALLOWLIST | 219 |

结论：`BLOCKER=0`。

## 4. Maven Test

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：通过。

| 指标 | 数值 |
|---|---:|
| Tests run | 811 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Total time | 06:18 |

日志中的若干异常堆栈来自测试用例主动模拟失败/降级路径，Surefire 最终汇总为通过。

## 5. SWIP Strict Eval 回退验证

当前库确认：

| 数据库 | 表 | 数量 |
|---|---|---:|
| `ai-rag-knowledge.lattice` | `source_files` | 2 |
| `ai-rag-knowledge.lattice` | `articles` | 4 |
| `ai-rag-knowledge.lattice` | `article_chunks` | 19 |

评测命令：

```bash
QUERY_REGRESSION_BASE_URL=http://127.0.0.1:18086 \
QUERY_REGRESSION_SUITE=docs/test/swip-query-eval-candidates.json \
QUERY_REGRESSION_OUTPUT_DIR=.codex/run/swip-rrf-revert-check-20260516-093800 \
QUERY_REGRESSION_ALLOW_FAILURES=1 \
bash scripts/run-query-regression.sh
```

说明：临时服务使用回退后的代码运行在 `18086`，评测后已停止。

回退后指标：

| 指标 | 数值 |
|---|---:|
| pass / fail | 11 / 12 |
| casePassRate | 0.4782608696 |
| Recall@5 | 0.9565217391 |
| Recall@10 | 0.9565217391 |
| citationPrecision | 0.8227743271 |
| llmSuccessRate | 0.8260869565 |
| averageCitationCoverage | 0.8703369095 |

与前两次 run 对照：

| Run | pass / fail | casePassRate | Recall@5 | Recall@10 | citationPrecision | llmSuccessRate |
|---|---:|---:|---:|---:|---:|---:|
| RRF 修复前基线 `swip-qfe-fix-before-20260516-003751` | 14 / 9 | 0.6086956522 | 0.9347826087 | 0.9347826087 | 0.8420289855 | 0.8260869565 |
| RRF retained content 修复后 `swip-rrf-retained-content-fix-20260516-091625` | 14 / 9 | 0.6086956522 | 0.9130434783 | 0.9130434783 | 0.8175983437 | 0.7826086957 |
| 回退检查 `swip-rrf-revert-check-20260516-093800` | 11 / 12 | 0.4782608696 | 0.9565217391 | 0.9565217391 | 0.8227743271 | 0.8260869565 |

## 6. 新增回归恢复确认

| Case | RRF 修复前 | RRF 修复后 | 回退后 | 结论 |
|---|---|---|---|---|
| `SWIP-USAGE-REPRINT-001` | PASS | FAIL | PASS | 本轮 RRF 修复引入的新增回归已恢复 |

回退后 `SWIP-USAGE-REPRINT-001` 状态：

| 项 | 值 |
|---|---|
| pass | true |
| generationMode | LLM |
| modelExecutionStatus | SUCCESS |
| answerOutcome | PARTIAL_ANSWER |
| citationPrecision | 0.8333333333 |

## 7. 回退后失败分类

回退后失败共 12 个。只分类，不继续扩大修改。

| Case | generationMode | answerOutcome | 失败分类 |
|---|---|---|---|
| `SWIP-USAGE-BANK-REFUND-001` | FALLBACK | PARTIAL_ANSWER | fallback / deterministic 摘要仍漏 requiredAnswerTerms |
| `SWIP-USAGE-BANK-SETTLEMENT-001` | LLM | INSUFFICIENT_EVIDENCE | LLM 漏点 / 过度拒答 |
| `SWIP-USAGE-SAND-SIGN-001` | LLM | SUCCESS | LLM 漏点 |
| `SWIP-USAGE-SAND-SETTLEMENT-001` | LLM | SUCCESS | LLM 漏点，回退后恢复到修复前失败状态 |
| `SWIP-FAQ-NO-RESPONSE-001` | LLM | SUCCESS | LLM 漏点 |
| `SWIP-INSTALL-CERT-NAMING-001` | LLM | SUCCESS | 回退 run 相对早先基线波动失败，需单独稳定性确认 |
| `SWIP-INSTALL-APP-LIST-001` | LLM | PARTIAL_ANSWER | 枚举项答案漏点 |
| `SWIP-INSTALL-IP-SUFFIX-001` | FALLBACK | SUCCESS | 回退 run 相对早先基线波动失败，需单独稳定性确认 |
| `SWIP-INSTALL-APP-UPGRADE-IMPACT-001` | LLM | PARTIAL_ANSWER | 回退 run 相对早先基线波动失败，需单独稳定性确认 |
| `SWIP-INSTALL-LOGS-001` | LLM | PARTIAL_ANSWER | 目录 / 枚举项答案漏点 |
| `SWIP-INSTALL-CERT-UPDATE-001` | LLM | PARTIAL_ANSWER | 时间阈值 / 状态条件答案漏点，RRF retained content 修复未改善 |
| `SWIP-FAQ-PRINT-PAPER-001` | LLM | INSUFFICIENT_EVIDENCE | LLM 漏点 / 过度拒答 |

## 8. 禁止范围确认

| 项 | 是否修改 |
|---|---|
| 题集 `docs/test/swip-query-eval-candidates.json` | 否 |
| query regression runner | 否 |
| prompt | 否 |
| fallback | 否 |
| citation | 否 |
| rerank / retrieval topK / RRF 权重 | 否 |
| `src/test/java/**` | 否 |
| `src/main/resources/**` | 否 |
| SWIP 源文档 | 否 |
| 业务特判 | 否 |

## 9. 结论

- 已回退本轮 RRF retained content 选择改动。
- `RrfFusionService.java` 当前无本轮 retained content helper 残留，且对 Git 无 diff。
- redline：`BLOCKER=0 / REVIEW=1830 / ALLOWLIST=219`。
- `mvn test`：`811 / 0 / 0 / 0`，通过。
- SWIP 回退检查中，RRF 修复引入的 `SWIP-USAGE-REPRINT-001` 新增回归已恢复 PASS。
- 回退检查整体为 `11/23`，相对更早基线出现若干 LLM/fallback 波动失败；本轮未继续分析或修复。

下一步只建议一个最小动作：不改代码，复跑一次 SWIP strict eval 稳定性校验，确认回退后相对早先基线的波动 case 是否可复现。
