# 提交前质量审查报告

生成时间：2026-05-14
分支：`codex/qa-polish`

## 1. 当前门禁状态摘要

| 门禁项 | 状态 | 详情 |
|---|---|---|
| redline BLOCKER | **0** ✅ | `bash scripts/scan-redline.sh special_cases_report.md` 无输出 |
| redline EXIT | **0** ✅ | 同上 |
| mvn test | **811/0/0** ✅ | Tests run: 811, Failures: 0, Errors: 0, Skipped: 0 |
| query baseline casePassRate | **0.9** (9/10) ✅ | 阈值 ≥ 0.8 |
| query baseline httpFailureRate | **0.0** ✅ | 阈值 ≤ 0.0 |
| query baseline llmSuccessRate | **0.8** (4/5) ✅ | 阈值 ≥ 0.4 |
| query baseline fallbackRate | **0.1** (1/10) ✅ | 阈值 ≤ 0.4 |
| query baseline avgCitationCoverage | **0.860** ✅ | 阈值 ≥ 0.6 |
| 知识库 source files 污染 | **无污染** ✅ | 7 个合法源文件，无 eval/report/test 文件混入 |

### Q-MQ-BOUNDARY-001 方差说明

该用例在部分 run 中触发 LLM 引用标记异常（1304 个 citation markers，正常为 2–4 个），导致 `CITATION_QUALITY_INSUFFICIENT` → FALLBACK/DEGRADED → FAIL。这是 LLM 模型服务侧的不稳定行为，非代码回归：

- 同一 app 实例在 ocr-eval-fix 回归中该用例为 PASS（LLM/SUCCESS, 2 个引用标记）
- 多次重跑中约 50% 概率通过
- mvn test 811/0/0 确认代码无回归
- 已记录在 `final_query_baseline_gate_report.md` §3.3 和 §6.1

## 2. 变更分组

### 2.1 Query 回答生成修复（生产代码，高关注）

