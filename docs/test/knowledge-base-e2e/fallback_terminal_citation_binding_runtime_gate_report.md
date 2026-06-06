# FALLBACK Terminal Citation Binding — Runtime Gate 验证报告

验证时间：2026-06-06 10:45 ~ 11:15
执行人：agentD（验证 Agent）
修复报告：`fallback_terminal_citation_binding_fix_result_report.md`（agentA）
前置分析：`fallback_terminal_citation_binding_analysis_report.md`（agentB）
对比基线：`recall_citation_metrics_collection_report.md`（agentD, 2026-06-05）

---

## 1. 验证范围

验证 `CitationValidator` terminal unit evidence 验证路径是否提升 PE2 FALLBACK 模式的 citation coverage，并确认无答案/搜索回归。

---

## 2. Git Status

仅 `special_cases_report.md`（redline 输出）修改。修复涉及文件：

| 文件 | 类型 |
|---|---|
| `CitationValidator.java` | 生产代码（terminal unit evidence 验证） |
| `CitationValidatorTests.java` | 测试（18 个，含 6 个新增） |
| 6 个其他测试文件 | 构造器参数适配 |

---

## 3. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| CitationValidatorTests | **18/0/0/0, BUILD SUCCESS** |
| 全量 mvn test | **BUILD SUCCESS** |

---

## 4. Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 4/5（PDF 未上传，FQ10 BLOCKED） |
| compile jobs | 4，全部 SUCCEEDED |
| review queue | **0**（全部 auto-published） |
| 服务端口 | 18082 |

---

## 5. FALLBACK Citation Coverage 修复前后对比

| 题号 | claim | 修复前 cov | 修复后 cov | 修复前 status | 修复后 status | 变化 |
|---|---|---|---|---|---|---|
| FQ3 | `max_borrow_days = 7` | 1.0 | **1.0** | source_near_complete | **terminal_unit_evidence_near_complete_verified** | 保持，路径改善 |
| FQ4 | `deposit_amount = 100` | 0.0 | **1.0** | DEMOTED | **VERIFIED** | **+1.0** ✅ |
| FQ4 | `deposit_amount = 1000` | 0.0 | **1.0** | DEMOTED | **VERIFIED** | **+1.0** ✅ |
| FQ5 | `api_endpoint = https://...` | 1.0 | **1.0** | source_near_complete | **terminal_unit_evidence_verified** | 保持 |
| FQ6 | `version = v2.3.1` | 1.0 | **1.0** | source_near_complete | **terminal_unit_evidence_verified** | 保持 |
| FG1 | `late_fee_per_day = 20` | 0.5 | **1.0** | mixed | **VERIFIED** | **+0.5** ✅ |
| FG1 | `late_fee_per_day = 5` | 0.5 | **1.0** | mixed | **VERIFIED** | **+0.5** ✅ |
| FG2 | `max_concurrent_requests = 50` | 0.0 | **0.0** | DEMOTED (0.600) | **DEMOTED** (0.600) | 未改善 |

---

## 6. 重点题明细

### 6.1 FQ4（deposit_amount 100 + 1000）

| queryId | answerOutcome | mode | coverage |
|---|---|---|---|
| d9b89c16 | SUCCESS | FALLBACK | **1.0**（verified=2, demoted=0, claims=2） |

**citation claim 明细**：

| claim | status | reason | overlap | excerpt |
|---|---|---|---|---|
| `equipment_types[0].deposit_amount = 100` | VERIFIED | terminal_unit_evidence_near_complete_verified | 0.6667 | `equipment_types[0].deposit_amount = 100` |
| `equipment_types[2].deposit_amount = 1000` | VERIFIED | terminal_unit_evidence_near_complete_verified | 0.6667 | `equipment_types[2].deposit_amount = 1000` |

terminal unit evidence 路径命中：✅ 两条 claim 均通过 terminal unit evidence 验证。

### 6.2 FG1（late_fee_per_day 20 + 5）

| queryId | answerOutcome | mode | coverage |
|---|---|---|---|
| 187e66ff | SUCCESS | FALLBACK | **1.0**（verified=2, demoted=0, claims=2） |

| claim | status | reason |
|---|---|---|
| `equipment_types[1].late_fee_per_day = 20` | VERIFIED | terminal_unit_evidence_verified |
| `equipment_types[0].late_fee_per_day = 5` | VERIFIED | terminal_unit_evidence_verified |

terminal unit evidence 路径命中：✅

### 6.3 FG2（max_concurrent_requests = 50）— 残留

| queryId | answerOutcome | mode | coverage |
|---|---|---|---|
| 65999750 | PARTIAL_ANSWER | FALLBACK | **0.0**（verified=0, demoted=1, claims=1） |

