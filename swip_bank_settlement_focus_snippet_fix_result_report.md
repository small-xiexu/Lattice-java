# SWIP-USAGE-BANK-SETTLEMENT-001 Focus Snippet 修复结果报告

- 生成时间：2026-05-16 21:55 +0800
- 角色：agentA
- 本轮性质：极窄代码修复

## 1. 修改范围

| 项 | 结果 |
|---|---|
| 修改生产代码文件 | `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java` |
| 修改方法 | `buildPromptFocusSnippets(...)` |
| 新增私有 helper | 同文件内新增 query token 覆盖、局部窗口、结构信号与窗口去重相关 helper |
| 是否只修改 `AnswerGenerationPromptEvidenceSupport.java` | 是，生产代码修复仅此文件 |
| 是否修改 `AnswerGenerationPayloadOrchestrator.java` | 否，本轮只复用既有 prompt audit instrumentation |
| 是否修改 `AnswerParagraphPostProcessor.java` | 否 |
| 是否修改 RRF / retrieval / fusion / retained content | 否 |
| 是否修改 fallback / citation / model config / outcome guard | 否 |
| 是否修改题集 / runner / eval 阈值 | 否 |
| 是否清库 / 重建库 / 重新导入资料 | 否 |
| 是否提交代码 | 否 |

## 2. 修复说明

本轮只改进同一 evidence hit 内的 `QUESTION-FOCUSED EVIDENCE` snippet 选择：

- 对 flow / enumeration / status / compound exact lookup / multi-focus 问题，允许在同一 hit 内选择最多 3 个受控局部窗口。
- 窗口选择基于通用信号：query token 覆盖、结构化行、列表/表格行、流程/状态/顺序信号、窗口去重与覆盖增益。
- 对 path / exact identifier 问题保持保守，不启用分散窗口，避免把相邻未请求路径列表带入 prompt。
- 没有扩大 retrieval topK、RRF 权重、prompt 总预算或 section budget。

本轮未写入 `SWIP`、银行、日结、结算成功、小票、收银端、文档名、case id、题目文本或答案片段特判。

## 3. 红线检查

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

| 指标 | 值 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1852 |
| ALLOWLIST | 238 |

补充核验：

```bash
rg -n "SWIP|银行|日结|结算成功|小票|收银端|BANK|SETTLEMENT|caseId|答案片段|文档名" \
  src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java
```

结果：无业务词、文档名、case id 或答案片段命中。

## 4. 测试

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

| Tests run | Failures | Errors | Skipped | Build |
|---:|---:|---:|---:|---|
| 811 | 0 | 0 | 0 | SUCCESS |

## 5. BANK-SETTLEMENT 单 Case

修复前参考：

- `swip_bank_settlement_prompt_evidence_truncation_analysis_report.md`
- `swip_answer_prompt_audit_instrumentation_result_report.md`
- `.codex/run/swip-bank-settlement-focus-snippet-single-20260516-211717/eval`

修复后验证：

- `.codex/run/swip-bank-settlement-focus-snippet-single-20260516-213205/eval`
- 服务使用临时 Redis cache prefix，未清库、未重导入。

| 阶段 | pass/fail | answerOutcome | generationMode | modelExecutionStatus | answer 是否含 `日结` | answer 是否含 `结算成功` | answer 是否含 `小票` |
|---|---|---|---|---|---:|---:|---:|
| 修复前 | FAIL | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | 否 | 否 | 否 |
| 修复后单 case | PASS | `PARTIAL_ANSWER` | `LLM` | `SUCCESS` | 是 | 是 | 是 |

修复后答案摘要：

> 银行卡结算建议在每日日结后执行；在相关管理入口点击日结。执行后会提示结账中，结账成功后会提示结算成功，弹窗关闭后返回系统，并打印结算小票。

## 6. Prompt Snapshot

修复前 prompt audit 摘要：

| 项 | 值 |
|---|---|
| promptLength | 13093 |
| prompt 中 `日结` | 是 |
| prompt 中 `结算成功` | 否 |
| prompt 中 `小票` | 否 |
| `QUESTION-FOCUSED EVIDENCE` | 不含三个目标词 |
| 观察 | `SOURCE EVIDENCE` / `ARTICLE EVIDENCE` 均发生截断，关键结果句未进入可见 prompt |

修复后 prompt snapshot：

| 项 | 值 |
|---|---|
| promptLength | 23815 |
| prompt 中 `日结` | 是 |
| prompt 中 `结算成功` | 是 |
| prompt 中 `小票` | 是 |
| `QUESTION-FOCUSED EVIDENCE` | 三个词均可见 |
| `SOURCE EVIDENCE` | `日结`、`结算成功` 可见，`小票` 未在该 section 可见 |
| `ARTICLE EVIDENCE` | 三个词均可见 |