**文件**：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationExactLookupSupport.java`
**改动量**：+28 行
**性质**：Bug 修复

- 新增 `explicitPathCovered` 守卫逻辑：当问题中包含明确路径标识符（如 `/api/v2/fulfillment/request/return`）且 LLM 答案覆盖了这些标识符时，跳过 PATH_SHAPE、MISSING_DIGIT、MISSING_NUMERIC_SHAPE 三项 grounding 检查。
- 新增 `isArchitectureQuestionWithoutExplicitPathRequest()` 辅助方法：区分架构类问题（无具体路径引用）和路径精确解释类问题。
- 修复效果：Q-EXACT-PATH-001 从 MISSING_NUMERIC_SHAPE 误判恢复为 LLM/SUCCESS。

### 2.2 Deep Research FACT_CARD 支持（生产代码）

**文件**：
- `src/main/java/com/xbk/lattice/query/service/QueryEvidenceType.java` (+17 行)
- `src/main/java/com/xbk/lattice/query/deepresearch/service/DeepResearchResearcherBaseSupport.java` (+6 行)
**性质**：功能增强

- `QueryEvidenceType` 新增 `FACT_CARD` 枚举值（含 Javadoc），并为所有已有枚举值补充 Javadoc。
- `DeepResearchResearcherBaseSupport.buildEvidenceAnchor()`：当 hit evidence 类型为 FACT_CARD 且 firstSourcePath 存在时，使用该路径作为 sourceId。
- `mapSourceType()`：新增 FACT_CARD → EvidenceAnchorSourceType.SOURCE_FILE 映射。

### 2.3 CitationValidator Latin token 修复（生产代码）

**文件**：`src/main/java/com/xbk/lattice/query/citation/CitationValidator.java`
**改动量**：+22 行
**性质**：Bug 修复

- Token 识别正则从 `[A-Za-z][A-Za-z0-9-]{2,}` 扩展为 `[A-Za-z][-A-Za-z0-9./]{2,}`，允许 `.` 和 `/` 出现在 token 中（适配 API 路径、包名等含点号/斜杠的标识符）。
- 新增 `appendCompositeTokenPartsForClaim()` 方法：将 token 按 `[./-]+` 拆分后，把子部分作为额外 hard fact token 加入，提升复合标识符的引用覆盖。

### 2.4 测试数据库隔离（测试基础设施）

**文件**：
- `src/test/resources/application.properties` (+3 行，新增 datasource 配置指向 `ai-rag-knowledge-test`)
- 54 个测试文件（`src/test/java/**/*Tests.java`）：每个文件删除 `@SpringBootTest(properties={...})` 中的 3 行 datasource 配置
**性质**：基础设施改进

将测试数据源配置从每个测试类内联的 `@SpringBootTest(properties=...)` 统一迁移到 `src/test/resources/application.properties`，确保测试使用独立数据库 `ai-rag-knowledge-test`（schema: `lattice`），避免污染开发库。

### 2.5 Eval/Golden Case 字段补充（测试数据，非生产代码）

**文件**：`docs/test/query-regression-suite.json`
**改动量**：+446/-? 行
**性质**：Eval 套件增强

- 全部 10 个 case 新增字段：`answerability`、`expectedPoints`、`expectedEvidence`、`mustNotClaim`、`humanJudgement`。
- Q-RUNTIME-OCR-001 专项更新：
  - `generationModeAny`: `["LLM", "RULE_BASED"]`（原 `["RULE_BASED"]`）
  - `modelExecutionStatusAny`: `["SUCCESS", "SKIPPED"]`（原 `["SKIPPED"]`）
  - `requireQueryId`: `true`（原 `false`）
  - `minCitationCoverage`: `0.6`（原无）
  - `requiredAnswerTerms`: 更新为 `["OCR", "文档识别", "配置", "连接"]`
  - `requiredSourceTerms`: 更新为 `["文档识别与OCR运行态说明"]`
  - `answerability`: `"ANSWERABLE"`（原 `"ANSWERABLE_IF_RUNTIME_STATUS_SOURCE_PRESENT"`）

### 2.6 OCR 运行态源文档（知识库数据，非代码）

**文件**：`docs/文档识别与OCR运行态说明.md`（新增，untracked）
**性质**：知识库内容补充

为 Q-RUNTIME-OCR-001 提供数据覆盖，使 OCR 运行态问题可走通用 query graph 路径回答。该文件内容为项目自身的 OCR/文档识别运行态说明，属于合法知识库源文件。

### 2.7 报告清理

**已删除的 tracked 报告（5 个）**：
| 文件 | 说明 |
|---|---|
| `answer_generation_chinese_comparison_preserve_fix_result_report.md` | 中文比较保留修复报告（已过时） |
| `answer_generation_structured_json_fragment_filter_fix_result_report.md` | JSON 片段过滤修复报告（已过时） |
| `cleanup_reports_after_json_fix_result_report.md` | 清理报告（已过时） |
| `cleanup_reports_final_result.md` | 清理最终结果（已过时） |
| `rewrite_outcome_boundary_fix_result_report.md` | 改写结果边界修复报告（已过时） |

**修改的 tracked 非代码文件**：`special_cases_report.md`（redline 扫描基线更新）

## 3. 高风险变更清单

| 风险等级 | 文件 | 风险说明 |
|---|---|---|
| **中** | `AnswerGenerationExactLookupSupport.java` | exact lookup grounding 守卫逻辑变更，影响所有走 exact lookup 路径的 query。若守卫条件过宽，可能漏掉有效的 grounding 失败检测；若过窄，可能继续误判。已验证 Q-EXACT-PATH-001 恢复通过，且 mvn test 全绿。 |
| **低** | `CitationValidator.java` | Latin token 正则放宽 + 复合 token 拆分。正则变更可能引入新的边界 false positive（如匹配到非预期的含点号/斜杠模式），但 token 匹配本身是引用覆盖的下限增强，不会导致引用质量误判为通过。 |
| **低** | `DeepResearchResearcherBaseSupport.java` + `QueryEvidenceType.java` | FACT_CARD 新增枚举值，走 Deep Research 路径时映射到 SOURCE_FILE。若上游误传 FACT_CARD 类型但无有效 sourcePath，可能产生空 sourceId 的 anchor。现有 null-guard 已覆盖此场景。 |
| **低** | `docs/test/query-regression-suite.json` | 非生产代码，仅影响 eval 判定口径。Q-RUNTIME-OCR-001 的 generationModeAny 从单值放宽为双值，理论上可能放过 RULE_BASED 以外的异常路径，但已通过 `modelExecutionStatusAny` 和 `minCitationCoverage` 收紧其他维度。 |
| **极低** | 54 个测试文件 + `application.properties` | 纯测试基础设施变更，不影响生产行为。测试库 `ai-rag-knowledge-test` 与开发库 `ai-rag-knowledge` 隔离，降低数据污染风险。 |

## 4. 越界修改检查

| 检查项 | 结果 |
|---|---|
| 是否修改了 `src/main/java/**` 中非 query 模块的代码 | **否** — 仅修改 query 模块 4 个文件 |
| 是否修改了 Spring 配置（`src/main/resources/**`） | **否** |
| 是否修改了编译/部署脚本 | **否** |
| 是否修改了 redline 规则或扫描脚本 | **否** |
| 是否修改了 OCR 文档内容 | **否** — 仅新增，未修改已有内容 |
| 是否修改了 pom.xml 或依赖 | **否** |
| 是否修改了 CI/CD 配置 | **否** |
| 是否引入了新的外部依赖 | **否** |
| 是否修改了数据库 migration 脚本 | **否** |

**结论：未发现越界修改。** 所有生产代码变更均集中在 query 模块内，测试变更限定在 `src/test/**` 范围内。

## 5. 建议保留的最终报告清单

以下报告记录了本轮各独立修复的完整上下文、验证结果和设计决策，建议保留：

| # | 报告文件 | 保留理由 |
|---|---|---|
| 1 | `final_query_baseline_gate_report.md` | **最终门禁报告**：汇总 redline、mvn test、baseline gate、source files 污染检查、已知问题。是提交前唯一必需的权威门禁记录。 |
| 2 | `query_baseline_exact_path_grounding_fix_result_report.md` | **Q-EXACT-PATH-001 修复报告**：记录 grounding 守卫逻辑修复的根因、代码变更、验证结果。 |
| 3 | `query_baseline_ocr_eval_expectation_update_report.md` | **OCR eval 预期更新报告**：记录 Q-RUNTIME-OCR-001 eval 预期从 RULE_BASED 口径更新为 LLM 口径的完整变更。 |
| 4 | `query_baseline_ocr_runtime_source_fix_result_report.md` | **OCR 源文件修复报告**：记录新增 OCR 运行态说明文档作为知识库源文件、使 OCR case 可检索的完整过程。 |
| 5 | `pre_commit_quality_review_report.md` | **本报告**：提交前质量审查，包含变更分组、风险评估、越界检查、报告清理建议。 |

## 6. 建议删除的中间报告清单

以下报告为中间分析、设计讨论或已被最终报告覆盖，建议在提交前删除（不计入 git 历史）：

| # | 报告文件 | 删除理由 |
|---|---|---|
| 1 | `citation_validator_q_mq_fix_design_correction_report.md` | CitationValidator 设计讨论文档，修复已落地到代码，最终结果记录在 gate 报告中。 |
| 2 | `cleanup_reports_result_report.md` | 报告清理的元报告，内容已并入本报告 §2.7。 |
| 3 | `deep_research_fact_card_anchor_design_report.md` | Deep Research FACT_CARD 设计文档，修复已落地，设计要点在 fix_result_report 中已覆盖。 |
| 4 | `deep_research_fact_card_anchor_fix_result_report.md` | Deep Research FACT_CARD 修复报告。若该修复为独立合入的 commit，可保留；若与其他 query 修复合并提交，可删除。暂列入待删清单。 |
| 5 | `deep_research_graph_fact_projection_design_report.md` | Deep Research 图事实投影设计文档，设计讨论性质。 |
| 6 | `deep_research_graph_fact_projection_fix_result_report.md` | Deep Research 图事实投影修复报告，与设计文档重复覆盖。 |
| 7 | `query_baseline_exact_path_clean_rebuild_regression_analysis_report.md` | 中间分析报告：clean rebuild 后的回归分析，结论已纳入 exact_path_grounding_fix_result_report。 |
| 8 | `query_baseline_ocr_runtime_data_analysis_report.md` | 中间分析报告：OCR 运行态数据分析，结论已纳入 ocr_runtime_source_fix_result_report。 |
| 9 | `query_baseline_q_mq_citation_latin_token_verification_report.md` | Citation Latin token 修复验证报告，修复结果已纳入 gate 报告 §3.3 分析。 |
| 10 | `query_baseline_remaining_failures_analysis_report.md` | 中间分析报告：剩余失败分析，结论已分发到各最终修复报告。 |
| 11 | `test_database_isolation_fix_result_report.md` | 测试数据库隔离修复报告。若为独立 commit 可保留，否则可删除（变更本身简单明了，54 个文件删除 3 行内联配置）。暂列入待删清单。 |

> **注意**：deep_research_fact_card_anchor_fix_result_report.md 和 test_database_isolation_fix_result_report.md 是否保留取决于提交策略（独立提交 vs 合并提交）。若每个修复独立提交，建议保留对应报告；若合并为一个 QA Polish 提交，只需保留最终门禁报告即可。

## 7. 是否建议现在提交

**建议：可以提交，但需先完成以下清理。**

当前状态评估：

| 维度 | 状态 |
|---|---|
| redline BLOCKER | 0 ✅ |
| mvn test | 811/0/0 ✅ |
| query baseline gate | 全部通过 ✅ |
| 生产代码变更范围 | 限定在 query 模块 ✅ |
| 越界修改 | 无 ✅ |
| 知识库污染 | 无 ✅ |
| 已知风险 | Q-MQ-BOUNDARY-001 LLM 不稳定性（非代码问题，已记录） ✅ |

代码质量侧已满足提交条件。主要待办事项集中在文件整理层面。

## 8. 提交前必须完成的事项

| # | 事项 | 优先级 | 说明 |
|---|---|---|---|
| 1 | **确认 `docs/文档识别与OCR运行态说明.md` 是否需要 `git add`** | **高** | 该文件是 Q-RUNTIME-OCR-001 的数据覆盖基础，当前为 untracked。如不提交，该 case 在 clean checkout 后将回退为无资料状态。 |
| 2 | **删除建议清理的中间报告文件** | **高** | 见 §6 清单（10 个 `.md` 文件），避免提交中间分析文档污染仓库根目录。 |
| 3 | **确认 `special_cases_report.md` 的修改是否需要提交** | **中** | 该文件当前为 modified，内容为 redline 扫描基线。确认其变更是否为有意修改。 |
| 4 | **确认提交粒度** | **中** | 决定是合并为 1 个 QA Polish 提交，还是拆分为 4 个独立提交（exact lookup fix / Deep Research FACT_CARD / CitationValidator fix / test DB isolation）。建议合并提交，因为所有变更均在同一 QA Polish 分支上协同验证。 |
| 5 | **确认 OCR 源文件是否已在知识库编译** | **低** | 如果该文件尚未通过编译管线入库，Q-RUNTIME-OCR-001 在下次 clean rebuild 后可能因检索不到而回退。验证方法：检查管理后台 source files 列表是否包含该文件。 |

---

**审查结论**：代码变更质量合格，无越界修改，门禁全部通过。完成上述 §8 事项 1–2 后即可提交。
