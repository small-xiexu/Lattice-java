# compile review observability 验证报告

验证时间：2026-05-17

## 1. 验证对象

- 本轮验证角色：agentD / 验证测试 Agent。
- 验证目标：只验证 compile review observability 后台可观测性改动是否在接口与后台页面真实可见。
- 本地服务：使用 `./scripts/run-local-dev.sh` 启动，profile 为 `local-dev`，端口 `18082`，未传 `--reset-schema`。
- 健康检查：`GET http://127.0.0.1:18082/actuator/health` 返回 `UP`。
- 验证 job：`fc155a9b-54f2-41af-87cc-0498c88521b9`。
- 验证 task：`compile-job:fc155a9b-54f2-41af-87cc-0498c88521b9`。
- 只读范围确认：本轮未清库、未重建、未重新导入资料、未跑 query baseline / SWIP eval、未提交代码。

已读取材料：

- `AGENTS.md`
- `docs/quality-progress-and-lessons.md`
- `docs/multi-agent-model-routing-guide.md`
- `compile_review_observability_fix_result_report.md`

已读取相关实现：

- `AdminCompileReviewSummaryService`
- `AdminCompileReviewSummaryResponse`
- `AdminCompileController`
- `AdminCompileJobResponse`
- `AdminProcessingTaskService`
- `AdminProcessingTaskItemResponse`
- 后台页面 `management-runtime` 中 `progressSteps[].detail` 渲染逻辑。

## 2. 接口验证结果

### 2.1 `GET /api/v1/admin/jobs/{jobId}`

请求：

- `GET http://127.0.0.1:18082/api/v1/admin/jobs/fc155a9b-54f2-41af-87cc-0498c88521b9`

结果：通过。

接口返回 `reviewSummary`，并包含本轮必须确认的字段：

| 字段 | 实际值 | 结论 |
|---|---|---|
| `reviewRoute` | `rule-based` | 通过 |
| `reviewModeLabel` | `规则审查（不是 LLM 内容审查）` | 通过 |
| `acceptedCount` | `4` | 通过 |
| `pendingReviewCount` | `0` | 通过 |
| `needsHumanReviewCount` | `0` | 通过 |
| `fixDisplayMessage` | `未触发自动修复：无 fixable issue` | 通过 |
| `reviewDisplayWarning` | `当前为规则审查，不是 LLM 内容审查。` | 通过 |

同时确认：

- `reviewStepPresent=true`
- `reviewStepName=review_articles`
- `reviewAgentRole=ReviewerAgent`
- `fixStepPresent=false`
- `fixAttemptCount=0`
- `fixRoute=null`

### 2.2 `GET /api/v1/admin/processing-tasks?limit=10`

请求：

- `GET http://127.0.0.1:18082/api/v1/admin/processing-tasks?limit=10`

结果：通过。

接口返回 1 条 processing task：

- `taskId=compile-job:fc155a9b-54f2-41af-87cc-0498c88521b9`
- `taskType=STANDALONE_COMPILE`
- `status=SUCCEEDED`
- `displayStatus=SUCCEEDED`
- `compileJobId=fc155a9b-54f2-41af-87cc-0498c88521b9`

该 task 的 `compileReviewSummary` 与 job 详情 `reviewSummary` 一致，包含：

- `reviewRoute=rule-based`
- `reviewModeLabel=规则审查（不是 LLM 内容审查）`
- `acceptedCount=4`
- `pendingReviewCount=0`
- `needsHumanReviewCount=0`
- `fixDisplayMessage=未触发自动修复：无 fixable issue`
- `reviewDisplayWarning=当前为规则审查，不是 LLM 内容审查。`

该 task 的 `REVIEW_ARTICLES` step detail 实际返回：

- `规则审查（不是 LLM 内容审查） · review_articles · model_route=rule-based · acceptedCount=4 · pendingReviewCount=0 · needsHumanReviewCount=0 · 未触发自动修复：无 fixable issue`

结论：

