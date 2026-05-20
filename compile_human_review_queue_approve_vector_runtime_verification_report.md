# Compile Human Review Queue Approve Vector Runtime Verification Report

## 1. Redline

- 初始 redline：`BLOCKER=0 / REVIEW=1863 / ALLOWLIST=244`，退出码 0。
- 复验后 redline：`BLOCKER=0 / REVIEW=1863 / ALLOWLIST=244`，退出码 0。
- 命令：`bash scripts/scan-redline.sh special_cases_report.md`。

## 2. 验证对象与环境

- 服务实例：`http://127.0.0.1:18084`。
- 数据库：`ai-rag-knowledge`，已 reset schema，确认 `compile_article_review_queue` 存在。
- 运行时配置：
  - compile writer/reviewer/fixer：`gpt-5.5`，route label 为 `compile.*.agentd-gpt-5-5-chat`。
  - embedding：`embedding-3`，profile id `2`，expected dimension `2000`。
  - compile review config：`autoFixEnabled=true`、`maxFixRounds=0`、`allowPersistNeedsHumanReview=true`。
- 初始正式表计数：`articles=0`、`article_chunks=0`、`article_vector_index=0`、`article_chunk_vector_index=0`、`compile_article_review_queue=0`、`article_review_audits=0`。

## 3. 使用的测试资料源

- approve 资料源：`/tmp/lattice-hr-approve-vector-src/approve-vector-source.md`
  - 关键标识：`HR-APPROVE-VECTOR-20260520-A`
  - 目标 article_key：`default-source--approve-vector-source`
- reject 资料源：`/tmp/lattice-hr-reject-vector-src/reject-vector-source.md`
  - 关键标识：`HR-REJECT-VECTOR-20260520-B`
  - 目标 article_key：`default-source--reject-vector-source`

## 4. needs_human_review 队列生成结果

- approve job：`07340636-73f4-44f2-928b-98ae026e842d`
  - `status=SUCCEEDED`
  - `persistedCount=0`
  - `reviewSummary.reviewRoute=compile.reviewer.agentd-gpt-5-5-chat`
  - `reviewSummary.needsHumanReviewCount=1`
  - 队列记录：`id=1`，`review_status=needs_human_review`
- reject job：`2eaadf9b-16a3-436d-a8b6-19151017e8da`
  - `status=SUCCEEDED`
  - `persistedCount=0`
  - `reviewSummary.reviewRoute=compile.reviewer.agentd-gpt-5-5-chat`
  - `reviewSummary.needsHumanReviewCount=1`
  - 队列记录：`id=2`，`review_status=needs_human_review`

结论：两份资料均成功产生 `needs_human_review` 队列记录；未 approve 前没有进入正式文章、chunk 或 vector index。

## 5. Approve 前状态

目标 article_key：`default-source--approve-vector-source`

| 对象 | 计数 / 状态 |
|---|---:|
| queue | `id=1`, `review_status=needs_human_review`, `published_article_key=null` |
| articles | 0 |
| article_chunks | 0 |
| article_vector_index | 0 |
| article_chunk_vector_index | 0 |
| article_review_audits | 0 |

## 6. Approve 后结果

动作：`POST /api/v1/admin/compile/review-queue/1/approve`

| 校验项 | 结果 |
|---|---|
| queue 状态 | `published` |
| publishedArticleKey | `default-source--approve-vector-source` |
| article_key | `default-source--approve-vector-source` |
| articles.review_status | `passed` |
| articles.lifecycle | `ACTIVE` |
| chunks 数量 | 1 |
| article_vector_index 数量 | 1 |
| article_chunk_vector_index 数量 | 1 |
| vector model | `embedding-3` |
| article vector dimension | `embedding_dimensions=2000`, `vector_dims(embedding)=2000` |
| chunk vector dimension | profile `expected_dimensions=2000`, `vector_dims(embedding)=2000` |
| audit action | `compile_review_queue_approve` |
| audit 状态流转 | `needs_human_review -> passed` |
| audit metadata | `source=compile_review_queue`, `queueId=1`, `jobId=07340636-73f4-44f2-928b-98ae026e842d` |
| article metadata | `humanReview.source=compile_review_queue`, `humanReview.queueId=1` |

结论：approve 后正式文章、chunk、文章级向量索引、chunk 级向量索引均真实写入，向量维度与运行时 embedding profile 一致。

## 7. Reject 后结果

动作：`POST /api/v1/admin/compile/review-queue/2/reject`

目标 article_key：`default-source--reject-vector-source`

| 校验项 | Reject 前 | Reject 后 |
|---|---:|---:|
| articles 中该 article_key | 0 | 0 |
| article_chunks 中该 article_key | 0 | 0 |
| article_vector_index 中该 article_key | 0 | 0 |
| article_chunk_vector_index 中该 article_key | 0 | 0 |
| 全局 articles | 1 | 1 |
| 全局 article_chunks | 1 | 1 |
| 全局 article_vector_index | 1 | 1 |
| 全局 article_chunk_vector_index | 1 | 1 |

- queue 状态：`rejected`
- `publishedArticleKey=null`
- audit action：`compile_review_queue_reject`
- audit 状态流转：`needs_human_review -> rejected`
- audit metadata：`source=compile_review_queue`、`queueId=2`、`jobId=2eaadf9b-16a3-436d-a8b6-19151017e8da`

结论：reject 后未写入正式 articles、chunks 或任何 vector index；全局向量索引计数未增加。

## 8. 是否修改代码

否。

本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、前端、prompt、`scripts/scan-redline.sh`、redline allowlist，也未提交代码。仅新增本验证报告；`special_cases_report.md` 由 redline 命令按既有流程更新。

## 9. 是否发现运行时缺陷

未发现。

本轮复验覆盖了：

- 未 approve 前：`articles/chunks/vector` 均不包含该草稿。
- approve 后：`article_vector_index > 0` 且 `article_chunk_vector_index > 0`。
- reject 后：正式表与 vector index 均不新增该文章。
- approve/reject audit 均记录 `compile_review_queue` 来源。

## 10. 下一步建议

通过，建议进入前端人工确认入口实现。
