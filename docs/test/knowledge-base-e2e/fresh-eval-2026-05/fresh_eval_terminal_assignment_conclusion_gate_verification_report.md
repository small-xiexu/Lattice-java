# fresh eval terminal assignment conclusion gate 验证报告

## 1. 验证边界

- 角色：agentD（验证/测试 Agent）
- 时间：2026-05-28
- 工作区：`/Users/sxie/xbk/Lattice-java`
- 本轮仅做验证与报告记录。
- 未修改 `src/**`、`scripts/**`、`src/main/resources/**`、prompt、allowlist、题集或资料包。
- 未读取、未修改 `docs/模型绑定配置参考.md`。
- 未清理 fresh eval 真实验收库，未重建 schema，未重导资料。
- 未 stage、未 commit、未 push。

## 2. git diff 范围核对

`git diff --name-only` 显示当前生产/测试代码 diff 仍限定在：

- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackConclusionSupport.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilderTests.java`
- `special_cases_report.md`

额外脏文件：

- `docs/模型绑定配置参考.md`

本轮验证未读取、未修改该额外脏文件。

Selector 相关文件无残留 diff：

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java`

## 3. redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

- 退出码：`0`
- `BLOCKER=0`
- `REVIEW=2064`
- `ALLOWLIST=259`

结论：红线阻断项通过。

## 4. Maven gate

### 4.1 定向测试

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerFallbackConclusionBuilderTests,AnswerGenerationServiceTests,AnswerFallbackEvidenceSelectorTests test
```

结果：通过。

- `AnswerFallbackConclusionBuilderTests`：6 run, 0 failures, 0 errors
- `AnswerGenerationServiceTests`：77 run, 0 failures, 0 errors
- `AnswerFallbackEvidenceSelectorTests`：6 run, 0 failures, 0 errors
- 合计：89 run, 0 failures, 0 errors

### 4.2 全量测试

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

最终 surefire 汇总：

- 测试类：225
- Tests run：925
- Failures：0
- Errors：0
- Skipped：0

补充说明：

- 本轮未清理测试库。
- `target/surefire-reports` 中存在 16:24 的历史 dump，内容为旧的 JUnit discovery 失败；17:20-17:26 这轮全量测试未产生新 dump，且最新 surefire 汇总全绿。

结论：全量 Maven gate 通过。

## 5. 服务与资料覆盖

启动命令：

```bash
./scripts/run-local-dev.sh
```

约束确认：

- 未使用 `--reset-schema`
- 健康检查：`GET http://127.0.0.1:18082/actuator/health -> {"status":"UP"}`

只读库覆盖：

| 表 | 计数 |
|---|---:|
| `source_files` | 5 |
| `articles` | 5 |
| `article_chunks` | 9 |
| `fact_cards` | 11 |

5 个目标资料源均为 `ACTIVE`：

- `lab-safety-management-handbook`
- `equipment-borrowing-policy`
- `chemical-storage-grading`
- `equipment-maintenance-schedule`
- `lab-emergency-pdf`

结论：满足复用当前 fresh eval 库、不清库、不重导的前提。

## 6. 5 个结构化 terminal value 题服务级结果

调用入口：

```bash
POST http://127.0.0.1:18082/api/v1/query
```

请求体仅包含 `question`。

| Case | queryId | retrieval run id | 预期 terminal value | answer 摘要 | answerOutcome / generationMode / modelExecutionStatus | fallbackReason | 判定 |
|---|---|---:|---|---|---|---|---|
| FQ3 | `5cf9d11c-7012-4604-bb7c-f1f9e0d47181` | 56 | `7` | 回答 `equipment_types[1].type = 精密仪器`，未回答最长借用天数 | `SUCCESS / FALLBACK / DEGRADED` | `DETERMINISTIC_EXACT_LOOKUP_PREFERRED` | FAIL |
| FQ4 | `1dd63398-9ccd-4cb2-9834-dfa28291fcc5` | 57 | `100 / 1000` | 回答 `equipment_types[0].type = 常规设备`，未回答押金 | `PARTIAL_ANSWER / FALLBACK / DEGRADED` | `CITATION_QUALITY_INSUFFICIENT` | FAIL |
| FQ6 | `f4bd0f43-feab-4461-a3c2-0bc4097e2f7d` | 58 | `v2.3.1` | 回答 `borrowing_system.name = 校园实验室设备预约系统`，未回答版本号 | `PARTIAL_ANSWER / FALLBACK / DEGRADED` | `CITATION_QUALITY_INSUFFICIENT` | FAIL |
| FG1 | `89c4241b-5d4b-46aa-9761-f2cdada6f892` | 59 | `20 / 5` | 回答 `damage_report_required`、通知渠道、`17:30`，未回答逾期罚金 | `SUCCESS / FALLBACK / DEGRADED` | `DETERMINISTIC_EXACT_LOOKUP_PREFERRED` | FAIL |
| FG2 | `f0e4257d-4e53-4f02-928b-62952d16590a` | 60 | `50` | 回答 `damage_report_required`、通知渠道、`17:30`，未回答最大并发请求数 | `SUCCESS / FALLBACK / DEGRADED` | `DETERMINISTIC_EXACT_LOOKUP_PREFERRED` | FAIL |