- route 可见：`model_route=rule-based`
- outcome 计数可见：`acceptedCount=4 / pendingReviewCount=0 / needsHumanReviewCount=0`
- fix 未触发原因可见：`未触发自动修复：无 fixable issue`
- rule-based 未被描述为 LLM 内容审查。

## 3. UI 验证结果

访问页面：

- `http://127.0.0.1:18082/admin`
- 切换到“当前处理任务”

页面可访问，浏览器控制台未捕获 error 级日志。

后台页面“当前处理任务”区域展示了同一条任务：

- 标题：`SWIP智能键盘系统使用手册-20250702.docx 等 2 个文件`
- 状态：`成功`
- 任务类型：`直接编译`
- 进度：`4 / 4 · 编译完成`

页面“质量检查”步骤实际可见文本：

- `规则审查（不是 LLM 内容审查） · review_articles · model_route=rule-based · acceptedCount=4 · pendingReviewCount=0 · needsHumanReviewCount=0 · 未触发自动修复：无 fixable issue`

UI 结论：

- 后台页面通过 `progressSteps[].detail` 真实展示了 processing task 的 `REVIEW_ARTICLES` step detail。
- UI 展示文本与 `GET /api/v1/admin/processing-tasks?limit=10` 中的 `REVIEW_ARTICLES.detail` 一致。
- UI 明确展示 `规则审查（不是 LLM 内容审查）`，没有暗示 LLM 内容审查。
- UI 展示了 route、outcome 计数和 fix 未触发原因。
- 未发现独立的 compile job 详情页面或单独的 `reviewSummary` 全量字段面板；当前 UI 可见性来自处理任务进度步骤详情，这与 fix result report 对“未修改前端、复用现有 step detail 展示”的描述一致。

## 4. 是否与 fix_result_report 一致

一致。

逐项核对：

| fix_result_report 声明 | 本轮验证结果 |
|---|---|
| `GET /api/v1/admin/jobs/{jobId}` 暴露 `reviewSummary` | 已验证，字段齐全 |
| `GET /api/v1/admin/processing-tasks?limit=10` 暴露 `compileReviewSummary` | 已验证，字段齐全 |
| `REVIEW_ARTICLES` step detail 增强为 route/outcome/fix 摘要 | 已验证，API 与 UI 均可见 |
| rule-based 显示为 `规则审查（不是 LLM 内容审查）` | 已验证，API 与 UI 均可见 |
| fix 未触发原因显示为 `未触发自动修复：无 fixable issue` | 已验证，API 与 UI 均可见 |
| 未修改前端静态资源，依赖现有 `progressSteps.detail` 渲染 | 与当前 diff 和 UI 表现一致 |

## 5. 剩余风险

- UI 当前只展示 processing task 的步骤详情文本，不展示 `reviewSummary` / `compileReviewSummary` 的全量结构化字段面板；若产品要求在 compile job 独立详情页逐字段展示，还需要后续前端改动。
- 本轮只验证现有 `rule-based` 成功 job；没有制造新的 compile job，也没有验证 LLM reviewer route 或存在 `fix_review_issues` 步骤时的 UI 展示。
- 本轮按要求未运行 redline、`mvn test`、query baseline、SWIP eval，因此本报告只覆盖接口与后台页面可观测性，不代表完整质量 gate。
- 当前数据库仍是既有 SWIP clean 数据状态；本轮未改动数据，也未验证主 baseline 库场景。

## 6. 结论：通过

本轮 compile review observability 改动通过验证。

确认点：

- `reviewSummary` 包含 `reviewRoute`、`reviewModeLabel`、`acceptedCount`、`pendingReviewCount`、`needsHumanReviewCount`、`fixDisplayMessage`、`reviewDisplayWarning`。
- rule-based 在接口与后台页面均明确显示为 `规则审查（不是 LLM 内容审查）`。
- processing task 的 `REVIEW_ARTICLES` step detail 真实展示了 route、outcome 计数与 fix 未触发原因。
- 后台页面可见文本与 processing task API 字段一致。
