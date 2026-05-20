# Compile Human Review Queue Frontend Runtime Verification Report

## 1. Redline

- 初始 redline：`BLOCKER=0 / REVIEW=1863 / ALLOWLIST=244`，退出码 0。
- 复验后 redline：`BLOCKER=0 / REVIEW=1863 / ALLOWLIST=244`，退出码 0。
- 命令：`bash scripts/scan-redline.sh special_cases_report.md`。

## 2. 后台页面 URL

- 页面：`http://127.0.0.1:18085/admin?tab=knowledge-runs`
- 服务：本轮临时启动 `SERVER_PORT=18085 ./scripts/run-local-dev.sh`
- 浏览器验证方式：
  - Codex in-app browser 验证页面入口、列表、详情、空态。
  - 本机 Chrome + Playwright 验证原生 `confirm/prompt` 二次确认与 approve/reject POST 联动。

## 3. 测试资料与队列记录

- approve 资料源：`/tmp/lattice-hr-frontend-approve-src/frontend-approve-source.md`
  - jobId：`80925eb3-7972-4146-8130-e3884760bfaf`
  - queue id：`3`
  - article_key：`default-source--frontend-approve-source`
- reject 资料源：`/tmp/lattice-hr-frontend-reject-src/frontend-reject-source.md`
  - jobId：`84ec3353-4d9c-4b92-97d7-5c3a533b6956`
  - queue id：`4`
  - article_key：`default-source--frontend-reject-source`

两条 compile job 均为 `SUCCEEDED`，`persistedCount=0`，`reviewSummary.needsHumanReviewCount=1`，成功产生 `needs_human_review` 队列记录。

## 4. 入口与空态

- “待人工确认”入口：可见。
- “刷新待确认”按钮：可见，可触发列表刷新。
- 初始列表加载前：API 返回 0 条时，页面显示“当前没有待人工确认草稿。”，空态清楚。
- approve/reject 完成后：列表再次显示“当前没有待人工确认草稿。”，空态清楚。

## 5. 列表加载

生成两条 `needs_human_review` 后，页面列表正确加载：

- 显示标题：`Frontend Approve Source`、`Frontend Reject Source`。
- 显示来源：`frontend-approve-source.md`、`frontend-reject-source.md`。
- 显示状态：中文 badge `待人工确认`。
- 显示说明：`Reviewer 判定需要人工确认`。
- 默认未展示内部字段名：列表中未直接展示 `compile_article_review_queue`、`review_route`、`model_route`。

## 6. 详情展示

- 草稿正文：可读，详情页展示“草稿正文”代码块。
- 来源文件：可见，展示对应 `frontend-approve-source.md` / `frontend-reject-source.md`。
- Reviewer 判定：可见；本轮 LLM reviewer 返回了结构化 issue，页面展示严重度、类型与描述。
- review issues 为空时兜底：代码路径存在兜底文案“Reviewer 判定需要人工确认，但未返回结构化问题详情。”；本轮真实数据非空，未触发该空 issue 兜底。
- 技术详情：默认折叠；折叠前不展示审查路线、模型等技术字段。
- 默认隐藏内部字段：列表和详情主体未直接展示 `compile_article_review_queue` / `review_route` / `model_route` 字段名。草稿正文自身包含 frontmatter `review_status: needs_human_review`，这是待确认草稿内容的一部分，不是前端额外暴露的列表/详情技术字段。

## 7. Approve 浏览器操作结果

浏览器操作：

- 在详情页点击“确认入库”。
- 原生二次确认出现，文案：`确认后文章将进入正式知识库并参与检索。`
- 原生 prompt 出现：
  - `请输入确认人`
  - `请输入确认说明（可选）`
- 页面发起 `POST /api/v1/admin/compile/review-queue/3/approve`。
- 操作后发起 `GET /api/v1/admin/compile/review-queue?status=needs_human_review` 刷新列表。

DB 结果：

| 校验项 | 结果 |
|---|---:|
| queue 3 状态 | `published` |
| published_article_key | `default-source--frontend-approve-source` |
| articles | 1 |
| article_chunks | 2 |
| article_vector_index | 1 |
| article_chunk_vector_index | 2 |
| audit | `compile_review_queue_approve`, `needs_human_review -> passed`, metadata `source=compile_review_queue`, `queueId=3` |

结论：approve 后正式文章、chunk、文章向量索引、chunk 向量索引均真实生成。

## 8. Reject 浏览器操作结果

浏览器操作：

- 在详情页点击“驳回”。
- 原生二次确认出现，文案：`驳回后该草稿不会进入正式知识库。`
- 原生 prompt 出现：
  - `请输入驳回人`
  - `请输入驳回原因（可选）`
- 页面发起 `POST /api/v1/admin/compile/review-queue/4/reject`。
- 操作后发起 `GET /api/v1/admin/compile/review-queue?status=needs_human_review` 刷新列表。

DB 结果：

| 校验项 | 结果 |
|---|---:|
| queue 4 状态 | `rejected` |
| published_article_key | `null` |
| articles | 0 |
| article_chunks | 0 |
| article_vector_index | 0 |
| article_chunk_vector_index | 0 |
| audit | `compile_review_queue_reject`, `needs_human_review -> rejected`, metadata `source=compile_review_queue`, `queueId=4` |

结论：reject 后未写入正式文章、chunk 或 vector index。

## 9. 当前处理任务刷新

- 页面操作期间捕获到 `GET /api/v1/admin/processing-tasks?limit=50`。
- 最终接口摘要：
  - `runningCount=0`
  - `waitingCount=0`
  - `succeededCount=4`
  - `failedCount=0`
  - 卡片“待确认”值为 `0`，提示“当前没有需要人工确认的任务”。
- 最终页面展示两个本轮任务 `frontend-approve-source.md`、`frontend-reject-source.md` 为已完成任务，待人工确认列表为空。

结论：当前处理任务随人工确认操作刷新。

## 10. 是否发现 UI / 交互问题

未发现阻断问题。

观察到一个非阻断边界：待确认草稿正文来自编译产物，正文 frontmatter 中可能包含 `review_status: needs_human_review` 等内部样式字段；前端列表和详情主体未额外暴露这些字段名，技术字段仍默认折叠。该现象符合“草稿正文可读”的验证口径，但若产品口径要求草稿正文也隐藏 frontmatter，可后续单独优化展示层。

## 11. 是否修改代码

否。

本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、前端文件、prompt、模型配置、`scripts/scan-redline.sh` 或 redline allowlist；未提交代码。仅新增本验证报告；`special_cases_report.md` 由 redline 扫描命令更新。

## 12. 下一步建议

通过，建议进入提交前质量复核。