判断：本轮修复把同一 hit 内被截断弱化的后半段结果句拉入了 prompt focus snippet；目标 case 从“prompt 缺关键事实”变成“prompt 与答案均覆盖关键事实”。

## 7. SWIP Strict Eval 三轮

完整 SWIP strict eval 输出目录：

- `.codex/run/swip-bank-settlement-focus-snippet-full-20260516-213317/round-1/eval`
- `.codex/run/swip-bank-settlement-focus-snippet-full-20260516-213317/round-2/eval`
- `.codex/run/swip-bank-settlement-focus-snippet-full-20260516-213317/round-3/eval`

| 轮次 | pass | casePassRate | Recall@5 | Recall@10 | citationPrecision | llmSuccessRate | fallbackRate | avgCitationCoverage | BANK-SETTLEMENT | IP-SUFFIX | NEG-UNANSWERABLE | CERT-NAMING |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
| R1 | 16 / 23 | 0.6957 | 0.9565 | 0.9565 | 0.7932 | 0.8696 | 0.1304 | 0.8195 | PASS | PASS | PASS | PASS |
| R2 | 17 / 23 | 0.7391 | 0.9348 | 0.9348 | 0.8319 | 0.9130 | 0.0870 | 0.8621 | PASS | PASS | PASS | PASS |
| R3 | 15 / 23 | 0.6522 | 0.9348 | 0.9348 | 0.8085 | 0.9130 | 0.0870 | 0.8544 | PASS | PASS | PASS | PASS |

目标 case `SWIP-USAGE-BANK-SETTLEMENT-001` 三轮均 PASS。保护 case `SWIP-INSTALL-IP-SUFFIX-001`、`SWIP-NEG-UNANSWERABLE-001`、`SWIP-INSTALL-CERT-NAMING-001` 三轮均 PASS。

三轮失败清单：

| 轮次 | 失败 case |
|---|---|
| R1 | `SWIP-USAGE-BANK-REFUND-001`, `SWIP-USAGE-SAND-SIGN-001`, `SWIP-FAQ-NO-RESPONSE-001`, `SWIP-INSTALL-APP-LIST-001`, `SWIP-INSTALL-LOGS-001`, `SWIP-INSTALL-CERT-UPDATE-001`, `SWIP-FAQ-PRINT-PAPER-001` |
| R2 | `SWIP-USAGE-BANK-REFUND-001`, `SWIP-USAGE-SAND-SIGN-001`, `SWIP-FAQ-NO-RESPONSE-001`, `SWIP-INSTALL-LOGS-001`, `SWIP-INSTALL-CERT-UPDATE-001`, `SWIP-FAQ-PRINT-PAPER-001` |
| R3 | `SWIP-USAGE-BANK-REFUND-001`, `SWIP-USAGE-REPRINT-001`, `SWIP-USAGE-SAND-SIGN-001`, `SWIP-FAQ-NO-RESPONSE-001`, `SWIP-INSTALL-APP-LIST-001`, `SWIP-INSTALL-LOGS-001`, `SWIP-INSTALL-CERT-UPDATE-001`, `SWIP-FAQ-PRINT-PAPER-001` |

是否出现新增回归：未观察到稳定新增回归。`SWIP-USAGE-REPRINT-001`、`SWIP-INSTALL-APP-LIST-001` 在既有稳定性报告中已标记为波动 case；本轮只记录波动，不扩大修改。

## 8. 禁止范围核对

| 项 | 是否触碰 |
|---|---|
| `AnswerGenerationPayloadOrchestrator.java` | 否 |
| `AnswerParagraphPostProcessor.java` | 否 |
| RRF / retrieval / fusion / retained content | 否 |
| fallback / citation / outcome guard | 否 |
| model config | 否 |
| prompt 模板 | 否 |
| 题集 / runner / eval 阈值 | 否 |
| 清库 / 重建库 / 重新导入资料 | 否 |
| 业务词 / 文档名 / case 特判 | 否 |

## 9. 结论与下一步

结论：本轮修复有正收益。BANK-SETTLEMENT 从修复前 FAIL 稳定变为三轮 PASS，prompt snapshot 已确认目标事实进入最终 LLM input；三轮总体 pass 为 `16/23`、`17/23`、`15/23`，未低于当前 `15-16/23` 区间，且保护 case 未回归。

下一步只建议一个最小动作：对仍稳定失败的 `SWIP-INSTALL-LOGS-001` 做只读 prompt audit，先确认缺失项是 prompt 可见性问题、LLM 漏点，还是 fallback / postprocess 问题；归因前不改代码。
