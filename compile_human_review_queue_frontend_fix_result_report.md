# compile human review queue frontend fix result report

## 1. 修改了哪些前端文件

- `src/main/resources/static/admin/index.html`
  - 在后台 `knowledge-runs` 区域新增“待人工确认”面板。
  - 增加待确认草稿列表容器、详情容器、状态提示、数量展示和“刷新待确认”按钮。
  - 引入 `/admin/compile-review-queue.js?v=20260520-human-review-1`。
- `src/main/resources/static/admin/compile-review-queue.js`
  - 新增 compile review queue 前端逻辑。
  - 调用列表、详情、approve、reject 后端 API。
  - 操作成功后刷新待确认列表，并触发当前处理任务刷新。
- `src/main/resources/static/admin/admin.css`
  - 新增待人工确认列表、详情、正文、review issues、操作按钮和响应式布局样式。

## 2. 新增了哪些页面 / 入口 / 按钮

- 新增后台“当前处理任务”区域下的“待人工确认”入口。
- 新增“刷新待确认”按钮。
- 草稿详情中新增“确认入库”和“驳回”按钮。
- 技术字段放入折叠的“技术详情”区域。

## 3. 是否支持列表 / 详情 / approve / reject

- 列表：是。调用 `GET /api/v1/admin/compile/review-queue?status=needs_human_review`。
- 详情：是。调用 `GET /api/v1/admin/compile/review-queue/{id}`。
- approve：是。调用 `POST /api/v1/admin/compile/review-queue/{id}/approve`。
- reject：是。调用 `POST /api/v1/admin/compile/review-queue/{id}/reject`。

## 4. 是否有二次确认

- 确认入库前有二次确认：
  - “确认后文章将进入正式知识库并参与检索。”
- 驳回前有二次确认：
  - “驳回后该草稿不会进入正式知识库。”

## 5. 是否避免默认暴露内部字段

是。

- 默认可见文案使用中文用户语义：
  - “待人工确认”
  - “确认入库”
  - “驳回”
  - “Reviewer 判定需要人工确认”
- 默认列表和详情不直接展示内部字段名：
  - `needs_human_review`
  - `compile_article_review_queue`
  - `review_route`
  - `model_route`
- `reviewRoute`、`reviewerModel`、`articleKey`、`conceptId`、原始 metadata / review issues JSON 等仅放在折叠的“技术详情”中。
- 如果 review issues 为空或无法解析，展示：
  - “Reviewer 判定需要人工确认，但未返回结构化问题详情。”

## 6. 是否修改后端 Java 业务逻辑

否。

本轮只修改后台静态前端文件和本报告。当前工作区中存在的后端 Java / schema 未提交改动属于本轮开始前已有状态，本轮未修改、未回滚。

## 7. 是否修改 Query / Reviewer / prompt

否。

- 未修改 Query / AnswerGeneration。
- 未修改 Reviewer / Fixer / Writer。
- 未修改 prompt。

## 8. redline BLOCKER 是否为 0

是。

- `bash scripts/scan-redline.sh special_cases_report.md`：通过。
- `BLOCKER=0`
- `REVIEW=1863`
- `ALLOWLIST=244`

## 9. mvn test 或前端检查是否通过

通过。

- `node --check src/main/resources/static/admin/compile-review-queue.js`：通过。
- `git diff --check`：通过。
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`：通过。
  - `Tests run: 844`
  - `Failures: 0`
  - `Errors: 0`
  - `Skipped: 0`
  - `BUILD SUCCESS`

## 10. 下一步建议

交给 agentD 做浏览器运行时验证：

- 打开后台“当前处理任务”区域，确认“待人工确认”入口可见。
- 使用真实 `needs_human_review` 草稿验证列表、详情、approve、reject。
- 验证 approve / reject 后列表刷新，当前处理任务同步刷新。
