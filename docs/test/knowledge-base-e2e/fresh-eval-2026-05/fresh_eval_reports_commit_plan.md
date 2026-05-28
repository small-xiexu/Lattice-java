# fresh eval 报告归档审计与提交计划

更新时间：2026-05-28

本轮同步 fresh eval 最新结论到台账，并补齐 scoped docs commit 前验证码；不修改生产代码、不 stage、不 commit、不 push；未读取或修改 `docs/模型绑定配置参考.md`。

## 结论摘要

- `fresh-eval-2026-05` 当前形成了三类文档：首轮验收基线、三轮失败实验记录、下一步 terminal unit 物化设计。
- 建议提交 fresh eval 报告链路本身，原因是它完整记录了从“结构化字段已召回但答案漏点”到“query fallback 方向连续失败，需转向 evidence unit 粒度”的决策过程。
- `eval-validation-roadmap.md` 与 `docs/quality-progress-and-lessons.md` 已同步当前结论：fresh eval 2 未通过，三轮 query fallback 实验均失败，下一步改为 terminal unit 第一阶段。
- 明确排除 `docs/模型绑定配置参考.md`、`special_cases_report.md`、`.DS_Store`、`target/**`、`.codex/**`。

## 最新同步结论

1. fresh eval 2 当前正式基线仍以 `acceptance-report.md` 为准。
2. 当前 fresh eval 2 未通过。
3. 结构化 terminal value 题 `FQ3/FQ4/FQ6/FG1/FG2` 仍是 `0/5 PASS`。
4. structured fact terminal binding、selector gate、conclusion gate 三轮 query fallback 方向实验均失败，不建议提交对应代码。
5. 失败根因已从 retrieval 召回转向 evidence unit 粒度：FACT_CARD 已召回，但整卡粒度导致 sibling 字段抢答。
6. 下一步方向改为 compile/index 层 structured terminal assignment evidence unit materialization。
7. 禁止继续在 query fallback 主链叠加 selector/conclusion/snippet gate。
8. 后续实现必须先做 terminal unit 第一阶段：生成 terminal units 并进入 FTS 检索。
9. hidden eval 不得被 AI 读取；field label/alias/description 只能来自源文件与通用结构规则，不能来自题集、答案或 query 日志。

## 当前 fresh eval 报告清单

| 文件 | 状态 | 作用 | 提交建议 |
|---|---|---|---|
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/README.md` | 已跟踪 | public fresh eval 资料包说明 | 本轮无需重复处理 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_assets_generation_report.md` | 已跟踪 | 资料包生成与自检记录 | 本轮无需重复处理 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md` | 未跟踪 | 首轮正式验收基线，记录指标与失败桶 | 建议提交 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md` | 未跟踪 | 首轮失败归因，拆分结构化字段桶与标题搜索桶 | 建议提交 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_fix_result_report.md` | 未跟踪 | 第一次 query fallback 修复尝试报告 | 建议作为失败实验归档 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_verification_report.md` | 未跟踪 | 第一次服务级复验，`0/5 PASS` | 建议作为失败实验归档 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md` | 未跟踪 | selector gate 修复尝试报告 | 建议作为失败实验归档 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_verification_report.md` | 未跟踪 | selector gate 服务级复验，`0/5 PASS` | 建议作为失败实验归档 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_fix_result_report.md` | 未跟踪 | conclusion gate 修复尝试报告 | 建议作为失败实验归档 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_verification_report.md` | 未跟踪 | conclusion gate 服务级复验，`0/5 PASS` | 建议作为失败实验归档 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md` | 未跟踪 | 当前下一步设计：terminal assignment evidence unit 物化 | 建议提交 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_reports_commit_plan.md` | 本轮新增 | 报告归档审计与提交计划 | 建议提交 |

## 建议提交

| 文件 | 理由 |
|---|---|
| `acceptance-report.md` | fresh eval 2 的正式基线，包含 redline、Maven、导入、逐题结果和指标。 |
| `fresh_eval_root_cause_analysis_report.md` | 明确失败分桶，证明优先根因是结构化 terminal field / sibling 绑定，不是召回缺失。 |
| 三组 `*_fix_result_report.md` + `*_verification_report.md` | 作为失败实验归档，证明 query fallback 层连续尝试未通过服务级 gate，避免后续重复同方向扩规则。 |
| `structured_terminal_evidence_unit_materialization_design_report.md` | 当前最有决策价值的下一步：把 terminal assignment 物化为独立 evidence unit，而不是继续在 fallback 中叠 gate。 |
| `fresh_eval_reports_commit_plan.md` | 给出本轮归档边界、提交清单、排除项和提交前校验命令。 |

