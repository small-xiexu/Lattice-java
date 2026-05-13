# Q-RUNTIME-OCR-001 Eval 预期更新结果报告

生成时间：2026-05-14

## 1. 修改范围

| 项目 | 内容 |
|---|---|
| 修改文件 | `docs/test/query-regression-suite.json` |
| 修改 case | Q-RUNTIME-OCR-001 |
| 是否只修改该文件 | **是** |
| 是否修改生产代码 | **否** |
| 是否修改 OCR 文档 | **否** |

### 1.1 修改字段一览

| 字段 | 旧值 | 新值 | 原因 |
|---|---|---|---|
| `generationModeAny` | `["RULE_BASED"]` | `["LLM", "RULE_BASED"]` | 允许通用 query graph 的 LLM 生成路径 |
| `modelExecutionStatusAny` | `["SKIPPED"]` | `["SUCCESS", "SKIPPED"]` | 允许 LLM 调用成功状态 |
| `requireQueryId` | `false` | `true` | queryId 应正常返回 |
| `minCitationCoverage` | (无) | `0.6` | 对齐全局 gate 阈值 |
| `requiredAnswerTerms` | `["OCR", "文档"]` | `["OCR", "文档识别", "provider", "连接"]` | 更新为通用 OCR 运行态事实关键词 |
| `requiredSourceTerms` | `["/api/v1/admin/document-parse"]` | `["文档识别与OCR运行态说明"]` | 匹配新增源文件，不再依赖旧无资料状态 |
| `answerability` | `ANSWERABLE_IF_RUNTIME_STATUS_SOURCE_PRESENT` | `ANSWERABLE` | 废弃条件性 answerability，走通用 query graph |
| `expectedPoints` | 旧描述 | 新描述 | 更新为 Provider 连接配置相关预期 |
| `expectedEvidence` | 旧 source/quoteHint | 新 source/quoteHint | 更新为 `docs/文档识别与OCR运行态说明.md` |
| `humanJudgement.passRule` | 旧规则 | 新规则 | 更新为允许 LLM 生成结果 |

## 2. JSON 校验

```
python3 -m json.tool docs/test/query-regression-suite.json → 通过
```

## 3. Redline

| 指标 | 值 |
|---|---|
| BLOCKER | **0** |
| EXIT | **0** |

`bash scripts/scan-redline.sh special_cases_report.md` 无输出。

## 4. Q-RUNTIME-OCR-001 更新前后对比

| 指标 | 更新前 | 更新后 |
|---|---|---|
| pass | FAIL ❌ | **PASS** ✅ |
| generationMode | LLM | **LLM** |
| modelExecutionStatus | SUCCESS | **SUCCESS** |
| answerOutcome | SUCCESS | SUCCESS |
| citationCoverage | 1.0 | 1.0 |
| sourceCount | 1 | 1 |
| retrievalMatchedAt_5 | 0 | **1** |
| retrievalMatchedAt_10 | 0 | **1** |
| elapsedMs | 19878 | 4533 |

## 5. 全量 Baseline 结果

| 用例 | 状态 |
|---|---|
| Q-RUNTIME-OCR-001 | ✅ PASS |
| Q-STRUCT-ROW-001 | ✅ PASS |
| Q-STRUCT-PROJECTION-001 | ✅ PASS |
| Q-STRUCT-AGG-001 | ✅ PASS |
| Q-STRUCT-COMPARE-001 | ✅ PASS |
| Q-EXACT-PATH-001 | ✅ PASS |
| Q-MQ-BOUNDARY-001 | ✅ PASS |
| Q-CONFIG-001 | ✅ PASS |
| Q-DEEP-001 | ✅ PASS |
| Q-NO-HIT-001 | ✅ PASS |

**10/10 PASS，无新增回归。**

## 6. 守约确认

| 禁手项 | 状态 |
|---|---|
| 是否只修改 docs/test/query-regression-suite.json | **是** ✅ |
| 是否修改生产代码 | **否** ✅ |
| 是否修改 OCR 文档 | **否** ✅ |
| 是否修改测试代码 | **否** ✅ |
| 是否修改 redline/scripts | **否** ✅ |
| 是否恢复 OCR 特殊旁路 | **否** ✅ |
| 是否降低全局 baseline gate | **否** ✅ |
| 是否删除该 case | **否** ✅ |
| 是否导入 eval/report/test 文件到知识库 | **否** ✅ |
| JSON 校验通过 | **是** ✅ |
| redline BLOCKER=0 | **是** ✅ |

## 7. 总结

Q-RUNTIME-OCR-001 的 eval 预期已从旧的无资料 RULE_BASED/SKIPPED 口径更新为通用 query graph LLM/SUCCESS 口径。数据覆盖已在前期通过新增 `docs/文档识别与OCR运行态说明.md` 完成，本轮仅更新 eval 预期使其与实际结果对齐。全量 baseline 首次达到 10/10 PASS。