结论：`0/5 PASS`，服务级 gate 失败。

## 7. Evidence flow

### 7.1 retrieval -> fallback markdown

5 个 case 的 retrieval run 均满足：

- `coverage_status=covered`
- `fact_card_hit_count=2`
- `FACT_CARD` 稳定进入 fused hits rank 2/3
- `SOURCE` rank 1

命中的关键 fact card：

- `fact_card_id=5`
- `card_type=FACT_ENUM`
- `title=结构化键值条目 - equipment-borrowing-policy.yaml#0`

该 fact card 真实包含目标 terminal assignment：

- `fieldPath: equipment_types[1].max_borrow_days = 7`
- `fieldPath: equipment_types[0].deposit_amount = 100`
- `fieldPath: equipment_types[2].deposit_amount = 1000`
- `fieldPath: borrowing_system.version = v2.3.1`
- `fieldPath: equipment_types[1].late_fee_per_day = 20`
- `fieldPath: equipment_types[0].late_fee_per_day = 5`
- `fieldPath: borrowing_system.max_concurrent_requests = 50`

但 fallback markdown 最终未消费这些目标 terminal value，而是选中了 sibling/近邻字段：

| Case | fallback markdown 实际消费 | 是否消费目标 terminal value |
|---|---|---|
| FQ3 | `equipment_types[1].type = 精密仪器` | 否 |
| FQ4 | `equipment_types[0].type = 常规设备` | 否 |
| FQ6 | `borrowing_system.name = 校园实验室设备预约系统` | 否 |
| FG1 | `return_policy.damage_report_required`、通知渠道、`same_day_return_cutoff=17:30` | 否 |
| FG2 | `return_policy.damage_report_required`、通知渠道、`same_day_return_cutoff=17:30` | 否 |

### 7.2 answer_markdown -> query_answer_claims

`query_answer_claims` 中未出现目标 terminal value：

| Case | claim 结果 |
|---|---|
| FQ3 | `fieldPath: equipment_types[1].type = 精密仪器` |
| FQ4 | `fieldPath: equipment_types[0].type = 常规设备` |
| FQ6 | `fieldPath: borrowing_system.name = 校园实验室设备预约系统` |
| FG1 | `damage_report_required=true`、通知渠道、`17:30` |
| FG2 | `damage_report_required=true`、通知渠道、`17:30` |

### 7.3 query_answer_citations

`query_answer_citations.source_type` 仍只出现：

- `ARTICLE`
- `SOURCE_FILE`

没有 `FACT_CARD` citation 投影。部分 citation 的 `matched_excerpt` 能覆盖源文件里的目标值，但 claim 本身没有提出目标 terminal value，因此 citation 无法支撑正确答案。

### 7.4 sibling / summary 误答

本轮仍存在 sibling 字段抢占：

- FQ3：抢到 `type`
- FQ4：抢到 `type`
- FQ6：抢到 `name`
- FG1：抢到 `return_policy`
- FG2：抢到 `return_policy`

这说明问题已不在 retrieval 召回，而在 FACT_CARD structured assignment 进入 fallback conclusion 时的目标字段选择。

## 8. 完整 fresh eval 与保护回归

由于 5 个结构化题不是 `5/5 PASS`，本轮按 gate 规则停止：

- 未执行完整 fresh eval 重跑
- 未生成新的 Answer Accuracy / Search Accuracy / Recall@5 / Recall@10 / Citation Accuracy / Abstain Accuracy / Hallucination Count 指标
- 未执行 Q6 terminal field alias 保护回归
- 未执行 S2 chunk/anchor identity 保护回归
- 未执行第一套知识库验收 Q1-Q12、S1-S4

当前历史正式 fresh eval 指标仍以 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md` 为准，本报告不刷新全量指标。

## 9. Gate 判定

结论：FAIL。

原因：

- 工程门禁通过：redline `BLOCKER=0`、定向测试通过、全量 Maven gate 通过。
- 服务级 terminal assignment gate 未通过：5 个目标结构化题 `0/5 PASS`。
- 虽然 FACT_CARD 已召回且包含目标 terminal assignment，但 fallback conclusion 仍选择 sibling/近邻字段，最终 answer、claims、citations 均未落到目标 terminal value。

是否建议提交：不建议提交。

## 10. 下一步唯一根因建议

下一轮只处理一个根因：

**修正 `AnswerGenerationFallbackConclusionSupport` 中 structured FACT_CARD terminal assignment 的目标字段选择逻辑，使其在同一 fact card 内基于问题 token、path segment、value shape 与 sibling context 选择目标 assignment，而不是选择同父级 `type/name/return_policy` 等近邻字段。**

本轮 evidence 已证明：

- 不需要先修 retrieval：FACT_CARD rank 2/3 稳定召回。
- 不应先扩 citation schema：当前正确 terminal claim 尚未进入 answer/claims。
- 不应跑完整 fresh eval：服务级 5 题已失败。

## 11. 合规声明

- 未修改代码。
- 未清 fresh eval 库。
- 未重导资料。
- 未读取或修改 `docs/模型绑定配置参考.md`。
- 未 stage、未 commit、未 push。
- 报告未记录真实 API key / token / password / `sk-` 明文。