| claim | status | reason | overlap |
|---|---|---|---|
| `borrowing_system.max_concurrent_requests = 50` | DEMOTED | source_insufficient_overlap | 0.600 |

terminal unit evidence 路径：**未命中**。走回原有 source file overlap 路径（0.600 < 阈值）。

数据库已确认 terminal unit 存在（`borrowing_system.max_concurrent_requests = 50`），且 claim value "50" 与 unit valueText "50" 一致。`claimValueMatchesUnit` 应通过。根因待进一步定位（可能为 tokenization 或 overlap 计算边界），不阻塞本轮主要结论。

### 6.4 FQ3/FQ5/FQ6（保护成功）

| 题号 | queryId | cov | answer |
|---|---|---|---|
| FQ3 | 5b13b756 | **1.0** | `max_borrow_days = 7` ✅ |
| FQ5 | 28ded667 | **1.0** | `api_endpoint = https://...` ✅ |
| FQ6 | b91e2c26 | **1.0** | `version = v2.3.1` ✅ |

---

## 7. 保护性回归

### 7.1 答案回归

| 题号 | outcome | mode | 判定 |
|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | 不变（一致） |
| FQ2 | PARTIAL_ANSWER | LLM | 不变（一致） |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | 不变（一致） |

**无 PASS→FAIL 回归。**

### 7.2 搜索回归

| 题号 | rank1 | 判定 |
|---|---|---|
| FS1 | 校园实验室安全管理手册 | **PASS** |
| FS2 | 校园实验室安全管理手册 | **PASS** |
| FS3 | 校园实验室安全管理手册 | **PASS** |
| FS4a | 协同手册 / 职责分工 | **PASS** |
| FS4c | equipment maintenance schedule | **PASS** |

**搜索 6/6 保持。**

### 7.3 Mixed Script

| 搜索词 | 结果数 | 判定 |
|---|---|---|
| B级 | 2 | **PASS** |
| B 级 | 2 | **PASS** |

---

## 8. Hallucination Count

**0**。无编造。

---

## 9. Query 红线风险检查

| 检查项 | 结果 |
|---|---|
| 是否写入题号/业务词/文档名/字段名？ | **否**（仅通用 SOURCE_FILE 类型判断） |
| 是否修改 AnswerFallbackConclusionBuilder？ | **否** |
| 数据来源是否为通用表结构？ | **是**（`fact_card_terminal_units`，所有事实卡类型共有） |
| 不匹配时是否回退现有路径？ | **是**（返回 null） |

---

## 10. 最终结论

### **PASS**（FQ4 + FG1 citation coverage 修复成功，无回归，FG2 为已知残留）

| 维度 | 判定 |
|---|---|
| Redline | **BLOCKER=0** |
| CitationValidatorTests | **18/0/0/0** |
| 全量 mvn test | **BUILD SUCCESS** |
| FQ4 citation | **0.0 → 1.0**（terminal unit evidence 路径命中） |
| FG1 citation | **0.5 → 1.0**（terminal unit evidence 路径命中） |
| FG2 citation | **0.0 → 0.0**（残留，terminal unit 存在但验证未通过） |
| FQ3/FQ5/FQ6 保护 | **保持 1.0** ✅ |
| Answer Accuracy 回归 | **无** |
| Search Accuracy 回归 | **无** |
| Mixed Script 回归 | **无** |
| Hallucination | **0** |

---

## 11. FG2 残留的下一步建议

**最小方向**：只读审计 FG2 claim `borrowing_system.max_concurrent_requests = 50` 在 `validateAgainstTerminalUnitEvidence` 中的完整执行路径。

- terminal unit 存在（displayText 匹配、valueText 匹配均已确认）
- `claimValueMatchesUnit` 大概率通过（value "50" == "50"）
- 疑点可能在 `calculateOverlapScore` 或 `isHighConfidencePartialOverlap` 边界：
  - `borrowing_system` token 被下划线分割后的 token 集合与 evidence text 的 overlap 计算
  - 0.6667（FQ4）可触发 near_complete，但 FG2 的相似 overlap 值走回了 source overlap（0.600）而非 terminal unit 路径
- 建议在 `validateAgainstTerminalUnitEvidence` 中加临时的 DEBUG 日志，抓取该 claim 的 `hardFactTokens`、`evidenceText`、`overlapScore`，确认是否通过了 `claimValueMatchesUnit` gate 并计算了正确的 overlap

---

## 12. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 scripts/config/schema/题集/redline allowlist
- [x] 未提交 commit
- [x] 所有结论基于 runtime API citationCheck 数据 + DB 只读查询
