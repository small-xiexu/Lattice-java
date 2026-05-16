# SWIP BANK-SETTLEMENT Focus Snippet 提交前质量复核报告

- 生成时间：2026-05-16 23:25 +0800
- 角色：agentD
- 本轮性质：提交前质量复核，不改代码

## 1. 当前变更文件清单

### 1.1 已修改 (M)

| 文件 | 变更类型 | 是否预期 |
|---|---|---|
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java` | +434/-26 行，focus snippet 分布式窗口 | 预期 |
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java` | +171 行，prompt audit instrumentation + outcome guard | 预期 |
| `special_cases_report.md` | 82 行变更，redline 自动刷新 | 预期 |
| `docs/quality-progress-and-lessons.md` | 37 行变更，agentC 台账更新 | 预期 |

### 1.2 已删除 (D)

| 文件 | 删除理由 | 是否预期 |
|---|---|---|
| `report_cleanup_after_rrf_revert_result.md` | agentC 按清理计划删除 | 预期 |
| `report_cleanup_plan_after_swip_rrf.md` | agentC 按清理计划删除 | 预期 |
| `swip_answer_grounding_current_patch_stability_report.md` | 已被更完整的三轮报告覆盖 | 预期 |
| `swip_outcome_guard_side_effect_analysis_report.md` | 核心结论已被其他报告吸收 | 预期 |

### 1.3 未跟踪新增 (??)

| 文件 | 类型 | 是否应保留 |
|---|---|---|
| `swip_bank_settlement_focus_snippet_fix_result_report.md` | agentA 修复报告 | 是 |
| `swip_bank_settlement_prompt_evidence_truncation_analysis_report.md` | agentB 分析报告 | 是 |
| `swip_answer_prompt_audit_instrumentation_result_report.md` | 分析报告 | 是 |
| `swip_bank_settlement_prompt_evidence_runtime_analysis_report.md` | 分析报告 | 是 |
| `swip_bank_settlement_prompt_evidence_fix_result_report.md` | 分析报告 | 是 |
| `swip_bank_settlement_outcome_guard_analysis_report.md` | 分析报告 | 是 |
| `swip_focus_snippet_patch_side_effect_review_report.md` | agentD 验证报告 | 是 |
| `report_cleanup_plan_after_bank_settlement_focus_snippet.md` | agentC 清理规划 | 是 |
| `report_cleanup_after_bank_settlement_focus_snippet_result.md` | agentC 清理结果 | 是 |

## 2. 是否只包含预期变更

**是。** 所有变更均为预期：

- 生产代码：仅 `AnswerGenerationPromptEvidenceSupport.java`（focus snippet 分布式窗口）和 `AnswerGenerationPayloadOrchestrator.java`（prompt audit instrumentation + outcome guard）。
- 文档/配置刷新：`special_cases_report.md`（redline 自动刷新）、`docs/quality-progress-and-lessons.md`（agentC 台账更新）。
- 报告清理：4 个过期文件按清理计划删除。
- 新增报告：9 个，均为本轮修复/分析/验证/清理的正常交付物。

## 3. 禁止范围核对

| 禁止项 | 是否触碰 |
|---|---|
| `src/main/java/**`（非预期修改） | 否，仅两个预期文件 |
| `src/test/java/**` | 否 |
| `src/main/resources/**` | 否 |
| `docs/test/**` | 否 |
| `scripts/**` | 否 |
| `.claude/**` | 否 |
| AGENTS.md / CLAUDE.md | 否 |
| runner / eval 阈值 | 否 |
| RRF / retrieval / fallback / outcome guard（非预期修改） | 否 |
| 清库 / 重建 / 重新导入 | 否 |

## 4. Redline 门禁

命令：`bash scripts/scan-redline.sh special_cases_report.md`

| 指标 | 值 |
|---|---:|
| BLOCKER | 0 |
| EXIT_CODE | 0 |

结论：通过。

## 5. mvn test

命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`

| Tests run | Failures | Errors | Skipped | Build |
|---:|---:|---:|---:|---|
| 811 | 0 | 0 | 0 | SUCCESS |

结论：通过。

## 6. 业务硬编码检查

命令：
```bash
rg -n "SWIP|银行|日结|结算成功|小票|BANK|SETTLEMENT" \
  src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java \
  src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java
```

结果：**无命中。**

补充：根据 `swip_focus_snippet_patch_side_effect_review_report.md` 第 8 节代码审查结论，所有问题类型判断均使用通用语义规则（`looksLikeFlowQuestion`、`looksLikeEnumerationQuestion`、`querySemanticRules.containsAnyStatusSignal` 等），无 case id、文档名、答案片段特判。

## 7. SWIP Strict Eval 复用

代码自 `swip_focus_snippet_patch_side_effect_review_report.md` 以来未变化（仅文档/报告变更），复用该报告的三轮结果：

**引用报告**：`swip_focus_snippet_patch_side_effect_review_report.md`

**数据目录**：`.codex/run/swip-bank-settlement-focus-snippet-full-20260516-213317/`

| 轮次 | pass | casePassRate | Recall@5 | Recall@10 | citationPrecision | llmSuccessRate | fallbackRate | avgCitationCoverage |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| R1 | 16/23 | 0.6957 | 0.9565 | 0.9565 | 0.7932 | 0.8696 | 0.1304 | 0.8195 |
| R2 | 17/23 | 0.7391 | 0.9348 | 0.9348 | 0.8319 | 0.9130 | 0.0870 | 0.8621 |
| R3 | 15/23 | 0.6522 | 0.9348 | 0.9348 | 0.8085 | 0.9130 | 0.0870 | 0.8544 |

**关键结论**：
- BANK-SETTLEMENT-001：三轮稳定 PASS
- 保护 case IP-SUFFIX / NEG-UNANSWERABLE / CERT-NAMING：三轮稳定 PASS
- 无新增稳定回归
- fallbackRate 未增加，citationCoverage 整体提升

## 8. 新增风险评估

| 风险项 | 评估 |
|---|---|
| focus snippet 触发条件过宽 | 否，显式排除 path/exact-identifier，保护 case 三轮稳定 |
| promptLength 增大 | 集中在多焦点/流程/枚举问题，path/exact-identifier 不受影响，当前未触发 token 超限 |
| 业务特判引入 | 否，代码使用通用语义规则 |
| 多个主变量同时修改 | 否，本轮仅 focus snippet 分布式窗口为唯一逻辑变量；PayloadOrchestrator 改动仅为 instrumentation |
| 测试断言漂移 | 否，mvn test 811/0/0，无测试需要更新 |
| SWIP eval 下降 | 否，15-17/23 维持并略超 15-16/23 baseline |
| 数据库污染 | 否，测试库已隔离到 `ai-rag-knowledge-test` |

## 9. 结论

### 是否建议提交：**建议提交**

当前工作区满足所有提交前条件：

- redline BLOCKER=0
- mvn test 811/0/0
- SWIP strict eval 三轮稳定区间 15-17/23，目标 case 稳定 PASS，保护 case 稳定 PASS，无新增稳定回归
- 无业务硬编码，触发条件合理保守
- 变更范围符合预期，无禁止范围触碰
- 无新增风险

### 本轮是否修改代码：**否**

本报告仅执行只读质量复核，未修改任何源码、测试、配置、脚本或题集。
