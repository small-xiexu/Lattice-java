# 后台状态摘要与人审队列数据源分析报告

## 结论

后台首页“状态摘要”的“需复核内容”当前没有统计 `compile_article_review_queue`。它只统计正式 `articles` 表中 `review_status != passed` 的文章，或质量接口中正式 `articles.review_status = needs_human_review` 的文章。

这会误导用户：新的编译草稿人工确认闭环已经改为“未通过审查不入正式 articles，进入 `compile_article_review_queue` 等待 approve/reject”。因此当队列里有待人工确认草稿时，首页“需复核内容”仍可能显示 0。

下一轮最小修复建议：后端 `GET /api/v1/admin/overview` 增加 `compileReviewQueuePendingCount`，由 `compile_article_review_queue.review_status='needs_human_review'` 统计；前端状态摘要新增或改名显示“待人工确认草稿”。优先后端汇总，不建议只在前端额外拼 queue API。

## redline

本轮执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

| 项 | 数量 |
| --- | ---: |
| BLOCKER | 0 |
| REVIEW | 1863 |
| ALLOWLIST | 244 |
| 总命中 | 2107 |
| 高风险 | 0 |
| 中风险 | 1863 |
| 低风险 | 244 |

结论：`BLOCKER=0`。

## 前端入口

| 页面 | 文件 | 说明 |
| --- | --- | --- |
| 工作台首页 | `src/main/resources/static/admin/index.html` | “状态摘要”标题和容器在这里，容器 id 为 `summary-cards` |
| 运行时入口 | `src/main/resources/static/admin/management.js` | 拼接 `management-runtime-part-01..05.js` 后执行 |
| 状态摘要渲染 | `src/main/resources/static/admin/modules/management-runtime-part-02.js` | `renderSummary(overview, health)` 生成 10 张卡片 |
| 人审队列面板 | `src/main/resources/static/admin/compile-review-queue.js` | 独立调用 review queue API，不参与 `summary-cards` |

注意：仓库里还存在旧版 `admin.js` / `admin-runtime-part-*`，其中有 `overview-cards`、`renderOverview(...)`。当前 `index.html` 实际加载的是 `management.js`，不是旧 `admin.js`；截图中的 10 张卡来自 `renderSummary()`。

## 后端 API

| 前端动作 | API | 后端入口 | 说明 |
| --- | --- | --- | --- |
| 刷新状态摘要 | `GET /api/v1/admin/overview` | `AdminOverviewController.overview()` | 返回 `status`、`quality`、`pending` |
| 刷新服务健康 | `/actuator/health` | Spring Actuator | 仅用于服务健康标签 |
| 加载资料源数 | `GET /api/v1/admin/sources?page=1&size=50` | Source admin API | 前端用返回 items 长度更新“资料源”卡 |
| 加载待人工确认草稿 | `GET /api/v1/admin/compile/review-queue?status=needs_human_review` | `AdminCompileReviewQueueController.list()` | 独立面板使用，不进入 overview |

当前 `AdminOverviewController` 只注入：

- `StatusService`
- `QualityMetricsService`
- `PendingQueryManager`

没有注入 `CompileArticleReviewQueueJdbcRepository` 或 `AdminCompileArticleReviewQueueService`。

## 状态摘要卡片数据源

当前 `renderSummary()` 的 10 张卡片如下：

| 卡片 | 前端字段 | 后端来源 | 实际含义 |
| --- | --- | --- | --- |
| 知识条目 | `overview.status.articleCount` | `StatusService.snapshot()` -> `articleJdbcRepository.findAll().size()` | 正式 articles 数量 |
| 资料文件 | `overview.status.sourceFileCount` | `StatusService.snapshot()` -> `sourceFileJdbcRepository.findAll().size()` | source_files 数量 |
| 资料源 | `state.sources.length` | `GET /api/v1/admin/sources?page=1&size=50` 返回 items 长度 | 当前前端页加载到的资料源数量，不来自 overview |
| 反馈沉淀 | `overview.status.contributionCount` | `StatusService.snapshot()` -> `contributionJdbcRepository.findAll().size()` | 已沉淀 contribution 数 |
| 待处理反馈 | `overview.status.pendingQueryCount` | `PendingQueryManager.listPendingQueries().size()` | query pending 队列数量 |
| 需复核内容 | `overview.status.reviewPendingArticleCount` | `StatusService.snapshot()` 遍历正式 articles，计数 `review_status != passed` | 正式 article 的复核积压，不含 compile review queue |
| 高风险内容 | `overview.status.highRiskArticleCount` | `StatusService.snapshot()` 遍历正式 articles，计数 `riskLevel=high` | 已入库文章风险 |
| 热点待抽检 | `overview.status.hotspotPendingVerificationCount` | `StatusService.snapshot()` 遍历正式 articles，计数 `isHotspot && requiresResultVerification` | 已入库热点文章待抽检 |
| 用户反馈风险 | `overview.status.userReportedAnswerCount` | `StatusService.snapshot()` 遍历正式 articles，计数 riskReasons 包含 `user_reported` | 已入库文章的用户反馈风险 |
| 结果反馈待处理 | `overview.status.answerFeedbackPendingCount` | `AnswerFeedbackJdbcRepository.countByStatus(STATUS_PENDING)` | answer feedback 待处理数 |

