# Compile Source Run Publish Semantics Runtime Verification Report

## 执行概要

- **执行时间**: 2026-05-20 22:13 - 22:32 CST
- **执行人**: agentD
- **代码分支**: codex/qa-polish
- **数据库**: ai-rag-knowledge (lattice schema)
- **应用端口**: 18082 (local-dev profile)
- **任务类型**: 只读验证，不改代码

---

## 1. Redline 扫描结果

- **BLOCKER**: 0
- **REVIEW**: 1863
- **ALLOWLIST**: 244
- **来源**: `special_cases_report.md` (date: 2026-05-20 21:56)

---

## 2. 场景A：全部待人工确认，尚未发布

### 2.1 输入样本

| 文件 | 内容要点 |
|---|---|
| `scenario_a_1.md` | 资料编号 SCENARIO-A-001, 关键状态 WAIT_CONFIRM_A1, 关键金额 519 元, 含 TODO 占位 |
| `scenario_a_2.md` | 资料编号 SCENARIO-A-002, 关键状态 WAIT_CONFIRM_A2, 关键金额 318 元, 含 TODO 占位 |

- Upload runId=2, compileJobId=7294df2b, sourceId=3

### 2.2 Source Run 结果

| 字段 | 值 |
|---|---|
| status | SUCCEEDED |
| displayStatusLabel | `待人工确认` |
| message | `草稿尚未入库，需人工确认后才能发布` |
| completionNotice | `草稿尚未入库，需人工确认后才能发布` |
| pendingHumanReviewCount | **2** |
| publishedCount | **0** |
| rejectedCount | **0** |
| reasonSummary | `质量检查已完成，等待人工确认后决定是否入库` |
| progressText | `待人工确认 2 篇` |
| nextStepHint | `去待人工确认处理` |

### 2.3 Processing Tasks 结果

| 字段 | 值 |
|---|---|
| summary.waitingCount | **1** |
| displayStatusLabel | `待人工确认` |
| completionNotice | `草稿尚未入库，需人工确认后才能发布` |
| pendingHumanReviewCount | **2** |
| publishedCount | **0** |
| rejectedCount | **0** |
| helpState.tone / title | `warning` / `有一批资料还在等待人工确认` |

### 2.4 Review Queue 统计

- `needs_human_review` queue: **2 项**
  - id=7, concept=scenario-a-1
  - id=8, concept=scenario-a-2

### 2.5 数据库落库结果

| 表 | 写入数 | 验证 |
|---|---|---|
| articles | 3 (baseline不变) | ✅ 未写入 |
| article_chunks | 4 (baseline不变) | ✅ 未写入 |
| article_vector_index | 3 (baseline不变) | ✅ 未写入 |
| article_chunk_vector_index | 4 (baseline不变) | ✅ 未写入 |
| compile_article_review_queue | +2 (needs_human_review) | ✅ 入队列 |

### 2.6 一致性校验

- [x] API 返回 publish outcome: pendingHumanReviewCount=2, publishedCount=0, rejectedCount=0
- [x] DB compile_article_review_queue 有 2 条 needs_human_review 记录
- [x] DB articles 未增加新条目
- [x] 页面/API/DB 三者一致
- [x] **不会**误显示"资料已写入知识库" — 正确显示"草稿尚未入库"

---

## 3. 场景B：部分 approve + 部分 reject

### 3.1 操作

| 队列ID | 概念 | 操作 | 结果 |
|---|---|---|---|
| 7 | scenario-a-1 | **approve** | reviewStatus=published |
| 8 | scenario-a-2 | **reject** | reviewStatus=rejected |

### 3.2 Source Run 结果

| 字段 | 值 |
|---|---|
| displayStatusLabel | `已处理` |
| message | `本次草稿已处理完成，但只有部分内容进入知识库` |
| completionNotice | `本次草稿已处理完成，但只有部分内容进入知识库` |
| pendingHumanReviewCount | **0** |
| publishedCount | **1** |
| rejectedCount | **1** |
| reasonSummary | `已入库 1 篇，已驳回 1 篇` |
| progressText | `已入库 1 篇 · 已驳回 1 篇` |

