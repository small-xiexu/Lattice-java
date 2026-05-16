# SWIP outcome guard side effect analysis report

## 1. 结论

两个新增 `PASS -> FAIL` 不建议归因于 outcome guard 的直接副作用。

| Case | 本轮判断 | 是否建议回退 / 缩小 outcome guard |
|---|---|---|
| `SWIP-INSTALL-APP-LIST-001` | LLM answer 内容波动。修复前后均为 `PARTIAL_ANSWER / LLM`，本轮 guard 只处理 `SUCCESS` 下调，不会触发。 | 否 |
| `SWIP-INSTALL-IP-SUFFIX-001` | fallback 证据选择 / deterministic exact lookup 替换波动。修复后为 `FALLBACK / SUCCESS`，最终答案来自 deterministic fallback，不是 structured LLM outcome guard 的最终输出。guard 是否在隐藏 LLM payload 上间接触发不可从现有 audit 证明。 | 否 |

建议保留 outcome guard。`SWIP-NEG-UNANSWERABLE-001` 已恢复为 `INSUFFICIENT_EVIDENCE` 并 PASS；`SWIP-INSTALL-CERT-NAMING-001` 仍 PASS。当前两个新增失败更像 answer 生成/降级选择波动，不足以支持回退或缩小 guard。

## 2. Redline

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1836 |
| ALLOWLIST | 218 |

本轮按要求运行 `bash scripts/scan-redline.sh special_cases_report.md`。`special_cases_report.md` 被刷新；未修改 redline allowlist。

## 3. 复跑说明

runner 不支持单 case 参数：

- `scripts/run-query-regression.sh` 只转发到 `scripts/run-query-regression.mjs`。
- mjs 固定遍历 suite 的全量 `cases`，未发现 case id 过滤参数或环境变量。

按用户允许的替代方案，本轮尝试复跑一次完整 SWIP strict eval：

- 输出目录：`.codex/run/swip-outcome-guard-side-effect-probe-20260516-122900`
- 结果无效：23/23 全部 `request_error:fetch failed`，耗时 0-42ms。
- `curl http://127.0.0.1:18086/api/v1/query` 返回连接失败，说明 query 服务当时不可用。

该 probe 不用于判断两个 case 的稳定性。

## 4. 前后对比总表

对比基线：

- 最近稳定 run：`.codex/run/swip-stability-round3`
- lead-in fix run：`.codex/run/swip-structured-leadin-fix-20260516-112955`
- outcome guard run：`.codex/run/swip-unanswerable-outcome-guard-fix-20260516-121843`

| Case | Run | Pass | Failed reason | GenerationMode | AnswerOutcome | Actual answer 摘要 | Required terms 覆盖 |
|---|---|---:|---|---|---|---|---|
| `SWIP-INSTALL-APP-LIST-001` | stability round3 | PASS | 无 | LLM | `PARTIAL_ANSWER` | 说不能完整确认全部 APP，但列出 `SWIP APP Store`、`SWIP网关APP`、资和信、杉德/得仕卡/苏州市民卡、易百等 5 个 APP/类别。 | runner required terms 全覆盖 |
| `SWIP-INSTALL-APP-LIST-001` | lead-in fix | PASS | 无 | LLM | `PARTIAL_ANSWER` | 说证据不足以完整确认 7 个支付渠道 APP 清单，但表格列出 `SWIP APP Store`、`SWIP网关APP`、资和信、杉德/得仕卡/苏州市民卡、易百。 | runner required terms 全覆盖 |
| `SWIP-INSTALL-APP-LIST-001` | outcome guard | FAIL | 缺 `SWIP APP Store`、`SWIP网关`、`资和信`、`易百`、`杉德`、`得仕卡`、`苏州市民卡` | LLM | `PARTIAL_ANSWER` | 只说安装手册提到“其他7个APP”，但未列完整名称；FAQ 目录中能看到 5 个升级/卸载重装条目。 | 只机械覆盖 `9`，其余 required terms 全漏 |
| `SWIP-INSTALL-IP-SUFFIX-001` | stability round3 | PASS | 无 | LLM | `SUCCESS` | 正确回答 POS 1 -> `149`，POS 2 -> `150`，颠倒时 `149 -> 151`、`150 -> 149`、`151 -> 150`。 | `149/150/151` 全覆盖 |
| `SWIP-INSTALL-IP-SUFFIX-001` | lead-in fix | PASS | 无 | LLM | `SUCCESS` | 正确回答 `149/150/151` 调整顺序。 | `149/150/151` 全覆盖 |
| `SWIP-INSTALL-IP-SUFFIX-001` | outcome guard | FAIL | 缺 `150`、`151` | FALLBACK | `SUCCESS` | deterministic fallback 只引用设备初始化和静态 IP 一般最后一段 `.149`，未回答 POS 2 后缀和颠倒调整步骤。 | 只覆盖 `149` |