## “需复核内容”当前统计口径

当前前端：

- `manualReviewCount = Number(status.reviewPendingArticleCount || 0)`
- 卡片 label：`需复核内容`
- 文案：`到“已入库内容”按复核状态筛选`

当前后端：

- `StatusService.snapshot()` 遍历 `articleJdbcRepository.findAll()`
- `if (!"passed".equalsIgnoreCase(articleRecord.getReviewStatus())) reviewPendingArticleCount++`

因此它包含：

| 来源 | 是否包含 |
| --- | --- |
| 正式 `articles.review_status != passed` | 是 |
| 正式 `articles.review_status = needs_human_review` | 是，如果这类文章已经入了 articles |
| `compile_article_review_queue.review_status='needs_human_review'` | 否 |
| query pending / 待处理反馈 | 否，另有 `pendingQueryCount` |
| answer feedback pending | 否，另有 `answerFeedbackPendingCount` |

`QualityMetricsService.measure()` 也只遍历正式 `articles`，其中 `needsHumanReviewArticles` 同样不统计 `compile_article_review_queue`。

## 当前接口响应旁证

本机当前服务监听 `18082`，只读请求结果：

`GET /api/v1/admin/overview` 摘要：

```json
{
  "status": {
    "articleCount": 2,
    "sourceFileCount": 4,
    "contributionCount": 0,
    "pendingQueryCount": 0,
    "reviewPendingArticleCount": 0,
    "highRiskArticleCount": 0,
    "hotspotPendingVerificationCount": 0,
    "userReportedAnswerCount": 0,
    "answerFeedbackPendingCount": 0
  },
  "quality": {
    "totalArticles": 2,
    "passedArticles": 2,
    "pendingReviewArticles": 0,
    "needsHumanReviewArticles": 0
  }
}
```

`GET /api/v1/admin/compile/review-queue?status=needs_human_review` 摘要：

```json
{
  "total": 0,
  "itemCount": 0
}
```

当前样本里队列也为 0，所以不能复现“overview=0 但 queue>0”的实时差异；但代码链路已经能确定：即使 queue>0，overview 也不会变。

## 是否误导用户

存在误导。

原因不是字段算错，而是语义过时：

- 旧语义：“需复核内容”表示正式 articles 中还有未 passed 内容。
- 新闭环：“未通过编译审查的草稿不会进正式 articles，而是进入 `compile_article_review_queue` 等待人工确认。”

所以首页显示“需复核内容=0”时，用户可能理解为没有待处理人工确认；实际上“待人工确认草稿”在另一个面板和另一个 API 中。

## 命名建议

建议区分两个概念：

| 展示名 | 含义 | 是否建议 |
| --- | --- | --- |
| 需复核内容 | 已入库内容中仍需治理/复核的文章 | 可保留，但不应代表编译草稿队列 |
| 待人工确认草稿 | 编译审查未通过、等待 approve/reject 的草稿 | 推荐新增或替换当前“需复核内容”主卡 |

更贴近当前用户问题的是“待人工确认草稿”。它能直接对应“待人工确认”面板和 `compile_article_review_queue`。

## 下一轮最小修复建议

建议修代码，最小范围：

- `src/main/java/com/xbk/lattice/governance/StatusSnapshot.java`
- `src/main/java/com/xbk/lattice/governance/StatusService.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminOverviewController.java` 如需注入队列服务或 repository
- `src/main/resources/static/admin/modules/management-runtime-part-02.js`

优先方案：

1. 后端 overview 增加 `compileReviewQueuePendingCount` 字段。
2. `StatusService.snapshot()` 或 `AdminOverviewController.overview()` 只读统计 `compile_article_review_queue.review_status='needs_human_review'`。
3. 前端 `renderSummary()` 新增一张“待人工确认草稿”卡，点击/文案指向“待人工确认”面板。
4. 保留“需复核内容”给正式 articles 的复核积压，或在空间不足时将其降级到详情/治理区域。

不建议第一步只在前端合并 queue API 数量。理由：

- 首页 summary 的真实状态应由后端聚合，避免多个 API 前端竞态。
- knowledge help card 也依赖 `state.overview.status`，只前端拼卡会导致提示语仍不感知 queue。
- 后端 DTO 增字段对调用方兼容，改动可控。

## 本轮是否修改代码

否。

本轮仅运行只读命令、只读 HTTP GET 请求、刷新 redline 报告，并新增本分析报告；未修改前端代码、后端代码、测试、数据库 schema、prompt、Query/AnswerGeneration、脚本或数据库数据。