## 失败实验报告

| 实验 | 报告 | 结果 | 是否建议提交归档 |
|---|---|---|---|
| structured fact terminal binding | `fresh_eval_structured_fact_terminal_binding_fix_result_report.md`、`fresh_eval_structured_fact_terminal_binding_verification_report.md` | 工程门禁通过，但服务级 `0/5 PASS`，不建议提交当时代码 | 建议提交报告，标记为失败实验 |
| selector gate | `fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md`、`fresh_eval_structured_fact_terminal_binding_selector_gate_verification_report.md` | FACT_CARD 仍未进入最终 answer / citation，`0/5 PASS` | 建议提交报告，标记为失败实验 |
| conclusion gate | `fresh_eval_terminal_assignment_conclusion_gate_fix_result_report.md`、`fresh_eval_terminal_assignment_conclusion_gate_verification_report.md` | FACT_CARD 已召回但仍选错 sibling，`0/5 PASS` | 建议提交报告，标记为失败实验 |

这些失败报告不表示对应代码应提交；它们的价值是保留负向证据和决策依据。

## 不建议提交

| 对象 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | 私有模型绑定配置，工作区已有脏改动；本轮明确排除，不读取、不提交。 |
| `special_cases_report.md` | redline 输出产物，当前为脚本覆盖结果，不作为报告归档提交。 |
| `src/**`、`scripts/**`、`src/main/resources/**` | 本轮是 agentC 文档治理，不提交生产代码、脚本或资源配置。 |
| `target/**` | 构建产物。 |
| `.codex/**` | 本地开发/临时配置。 |
| `.DS_Store` | macOS 本地元数据。 |

## 台账同步判断

| 文件 | 是否需要同步 | 建议 |
|---|---|---|
| `docs/quality-progress-and-lessons.md` | 已同步 | 已新增 fresh eval 2 当前 gate、三轮 fallback 失败结论、下一步 terminal unit 物化方向。 |
| `docs/test/knowledge-base-e2e/eval-validation-roadmap.md` | 已同步 | 已将 Public Eval 2 的下一步从早期 query fallback 修复更新为 terminal assignment evidence unit 物化。 |

## scoped commit 文件清单建议

最终 scoped docs commit 建议：

```text
docs/test/knowledge-base-e2e/eval-validation-roadmap.md
docs/quality-progress-and-lessons.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_fix_result_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_verification_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_verification_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_fix_result_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_verification_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_reports_commit_plan.md
```

## 建议 commit message

```text
docs(eval): archive fresh eval reports and terminal unit plan
```

## 明确排除

```text
docs/模型绑定配置参考.md
special_cases_report.md
.DS_Store
target/**
.codex/**
```

## 提交前校验命令

模板：

```bash
git diff --check
rg -n "apiKey|sk-[A-Za-z0-9]|password|token" <拟提交文件>
```

按最终 scoped docs commit 清单展开：

```bash
git diff --check -- \
  docs/test/knowledge-base-e2e/eval-validation-roadmap.md \
  docs/quality-progress-and-lessons.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_fix_result_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_verification_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_verification_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_fix_result_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_verification_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_reports_commit_plan.md

rg -n "apiKey|sk-[A-Za-z0-9]|password|token" \
  docs/test/knowledge-base-e2e/eval-validation-roadmap.md \
  docs/quality-progress-and-lessons.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_fix_result_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_verification_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_verification_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_fix_result_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_verification_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md \
  docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_reports_commit_plan.md
```

说明：上述敏感词扫描可能命中文档中的校验命令、合规声明或“question token”等非凭据文本；提交前需逐条确认没有真实密钥、访问令牌、口令或 secret-like 明文。

## 真实验证码

### 1. 最终拟提交文件清单

```text
docs/test/knowledge-base-e2e/eval-validation-roadmap.md
docs/quality-progress-and-lessons.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_fix_result_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_verification_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_verification_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_fix_result_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_verification_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_reports_commit_plan.md
```

### 2. 明确排除文件清单

```text
docs/模型绑定配置参考.md
special_cases_report.md
.DS_Store
target/**
.codex/**
src/**
scripts/**
src/main/resources/**
```

