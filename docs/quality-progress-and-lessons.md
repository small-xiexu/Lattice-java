# 项目质量打磨进度与踩坑台账

更新时间：2026-05-16（RRF 主线收口后更新）

本台账记录质量打磨、Query/SWIP eval、baseline 修复与多 agent 协作的当前状态。后续推进前先读本文件；阶段结论变化后必须回写。

## 当前阶段

- 主工程状态：Phase 12 clean rebuild 已完成阶段性验收，主工程进入质量打磨、RAG eval、多 agent 协作阶段。
- 主 query baseline 状态：`final_query_baseline_gate_report.md` 与 `phase12_final_clean_rebuild_gate_report.md` 均给出 gate 通过结论；但当前数据库据 SWIP 报告为 SWIP clean 库，不能直接代表主 baseline 可跑状态。
- SWIP eval 状态：SWIP 题集已接入 strict eval；expect 机械断言修正后从 `0/23` 恢复到 `13/23`；RRF revert 后三轮稳定性校验确认稳定区间为 `13-14/23`，11/23 为偶发波动未复现。SWIP strict eval 基线已收口。
- RRF retained content 主线：已回退。修复尝试 strict pass 未提升、Recall/Citation/LLM 指标下降且新增回归，结论为不保留。`swip_rrf_retained_content_revert_report.md` 与 `swip_rrf_revert_stability_verification_report.md` 确认回退稳定，RRF 主线收口。
- 报告 cleanup：本轮已完成，过期中间报告已删除，详见 `report_cleanup_after_rrf_revert_result.md`。
- 下一步候选主线：SWIP answer grounding 修复、compile review 治理落地、阶段性提交。

## 当前 Gate

| 项 | 当前状态 | 说明 |
|---|---|---|
| redline | 最近报告均为 `BLOCKER=0` | 新一轮改代码、eval、baseline 前仍必须重新跑；本轮未运行。 |
| mvn test | 最近 RRF 报告为 `811/0/0` 通过 | 本轮不运行；测试库已隔离到 `ai-rag-knowledge-test`。 |
| main baseline | 阶段 gate 已通过 | `final_query_baseline_gate_report.md` 为 `9/10` 且 gate 通过；`phase12_final_clean_rebuild_gate_report.md` 为 `8/10` 且 6 项 gate 通过。 |
| SWIP strict eval | 已收口 | expect 调整后 `13/23`；RRF revert 后三轮稳定性校验确认稳定区间 `13-14/23`，11/23 偶发波动未复现。详见 `swip_rrf_revert_stability_verification_report.md`。 |
| 当前数据库状态 | SWIP clean 库 | 据 RRF/QFE 报告：`source_files=2`、`articles=4`，只含 SWIP 两份 docx；不能在该库跑主 baseline。 |
| 模型配置状态 | 不在本轮变更范围 | Phase 12 主链曾配置 `gpt-5.5 + zhipu_embedding`；compile review 当前默认 `review-enabled=false`，实际 review route 为 rule-based。 |

## 多 Agent 当前职责

| Agent | 职责 | 当前状态 | 是否允许改代码 |
|---|---|---|---|
| agentA | 单一代码修复执行者 | RRF retained content 已回退，主线收口；待下一条主线分配 | 是，但同一轮只能有一个 agentA 改主链 |
| agentB | 治理/链路分析 | 已产出 compile review 治理分析；只读判断 rule-based 不等于 LLM 内容审查 | 否 |
| agentC | 项目进度台账与文档治理 | 本轮维护本台账、AGENTS 规则与文档更新报告 | 否，除文档/报告 |
| agentD | 验证/测试 | 负责 redline、`mvn test`、baseline、业务 eval 验证报告；代码修复未收口前不主动扰动数据库 | 否，除验证报告 |

## 已验证结论

- Q-MQ 已闭环；主 baseline 阶段 gate 已通过，但需注意 LLM 引用标记偶发不稳定。
- 测试库隔离已完成，`mvn test` 默认写入 `ai-rag-knowledge-test`，不再污染真实 baseline 库。
- 主 query baseline 已达到当前 gate。
- SWIP 题集已接入 strict eval。
- SWIP docx 抽取没有整体缺失；剩余失败主要不是源文抽取缺失。
- prompt companion snippet 尝试负收益，已回退，不建议继续沿该形态扩大。
- RRF retained content 修复尝试负收益或无净提升，已回退且确认不保留；RRF 主线已收口。
- compile review 当前是 rule-based，不等于 LLM 内容审查；若产品承诺“审查后入库”，需启用 LLM reviewer 并补 query 可见性门禁。

