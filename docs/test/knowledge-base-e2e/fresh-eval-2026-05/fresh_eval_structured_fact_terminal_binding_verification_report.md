# fresh eval structured fact terminal binding 服务级复验报告

## 1. 任务范围与合规声明

- 角色：agentD（验证/测试）
- 本轮只执行验证、查询、健康检查与报告输出
- 未修改：
  - `src/**`
  - `scripts/**`
  - 任何配置 / prompt / allowlist
  - `docs/模型绑定配置参考.md`
  - fresh eval 资料包 / 题集
- 未 stage、未 commit、未 push
- 未清库、未重建 schema、未重新导入 fresh eval 资料

## 2. 前置门禁结果

### 2.1 redline

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：
  - `BLOCKER=0`
  - `REVIEW=2078`
  - `ALLOWLIST=259`
- 结论：通过

### 2.2 定向测试

- 命令：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests,AnswerFallbackEvidenceSelectorTests,AnswerFallbackConclusionBuilderTests test`
- 结果：
  - `BUILD SUCCESS`
  - `Tests run: 87, Failures: 0, Errors: 0, Skipped: 0`
- 结论：通过

### 2.3 全量 `mvn test`

- 命令：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：
  - `BUILD SUCCESS`
  - `Tests run: 923, Failures: 0, Errors: 0, Skipped: 0`
  - 总耗时约 `06:27`
- 结论：通过

## 3. 服务与库状态

### 3.1 健康检查与启动

- 启动前健康检查：
  - `GET http://127.0.0.1:18082/actuator/health`
  - 结果：连接失败，服务未运行
- 启动方式：
  - `./scripts/run-local-dev.sh`
  - 未使用 `--reset-schema`
- 启动后健康检查：
  - `GET http://127.0.0.1:18082/actuator/health`
  - 结果：`{"status":"UP"}`

### 3.2 当前 fresh eval 库复用情况

- 本轮确认当前库已存在 fresh eval 资料，因此**未重新导入**
- 只读核验结果：
  - `source_files = 5`
  - `articles = 5`
  - `article_chunks = 9`
  - `fact_cards = 11`
- 当前可见资料：
  - `lab-safety-management-handbook.md`
  - `equipment-borrowing-policy.yaml`
  - `chemical-storage-grading.xlsx`
  - `equipment-maintenance-schedule.csv`
  - `lab-emergency-response-procedures.pdf`

结论：满足“复用当前 fresh eval 库、不清库、不重导”的约束。

## 4. FQ3 / FQ4 / FQ6 / FG1 / FG2 服务级复验

### 4.1 结果总表

| Case | queryId | 预期 | 实际 answer 摘要 | outcome | citations / evidence | 判定 | 失败类型 |
|---|---|---|---|---|---|---|---|
| FQ3 | `e768780b-f14f-4e4f-99aa-c753ab84a6fa` | `7` | 仍回答“三类设备 + 审批链 + 系统名称”，没有给出 `7` | `SUCCESS / FALLBACK / DEGRADED` | 仅落在 `equipment borrowing policy` 粗粒度 article/source | FAIL | 证据已召回但回答漏点 |
| FQ4 | `8cb0a9a7-c24c-4b5e-9c82-627cf79dd216` | `100 / 1000` | 仍回答“三类设备 + 审批链 + 系统名称”，没有给出两个押金值 | `SUCCESS / FALLBACK / DEGRADED` | 同上 | FAIL | 证据已召回但回答漏点 |
| FQ6 | `f0476632-2af8-487e-9cd5-a5f13c315687` | `v2.3.1` | 仍回答“系统名称 + API endpoint + damage_report_required=true” | `SUCCESS / FALLBACK / DEGRADED` | 同上 | FAIL | 证据已召回但回答漏点 |
| FG1 | `691114e2-d294-4017-84c4-e8363416d586` | `20 / 5` | 仍回答 `damage_report_required`、通知渠道、截止时间 | `SUCCESS / FALLBACK / DEGRADED` | 同上 | FAIL | 证据已召回但回答漏点 |
| FG2 | `5693cef9-ece7-41ec-b53d-068a8d60c351` | `50` | 仍回答 `damage_report_required`、通知渠道、截止时间 | `SUCCESS / FALLBACK / DEGRADED` | 同上 | FAIL | 证据已召回但回答漏点 |

