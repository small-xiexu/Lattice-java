# SWIP Answer Grounding Current Patch Stability Report

- **生成时间**: 2026-05-16 13:00 +0800
- **分支**: `codex/qa-polish`
- **本轮角色**: agentD（验证/测试）
- **本轮是否修改代码**: 否
- **当前两处生产代码改动**:
  1. `AnswerParagraphPostProcessor.java` — structured/exact lookup lead-in 裁剪修复
  2. `AnswerGenerationPayloadOrchestrator.java` — SUCCESS + 拒答正文 outcome guard

## 1. Redline

| 指标 | 值 |
|------|----|
| BLOCKER | 0 |
| REVIEW | 已标记 |
| ALLOWLIST | 已标记 |
| Exit code | 0 |
| 结论 | 通过 |

## 2. mvn test

| 指标 | 值 |
|------|----|
| Tests run | 811 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Build | SUCCESS |

## 3. 服务可用性确认

| 检查项 | 结果 |
|------|------|
| 进程 | 已清理旧进程 (PID 33978, 33804)，重新在 18086 启动 |
| Health | `{"status":"UP"}` |
| Query 端点 | HTTP 200 |
| 启动方式 | `mvn spring-boot:run -Dspring-boot.run.profiles=local-dev --server.port=18086` |
| 库状态 | SWIP clean 库, `ai-rag-knowledge` |

## 4. 三轮 SWIP Strict Eval 指标

### 4.1 整体指标对比

| 指标 | Round 1 | Round 2 | Round 3 |
|------|---------|---------|---------|
| Pass | **14/23** (60.87%) | **14/23** (60.87%) | **13/23** (56.52%) |
| Recall@5 | 0.9783 | 0.9565 | 0.9783 |
| Recall@10 | 0.9783 | 0.9565 | 0.9783 |
| MRR | 0.9565 | 0.9783 | 0.9565 |
| citationPrecision | 0.7923 | 0.7944 | 0.7558 |
| llmSuccessRate | 0.9130 | 0.8696 | 0.8696 |
| fallbackRate | 0.0870 | 0.1304 | 0.1304 |
| avgCitationCoverage | 0.8247 | 0.8237 | 0.7851 |
| httpFailureRate | 0 | 0 | 0 |
| timeoutRate | 0 | 0 | 0 |

### 4.2 与历史基线对比

| 基线 | Pass | Recall@5 | llmSuccessRate | 说明 |
|------|------|----------|----------------|------|
| RRF revert stability R1 | 14/23 | 0.9348 | 0.8261 | RRF 回退后最佳 |
| RRF revert stability R2/R3 | 13/23 | 0.9348–0.9565 | 0.8696 | RRF 回退后典型 |
| **本轮 R1** | **14/23** | **0.9783** | **0.9130** | 持平历史最佳 |
| **本轮 R2** | **14/23** | **0.9565** | **0.8696** | 持平历史最佳 |
| **本轮 R3** | **13/23** | **0.9783** | **0.8696** | 持平历史典型 |

Recall@5/Recall@10 和 llmSuccessRate 均较 RRF 回退基线有明显改善。

## 5. 重点 Case 三轮矩阵

| Case ID | R1 | R2 | R3 | outcome | generation | 说明 |
|---------|----|----|----|---------|------------|------|
| SWIP-INSTALL-CERT-NAMING-001 | **PASS** | **PASS** | **PASS** | SUCCESS/SUCCESS/SUCCESS | LLM/LLM/LLM | 稳定，lead-in 修复目标达成 |
| SWIP-NEG-UNANSWERABLE-001 | **PASS** | **PASS** | **PASS** | INSUFFICIENT_EVIDENCE ×3 | LLM ×3 | 稳定，outcome guard 目标达成 |
| SWIP-FAQ-NO-RESPONSE-001 | FAIL | FAIL | FAIL | SUCCESS ×3 | LLM ×3 | 稳定 FAIL，始终缺"区域IT伙伴" |
| SWIP-INSTALL-APP-LIST-001 | PASS | PASS | FAIL | PARTIAL→INSUFFICIENT→PARTIAL | LLM ×3 | 波动，R3 大量列表项缺失 |
| SWIP-INSTALL-IP-SUFFIX-001 | **FAIL** | **FAIL** | **FAIL** | SUCCESS ×3 | LLM ×3 | **新增稳定回归**，均缺"151" |

## 6. 完整 Case 三轮矩阵

