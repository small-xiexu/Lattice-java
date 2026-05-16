# SWIP RRF Revert Stability Verification Report

- **生成时间**: 2026-05-16
- **分支**: `codex/qa-polish`
- **Git commit**: d450796
- **验证目标**: 确认 11/23 是偶发波动还是稳定退化
- **代码变更**: 本轮未修改任何源码、测试、题集、配置、脚本

## 1. Redline 状态

| 指标 | 值 |
|------|----|
| BLOCKER | 0 |
| REVIEW | 已标记 |
| ALLOWLIST | 已标记 |
| 结论 | 通过，无阻断项 |

## 2. RrfFusionService.java 确认

`git diff -- src/main/java/com/xbk/lattice/query/service/RrfFusionService.java` 输出为空，确认无 diff。

## 3. 三轮 SWIP Strict Eval 汇总

### 3.1 整体指标

| 指标 | Round 1 | Round 2 | Round 3 |
|------|---------|---------|---------|
| Pass | **14/23** (60.87%) | **13/23** (56.52%) | **13/23** (56.52%) |
| Recall@5 | 0.9348 | 0.9348 | 0.9565 |
| Recall@10 | 0.9348 | 0.9348 | 0.9565 |
| MRR | 0.9783 | 0.9783 | 0.9783 |
| citationPrecision | 0.8905 | 0.8435 | 0.8725 |
| llmSuccessRate | 0.8261 | 0.8696 | 0.8696 |
| fallbackRate | 0.1304 | 0.1304 | 0.1304 |
| avgCitationCoverage | 0.9038 | 0.8607 | 0.8906 |
| httpFailureRate | 0 | 0 | 0 |
| timeoutRate | 0 | 0 | 0 |

### 3.2 逐 Case 三次结果矩阵

| Case ID | 分类 | R1 | R2 | R3 | 稳定性 |
|---------|------|----|----|----|--------|
| SWIP-USAGE-GOAL-001 | 系统目标 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-USAGE-SVC-READ-001 | SVC卡操作 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-USAGE-BANK-REFUND-001 | 银行卡操作 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-USAGE-BANK-SETTLEMENT-001 | 银行卡结算 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-USAGE-REPRINT-001 | 银行卡重印 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-USAGE-SAND-SIGN-001 | 杉德操作 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-USAGE-SAND-SETTLEMENT-001 | 杉德结算 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-USAGE-DESHI-VOID-001 | 得仕卡撤销 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-USAGE-EBUY-SETTLEMENT-001 | 银行积分 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-FAQ-NO-RESPONSE-001 | FAQ排障 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-FAQ-TERMINATE-STUCK-001 | FAQ排障 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-INSTALL-CERT-NAMING-001 | 证书配置 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-INSTALL-APP-LIST-001 | APP安装 | PASS | FAIL | PASS | **波动** |
| SWIP-INSTALL-IP-SUFFIX-001 | IP规则 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-INSTALL-SWIP-POS-JSON-001 | POS绑定 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-INSTALL-KEY-FILE-001 | 交易密钥 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-INSTALL-DLL-ORDER-001 | POS初始化 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | APP升级影响 | PASS | PASS | FAIL | **波动** |
| SWIP-INSTALL-LOGS-001 | 日志排查 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-INSTALL-CERT-UPDATE-001 | 证书更新 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-FAQ-PRINT-PAPER-001 | 耗材 | FAIL | FAIL | FAIL | **稳定 FAIL** |
| SWIP-NEG-UNANSWERABLE-001 | 无答案保护 | PASS | PASS | PASS | **稳定 PASS** |
| SWIP-NEG-UNANSWERABLE-002 | 无答案保护 | PASS | PASS | PASS | **稳定 PASS** |

### 3.3 统计

| 类型 | 数量 | Case 数 |
|------|------|---------|
| 稳定 PASS (3/3) | 12 | — |
| 稳定 FAIL (3/3) | 9 | — |
| 波动 (2/3) | 2 | APP-LIST-001, APP-UPGRADE-IMPACT-001 |
| **合计** | **23** | |