### 4.2 关键观察

1. 5 个目标题**没有出现明显改善**。
2. 返回形态与 `acceptance-report.md` 中的失败答案口径一致，仍是：
   - `equipment_types` 汇总描述
   - `borrowing_system` 的无关 sibling
   - `return_policy` 的无关 sibling
3. 5 题全部仍走：
   - `answerOutcome=SUCCESS`
   - `generationMode=FALLBACK`
   - `modelExecutionStatus=DEGRADED`
4. `citationCheck.coverageRate` 仍为：
   - `FQ3/FQ4 = 0.6000`
   - `FQ6/FG1/FG2 = 0.6667`
   这说明 citation 机器核验并未转化成 terminal field 正确回答。

## 5. 审计侧复核

### 5.1 retrieval audit

本轮新产生的 retrieval run：

| run_id | case | fused_hit_count | fact_card_hit_count | source_chunk_hit_count | coverage_status |
|---:|---|---:|---:|---:|---|
| 44 | FQ3 | 5 | 2 | 2 | `covered` |
| 45 | FQ4 | 5 | 2 | 3 | `covered` |
| 43 | FQ6 | 7 | 2 | 1 | `covered` |
| 46 | FG1 | 5 | 2 | 2 | `covered` |
| 47 | FG2 | 7 | 2 | 1 | `covered` |

### 5.2 fused hits 结构

5 个题的 fused hits 结构仍保持与 root cause report 一致：

- rank 1：`source_chunk_fts`
- rank 2：`FACT_CARD` `结构化键值条目 - equipment-borrowing-policy.yaml#0`
- rank 3：`FACT_CARD` `结构化列表条目 - equipment-borrowing-policy.yaml#0`
- rank 4：`SOURCE`
- rank 5：`ARTICLE` / `refkey`

### 5.3 answer audit

本轮新产生的 answer audit：

| audit_id | case | answer_outcome | generation_mode | citation_coverage | unsupported_claim_count |
|---:|---|---|---|---:|---:|
| 31 | FQ3 | `SUCCESS` | `FALLBACK` | 0.6000 | 2 |
| 32 | FQ4 | `SUCCESS` | `FALLBACK` | 0.6000 | 2 |
| 33 | FQ6 | `SUCCESS` | `FALLBACK` | 0.6667 | 1 |
| 35 | FG1 | `SUCCESS` | `FALLBACK` | 0.6667 | 1 |
| 34 | FG2 | `SUCCESS` | `FALLBACK` | 0.6667 | 1 |

### 5.4 只读结论

- 当前服务已加载新构建产物并真实执行了新一轮 query
- 当前库中 exact `FACT_ENUM` 仍稳定召回
- 但最终答案仍未消费 terminal field value
- 因此本轮更像**修复未生效 / 未命中真正 runtime gate**，而不是服务未更新或库状态漂移

## 6. FS1-FS3 快速确认

虽然 5 个结构化题未见改善，本轮仍补做了 `FS1-FS3` 搜索 spot check，结论如下：

| Case | 结果 | 结论 |
|---|---|---|
| FS1 `校园实验室安全管理手册` | Top5 仍由文档级 article + chunk 级 article 占前列 | 仍是 sourceTitle / 主条目身份稳定性问题 |
| FS2 `化学品分类存储` | Top5 仍以整篇 `校园实验室安全管理手册` 为主，不是独立弱标题条目 | 仍是弱标题 / anchor materialization 问题 |
| FS3 `实验室化学品分级存储管理规范` | Top5 仍以整篇 article 为主，不是 representativeTitle 自身条目 | 仍是 representativeTitle 画像缺失问题 |