### 3. 实际执行的校验命令

```bash
git diff --check -- <最终拟提交文件清单>

for f in <未跟踪拟提交文件清单>; do
  git diff --no-index --check /dev/null "$f"
done

rg -n "apiKey|sk-[A-Za-z0-9]|password|token" <最终拟提交文件清单>

rg -n "docs/模型绑定配置参考.md|special_cases_report.md|target/|\\.codex/|\\.DS_Store" <最终拟提交文件清单>
```

说明：
- 第一条是用户指定的 `git diff --check`，限定在最终拟提交文件上，避免读取明确排除文件。
- 第二条是补充校验：未跟踪文件不会被普通 `git diff --check` 覆盖，因此用 `git diff --no-index --check /dev/null "$f"` 检查新增文件内容；该命令对“存在新增差异”返回 `1` 属正常，是否失败看输出中是否有 whitespace warning。

### 4. 实际校验结果

| 校验 | 结果 | 说明 |
|---|---|---|
| `git diff --check -- <最终拟提交文件清单>` | 通过 | 退出码 `0`，无输出。 |
| 未跟踪文件补充 whitespace check | 通过 | 已移除 3 个报告末尾多余空行；复验无 whitespace warning。`git diff --no-index` 对新增差异返回 `1` 属预期，判断依据是无 warning 输出。 |
| 敏感信息扫描 | 有命中，均判定非真实凭据 | 命中为文档中的校验命令、合规声明、`question token` / `path token` 等术语、`apiKey 已脱敏` 文案。 |
| 排除项扫描 | 有命中，均判定为文本引用 | 命中为报告中的“未读取/未修改/明确排除/命令记录/历史校验说明”，未包含排除文件本体或 redline 输出内容。 |

已修复的 EOF 空白问题：

```text
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md:253: new blank line at EOF.
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_verification_report.md:259: new blank line at EOF.
docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_verification_report.md:238: new blank line at EOF.
```

复验结果：上述 3 个 warning 已消失。

### 5. 敏感信息扫描命中及逐条判定

| 命中类型 | 位置 | 判定 |
|---|---|---|
| `question token` / `path token` / `promptLength` 等普通技术词 | `fresh_eval_terminal_assignment_conclusion_gate_*`、`structured_terminal_evidence_unit_materialization_design_report.md`、`docs/quality-progress-and-lessons.md` | 不是访问令牌，不是密钥。 |
| `API key / token / password / sk- 明文` 合规声明 | `fresh_eval_*_verification_report.md`、`acceptance-report.md`、`fresh_eval_reports_commit_plan.md` | 明确声明未记录真实凭据，不含 secret 值。 |
| `apiKey 已脱敏` | `acceptance-report.md` | 脱敏状态说明，不含真实 key。 |
| `fix(llm): apiKey 解密失败优雅降级` | `docs/quality-progress-and-lessons.md` | commit 描述，不含真实 key。 |
| `rg -n "apiKey|sk-[A-Za-z0-9]|password|token"` | `fresh_eval_reports_commit_plan.md` | 校验命令本身，不是凭据。 |

结论：未发现真实 API key、token、password 或 `sk-` 明文。

### 6. 排除项扫描命中及判定

| 命中对象 | 判定 |
|---|---|
| `docs/模型绑定配置参考.md` | 仅作为“未读取/未修改/明确排除/既有脏文件说明”出现；未包含该文件内容。 |
| `special_cases_report.md` | 仅作为 redline 命令参数、脚本产物说明或排除项出现；不提交该文件本体。 |
| `target/` | 仅作为历史 Maven dump 路径或排除项出现；不提交 `target/**`。 |
| `.codex/` | 仅作为 Maven settings 路径或排除项出现；不提交 `.codex/**`。 |
| `.DS_Store` | 仅作为排除项出现；未发现文件本体。 |

结论：未误包含 `docs/模型绑定配置参考.md` / `special_cases_report.md` / `target/**` / `.codex/**` / `.DS_Store` 文件本体。

### 7. 是否允许进入 scoped docs commit

允许进入 scoped docs commit。

理由：本轮已修复 3 个 EOF 空白问题，`git diff --check` 通过，未跟踪文件补充 whitespace check 无 warning，敏感扫描未发现真实凭据，排除项扫描未发现误包含私有配置或 redline 输出文件本体。

### 8. 建议 commit message

```text
docs(eval): archive fresh eval reports and terminal unit plan
```
