# SWIP Answer Grounding 提交前质量复核报告

## 1. redline 结果

本轮执行 `bash scripts/scan-redline.sh special_cases_report.md`，结果如下：

| 项目 | 数量 | 结论 |
|---|---:|---|
| BLOCKER | 0 | 未发现阻塞提交的红线命中 |
| REVIEW | 1836 | 需要人工复核的通用中文/业务文本命中，未构成 BLOCKER |
| ALLOWLIST | 238 | 已进入候选白名单语义的低风险命中 |

结论：redline 当前允许继续提交前复核；本轮无需进入 redline BLOCKER 修复线。

## 2. 当前 git diff 摘要

当前工作区与本轮 patch 直接相关的 diff：

| 文件 | 变更摘要 | 复核结论 |
|---|---|---|
| `src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java` | structured/exact lookup 答案压缩保留 lead-in 后的结构化主体，并对顺序型补充段做有上限保留 | 属于通用后处理能力，未发现 case 特判 |
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java` | 对 `SUCCESS` 但正文表达证据不足的结构化答案做 outcome 下调 | 属于通用 outcome 归一化能力，但存在误降级残余风险 |
| `special_cases_report.md` | redline 扫描报告刷新 | 只看到扫描结果刷新，未修改扫描规则或 allowlist |

`git diff --stat` 显示 3 个文件变更，合计 `531 insertions`、`36 deletions`。本轮禁区检查未发现 `src/test/**`、`docs/test/**`、`src/main/resources/**`、`scripts/**`、`.claude/**`、`AGENTS.md`、`CLAUDE.md` 变更。

## 3. 本轮 patch 收益

来自 `swip_answer_grounding_patch_stability_verification_report.md` 的最新验证结果：

| 指标 | 结果 |
|---|---|
| redline | `BLOCKER=0 / REVIEW=1836 / ALLOWLIST=238` |
| `mvn test` | `811 / 0 / 0` |
| SWIP strict eval R1 | `15/23` |
| SWIP strict eval R2 | `16/23` |
| SWIP strict eval R3 | `15/23` |
| 新增稳定回归 | 无 |

目标 case 稳定性：

| Case | 三轮结果 | 结论 |
|---|---|---|
| `SWIP-INSTALL-IP-SUFFIX-001` | `3/3 PASS` | 已从稳定 FAIL 修回稳定 PASS |
| `SWIP-INSTALL-CERT-NAMING-001` | `3/3 PASS` | structured/exact lookup lead-in 修复收益稳定 |
| `SWIP-NEG-UNANSWERABLE-001` | `3/3 PASS` | outcome guard 修复收益稳定 |

整体判断：当前 patch 对目标问题有稳定正收益，且三轮完整 strict eval 未出现新增稳定回归。

## 4. 代码风险评审

### 4.1 PostProcessor 风险

`AnswerParagraphPostProcessor` 的核心变化是：

- 对 structured/exact lookup 答案，避免只保留引导句而裁掉结构化主体。
- 对顺序、步骤、调整类问题，在 `dangling lead-in + structured body` 后允许继续保留同一答案主体的补充段。
- 使用 `MAX_SEQUENTIAL_COMPRESSED_PARAGRAPHS = 5` 做上限，避免无限扩张答案。
- 仍要求压缩结果包含 citation literal，否则回退原答案。

复核结论：

- 未发现 `SWIP`、`IP`、`151`、`POS`、文件名、题目文本、答案片段等 case 特判。
- 规则基于 Markdown 结构、citation、顺序/步骤语义与段落形态，属于通用后处理能力。
- 残余风险是 `containsSequentialActionSignal(...)` 中存在 Java 主链内置的中文通用动作词。它不是特定业务特判，但与项目约定“中文问法识别优先配置化”的长期治理方向不完全一致。当前 redline 未将其列为 BLOCKER，且已有三轮完整 eval 兜底，因此不建议因此回退。

### 4.2 Outcome guard 风险

`AnswerGenerationPayloadOrchestrator` 的核心变化是：

- 当结构化载荷声明 `SUCCESS`，但答案正文首行或结论行明显表达证据不足时，下调为 `INSUFFICIENT_EVIDENCE`。
- 触发依据是通用拒答/证据不足信号，而不是具体 case、文档或答案内容。
- 不改变 `PARTIAL_ANSWER` 升级逻辑，只补上 `SUCCESS` 过度乐观时的下调路径。

复核结论：

- 未发现 `SWIP`、业务域、具体题目、具体答案片段特判。
- 该 guard 修复了 `SWIP-NEG-UNANSWERABLE-001` 暴露出的 outcome normalization 问题，方向符合“拒答正文不能标 SUCCESS”的通用治理原则。
- 残余风险是 guard 对中文拒答信号较敏感，`SWIP-USAGE-BANK-SETTLEMENT-001` 三轮均为 `INSUFFICIENT_EVIDENCE`，疑似存在过度降级。该风险应作为下一轮单独归因，不建议在本轮扩大或回退。

### 4.3 是否存在过宽规则

PostProcessor 有结构化主体判断、citation 保底和最多 5 段的上限，当前未见阻塞性过宽。Outcome guard 的过宽风险高于 PostProcessor，已有一个疑似误降级遗留 case，但最新三轮验证显示它未造成新增稳定回归，也未破坏目标无答案保护。

## 5. 禁区检查

| 禁区 | 是否变更 | 结论 |
|---|---:|---|
| `src/test/java/**` | 否 | 未修改测试断言 |
| `docs/test/**` | 否 | 未修改 eval 题集或期望 |
| `src/main/resources/**` | 否 | 未修改配置 |
| `scripts/**` | 否 | 未修改 eval / baseline / runner 脚本 |
| `scripts/scan-redline.sh` | 否 | 未修改 redline 脚本 |
| redline allowlist | 否 | 未扩大 allowlist |
| `.claude/**` | 否 | 未修改 agent 配置 |
| `AGENTS.md` / `CLAUDE.md` | 否 | 未修改项目治理规则 |

结论：本轮 patch 未触碰禁止修改范围。

## 6. 遗留问题判断

| 遗留问题 | 当前现象 | 是否阻塞本 patch |
|---|---|---|
| `SWIP-USAGE-BANK-SETTLEMENT-001` | 三轮均为 `INSUFFICIENT_EVIDENCE`，疑似 outcome guard 过度降级 | 不阻塞代码收益判断，但应作为下一轮唯一质量主线 |
| `REPRINT` 波动 | 验证报告中表现为单轮内容波动 | 不阻塞，未形成新增稳定回归 |
| 其他稳定 FAIL | 属于本 patch 前已存在的存量失败 | 不阻塞，本轮目标不是一次性修完 SWIP |

判断：遗留问题不要求回退当前 patch，也不要求本轮继续修代码。`BANK-SETTLEMENT` 的风险需要单独分析，避免把 outcome guard 扩大或缩小成多变量修复。

## 7. 最终结论

最终结论：**暂不提交，先做一个最小动作**。

代码与验证层面，当前 patch 已具备保留条件：redline `BLOCKER=0`、`mvn test=811/0/0`、目标 case 三轮稳定 PASS、无新增稳定回归，不建议回退，也不需要先补验证。

暂不直接提交的原因不是代码 gate 失败，而是项目质量台账仍停留在旧状态。`docs/quality-progress-and-lessons.md` 当前未同步本轮 answer grounding patch 的最新 gate、收益、残余风险和下一步主线；按照项目级质量打磨台账要求，提交前应先同步该文档。

## 8. 提交前清理建议

建议保留的报告：

| 报告 | 保留原因 |
|---|---|
| `swip_stable_answer_missing_terms_analysis_report.md` | 记录本轮 answer grounding 主线的初始 9 个稳定失败归因 |
| `swip_structured_exact_lookup_leadin_fix_result_report.md` | 记录 lead-in / structured body 裁剪修复结果 |
| `swip_unanswerable_regression_analysis_report.md` | 记录无答案回归根因 |
| `swip_unanswerable_outcome_guard_fix_result_report.md` | 记录 outcome guard 修复结果 |
| `swip_ip_suffix_regression_analysis_report.md` | 记录 IP-SUFFIX 稳定回归根因 |
| `swip_ip_suffix_postprocessor_fix_result_report.md` | 记录 IP-SUFFIX 后处理修复结果 |
| `swip_answer_grounding_patch_stability_verification_report.md` | 当前最重要的最终门禁与三轮稳定性报告 |
| `swip_answer_grounding_pre_commit_quality_review_report.md` | 本提交前质量复核结论 |

可在报告清理轮考虑归档或删除的过期中间报告：

| 报告 | 处理建议 |
|---|---|
| `swip_answer_grounding_current_patch_stability_report.md` | 已被最终三轮 verification 报告覆盖，可清理 |
| `swip_outcome_guard_side_effect_analysis_report.md` | 若最终报告链需要精简，可在确认其结论已被后续报告吸收后清理；若要保留 outcome guard 副作用追溯，则继续保留 |

需要更新 `docs/quality-progress-and-lessons.md`：是。建议记录最新 gate 为 SWIP strict eval `15-16/23` 区间、当前 patch 的两个生产改动、三个目标 case 已稳定修复、无新增稳定回归，以及下一轮唯一候选为 `BANK-SETTLEMENT` outcome guard 过度降级归因。

## 9. 下一步唯一最小动作

下一步只建议做一个最小动作：由文档/报告 agent 同步更新 `docs/quality-progress-and-lessons.md`，把当前 answer grounding patch 的 gate、收益、残余风险和下一轮唯一主线写入质量台账。

完成该动作后，若工作区仍只包含当前两处生产代码改动、redline 报告刷新与必要报告文件，则可以进入提交。

## 10. 本轮是否修改代码

否。本轮只新增 `swip_answer_grounding_pre_commit_quality_review_report.md`，未修改生产代码、测试、配置、题集、脚本、redline 规则或数据库。
