# Compile Review Observability Fix Result Report

## 1. 修改文件

- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewSummaryResponse.java`
  - 新增后台审查摘要 DTO，暴露 `reviewStepPresent`、`reviewAgentRole`、`reviewRoute`、`reviewModeLabel`、`acceptedCount`、`pendingReviewCount`、`needsHumanReviewCount`、`fixStepPresent`、`fixAttemptCount`、`fixRoute`、`fixDisplayMessage`、`reviewDisplayWarning`。
- `src/main/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryService.java`
  - 新增后台只读摘要服务，复用 `compile_job_steps` 解析 `review_articles` 与 `fix_review_issues`。
  - 明确将 `rule-based` 展示为 `规则审查（不是 LLM 内容审查）`。
  - 当未记录 `fix_review_issues` 且审查步骤存在时，展示 `未触发自动修复：无 fixable issue`。
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileJobResponse.java`
  - 新增 `reviewSummary` 字段。
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileController.java`
  - 在 compile job 详情/列表响应中填充 `reviewSummary`。
- `src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskItemResponse.java`
  - 新增 `compileReviewSummary` 字段。
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java`
  - 在 processing task 响应中填充 `compileReviewSummary`。
  - 将现有 `REVIEW_ARTICLES` 进度步骤 detail 增强为审查 route/outcome 摘要，不改变步骤状态。
- `special_cases_report.md`
  - 仅由 redline 扫描命令更新。

未修改 `src/main/resources/static/admin/**`。后台页面当前已渲染 processing task 的 `progressSteps.detail`，本轮通过 API 字段与现有 step detail 增强即可展示，不需要改前端静态资源。

## 2. 是否只改后台可观测性

是。

本轮只在 admin API / admin service 响应层增加 compile review 摘要和展示 detail，不改变编译、审查、修复、入库、查询链路。

## 3. 行为影响确认

- 是否改变 compile 行为：否。
- 是否改变 review 行为：否。
- 是否改变 persist 行为：否。
- 是否改变 query 行为：否。
- 是否启用 LLM reviewer：否。
- 是否修改 query 可见性过滤：否。
- 是否新增数据库 schema / migration：否。
- 是否运行 compile：否。

## 4. Redline

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：通过，退出码 0。
- BLOCKER：0。
- REVIEW：1351。
- ALLOWLIST：165。

REVIEW / ALLOWLIST 为既有人工复核候选，不阻断本轮后台可观测性修改。

## 5. Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：通过。
- Maven 汇总：`Tests run: 811, Failures: 0, Errors: 0, Skipped: 0`

## 6. 最近 Compile Job 展示字段示例

只读验证对象：

- jobId：`fc155a9b-54f2-41af-87cc-0498c88521b9`
- status：`SUCCEEDED`

`GET /api/v1/admin/jobs/{jobId}` 中的 `reviewSummary` 示例：

```json
{
  "reviewStepPresent": true,
  "reviewStepName": "review_articles",
  "reviewAgentRole": "ReviewerAgent",
  "reviewRoute": "rule-based",
  "reviewModeLabel": "规则审查（不是 LLM 内容审查）",
  "acceptedCount": 4,
  "pendingReviewCount": 0,
  "needsHumanReviewCount": 0,
  "fixStepPresent": false,
  "fixStepName": null,
  "fixAttemptCount": 0,
  "fixRoute": null,
  "fixDisplayMessage": "未触发自动修复：无 fixable issue",
  "reviewDisplayWarning": "当前为规则审查，不是 LLM 内容审查。"
}
```

`GET /api/v1/admin/processing-tasks?limit=10` 中同一 job 的 `REVIEW_ARTICLES` step detail 示例：

```text
规则审查（不是 LLM 内容审查） · review_articles · model_route=rule-based · acceptedCount=4 · pendingReviewCount=0 · needsHumanReviewCount=0 · 未触发自动修复：无 fixable issue
```

## 7. Rule-Based 展示确认

rule-based 已明确显示为：

- `规则审查（不是 LLM 内容审查）`
- `当前为规则审查，不是 LLM 内容审查。`
- `model_route=rule-based`

文案未暗示 LLM 内容审查。

## 8. Fix 未触发原因

可见。

- `fixStepPresent=false`
- `fixAttemptCount=0`
- `fixDisplayMessage=未触发自动修复：无 fixable issue`

## 9. 禁止范围确认

- 是否修改 compiler graph：否。
- 是否修改 `ReviewArticlesNode` / `FixReviewIssuesNode` / `PersistArticlesNode`：否。
- 是否修改 `ArticleReviewerGateway` / `RuleBasedArticleReviewer` / `ReviewFixService`：否。
- 是否修改 `LlmProperties` / `lattice-llm.yml` / 模型配置：否。
- 是否修改 query 检索 SQL / mapper：否。
- 是否修改 `src/test/java/**`：否。
- 是否修改 `docs/test/**`：否。
- 是否修改 `scripts/**`：否。
- 是否修改 redline allowlist：否。
- 是否清库 / 重建 / 重新导入资料：否。
- 是否提交代码：否。

## 10. 下一步建议

只建议一个最小动作：由验证 agent 或人工在后台页面打开最近 compile job / processing task 详情，确认 UI 展示的 step detail 与 API 字段一致。
