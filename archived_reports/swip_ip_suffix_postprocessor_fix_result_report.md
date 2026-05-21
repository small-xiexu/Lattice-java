# SWIP-INSTALL-IP-SUFFIX-001 PostProcessor 修复结果报告

- 生成时间：2026-05-16 17:20 +0800
- 角色：agentA
- 本轮性质：极窄代码修复

## 1. 修改范围

| 项 | 结果 |
|---|---|
| 修改生产代码文件 | `src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java` |
| 修改方法 | `compressStructuredExactLookupAnswer(...)`、`looksLikeSequentialExactLookupQuestion(...)`、`hasSequentialSupplementAfterStructuredBody(...)`、`looksLikeSequentialSupplementParagraph(...)` |
| 是否只修改 `AnswerParagraphPostProcessor.java` | 是，生产代码修复仅此文件；另新增本报告 |
| 是否修改 `AnswerGenerationPayloadOrchestrator.java` | 否 |
| 是否修改 retrieval / rerank / RRF / citation / prompt / outcome guard | 否 |
| 是否修改测试 / 题集 / 脚本 / 配置 | 否 |
| 是否清库 / 重新导入 / 重建库 | 否 |
| 是否提交代码 | 否 |

## 2. 修复说明

本轮将 structured/exact lookup 的段落压缩边界收窄为通用 sequence 问题才继续保留后续补充段：

- 对纯精确查值 / 单一结构化值答案，仍在 `dangling lead-in + structured body` 后保持压缩。
- 对问题命中通用顺序、步骤、调整类语义时，允许继续保留后续带引用、列表、结构化主体或步骤动作信号的段落。
- 保留段落数仍有上限，避免所有结构化答案无限展开。

## 3. 红线检查

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

| 指标 | 值 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1836 |
| ALLOWLIST | 238 |

本轮是否新增 `SWIP` / `IP` / `151` / `POS` / 文档名 / 题目文本 / 答案片段特判：否。

## 4. 单元与集成测试

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

| Tests run | Failures | Errors | Skipped | Build |
|---:|---:|---:|---:|---|
| 811 | 0 | 0 | 0 | SUCCESS |

## 5. 目标 Case 结果

验证入口：

- 三题目标 suite：`.codex/run/swip-ip-suffix-postprocessor-fix-target-20260516-1715`
- 完整 SWIP strict eval：`.codex/run/swip-ip-suffix-postprocessor-fix-full-20260516-1716`
- 为避免旧 Redis 答案缓存污染，本轮服务使用临时 query cache 前缀；未清理 Redis、未清库、未重导入。

| Case | 修复前 | 修复后 | outcome | generation | 说明 |
|---|---|---|---|---|---|
| `SWIP-INSTALL-IP-SUFFIX-001` | FAIL，缺 `151` | PASS | SUCCESS | LLM / SUCCESS | 已恢复 |
| `SWIP-INSTALL-CERT-NAMING-001` | PASS | PASS | SUCCESS | LLM / SUCCESS | 保持通过 |
| `SWIP-NEG-UNANSWERABLE-001` | PASS | PASS | INSUFFICIENT_EVIDENCE | LLM / SUCCESS | 保持通过 |

IP-SUFFIX 修复后答案检查：

| 要求 | 是否满足 |
|---|---|
| 包含 `149` | 是 |
| 包含 `150` | 是 |
| 包含 `151` | 是 |
| 包含顺序颠倒调整思路 | 是：先 `149 -> 151`，再 `150 -> 149`，最后 `151 -> 150` |

修复后答案核心内容：

> POS 机号 `1` 对应键盘 IP 后缀 `149`；POS 机号 `2` 对应键盘 IP 后缀 `150`。如果顺序颠倒，按临时中转方式调整：先将后缀 `149` 的机器改成 `151`，再把后缀 `150` 的机器改成 `149`，最后再把后缀 `151` 的机器改成 `150`。

## 6. 完整 SWIP Strict Eval 指标

| 指标 | 值 |
|---|---:|
| pass 数 | 15 / 23 |
| Recall@5 | 0.8913 |
| Recall@10 | 0.8913 |
| citationPrecision | 0.7543 |
| llmSuccessRate | 0.8696 |
| fallbackRate | 0.1304 |
| avgCitationCoverage | 0.7727 |

失败 case 清单：

| Case | 失败原因 |
|---|---|
| `SWIP-USAGE-BANK-REFUND-001` | 缺 `参考号`、`原交易日期` |
| `SWIP-USAGE-BANK-SETTLEMENT-001` | 缺 `日结`、`结算成功`、`小票` |
| `SWIP-USAGE-SAND-SIGN-001` | 缺 `开店前` |
| `SWIP-USAGE-SAND-SETTLEMENT-001` | 缺 `卡种` |
| `SWIP-FAQ-NO-RESPONSE-001` | 缺 `区域IT伙伴` |
| `SWIP-INSTALL-LOGS-001` | 缺日志访问与目录项 |
| `SWIP-INSTALL-CERT-UPDATE-001` | 缺证书更新时间与状态要求 |
| `SWIP-FAQ-PRINT-PAPER-001` | 缺打印纸规格与门店准备方 |

是否出现新增回归：未观察到。完整 pass 数为 `15/23`，目标回归 `SWIP-INSTALL-IP-SUFFIX-001` 已恢复 PASS；其他失败只记录，不扩大修改。

## 7. 下一步建议

只做一个最小动作：复核 `SWIP-USAGE-BANK-SETTLEMENT-001` 的 `INSUFFICIENT_EVIDENCE` 是否属于 outcome guard 过度降级，先只读归因，不改代码。
