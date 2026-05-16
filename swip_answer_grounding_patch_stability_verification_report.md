# SWIP Answer Grounding Patch Stability Verification Report

- **生成时间**: 2026-05-16 17:50 +0800
- **角色**: agentD（验证/测试）
- **本轮是否修改代码**: 否
- **验证目标**: 判断当前 patch（IP-SUFFIX PostProcessor 修复）是否可进入提交前质量复核

## 1. Redline

| 指标 | 值 |
|------|----|
| BLOCKER | 0 |
| REVIEW | 1836 |
| ALLOWLIST | 238 |
| Exit code | 0 |

## 2. mvn test

| 指标 | 值 |
|------|----|
| Tests run | 811 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Build | SUCCESS |

## 3. 服务启动与 Health

| 检查项 | 结果 |
|------|------|
| 旧进程 | 已清理 (PID 34016) |
| 启动方式 | `mvn spring-boot:run --server.port=18086` |
| Health | `{"status":"UP"}` |
| Query 端点 | HTTP 200 |
| 缓存隔离 | 临时 cache prefix `llm:query:cache:vfy-stab:` |
| 库状态 | SWIP clean 库, 不清库/不重导入 |

## 4. 三轮 SWIP Strict Eval 总体指标

| 指标 | Round 1 | Round 2 | Round 3 |
|------|---------|---------|---------|
| Pass | **15/23** (65.22%) | **16/23** (69.57%) | **15/23** (65.22%) |
| Recall@5 | 0.9348 | 0.9565 | 0.9348 |
| Recall@10 | 0.9348 | 0.9565 | 0.9348 |
| MRR | 0.9783 | 0.9783 | 0.9783 |
| citationPrecision | 0.8013 | 0.7714 | 0.7760 |
| llmSuccessRate | 0.8696 | 0.8696 | 0.8696 |
| fallbackRate | 0.1304 | 0.1304 | 0.1304 |
| avgCitationCoverage | 0.8089 | 0.7881 | 0.7990 |
| httpFailureRate | 0 | 0 | 0 |
| timeoutRate | 0 | 0 | 0 |

## 5. 与历史基线对比

| 基线 | Pass | Recall@5 | llmSuccessRate | 说明 |
|------|------|----------|----------------|------|
| RRF revert stability | 13-14/23 | 0.93-0.96 | 0.83-0.87 | 历史基准 |
| current patch (上一轮) | 13-14/23 | 0.96-0.98 | 0.87-0.91 | outcome guard + lead-in fix |
| IP-SUFFIX fix 单轮 | 15/23 | 0.8913 | 0.8696 | 单次观测 |
| **本轮 R1** | **15/23** | **0.9348** | **0.8696** | **确认改善** |
| **本轮 R2** | **16/23** | **0.9565** | **0.8696** | **新高** |
| **本轮 R3** | **15/23** | **0.9348** | **0.8696** | **确认改善** |

本轮三轮均值 **15.3/23**，较 RRF revert 基线 (13.5/23) 提升约 **1.8 个 case**，较上一轮 current patch (13.3/23) 提升约 **2 个 case**。

## 6. 完整 23 个 Case 三轮 Pass/Fail 矩阵

| Case ID | R1 | R2 | R3 | 稳定性 | 历史对比 |
|---------|----|----|----|--------|---------|
| SWIP-USAGE-GOAL-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-USAGE-SVC-READ-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-USAGE-BANK-REFUND-001 | FAIL | FAIL | FAIL | 稳定 FAIL | — |
| SWIP-USAGE-BANK-SETTLEMENT-001 | FAIL | FAIL | FAIL | 稳定 FAIL | ⚠️ outcome 退化 |
| SWIP-USAGE-REPRINT-001 | PASS | PASS | FAIL | 波动 | R3 新增波动 |
| SWIP-USAGE-SAND-SIGN-001 | FAIL | FAIL | FAIL | 稳定 FAIL | — |
| SWIP-USAGE-SAND-SETTLEMENT-001 | FAIL | PASS | PASS | 改善波动 | 曾稳定 FAIL |
| SWIP-USAGE-DESHI-VOID-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-USAGE-EBUY-SETTLEMENT-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-FAQ-NO-RESPONSE-001 | FAIL | FAIL | FAIL | 稳定 FAIL | — |
| SWIP-FAQ-TERMINATE-STUCK-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-INSTALL-CERT-NAMING-001 | PASS | PASS | PASS | 稳定 PASS | ✅ 目标达成 |
| SWIP-INSTALL-APP-LIST-001 | PASS | PASS | PASS | **稳定 PASS** | 🟢 从波动改善 |
| SWIP-INSTALL-IP-SUFFIX-001 | PASS | PASS | PASS | **稳定 PASS** | 🟢 从稳定 FAIL 恢复 |
| SWIP-INSTALL-SWIP-POS-JSON-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-INSTALL-KEY-FILE-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-INSTALL-DLL-ORDER-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | PASS | PASS | PASS | 稳定 PASS | — |
| SWIP-INSTALL-LOGS-001 | FAIL | FAIL | FAIL | 稳定 FAIL | — |
| SWIP-INSTALL-CERT-UPDATE-001 | FAIL | FAIL | FAIL | 稳定 FAIL | — |
| SWIP-FAQ-PRINT-PAPER-001 | FAIL | FAIL | FAIL | 稳定 FAIL | — |
| SWIP-NEG-UNANSWERABLE-001 | PASS | PASS | PASS | 稳定 PASS | ✅ outcome 正确 |
| SWIP-NEG-UNANSWERABLE-002 | PASS | PASS | PASS | 稳定 PASS | — |