结论：`FS1-FS3` 仍是**独立标题搜索问题**，本轮 structured fact terminal binding 修复既未解决，也未明显误伤这一桶。

## 7. 完整 fresh eval 回归与指标

### 7.1 是否执行完整回归

- **未执行完整 fresh eval 重跑**
- 原因：
  - 用户给定规则是“若 5 个结构化失败题至少有明显改善，再跑完整回归”
  - 本轮 5 个目标题 **0/5 改善**
  - 因此不满足继续跑完整回归的前置条件

### 7.2 当前指标口径

由于未触发完整重跑，本轮**不生成新的全量指标**；当前正式指标仍以 [`acceptance-report.md`](/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md) 为准：

| 指标 | 当前正式口径 |
|---|---:|
| Answer Accuracy | `10/15 (66.7%)` |
| Search Accuracy | `1/4 (25.0%)` |
| Recall@5 | `13/15 (86.7%)` |
| Recall@10 | `13/15 (86.7%)` |
| Citation Accuracy | `2/15 (13.3%)` |
| Abstain Accuracy | `2/2 (100.0%)` |
| Hallucination Count | `5` |

### 7.3 对当前口径的只读判断

- 结构化字段桶：**未从 FAIL 收敛**
- `FS1-FS3`：仍是独立标题搜索问题
- 因 5 个目标题未改善，本轮没有理由认为全量指标会优于上述正式基线

## 8. Gate 判定

### 8.1 是否通过本轮修复 gate

- **不通过**

原因：

1. 前置门禁全部通过，但这只说明代码可编译、测试绿
2. 真实服务级复验中，5 个目标结构化题 `0/5 PASS`
3. retrieval audit 继续证明 exact fact card 已召回
4. 但最终答案仍未把 terminal field 写入终答

结论：这是**功能 gate FAIL**，不是工程 gate FAIL。

### 8.2 是否存在硬编码风险

- 本轮验证未发现新的 redline `BLOCKER`
- 也未看到“通过题目而新增 case 特判”的直接证据
- 但因为修复**没有带来预期行为变化**，当前更应判断为：
  - **无明确硬编码新增证据**
  - **但修复无效，不能据此建议提交**

## 9. 是否建议提交 agentA 本轮代码

- **不建议提交**

理由：

1. 真实服务级复验没有验证到目标收益
2. 5 个结构化题全部仍 FAIL
3. 当前不能证明修复覆盖到了实际 runtime 缺口

## 10. 是否需要 agentB 继续归因

- **需要**

建议 agentB 下一轮只读归因聚焦：

1. 为什么新修复在真实服务链路上没有改变 `source_chunk_fts rank1 + FACT_ENUM rank2/3` 之后的答案消费结果
2. 是否仍有更靠后的 runtime gate 覆盖了 agentA 这轮改动
3. `fallbackReason=DETERMINISTIC_EXACT_LOOKUP_PREFERRED` 下，究竟是哪一段逻辑最终把 direct fact card 又退化成 summary / return_policy

## 11. Q6 / 第一套保护场景是否需要后续单独回归

- **需要，但不建议在本轮继续执行**

理由：

1. 当前目标修复在 fresh eval 结构化桶上未生效
2. 在这种情况下继续扩跑 `Q6 / 第一套保护场景`，信息增量有限
3. 更合适的顺序是：
   - 先让 agentB 做失败归因
   - 再由 agentA 做下一轮唯一根因修复
   - 然后再由 agentD 一并回归：
     - `FQ3/FQ4/FQ6/FG1/FG2`
     - `FS1-FS3`
     - `Q6 / 第一套保护场景`

## 12. 最终结论

- 工程门禁：`redline`、定向测试、全量测试均通过
- 服务门禁：失败
- 当前 fresh eval 库已复用，未清库、未重导
- 5 个结构化失败题未见改善，`0/5 PASS`
- `FS1-FS3` 仍是独立标题搜索问题
- 本轮修复 gate 不通过
- 不建议提交 agentA 本轮代码
- 建议回到 agentB 做进一步只读归因
