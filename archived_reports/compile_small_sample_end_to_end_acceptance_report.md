# Compile Small Sample End-to-End Acceptance Report

## 1. redline BLOCKER / REVIEW / ALLOWLIST

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：通过，退出码 `0`
- `BLOCKER=0`
- `REVIEW=1863`
- `ALLOWLIST=244`

## 2. 验收使用的资料文件

本轮使用真实上传接口导入 2 个小文件：

- `/tmp/lattice-small-sample-e2e/small-approve.md`
- `/tmp/lattice-small-sample-e2e/small-reject.md`

关键标识：

- approve 场景：`SMALL-E2E-APPROVE-20260520-A`
- reject 场景：`SMALL-E2E-REJECT-20260520-B`

真实运行对象：

- `source-run id = 1`
- `compileJobId = 0fb6e6c5-f0eb-424e-aaaa-47e2ac82875f`
- 资料源：`small-approve`

## 3. 每个阶段是否真实发生

### 3.1 资料接收

真实发生。

- 上传接口：`POST /api/v1/admin/uploads`
- 返回 `source-run id = 1`
- `progressSteps` 首步 `TASK_RECEIVED / 资料接收` 进入 `COMPLETED`

### 3.2 Writer 生成

真实发生。

- `compile_job_steps.step_name = compile_new_articles`
- `agent_role = WriterAgent`
- `model_route = compile.writer.agentd-gpt-5-5-chat`
- 状态：`succeeded`
- 时间：`2026-05-20 09:55:44.903+00` -> `2026-05-20 09:56:49.117+00`
- 耗时约：`64.214s`

### 3.3 Reviewer 审查

真实发生。

- `compile_job_steps.step_name = review_articles`
- `agent_role = ReviewerAgent`
- `model_route = compile.reviewer.agentd-gpt-5-5-chat`
- 状态：`succeeded`
- 结果摘要：`needsHumanReviewCount=2`
- 时间：`2026-05-20 09:56:49.120+00` -> `2026-05-20 09:57:39.911+00`
- 耗时约：`50.791s`

### 3.4 Fixer 修复

未触发。

- 未生成 `fix_review_issues` / `FixerAgent` 对应 step
- `fixAttemptCount=0`

### 3.5 re-review

未触发。

- 无 fix loop，因此无 re-review

### 3.6 人工确认

真实发生。

上传完成后，2 条草稿都进入 `compile_article_review_queue`：

- queue `id=5`：`small-approve--small-approve`
- queue `id=6`：`small-approve--small-reject`

二者初始均为：

- `review_status=needs_human_review`
- `published_article_key=null`

随后执行：

- approve queue `5`
- reject queue `6`

### 3.7 入库 / 不入库

真实发生。

- approve 后：
  - `articles_approve=1`
  - `chunks_approve=1`
  - `vec_approve=1`
  - `chunk_vec_approve=1`
- reject 后：
  - `articles_reject=0`
  - `chunks_reject=0`
  - `vec_reject=0`
  - `chunk_vec_reject=0`

## 4. 是否触发人工确认

是。

这是本轮最关键的正向验证结果之一：

- Writer / Reviewer 跑完后，2 个小文件都没有直接入库
- 而是先进入 `needs_human_review` 队列
- 人工确认前，正式知识库与向量表中均为 `0`

## 5. 确认 / 驳回后的真实落库结果

### approve（queue id = 5）

- queue 状态：`published`
- `published_article_key = small-approve--small-approve`
- DB：
  - `articles = 1`
  - `article_chunks = 1`
  - `article_vector_index = 1`
  - `article_chunk_vector_index = 1`
- audit：
  - `compile_review_queue_approve`
  - `needs_human_review -> passed`

### reject（queue id = 6）

- queue 状态：`rejected`
- `published_article_key = null`
- DB：
  - `articles = 0`
  - `article_chunks = 0`
  - `article_vector_index = 0`
  - `article_chunk_vector_index = 0`
- audit：
  - `compile_review_queue_reject`
  - `needs_human_review -> rejected`

## 6. 页面状态与数据库状态是否一致

部分一致，部分不一致。

一致的部分：

- 页面“待人工确认草稿”区域在上传完成后真实出现 2 条草稿
- approve/reject 后，队列页面清空，`GET /api/v1/admin/compile/review-queue?status=needs_human_review` 返回 `total=0`
- DB 也对应变为一条已发布、一条已驳回

不一致的部分：

- `source-run:1` 在两条草稿都尚未人工确认、且正式表仍是 `0` 时，就已经返回：
  - `status = SUCCEEDED`
  - `displayStatusLabel = 已完成`
  - `completionNotice = 处理成功，资料已写入知识库`
- 同时 `processing-tasks` 汇总也显示：
  - `已完成`
  - `最近已经成功处理并写入知识库`

但此时数据库真实状态是：

- 2 条草稿都还在 `needs_human_review`
- `articles/chunks/vector` 全部还是 `0`

结论：

- “待人工确认队列”和数据库是对得上的
- 但 `source-run / processing-tasks` 的“已完成 / 已写入知识库”语义与真实落库状态不一致

## 7. 是否还存在“显示已完成但实际未入库/已驳回”的语义问题

是，存在。

这是本轮发现的新的阻塞问题。

具体表现：

1. 当 2 个小文件都进入 `needs_human_review` 队列、尚未正式入库时：
   - 页面/API 已显示 `已完成`
   - completion notice 已写成“资料已写入知识库”
2. 但数据库真实状态是：
   - 尚未写入 `articles`
   - 尚未写入 `article_chunks`
   - 尚未写入 `article_vector_index`
   - 尚未写入 `article_chunk_vector_index`
3. 最终人工确认后，实际只有其中 1 条 approve 入库，另一条 reject 不入库
   - 这进一步说明 bundle 级“资料已写入知识库”文案会误导

## 8. 总耗时与主要耗时阶段

### 总耗时

- 请求提交到 worker 开始：约 `0.540s`
- worker 开始到 compile job 完成：约 `115.441s`
- 端到端（上传请求到 compile 完成）：约 `115.982s`

### 主要耗时阶段

- Writer：约 `64.214s`
- Reviewer：约 `50.791s`
- persist/finalize：秒级，可忽略不计
- Fixer / re-review：未触发

说明：

- 人工确认动作是验收操作的一部分，不计入系统自动 compile 耗时

## 9. 是否存在新的阻塞问题

存在。

新的阻塞问题为：

- `source-run / processing-tasks` 在“全部进入待人工确认、尚未正式入库”时，仍宣称“处理成功，资料已写入知识库”

这会直接误导用户：

- 用户会以为资料已经入库
- 实际却仍需到“待人工确认”手动处理
- 而且最终还可能存在部分驳回、不入库的条目

## 10. 本轮是否修改代码

否。

本轮只做了：

- redline
- 真实上传
- 真实页面查看
- 只读 API 查询
- 数据库只读核对
- approve / reject 运行时动作

未修改代码、前端、测试、后端或数据结构。

