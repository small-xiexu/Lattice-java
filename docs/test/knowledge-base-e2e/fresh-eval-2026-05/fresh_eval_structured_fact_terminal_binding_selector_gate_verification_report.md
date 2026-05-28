# fresh eval structured fact terminal binding selector gate 服务级复验报告

## 1. 任务范围与合规声明

- 角色：agentD（验证/测试）
- 本轮仅执行：
  - 只读审计
  - redline / 测试门禁
  - 服务健康检查
  - 5 个结构化题服务级复验
  - 验证报告输出
- 本轮未修改：
  - `src/**`
  - `scripts/**`
  - `src/main/resources/**`
  - prompt / allowlist / 题集 / 资料包
  - `/Users/sxie/xbk/Lattice-java/docs/模型绑定配置参考.md`
- 本轮未清库、未重建 schema、未重导 fresh eval 资料
- 本轮未 stage、未 commit、未 push
- 报告未包含任何真实 API key / token / password / `sk-` 明文

## 2. 输入报告

- [`acceptance-report.md`](/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md)
- [`fresh_eval_root_cause_analysis_report.md`](/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md)
- [`fresh_eval_structured_fact_terminal_binding_verification_report.md`](/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_verification_report.md)
- [`fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md`](/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md)

## 3. 前置门禁结果

### 3.1 git status / 允许改动范围核对

- `git diff --name-only` 显示的生产代码脏改动仅有：
  - `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
  - `src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java`
- 另有：
  - `special_cases_report.md`
  - 私有配置脏改动：`docs/模型绑定配置参考.md`
  - 多个报告文件和 1 个文档类未跟踪文件：`docs/test/knowledge-base-e2e/eval-validation-roadmap.md`
- 结论：
  - **生产代码改动范围符合本轮约束**
  - 但工作区并非只含本轮目标报告，存在额外文档类未跟踪文件；本轮未触碰这些文件

### 3.2 redline

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：
  - `BLOCKER=0`
  - `REVIEW=2041`
  - `ALLOWLIST=259`
- 结论：通过

### 3.3 定向测试

- 命令：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerFallbackEvidenceSelectorTests,AnswerGenerationServiceTests,AnswerFallbackConclusionBuilderTests test`
- 结果：
  - `BUILD SUCCESS`
  - `Tests run: 86, Failures: 0, Errors: 0, Skipped: 0`
- 结论：通过

### 3.4 全量测试

- 命令：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：
  - `BUILD SUCCESS`
  - `Tests run: 922, Failures: 0, Errors: 0, Skipped: 0`
- 结论：通过

## 4. 服务与数据库状态

### 4.1 服务健康

- 启动前：
  - `GET http://127.0.0.1:18082/actuator/health`
  - 结果：连接失败，服务未运行
- 启动方式：
  - `./scripts/run-local-dev.sh`
  - **未使用** `--reset-schema`
- 启动后：
  - `GET http://127.0.0.1:18082/actuator/health`
  - 结果：`{"status":"UP"}`

### 4.2 当前 fresh eval 库状态（只读）

- 只读计数：
  - `source_files = 5`
  - `articles = 5`
  - `article_chunks = 9`
  - `fact_cards = 11`
- 5 个目标资料源均存在且为 `ACTIVE`：
  - `lab-safety-management-handbook`
  - `equipment-borrowing-policy`
  - `chemical-storage-grading`
  - `equipment-maintenance-schedule`
  - `lab-emergency-pdf`

结论：当前库覆盖 fresh-eval-2026-05 的 5 份资料，满足“复用当前库、不重导”的前提。

## 5. 5 个结构化题逐题结果表