### 3.3 Processing Tasks 结果

| 字段 | 值 |
|---|---|
| summary.waitingCount | **0** |
| displayStatusLabel | `已处理` |
| completionNotice | `本次草稿已处理完成，但只有部分内容进入知识库` |
| pendingHumanReviewCount | **0** |
| publishedCount | **1** |
| rejectedCount | **1** |

### 3.4 数据库落库结果

| 表 | 写入数 | 验证 |
|---|---|---|
| articles | 4 (+1: scenario-a-1) | ✅ approved 入库 |
| article_chunks | 5 (+1) | ✅ |
| article_vector_index | 4 (+1) | ✅ |
| article_chunk_vector_index | 5 (+1) | ✅ |
| compile_article_review_queue | id=7: published, id=8: rejected | ✅ |

- scenario-a-2: **未**在 articles 表中出现 ✅ 驳回未入库

### 3.5 一致性校验

- [x] API publish outcome: publishedCount=1, rejectedCount=1, pendingHumanReviewCount=0
- [x] DB articles 增加了 scenario-a-1，无 scenario-a-2
- [x] 页面/API/DB 三者一致
- [x] 不会误显示"资料已写入知识库" — 正确显示"只有部分内容进入知识库"

---

## 4. 场景C：全部 approve

### 4.1 操作

- Upload scenario_c.md (runId=3, jobId=fc583fd8)
- Approve queue id=9 (concept=scenario-c)

### 4.2 Source Run 结果

| 字段 | 值 |
|---|---|
| displayStatusLabel | `已完成` |
| message | `资料已正式发布到知识库` |
| completionNotice | `资料已正式发布到知识库` |
| pendingHumanReviewCount | **0** |
| publishedCount | **1** |
| rejectedCount | **0** |
| reasonSummary | `资料已正式发布到知识库` |
| progressText | `已入库 1 篇` |

### 4.3 数据库落库

- articles: 5 (+1: scenario-c--scenario-c) ✅
- article_chunks: 6 (+1) ✅
- article_vector_index: 5 (+1) ✅
- article_chunk_vector_index: 6 (+1) ✅

### 4.4 一致性校验

- [x] API/DB 一致
- [x] displayStatusLabel = `已完成` 且 message = `资料已正式发布到知识库`

---

## 5. 场景D：全部 reject

### 5.1 操作

- Upload scenario_d.md (runId=4, jobId=3dc9f0f0)
- Reject queue id=10 (concept=scenario-d)

### 5.2 Source Run 结果

| 字段 | 值 |
|---|---|
| displayStatusLabel | `未入库` |
| message | `本次草稿已全部驳回，未进入正式知识库` |
| completionNotice | `本次草稿已全部驳回，未进入正式知识库` |
| pendingHumanReviewCount | **0** |
| publishedCount | **0** |
| rejectedCount | **1** |
| reasonSummary | `本次草稿已全部驳回，未进入正式知识库` |
| progressText | `已驳回 1 篇` |

### 5.3 数据库落库

- articles: **5 (不变)** ✅ scenario-d 未写入
- scenario-d 在所有正式表（articles/chunks/vectors）中均不存在
- compile_article_review_queue: id=10 review_status=rejected, published_article_key=null ✅

### 5.4 一致性校验

- [x] API/DB 一致
- [x] displayStatusLabel = `未入库` 且 message = `本次草稿已全部驳回`

---

## 6. Processing Tasks 最终状态汇总

| 任务 | displayStatusLabel | completionNotice | pub | rej | pend |
|---|---|---|---|---|---|
| source-run:4 (D) | 未入库 | 本次草稿已全部驳回，未进入正式知识库 | 0 | 1 | 0 |
| source-run:3 (C) | 已完成 | 资料已正式发布到知识库 | 1 | 0 | 0 |
| source-run:2 (B) | 已处理 | 本次草稿已处理完成，但只有部分内容进入知识库 | 1 | 1 | 0 |
| source-run:1 (旧) | 已处理 | 本次草稿已处理完成，但只有部分内容进入知识库 | 1 | 1 | 0 |