## 4. 稳定失败 Case 清单（9 个）

所有稳定失败 case 的失败原因均为 `answer_missing_term`，即 LLM 生成的回答中缺少预期的领域关键术语：

| Case ID | 缺失关键术语 |
|---------|------------|
| SWIP-USAGE-BANK-REFUND-001 | 参考号、原交易日期 |
| SWIP-USAGE-BANK-SETTLEMENT-001 | 日结、结算成功、小票 |
| SWIP-USAGE-SAND-SIGN-001 | 开店前 |
| SWIP-USAGE-SAND-SETTLEMENT-001 | 卡种 |
| SWIP-FAQ-NO-RESPONSE-001 | SNIFF、HTTPS服务、已启动、区域IT伙伴 |
| SWIP-INSTALL-CERT-NAMING-001 | swip-门店号-POS机号.starbucks.net、swip-https-门店号-POS机号.starbucks.net |
| SWIP-INSTALL-LOGS-001 | 6666、XBKSW、ebxbk、XBKYH、XBKXT、sand |
| SWIP-INSTALL-CERT-UPDATE-001 | 51/31天、50/30天、晚上11点、开机、SWIP网关APP |
| SWIP-FAQ-PRINT-PAPER-001 | 40mm、58mm、杉德、工坊 |

特征：全部为业务领域高度具体的术语/数字/代码。失败模式高度一致，三轮中缺失的术语完全相同。

## 5. 波动 Case 清单（2 个）

| Case ID | R1 | R2 | R3 | R2 失败原因 | R3 失败原因 |
|---------|----|----|----|------------|------------|
| SWIP-INSTALL-APP-LIST-001 | PASS | FAIL | PASS | answer_missing_term: SWIP APP Store、SWIP网关、资和信、易百、杉德、得仕卡、苏州市民卡 | — |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | PASS | PASS | FAIL | — | answer_missing_term: SWIP APP Store、SWIP网关APP |

波动模式分析：
- 两个 case 从未在同一轮同时失败
- APP-LIST-001 在 R1/R3 通过，R2 失败
- APP-UPGRADE-IMPACT-001 在 R1/R2 通过（R1 为 FALLBACK+DEGRADED 通过），R3 失败
- 失败原因与稳定失败 case 相同类别：业务术语遗漏

## 6. 是否恢复到 14/23 附近

**是，已确认**。14/23 在 3 轮中达成 1 次 (R1)，证明代码路径可达到此分数。

实际基线：
- **上限**：14/23 (60.87%)，出现在 1/3 轮
- **典型值**：13/23 (56.52%)，出现在 2/3 轮
- **历史低点**：11/23 (47.83%)，仅在早前单次观测中出现

## 7. 11/23 是偶发波动还是稳定退化

**结论：偶发波动，非稳定退化。**

依据：
1. 11/23 在三轮复跑中从未再现
2. 13-14/23 是当前正常波动区间
3. 所有失败都是 `answer_missing_term` 类型，属于 LLM 回答生成的非确定性波动
4. RRF Revert 后没有引入新的失败模式（失败 case 与之前基线一致）
5. 无 HTTP 错误、无超时、无 fallback 结构异常

## 8. 是否可以确认 RRF 主线收口

**可以确认。**

- RrfFusionService.java 无 diff
- BLOCKER = 0
- SWIP-USAGE-REPRINT-001（之前 RRF 修复引入的回归）在全部 3 轮中稳定 PASS
- 波动模式与 RRF 无关，属于 LLM 生成层面的非确定性
- Recall@5/Recall@10 三轮均 ≥ 0.93，检索层面稳定

## 9. 是否建议进入报告 cleanup

**建议进入。**

- RRF 代码变更已回退并验证稳定
- Redline 无阻断
- 当前分支有大量 `??` 报告文件待清理
- SWIP eval 基线已确认在 13-14/23 区间
- 继续保留中间报告不再有增量价值

## 10. 本轮是否修改代码

**否。** 本轮未修改任何源码、测试、题集、配置、脚本。仅运行只读的 redline 扫描和 eval 复跑。