## 5. `SWIP-INSTALL-APP-LIST-001` 归因

### 5.1 期望

- 问题：`SWIP 系统一共有几个 APP，分别是什么？`
- 机器 required terms：`9`、`SWIP APP Store`、`SWIP网关`、`资和信`、`易百`、`杉德`、`得仕卡`、`苏州市民卡`
- 人工期望：应列出数量和全部 9 个 APP，地区限制可简述。

### 5.2 outcome guard 是否触发

未触发。

依据：

- outcome guard 条件是 `answerOutcome == SUCCESS && looksLikeInsufficientEvidenceAnswer(answerMarkdown)`。
- 三个有效 run 中该 case 都是 `PARTIAL_ANSWER / LLM`。
- outcome guard 不处理 `PARTIAL_ANSWER` 下调。
- 修复后失败原因是 `answer_missing_term:*`，不是 `answerOutcome_*`。

### 5.3 失败原因

这是 answer terms 漏掉，不是 outcome 变化。

outcome guard run 的答案只剩一句概述：

- 提到“其他7个APP”。
- 提到 FAQ 有 5 个条目。
- 未列出 `SWIP APP Store`、`SWIP网关`、`资和信`、`易百`、`杉德`、`得仕卡`、`苏州市民卡`。

因此 strict eval 失败。

### 5.4 是否为 LLM 波动

是。更准确地说，是 LLM 在同样 `PARTIAL_ANSWER` 口径下是否展开列表的内容波动。

注意：前两次 PASS 也不是强语义 PASS。它们仍回答“证据不足/无法完整确认”，且只列出 5 个 APP/类别；只是机械 required terms 都出现了。当前 FAIL 是列表省略得更短，导致 required terms 缺失。

结论：

- 不是 outcome guard 造成。
- 不建议为该 case 回退或缩小 outcome guard。

## 6. `SWIP-INSTALL-IP-SUFFIX-001` 归因

### 6.1 期望

- 问题：`POS 机号 1 和 POS 机号 2 分别对应键盘 IP 的哪个后缀，如果顺序颠倒应该怎么调整？`
- 机器 required terms：`149`、`150`、`151`
- 人工期望：POS 1 -> `149`；POS 2 -> `150`；颠倒时先将 `149` 改为 `151`，再把 `150` 改为 `149`，最后把 `151` 改为 `150`。

### 6.2 outcome guard 是否触发

最终可观察结果中没有触发证据。

依据：

- outcome guard 位于 structured LLM payload normalization，仅对 `SUCCESS` 下调。
- 本轮最终响应是 `FALLBACK / SUCCESS`，fallback reason 为 `DETERMINISTIC_EXACT_LOOKUP_PREFERRED`。
- deterministic fallback 最终答案没有 `150/151`，只包含 `.149`。
- `query_answer_audits.model_snapshot_json` 为空 `{}`，没有保存 raw LLM payload。
- audit 只保存最终 fallback answer，无法直接看到被替换前的 LLM answer/outcome，也无法确认 deterministic preference 的细分原因是 `OUTCOME_NOT_SUCCESS`、`OVERCAUTIOUS_PHRASE` 还是 `GROUNDING_MISMATCH`。

可成立的路径：

- 如果隐藏 LLM payload 原本是 `SUCCESS` 且命中新 guard 的 insufficient evidence signals，guard 可能先下调，再触发 deterministic exact lookup fallback。
- 如果隐藏 LLM payload 原本就是 `PARTIAL_ANSWER`、含旧有 overcautious phrase、或 grounding mismatch，旧逻辑也会触发 deterministic fallback。

现有数据不能区分这两条路径。

### 6.3 失败原因

这是 fallback 证据选择 / deterministic replacement 后的答案漏项。

对比：

- lead-in fix run：`LLM / SUCCESS`，答案直接包含 `149/150/151`。
- outcome guard run：`FALLBACK / SUCCESS`，fallback 选到设备初始化证据，只说明静态 IP 一般最后一段 `.149`，没有覆盖 POS 2 后缀 `150` 和临时调整后缀 `151`。

retrieval 不是直接问题：