Summary: running=0, waiting=0, succeeded=8

---

## 7. 全部场景预期 vs 实际对比

| 场景 | 预期 displayStatusLabel | 预期 message | 实际 | 是否一致 |
|---|---|---|---|---|
| A (全待确认) | `待人工确认` | `草稿尚未入库，需人工确认后才能发布` | `待人工确认` / `草稿尚未入库...` | ✅ |
| B (部分通过) | `已处理` | `本次草稿已处理完成，但只有部分内容进入知识库` | `已处理` / `本次草稿已处理完成...` | ✅ |
| C (全通过) | `已完成` | `资料已正式发布到知识库` | `已完成` / `资料已正式发布...` | ✅ |
| D (全驳回) | `未入库` | `本次草稿已全部驳回，未进入正式知识库` | `未入库` / `本次草稿已全部驳回...` | ✅ |

---

## 8. 关键问题核实

### 8.1 是否还存在"未正式入库却显示资料已写入知识库"的问题？

**对于 source-run 类型任务：不再存在。**

- 场景A中，2篇草稿全部待确认，API正确返回 `pendingHumanReviewCount=2`，displayStatusLabel=待人工确认，message=草稿尚未入库
- 场景B中，1篇approved + 1篇rejected，API正确返回 `publishedCount=1, rejectedCount=1`，message=只有部分内容进入知识库
- 场景C中，1篇全部approved，API正确返回 displayStatusLabel=已完成，message=资料已正式发布
- 场景D中，1篇全部rejected，API正确返回 displayStatusLabel=未入库，message=已全部驳回

### 8.2 注意：历史 compile-job 类型任务

历史遗留的 `compile-job:*` 类型任务（非 source-run 类型）在 processing-tasks 列表中仍显示 `处理成功，资料已写入知识库`，但其 `pendingHumanReviewCount/publishedCount/rejectedCount` 均为 0。

这是**预期的兼容行为**：这些任务并非通过 source-upload 链路产生，而是直接 compile 提交的任务，未经过 `SourceUploadWorkflowSupport` 的 publish outcome 聚合逻辑。它们不在本轮修复范围内，也不会误导用户（它们对应的是更早的历史测试数据）。

---

## 9. 一致性矩阵

| 维度 | 场景A | 场景B | 场景C | 场景D |
|---|---|---|---|---|
| source-run API | ✅ | ✅ | ✅ | ✅ |
| processing-tasks API | ✅ | ✅ | ✅ | ✅ |
| review-queue API | ✅ | ✅ | ✅ | ✅ |
| DB compile_article_review_queue | ✅ | ✅ | ✅ | ✅ |
| DB articles | ✅ | ✅ | ✅ | ✅ |
| DB article_chunks | ✅ | ✅ | ✅ | ✅ |
| DB article_vector_index | ✅ | ✅ | ✅ | ✅ |
| DB article_chunk_vector_index | ✅ | ✅ | ✅ | ✅ |
| API/DB 一致 | ✅ | ✅ | ✅ | ✅ |

---

## 10. 结论

### 10.1 本轮是否修改代码

**否。本轮未修改任何代码、测试、前端或配置。**

### 10.2 是否建议进入提交前质量复核

**是。** 理由：
1. redline BLOCKER=0
2. 4个场景全部通过小样本真实链路复验
3. API/DB/语义一致性均已验证
4. "未正式入库却显示已写入知识库"的误读问题已在 source-run 层面修复
5. publish outcome 统计（pendingHumanReviewCount / publishedCount / rejectedCount）在所有场景下均正确

### 10.3 遗留关注项（非阻塞）

1. `compile-job:*` 类型的历史任务在 processing-tasks 列表中未接入 publish outcome 统计（pub=0, rej=0, pend=0），但其 `completionNotice` 仍为"资料已写入知识库"。对于 source-upload 链路的新任务，正确语义已生效。
2. 建议后续为 compile-job 类型任务也接入同样的 publish outcome 聚合逻辑，使 processing-tasks 全场景语义一致。