| Case | queryId | retrieval run id | answer 摘要 | answerOutcome / generationMode / modelExecutionStatus | citations / evidence 类型 | FACT_CARD 在 fused hits | FACT_CARD 进入最终 answer/citation | 仍只落 ARTICLE / SOURCE_FILE | 仍回答 sibling / summary | 判定 |
|---|---|---:|---|---|---|---|---|---|---|---|
| FQ3 | `a0e7a654-6331-4b38-a5ab-b4c3b5401a75` | 51 | 仍回答“三类设备汇总 + 审批链 + 系统名称”，未给出 `7` | `SUCCESS / FALLBACK / DEGRADED` | `ARTICLE + SOURCE_FILE` | 是（rank 2/3） | 否 | 是 | 是（`approval_chain`、系统名称） | FAIL |
| FQ4 | `95753cb7-56ea-42c3-aab9-8e8bd4c31bb6` | 52 | 仍回答“三类设备汇总 + 审批链 + 系统名称”，未给出 `100 / 1000` | `SUCCESS / FALLBACK / DEGRADED` | `ARTICLE + SOURCE_FILE` | 是（rank 2/3） | 否 | 是 | 是（`approval_chain`、系统名称） | FAIL |
| FQ6 | `ec1242fd-a4eb-40c3-8915-93f220afa836` | 53 | 仍回答“系统名称 + API endpoint + damage_report_required”，未给出 `v2.3.1` | `SUCCESS / FALLBACK / DEGRADED` | `ARTICLE + SOURCE_FILE` | 是（rank 2/3） | 否 | 是 | 是（`api_endpoint`、`damage_report_required`） | FAIL |
| FG1 | `287a75ca-eb96-48b6-8000-5e87871b3deb` | 54 | 仍回答 `damage_report_required`、通知渠道、`17:30`，未给出 `20 / 5` | `SUCCESS / FALLBACK / DEGRADED` | `ARTICLE + SOURCE_FILE` | 是（rank 2/3） | 否 | 是 | 是（`return_policy`、通知渠道、`same_day_return_cutoff`） | FAIL |
| FG2 | `64cd5255-57db-4dbc-b155-8e64975ce8b6` | 55 | 仍回答 `damage_report_required`、通知渠道、`17:30`，未给出 `50` | `SUCCESS / FALLBACK / DEGRADED` | `ARTICLE + SOURCE_FILE` | 是（rank 2/3） | 否 | 是 | 是（`return_policy`、通知渠道、`same_day_return_cutoff`） | FAIL |

结论：**5/5 全部 FAIL**。

## 6. retrieval -> fallback evidence -> answer -> citation 证据流转表

| Case | retrieval fused Top5 | FACT_CARD 是否进入 fused hits | answer / claim 是否消费 terminal field value | citation projection 是否扩展到 FACT_CARD | 结果说明 |
|---|---|---|---|---|---|
| FQ3 | rank1=`SOURCE`，rank2=`FACT_CARD(结构化键值)`，rank3=`FACT_CARD(结构化列表)`，rank4=`SOURCE`，rank5=`ARTICLE/refkey` | 是 | 否；claim 仍是 “三类设备汇总” | 否；`query_answer_citations` 只有 `ARTICLE` / `SOURCE_FILE` | selector gate 后 exact fact 仍未进入终答 |
| FQ4 | 同 FQ3 | 是 | 否；claim 仍是 “三类设备汇总” | 否 | sibling 押金字段未被消费 |
| FQ6 | rank1=`SOURCE`，rank2=`FACT_CARD(结构化键值)`，rank3=`FACT_CARD(结构化列表)` | 是 | 否；claim 落到 `系统名称`、`API 端点`、`damage_report_required` | 否 | terminal `version` 未被消费 |
| FG1 | rank1=`SOURCE`，rank2=`FACT_CARD(结构化键值)`，rank3=`FACT_CARD(结构化列表)` | 是 | 否；claim 落到 `damage_report_required`、通知渠道、`17:30` | 否 | `late_fee_per_day` sibling 未被消费 |
| FG2 | rank1=`SOURCE`，rank2=`FACT_CARD(结构化键值)`，rank3=`FACT_CARD(结构化列表)` | 是 | 否；claim 落到 `damage_report_required`、通知渠道、`17:30` | 否 | `max_concurrent_requests` terminal field 未被消费 |

补充说明：

1. `query_retrieval_runs` 显示 5 个 case 全部 `coverage_status=covered`。
2. `fact_card_hit_count=2`，说明 exact `FACT_ENUM` 与列表 fact card 都被召回。
3. 但 `query_answer_citations` 表结构本身限定 `source_type` 只能是 `ARTICLE` / `SOURCE_FILE`，本轮真实结果也全部如此；**没有任何 FACT_CARD citation 投影**。
4. `query_answer_claims` 显示最终 claim 仍绑定在：
   - summary
   - `approval_chain`
   - `return_policy`
   - `borrowing_system.name`
   - `api_endpoint`
   - `damage_report_required`
   上，而不是目标 terminal field。