- outcome guard run 的 retrieval 仍为 covered。
- FAQ 33 在 fused rank 1。
- 但 deterministic fallback 最终采用的摘要偏向“设备初始化整体步骤 / 一般 .149”，没有把 FAQ 33 中的颠倒调整规则抽出来。

### 6.4 是否为 fallback / LLM 波动

是。该 case 的新增失败主要表现为生成路径从 `LLM / SUCCESS` 波动到 `FALLBACK / SUCCESS`，且 fallback 内容选择不完整。

不能证明是 outcome guard 直接造成；也不能完全排除 outcome guard 对隐藏 LLM payload 的间接影响。缺口在于 raw LLM payload 和 deterministic preference subreason 未落库。

结论：

- 不建议仅凭该 case 回退 outcome guard。
- 若后续要修，应优先分析 deterministic exact lookup fallback preference / evidence selection，而不是先缩小 no-answer guard。

## 7. 是否经过 `normalizeStructuredAnswerOutcome(...)`

| Case | 是否经过 | 判断 |
|---|---|---|
| `SWIP-INSTALL-APP-LIST-001` | 是 | final mode 为 `LLM`，structured answer path 会调用 `AnswerPayloadParser.parseStructuredAnswerPayload(...)`，随后调用 `normalizeStructuredAnswerOutcome(...)`。但 outcome 是 `PARTIAL_ANSWER`，新 guard 不触发。 |
| `SWIP-INSTALL-IP-SUFFIX-001` | 是，若 LLM structured payload 成功解析 | final reason 是 `DETERMINISTIC_EXACT_LOOKUP_PREFERRED`，该路径位于 structured payload 解析后的 `preferDeterministicExactLookupPayload(...)`。因此 normalized LLM payload 很可能存在；但最终 audit 只保存 fallback payload，无法观察 guard 是否下调过 hidden LLM payload。 |

## 8. 是否由本轮改动导致

| Case | 结论 | 说明 |
|---|---|---|
| `SWIP-INSTALL-APP-LIST-001` | 否 | final outcome 未变，guard 条件不满足；失败是 answer text 省略列表项。 |
| `SWIP-INSTALL-IP-SUFFIX-001` | 未证实 | final 是 deterministic fallback，guard 不直接生成该答案；可能存在间接路径，但 raw LLM payload 缺失，无法证明。 |

总体判断：当前证据不足以支持“outcome guard 造成两个新增失败”的结论。

## 9. 是否建议保留 outcome guard

建议保留。

理由：

- 目标 case `SWIP-NEG-UNANSWERABLE-001` 已从 `SUCCESS` 下调为 `INSUFFICIENT_EVIDENCE` 并 PASS。
- `SWIP-INSTALL-CERT-NAMING-001` 仍 PASS。
- `APP-LIST` 与 guard 条件不相交。
- `IP-SUFFIX` 的可观察失败点在 deterministic fallback 取证与答案生成，不在最终 outcome guard 输出。
- 回退 guard 会重新暴露 no-answer case 的 `SUCCESS` 可缓存风险。

不建议当前动作：

- 不建议回退 outcome guard。
- 不建议为了两个未证实副作用缩小 insufficient evidence signals。
- 不建议改题集。

## 10. 是否建议修代码

本轮不建议立即修代码。先需要一次有效复跑确认这两个新增失败是否稳定复现；本轮 attempted probe 因 query 服务不可用无效。

如果用户要求下一轮必须选一个最小修复点，建议只选：

- **为 deterministic exact lookup preference 增加可观测 subreason audit/log**，记录触发原因是 `OUTCOME_NOT_SUCCESS`、`OVERCAUTIOUS_PHRASE` 还是 `GROUNDING_MISMATCH`。
- 最小候选范围：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationExactLookupSupport.java` 与 answer audit 字段/日志二选一；若不改 schema，则仅日志最小。

该建议不是为了提高 pass，而是为了让下一次能判断 `IP-SUFFIX` 是否真由 outcome guard 间接触发。行为修复应等有效复跑和 subreason 明确后再做。

## 11. 本轮改动声明

- 本轮是否修改代码：否。
- 本轮是否修改测试/配置/题集/脚本/.claude/AGENTS/redline allowlist：否。
- 本轮是否处理其他 SWIP 失败：否。
- 本轮刷新 redline：是，`special_cases_report.md`。
- 本轮新增报告：`swip_outcome_guard_side_effect_analysis_report.md`。
- 本轮新增无效 probe 输出目录：`.codex/run/swip-outcome-guard-side-effect-probe-20260516-122900`；因 query 服务不可用，结果不参与结论。