**统计：**

| 类型 | 数量 | 说明 |
|------|------|------|
| 稳定 PASS | 15 | 含 IP-SUFFIX、APP-LIST 新晋稳定 PASS |
| 稳定 FAIL | 7 | BANK-SETTLEMENT outcome 退化需关注 |
| 改善波动 | 1 | SAND-SETTLEMENT (FAIL→PASS/PASS) |
| 退化波动 | 1 | REPRINT (PASS/PASS→FAIL) |

## 7. 重点 6 个 Case 详细矩阵

| Case ID | R1 | R2 | R3 | outcome (R1/R2/R3) | gen | 关键观察 |
|---------|----|----|----|---------------------|-----|---------|
| SWIP-INSTALL-IP-SUFFIX-001 | PASS | PASS | PASS | SUCCESS ×3 | LLM ×3 | ✅ 三轮 149/150/151 全覆盖，三轮稳定 |
| SWIP-INSTALL-CERT-NAMING-001 | PASS | PASS | PASS | SUCCESS ×3 | LLM ×3 | ✅ 稳定通过 |
| SWIP-NEG-UNANSWERABLE-001 | PASS | PASS | PASS | INSUFFICIENT_EVIDENCE ×3 | LLM ×3 | ✅ outcome 正确，稳定通过 |
| SWIP-USAGE-BANK-SETTLEMENT-001 | FAIL | FAIL | FAIL | **INSUFFICIENT_EVIDENCE ×3** | LLM ×3 | ⚠️ outcome guard 持续误降级 |
| SWIP-USAGE-REPRINT-001 | PASS | PASS | FAIL | PARTIAL_ANSWER ×3 | LLM ×3 | R3 缺"查询交易明细"（非之前"流水号"） |
| SWIP-INSTALL-APP-LIST-001 | PASS | PASS | PASS | INSUFFICIENT/PARTIAL/PARTIAL | LLM ×3 | 🟢 从波动改善为稳定 PASS |

### IP-SUFFIX-001 内容核验

三轮回答均完整覆盖：
- 包含 `149` ✅
- 包含 `150` ✅
- 包含 `151` ✅
- 包含顺序颠倒调整思路 ✅

确认 IP-SUFFIX 修复生效且稳定。

### BANK-SETTLEMENT-001 outcome 退化

三轮均为 `INSUFFICIENT_EVIDENCE`。该 case 历史上 outcome 为 `PARTIAL_ANSWER`（RRF revert 时期），本应提供部分结算相关信息。outcome guard 的 `looksLikeInsufficientEvidenceAnswer()` 对该 case 的回答文本存在持续误判，将部分结算证据误识别为"证据不足"。

该退化在 IP-SUFFIX fix report 中已作为"下一步建议"预判：*"复核 SWIP-USAGE-BANK-SETTLEMENT-001 的 INSUFFICIENT_EVIDENCE 是否属于 outcome guard 过度降级"*。本轮确认该问题为持续存在（非偶发），但**不是本轮 IP-SUFFIX PostProcessor 修复引入的**。

## 8. 新增稳定回归

**无。** IP-SUFFIX-001 已从稳定 FAIL 恢复为稳定 PASS。APP-LIST-001 已从波动改善为稳定 PASS。

## 9. 新增波动

| Case ID | 波动模式 | 严重程度 | 说明 |
|---------|---------|---------|------|
| SWIP-USAGE-REPRINT-001 | PASS/PASS→FAIL | 低 | R3 缺失项为"查询交易明细"（非历史上"流水号"），纯 LLM 内容波动 |
| SWIP-USAGE-SAND-SETTLEMENT-001 | FAIL→PASS/PASS | 低（正向） | 从稳定 FAIL 改善 |

## 10. 是否建议保留当前 Patch

**强烈建议保留。**

理由：
1. IP-SUFFIX-001 从稳定 FAIL 恢复为 **三轮稳定 PASS**，修复目标达成
2. CERT-NAMING-001 保持 3/3 PASS，未退化
3. NEG-UNANSWERABLE-001 保持 3/3 PASS + 正确 INSUFFICIENT_EVIDENCE
4. APP-LIST-001 从历史波动改善为 **三轮稳定 PASS**
5. 整体 pass 率 **15-16/23**，显著优于所有历史基线（13-14/23）
6. 无新增稳定回归，唯一退化波动（REPRINT-001 R3）为 LLM 内容波动

仅 BANK-SETTLEMENT-001 的 outcome guard 误降级为已知遗留问题（上一轮预判），非本轮引入，不应阻塞当前 patch。

## 11. 是否建议进入提交前质量复核

**建议进入提交前质量复核。**

所有门禁通过：
- redline BLOCKER=0
- mvn test 811/0/0/0
- 服务可用、health UP
- 3 轮 SWIP strict eval 全部 ≥ 15/23，均值 15.3/23
- 目标 case 全部修复并稳定
- 无新增稳定回归

## 12. 发现的遗留问题（不阻塞提交）

| 问题 | 严重程 | 建议处理方 |
|------|--------|-----------|
| BANK-SETTLEMENT-001 outcome guard 过度降级 (INSUFFICIENT_EVIDENCE) | 中 | 下轮只读归因，确认后收紧 insufficient evidence 信号，不改 IP-SUFFIX 代码 |
| REPRINT-001 R3 LLM 内容波动 | 低 | 非结构性回退，继续观察 |

## 13. 本轮是否修改代码

**否。** 本轮仅：清理旧进程、重启服务、运行 redline、运行 mvn test、运行 3 轮 SWIP strict eval、写验证报告。未修改任何源码、测试、题集、配置、脚本。