## 7. 关键问题回答

### 7.1 selector gate 修复后，FACT_CARD 是否从 retrieval hits 进入 final fallback evidence？

- **没有证据证明进入了最终 answer 可消费链路**
- 证据：
  - retrieval fused hits 中 `FACT_CARD` 稳定存在（rank 2/3）
  - 但最终 `answer_markdown` 和 `query_answer_claims` 里未出现目标 terminal field value
  - 说明问题不在 retrieval 召回，而在 **retrieval 之后到 final answer 之间的消费链路**

### 7.2 final answer 是否消费了 terminal field value？

- **没有**
- 5 个 case 分别未消费：
  - FQ3: `7`
  - FQ4: `100 / 1000`
  - FQ6: `v2.3.1`
  - FG1: `20 / 5`
  - FG2: `50`

### 7.3 citation projection 是否从 ARTICLE / SOURCE_FILE 扩展到 FACT_CARD 或可支撑结构化字段的证据？

- **没有**
- 本轮所有 `query_answer_citations.source_type` 仍仅为：
  - `ARTICLE`
  - `SOURCE_FILE`
- 且表结构检查显示：
  - `chk_query_answer_citations_source_type` 本身就只允许这两种类型

### 7.4 是否出现 outcome 过度升级？

- **是**
- 5 个错误答案全部仍返回：
  - `answerOutcome=SUCCESS`
  - `generationMode=FALLBACK`
  - `modelExecutionStatus=DEGRADED`
- 说明当前错误答案没有被降级成 `PARTIAL_ANSWER / INSUFFICIENT_EVIDENCE`

### 7.5 是否影响 Q6 / S2 保护场景？

- **本轮未执行**
- 原因：
  - 用户要求：5 个结构化题若不是 `5/5 PASS`，本轮 gate 直接 FAIL，不继续扩展完整回归
  - 当前结果是 `0/5 PASS`

### 7.6 是否建议提交本轮代码？

- **不建议**
- 原因：
  - 定向测试与全量测试虽然通过
  - 但服务级真实链路 `0/5 PASS`
  - 说明当前改动未转化成目标行为收益

## 8. 完整 fresh eval / 保护回归执行情况

- 完整 fresh eval：**未执行**
- Q6 terminal field alias 保护回归：**未执行**
- S2 chunk/anchor identity 保护回归：**未执行**
- 第一套知识库验收 `Q1-Q12 / S1-S4`：**未执行**

原因统一为：

- 根据本轮 gate 规则，5 个结构化题未达 `5/5 PASS`，因此**直接停止扩展回归**。

## 9. Gate 判定

- **Gate: FAIL**

直接原因：

1. `FQ3 / FQ4 / FQ6 / FG1 / FG2` 没有任何一题进入目标 terminal field 值
2. retrieval fused hits 明确显示 `FACT_CARD` 已召回
3. 但 answer / claim / citation 仍停留在 `ARTICLE / SOURCE_FILE` 粗粒度链路

## 10. 是否建议提交

- **不建议提交**

## 11. 下一步唯一根因建议

下一步建议只交给 agentB 做一个根因归因：

- **定位 `DETERMINISTIC_EXACT_LOOKUP_PREFERRED` 之后、`query_answer_claims` 生成之前，哪一段 runtime 逻辑仍然把已召回的 structured FACT_CARD terminal field 降解回 summary / return_policy / api_endpoint / damage_report_required 这类 sibling/粗粒度证据。**

换句话说：

- 本轮 selector gate 看起来**没有改变最终 consumer**
- 下一轮不要再扩 selector 规则，而要只读确认：
  - 是不是 `AnswerFallbackConclusionBuilder`
  - 或 `exact lookup grounding`
  - 或后续 claim segmentation / citation binding
  才是真正把 terminal field 吃掉的那个 runtime gate

## 12. 最终合规声明

- 未修改代码
- 未清库
- 未重导 fresh eval 资料
- 未 stage
- 未 commit
- 未 push