| Case ID | R1 | R2 | R3 | 稳定性 |
|---------|----|----|----|--------|
| SWIP-USAGE-GOAL-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-USAGE-SVC-READ-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-USAGE-BANK-REFUND-001 | FAIL | FAIL | FAIL | 稳定 FAIL |
| SWIP-USAGE-BANK-SETTLEMENT-001 | FAIL | FAIL | FAIL | 稳定 FAIL |
| SWIP-USAGE-REPRINT-001 | FAIL | PASS | PASS | **波动** (曾稳定 PASS) |
| SWIP-USAGE-SAND-SIGN-001 | FAIL | FAIL | FAIL | 稳定 FAIL |
| SWIP-USAGE-SAND-SETTLEMENT-001 | PASS | FAIL | FAIL | **波动** (曾稳定 FAIL) |
| SWIP-USAGE-DESHI-VOID-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-USAGE-EBUY-SETTLEMENT-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-FAQ-NO-RESPONSE-001 | FAIL | FAIL | FAIL | 稳定 FAIL |
| SWIP-FAQ-TERMINATE-STUCK-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-INSTALL-CERT-NAMING-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-INSTALL-APP-LIST-001 | PASS | PASS | FAIL | 波动 |
| SWIP-INSTALL-IP-SUFFIX-001 | FAIL | FAIL | FAIL | **新增稳定回归** (曾稳定 PASS) |
| SWIP-INSTALL-SWIP-POS-JSON-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-INSTALL-KEY-FILE-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-INSTALL-DLL-ORDER-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-INSTALL-LOGS-001 | FAIL | FAIL | FAIL | 稳定 FAIL |
| SWIP-INSTALL-CERT-UPDATE-001 | FAIL | FAIL | FAIL | 稳定 FAIL |
| SWIP-FAQ-PRINT-PAPER-001 | FAIL | FAIL | FAIL | 稳定 FAIL |
| SWIP-NEG-UNANSWERABLE-001 | PASS | PASS | PASS | 稳定 PASS |
| SWIP-NEG-UNANSWERABLE-002 | PASS | PASS | PASS | 稳定 PASS |

### 统计

| 类型 | 数量 |
|------|------|
| 稳定 PASS | 12 |
| 稳定 FAIL | 8 |
| 波动 (2/3) | 3 |
| **新增稳定回归** | **1** (IP-SUFFIX-001) |

## 7. 新增稳定回归

| Case ID | 历史状态 | 当前状态 | 失败原因 | outcome | 分析 |
|---------|---------|---------|---------|---------|------|
| SWIP-INSTALL-IP-SUFFIX-001 | RRF revert 三轮稳定 PASS | **三轮稳定 FAIL** | `answer_missing_term:151` | SUCCESS（未降级） | outcome guard 未触发（保持 SUCCESS），outcome 路径未变。lead-in 裁剪修复可能改变了 IP 后缀表格的呈现方式，导致 "151" 被省略。待归因。 |

REPRINT-001 虽非稳定回归，但 R1 出现了 `INSUFFICIENT_EVIDENCE` 降级（历史上为 PARTIAL_ANSWER/SUCCESS），说明 outcome guard 的 insufficient evidence 信号识别在该 case 的 LLM 回答上有边界触发。

## 8. 波动 Case 清单

| Case ID | R1 | R2 | R3 | 波动特征 |
|---------|----|----|----|---------|
| SWIP-USAGE-REPRINT-001 | FAIL | PASS | PASS | R1 outcome guard 误降级为 INSUFFICIENT_EVIDENCE |
| SWIP-USAGE-SAND-SETTLEMENT-001 | PASS | FAIL | FAIL | R1 偶发改善，回落典型 FAIL |
| SWIP-INSTALL-APP-LIST-001 | PASS | PASS | FAIL | 列表展开/省略的 LLM 内容波动 |

## 9. 是否建议保留当前两处代码改动

**建议保留。**

理由：

1. **目标 case 全部修复**：
   - CERT-NAMING-001：三轮稳定 PASS（lead-in 修复目标）
   - NEG-UNANSWERABLE-001：三轮稳定 PASS + 正确 `INSUFFICIENT_EVIDENCE` 语义（outcome guard 目标）

2. **整体 pass 率持平或改善**：13-14/23，与 RRF 回退基线一致；Recall@5 从 0.93 提升至 0.96-0.98，llmSuccessRate 从 0.83-0.87 提升至 0.87-0.91。

3. **IP-SUFFIX-001 回归不能直接归因于 outcome guard**：该 case 三轮 outcome 均为 SUCCESS（guard 未触发），失败原因纯粹是 answer text 缺少 "151"。更可能与 lead-in 裁剪逻辑对 IP 配置类结构化答案的段落选择变化有关。

4. **REPRINT-001 波动为 outcome guard 边界触发**：R1 被误降级为 INSUFFICIENT_EVIDENCE，但 R2/R3 恢复正常 PASS。说明 guard 的 insufficient evidence 信号对该 case 的部分 LLM 回答有边界敏感性。

5. **净收益为正**：以 1 个新增稳定回归（IP-SUFFIX）+ 1 个新增波动（REPRINT）的代价，换取了 2 个目标 case 的稳定修复 + Recall/llmSuccess 整体提升。

## 10. 风险提示

- outcome guard 的 `looksLikeInsufficientEvidenceAnswer()` 对 REPRINT-001 的部分回答存在边界误判（R1），后续可能需要收紧 insufficient evidence 信号匹配规则，但不建议当前回退。
- IP-SUFFIX-001 的三轮稳定 FAIL 需要在下一轮做只读归因，先确认是 lead-in 裁剪导致的段落选择变化，还是 LLM 生成波动。归因前不修改代码。

## 11. 本轮是否修改代码

**否。** 本轮仅：清理旧进程、重启服务、运行 redline、运行 mvn test、运行 3 轮 SWIP eval、写报告。未修改任何源码、测试、题集、配置、脚本。
