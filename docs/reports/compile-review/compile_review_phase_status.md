# Compile + Human Review 阶段状态台账

- **更新日期**：2026-05-22
- **当前分支**：`codex/qa-polish`
- **阶段名称**：Compile Review Queue 收口阶段（第 4 轮：去重 + 幂等 + 预算 + 容错）

---

## 1. 最近已提交的关键 Commit

| Commit | 日期 | 类型 | 说明 |
|--------|------|------|------|
| `6647c89` | 05-21 | fix(compile) | deduplicate pending review-queue drafts by article key |
| `40e6a35` | 05-21 | fix(admin) | make compile review queue approve idempotent |
| `202f7e3` | 05-21 | fix(redis) | degrade interrupted compile working-set writes to local fallback |
| `38f86b1` | 05-21 | perf(compile) | add writer payload budget limits |
| `252253e` | 05-21 | perf(compile) | slim reviewer payload with relevant source sections |
| `a797c51` | 05-21 | feat(compile) | collapse over-fragmented document topics before writer |

前序相关 commit（已在前几轮收口）：

| Commit | 日期 | 说明 |
|--------|------|------|
| `7c733bf` | 05-20 | align compile publish semantics with human review queue state |
| `b453627` | 05-20 | expose compile human review queue |
| `8fe7001` | 05-19 | add human review queue publish flow |
| `cd1e5ca` | 05-19 | 收口编译任务进度展示语义 |
| `e422629` | 05-19 | deduplicate active compile job submissions |
| `e47d177` | 05-18 | gate large structured tables before article writer |

---

## 2. 已完成能力

### 2.1 Compile 管线稳定性

| 能力 | 验证状态 | 关键 Commit |
|------|:--:|------|
| Writer Unit Routing Gate（过度专题化文档 collapse 为 overview） | 通过 | `a797c51` |
| Reviewer Payload Slimming（按 source section 裁剪 reviewer payload） | 通过 | `252253e` |
| Writer Payload Budget Limits（Writer 输入 token 预算上限） | 通过 | `38f86b1` |
| Redis 中断容错（Redis 不可用时降级为本地文件写入） | 通过 | `202f7e3` |
| Structured Table Gate（超大型表格门禁） | 通过 | `e47d177` |
| Job 提交幂等去重 | 通过 | `e422629` |

**结论**：compile 主链路（Writer routing gate → Reviewer slimming → Writer budget → Redis resilience）已稳定，无逻辑故障。

### 2.2 Review Queue 数据完整性

| 能力 | 验证状态 | 关键 Commit |
|------|:--:|------|
| Pending 草稿去重（同一 article_key 跨 job 只保留最新一条） | 通过 | `6647c89` |
| Approve 幂等（article_key 已存在时跳过入库而非报错） | 通过 | `40e6a35` |
| Approve 流程（needs_human_review → published + 入库 + chunk/vector） | 通过 | `8fe7001` |
| Reject 流程（needs_human_review → rejected，不入库） | 通过 | `8fe7001` |
| 已发布内容不污染待确认队列 | 通过 | `7c733bf` |
| Published/Rejected 历史保留（同一 article_key 可重新入队） | 通过 | `6647c89` |

### 2.3 入库一致性

| 验证点 | 状态 |
|--------|:--:|
| Articles API 与数据库一致 | 通过 |
| 向量索引覆盖率 100% | 通过 |
| 待确认草稿不出现于 articles 列表 | 通过 |

---

## 3. 当前仍存在的问题

### 3.1 Compile 线

| # | 问题 | 严重度 | 状态 |
|---|------|:--:|------|
| C1 | 卡券三期 overview Writer 耗时在 budget slimming 后上升 25.8%（128s→161s），可能与 structured sections 截断有关 | 中 | 待观察 |
| C2 | 3 个历史 FAILED 作业根因为 InterruptedException（应用重启），非逻辑故障，无需修代码 | 低 | 已知，不复现 |
| C3 | `--spring.devtools.restart.enabled=false` 未完全禁用 devtools 类路径重载，编译中途仍可能触发重启 | 低 | 已知，不影响编译结果 |

### 3.2 Query / 产品体验线

| # | 问题 | 严重度 | 状态 |
|---|------|:--:|------|
| Q1 | 24 条待确认草稿堆积（多轮 compile 累积），需人工逐一处理 | 中 | 待运维清理 |
| Q2 | PARTIAL_ANSWER 场景回答完整度可优化 | 低 | 待 query 侧迭代 |
| Q3 | review_queue 中存在历史重复记录（去重修复前已入队的旧数据） | 低 | 数据库已有，新入队不会重复 |

---

## 4. 问题分线归属

| 问题编号 | 归属线 | 说明 |
|:--:|------|------|
| C1 | **Compile** | Writer payload budget 参数调优 |
| C2 | **Compile** | 已知 InterruptedException，非代码缺陷 |
| C3 | **Compile** | Devtools 配置问题，非关键 |
| Q1 | **Query/产品体验** | 运维操作（批量 approve/reject），非代码问题 |
| Q2 | **Query/产品体验** | Query generation 质量优化 |
| Q3 | **Query/产品体验** | 历史数据清理，新入队已去重 |

---

## 5. 下一阶段建议优先级

**建议转回 query / 产品体验侧**，compile 主线已收口。

| 优先级 | 事项 | 类型 | 说明 |
|:--:|------|------|------|
| P0 | 清理 24 条待确认草稿 | 运维 | 批量 approve/reject，清空积压 |
| P1 | 优化 PARTIAL_ANSWER 场景完整度 | query | Query generation prompt / context 调优 |
| P2 | 清理 review_queue 历史重复数据 | 运维 | 去重修复前已入队的旧记录（新入队不会再重复） |
| P3 | 观测卡券三期 Writer 耗时趋势 | compile | 更大样本集上验证是否持续偏高 |
| P4 | NO_RELEVANT_KNOWLEDGE 场景的 fallback 体验 | query | Fallback 文案和引导优化 |

---

## 6. 当前不建议再重复分析的已解决问题

以下问题已被最终 runtime/fix 报告充分验证，**不建议再投入时间重复分析**：

1. **Approve 幂等性**：已通过 `40e6a35` 修复，runtime 验证通过（id=12 场景），各表数量变化正常
2. **Review Queue 去重**：已通过 `6647c89` 修复，DB 层 partial unique index + 应用层 ON CONFLICT DO UPDATE 双重保证
3. **Writer Payload Budget**：token 成本优化已生效，Writer 覆盖面和 structured sections 未退化
4. **Reviewer Payload Slimming**：Reviewer 耗时从 48-78s 降至 21-40s，效果稳定
5. **Redis 中断容错**：降级路径已生效，3 个 InterruptedException 均为外部中断非 Redis 相关
6. **Writer Unit Routing Gate**：过度专题化文档 collapse 行为正常，普通文档拆分不受影响
7. **Structured Table Gate**：超大型表格门禁已生效
8. **Job 提交幂等**：相同参数不重复创建 compile job

---

## 7. 本轮是否修改代码

**否。** 本轮仅整理报告和更新阶段台账，未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、脚本或任何配置文件。