## 踩坑记录

| 坑 | 表现 | 结论 | 后续规则 |
|---|---|---|---|
| SWIP `23/23` 弱通过不是严格验收 | 弱口径可能只证明链路跑通，不能证明答案完整覆盖 required terms | 必须以 strict eval 和人工字段共同判断 | 禁止把弱通过当作质量收口。 |
| `requiredSourceTerms` 机械映射导致 `0/23` 伪失败 | runner 的 `sourceText` 不包含部分人工 evidence 线索，机器硬断言全挂 | 这是评测断言口径问题，不是系统能力全挂 | 机器断言只放 runner 稳定可见字段；人工证据留在顶层验收字段。 |
| prompt companion snippet 负收益 | `14/23` 降到 `13/23`，新增原通过 case 回归 | 追加 companion 会扰动 LLM 对证据的取舍 | 不继续沿 prompt companion 形态扩大；失败也要记录并回退。 |
| RRF retained content 修复尝试负收益 | strict pass 未提升，Recall/Citation/LLM 指标下降，并新增 SWIP-USAGE-REPRINT-001 回归 | 当前 retained content 选择规则不是安全收益点 | 已回退且确认不保留；RRF 主线收口。回退后 REPRINT-001 回归已恢复。 |
| SWIP clean 库不能跑主 baseline | 当前库只含 2 个 SWIP source、4 篇文章 | 主 baseline 与 SWIP eval 的知识库前提不同 | 跑主 baseline 前必须确认库已恢复主 clean rebuild 数据。 |
| rule-based review 不等于 LLM reviewer | compile step 显示 review 成功，但 route 为 `rule-based` | 只能证明结构兜底检查通过，不能证明内容审查通过 | 后台、报告和产品口径必须展示 review route；启用 LLM reviewer 前不得宣称 LLM 审查。 |
| 测试库曾污染真实 baseline 库 | 旧测试硬编码写入 `ai-rag-knowledge` | 已通过 `ai-rag-knowledge-test` 隔离 | 发现 baseline 异常先查库污染；`mvn test` 后应确认未改真实库。 |
| 多 agent 同时改主链会导致不可归因 | RRF、prompt、fallback、citation、题集若同时变更，eval 波动无法定位 | 每轮只允许一个代码主变量 | 同一轮最多一个 agent 改 `src/main/java/**`；其他 agent 只读或写报告。 |

## 当前禁止事项

- 不准多个 agent 同时改主链。
- 不准在 SWIP clean 库上跑主 baseline。
- 不准继续调题集来追 pass。
- 不准写 SWIP / 文档 / case 特判。
- 不准跳过 redline / `mvn test` / baseline 归因。
- 不准把 rule-based review 描述成 LLM 内容审查。
- 不准在代码修复未收口时删除仍可能被引用的 SWIP/RRF 报告。

## 下一步计划

1. （已完成）agentA 已回退 RRF retained content 改动，RRF 主线已收口。
2. （已完成）已审 `swip_rrf_retained_content_revert_report.md` 与 `swip_rrf_revert_stability_verification_report.md`。
3. （已完成）报告 cleanup 已执行，详见 `report_cleanup_after_rrf_revert_result.md`。
4. 决定下一条主线（按优先级排列）：
   - SWIP answer grounding 修复；
   - compile review 治理落地；
   - 阶段性提交。

## 更新规则

- 每次质量打磨、Query/SWIP eval、baseline 修复或多 agent 并行前，先读本文件。
- 每轮阶段性结论变化后，更新本文件。
- 当前 gate、下一步计划、踩坑结论、agent 分工发生变化时，必须同步更新本文件。
- 代码修复失败、回退、负收益也必须记录。
- 不允许只在聊天里说明而不更新台账。
- 本文件是质量打磨阶段的进度台账，不替代用户指定的计划文件；如果用户指定 `docs/**/plans/*.md`，仍以计划文件为唯一进度台账并随做随回写。
