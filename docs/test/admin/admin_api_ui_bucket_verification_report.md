# admin API / UI 桶验证报告

- 验证时间：2026-05-27 17:16（Asia/Shanghai）
- 验证性质：只读审计
- 验证 Agent：agentD
- 结论用途：判断 admin API / UI 桶是否具备独立提交条件，以及是否应拆成两个子提交
- 约束声明：未 stage、未 commit、未 push，且未修改任何业务代码

## 1. 工作区只读盘点

### 1.1 候选文件（admin API / UI，共 16 个）

| 文件 | 类型 | 主题归属 |
|---|---|---|
| `src/main/java/com/xbk/lattice/api/admin/AdminArticleTitleProfile.java` | 新文件 | title-profile API |
| `src/main/java/com/xbk/lattice/api/admin/AdminArticleController.java` | 生产代码 | title-profile API |
| `src/main/java/com/xbk/lattice/api/admin/AdminArticleDetailResponse.java` | 生产代码 | title-profile API |
| `src/main/java/com/xbk/lattice/api/admin/AdminArticleSummaryResponse.java` | 生产代码 | title-profile API |
| `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java` | 生产代码 | UI/queue |
| `src/main/resources/static/admin/admin.css` | 静态资源 | UI/queue |
| `src/main/resources/static/admin/index.html` | 静态资源 | UI/queue |
| `src/main/resources/static/admin/management.js` | 静态资源 | UI/queue |
| `src/main/resources/static/admin/modules/admin-runtime-part-02.js` | 静态资源 | UI/queue |
| `src/main/resources/static/admin/modules/management-history-part.js` | 静态资源 | UI/queue |
| `src/test/java/com/xbk/lattice/api/admin/AdminManagementControllerTests.java` | 测试代码 | title-profile API (部分) |
| `src/test/java/com/xbk/lattice/api/admin/AdminPageControllerTests.java` | 测试代码 | UI/queue |
| `src/test/java/com/xbk/lattice/api/admin/AdminProcessingTaskControllerTests.java` | 测试代码 | UI/queue |
| `src/test/java/com/xbk/lattice/api/admin/AdminUploadControllerTests.java` | 测试代码 | UI/queue |
| `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java` | 测试代码 | UI/queue |
| `src/test/resources/admin/management-js-runtime-test.js` | 新文件 | UI/queue |

### 1.2 明确排除文件

- `docs/模型绑定配置参考.md` — 私有配置，永远排除提交
- `docs/项目全流程真实验收手册.md` — 不属于本桶
- `special_cases_report.md` — redline 输出，排除
- `src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java` — 与 admin 无关
- `docs/plans/*`（untracked）— 计划文档
- `docs/test/knowledge-base-e2e/q6_*`（untracked）— Q6 余波报告

## 2. 候选文件 diff 审核

### 2.1 title-profile API 主题（4 个文件）

**AdminArticleTitleProfile.java（新文件）**
- 新增 DTO 类，承载 `sourceTitle`、`anchorTitle`、`representativeTitle`、`titleGenerationMode` 四个字段
- 纯数据载体，无业务逻辑，用于 admin 列表与详情页共享标题来源链路信息

**AdminArticleController.java**
- 新增 `OBJECT_MAPPER` 常量、`resolveTitleProfile()` 方法、`readText()` 辅助方法
- `resolveTitleProfile()` 从 article `metadataJson` 中读取 `titleProfile` 节点，解析为 `AdminArticleTitleProfile`
- 解析失败时优雅降级为 `LEGACY_UNSET`，不会 NPE
- 列表与详情两个端点均透传 `titleProfile`

**AdminArticleDetailResponse.java**
- 新增 `AdminArticleTitleProfile titleProfile` 字段
- 构造函数 +1 参数，getter +1

**AdminArticleSummaryResponse.java**
- 同上，新增 `titleProfile` 字段、构造函数参数、getter

### 2.2 UI/queue 诊断刷新主题（12 个文件）

**AdminProcessingTaskService.java**
- 新增 `waitingDraftCount`：从 `pendingHumanReviewCount` 累计
- 摘要卡片"待确认"改名为"待人工确认草稿"（当存在人工确认草稿时）
- 卡片值从 `waitingCount` 切换为 `waitingDraftCount`，提示文案同步调整
- 这是人工确认队列摘要计数的 UI 后端支持

**admin.css**
- 新增约 230 行样式：`.pill.active`、history filter bar、history/diagnostic toolbar、history list item 全套样式（含 responsive 断点）、status pill、loading/error/empty 状态样式
- 纯 CSS，无 JS 逻辑，无硬编码 URL

**index.html**
- 处理历史区域重构：filter bar 与 toolbar 合并为一行，删除旧的 `panel-title-row`
- 资料源诊断区域重构：新增 diagnostic toolbar、CSS class 重命名
- CSS/JS 版本号统一升级为 `20260526-diagnostic-ui-align-1`
- 删除 `top-gap` class 引用，改用专用 class

**management.js**
- 模块 import 全部添加版本查询字符串 `?v=20260526-diagnostic-ui-align-1`
- 模块执行顺序调整：`partHistory` 从 part04 之后移到 part05 之后（最末尾）
- 原因：IIFE 化后的 history-part 需要 runtime parts 先完成初始化

**admin-runtime-part-02.js**
- 从原始单行 `export default "..."` 改写为 IIFE 包裹格式
- 本质上是对旧版管理页兼容运行时代码的格式统一（与 part-01/03/04/05 格式对齐）
- 未改变运行时逻辑

**management-history-part.js**
- 从原始单行 `export default "..."` 改写为 IIFE 包裹格式
- 关键改进：
  - 使用 `window.AdminCommon` 获取 `fetchJson`/`escapeHtml`/`formatDateTime`（带 fallback）
  - 新增 `updateHistoryStatus()`（状态计数展示）
  - 新增 `normalizeStatus()`/`getHistoryBadgeLabel()`/`renderHistoryBadge()`（状态标准化）
  - 新增 `formatHistorySourceName()`/`formatHistorySourceIdentity()`（名称格式化）
  - 改进 `viewHistoryDetail()`（正确跳转到 knowledge tab + 设置 source filter）
  - filter 按钮事件委托改进（支持子元素点击）
- 这些都是 admin 后台 UI 的质量提升，无业务特判

**AdminManagementControllerTests.java**
- 列表/详情端点新增 `titleProfile` 断言（`sourceTitle`、`anchorTitle`、`representativeTitle`、`titleGenerationMode`）
- 测试用 metadata JSON 从 `{"domain":"payments"}` 更新为含 `titleProfile` 的版本
- 测试数据为通用样本（`Payments Knowledge Base`、`Retry Policy`），无真实业务耦合

**AdminPageControllerTests.java**
- HTML 元素 ID 断言更新：`refresh-hotspots` → 删除，`article-technical-info` → `article-metadata`
- 新增 `knowledge-help-card`、`health-indicator-note` 元素 ID 断言
- 新增不应出现的元素断言（`refresh-hotspots`、`hotspot-refresh-status`、`热点刷新`）
- CSS/JS 版本号断言同步更新

**AdminProcessingTaskControllerTests.java**
- 新增"待人工确认草稿"卡片断言：label、value、note

**AdminUploadControllerTests.java**
- 新增 `shouldSkipMarkdownFrontmatterSeparatorWhenExtractingTitleHint` 测试
- 验证 Markdown YAML frontmatter 分隔线 `---` 不会被误提取为 title hint
- 测试样本使用 `Kubernetes 探针与事件响应协同手册` 作为文档标题——属于测试数据，不是生产特判

**ManagementJsRuntimeTests.java**
- `buildHarnessScript()` 方法从 ~1013 行内联 JS 字符串缩减为读取外部文件
- 代码从 1389 行减少到 7 行（+376 行 → 保留 7 行）
- 属于测试代码重构，不改变测试语义

**management-js-runtime-test.js（新文件）**
- 从 `ManagementJsRuntimeTests.java` 提取的 Node.js 测试 harness 脚本
- 与之前内联版本逻辑一致，新增 `async/await` 支持和更完整的 sandbox 模拟

## 3. Redline 结果

```
命令：bash scripts/scan-redline.sh special_cases_report.md
结果：BLOCKER=0, REVIEW=2028, ALLOWLIST=259
```

红线没有阻塞项，通过。

## 4. Maven 全量测试

```
命令：mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
结果：Tests run: 915, Failures: 0, Errors: 0, Skipped: 0
结论：BUILD SUCCESS
```

## 5. Admin 定向测试

```
命令：mvn ... -Dtest="AdminManagementControllerTests,AdminPageControllerTests,AdminProcessingTaskControllerTests,AdminUploadControllerTests,ManagementJsRuntimeTests" test
结果：
  AdminUploadControllerTests:         11/0/0
  ManagementJsRuntimeTests:            8/0/0
  AdminProcessingTaskControllerTests:  6/0/0
  AdminManagementControllerTests:      4/0/0
  AdminPageControllerTests:            2/0/0
  Total: 31/0/0
结论：BUILD SUCCESS
```

## 6. 前端静态资源 / JS runtime 检查

### 6.1 全局变量污染

- `management-history-part.js` 通过 `window.AdminCommon` 获取工具函数，带 fallback 实现
- 使用 IIFE 包裹避免全局作用域污染
- `globalThis.__LATTICE_ADMIN_TEST__` 仅用于测试 harness，生产环境不存在

### 6.2 事件绑定

- filter 按钮使用事件委托（`.history-filter-bar` 上的 click 监听），改用 `closest()` 支持子元素点击
- `DOMContentLoaded` 中检查元素存在性后再绑定，不会因元素缺失报错
- `historyPanel` 的 `toggle` 事件使用标准 `details` 元素 API

### 6.3 接口路径

- 历史数据：`/api/v1/admin/processing-tasks?limit=50&status=terminal`
- 均为已有 API 端点，无新增接口路径

### 6.4 ManagementJsRuntimeTests 覆盖

- 8 个 JS runtime 测试全部通过
- 覆盖：历史列表渲染、filter 切换、状态 badge、名称格式化、耗时格式化、详情跳转
- 不足：测试使用 mock DOM，无法覆盖真实浏览器渲染效果——这是 JS runtime 测试的固有限制

### 6.5 结论

无明显全局变量污染、未定义函数、事件绑定失效或接口路径错误风险。JS runtime 测试覆盖了核心逻辑路径。

## 7. 硬编码 / 过拟合 / 敏感信息扫描

### 7.1 生产代码（Java + 静态资源）

- 生产 admin Java 代码中**未发现** Q6、S2、Kubernetes、8080、tcp-liveness-readiness 等关键词
- 静态资源中**未发现**真实 `sk-` 格式 API key
- `titleProfile` 字段名（`sourceTitle`、`anchorTitle`、`representativeTitle`、`titleGenerationMode`）为结构化字段，可接受

### 7.2 测试代码

- `AdminUploadControllerTests` 测试样本标题包含 `Kubernetes 探针与事件响应协同手册`——属于测试数据，非生产特判，可接受
- `LlmConfigCenterIntegrationTests` 使用 mock API key（`sk-compile-1234567890` 等）——测试 mock 值，可接受
- `expectedReviewStatus` 在 `CompileArticleReviewQueueActionRequest` 中为参数名——属于业务字段名，可接受

### 7.3 结论

- 未发现真实 API 密钥
- 未发现生产代码中的 Q6/S2/题集/端口值/业务样本特判
- 测试代码中的样本数据可接受

## 8. 架构边界判断

| 问题 | 结论 |
|---|---|
| 这些文件是否能作为一个 admin 桶提交？ | **技术上可以**，但**不建议**，应拆成两个提交 |
| 是否更应该拆成 title-profile API 与 UI/queue 两个提交？ | **是**，强烈建议拆分 |
| `AdminArticleTitleProfile.java` 是否应该跟 admin response/controller 一起提交？ | **是**，属于 title-profile API 提交 |
| `AdminProcessingTaskService.java` 是否和静态 UI 同属人工确认队列摘要？ | **是**，属于 UI/queue 提交 |
| admin 改动是否依赖 title-generation commit `02f220e`？ | title-profile API **依赖**（读取 metadata 中的 `titleProfile` 节点）；UI/queue **不依赖** |
| admin 改动是否依赖 documentparse commit `e551d4c`？ | **不依赖** |
| 是否与 docs/plans、Q6 余波、ExecutionLlmSnapshotService 有关系？ | **无关** |
| 是否需要同步 `docs/quality-progress-and-lessons.md`？ | **建议提交后更新** |
| 是否建议进入提交阶段？ | **是**，但必须拆成两个提交 |

### 8.1 拆分理由

两个主题的变更原因、风险剖面和依赖关系不同：

- **title-profile API**：纯后端 API 响应字段扩展，依赖 title-generation 的 `titleProfile` 元数据输出。改动集中在 Controller + Response DTO，风险低。
- **UI/queue 诊断刷新**：涉及前端 HTML/CSS/JS 重构 + 后端 summary card 逻辑调整。改动面广（静态资源 + 测试重构），但完全自包含，不依赖 title-generation。

混在一起提交通常不利于回滚和 blame 追溯。

## 9. 提交建议

### 9.1 是否建议提交

**建议提交，但必须拆成两个独立提交。**

同时满足以下全部条件：

- [x] redline `BLOCKER=0`
- [x] 全量 `mvn test` 通过（915/0/0）
- [x] admin 定向测试通过（31/0/0）
- [x] 静态 UI / JS runtime 检查无明显风险
- [x] 生产代码未发现 Q6/S2/题集/端口值/业务样本特判
- [x] 未发现真实密钥
- [x] 文件边界与 docs/plans、Q6 余波、ExecutionLlmSnapshotService、私有配置可独立拆清楚

### 9.2 提交一：admin title-profile API

**staged 文件清单（5 个）：**

```
src/main/java/com/xbk/lattice/api/admin/AdminArticleTitleProfile.java
src/main/java/com/xbk/lattice/api/admin/AdminArticleController.java
src/main/java/com/xbk/lattice/api/admin/AdminArticleDetailResponse.java
src/main/java/com/xbk/lattice/api/admin/AdminArticleSummaryResponse.java
src/test/java/com/xbk/lattice/api/admin/AdminManagementControllerTests.java
```

**说明：** `AdminManagementControllerTests.java` 同时包含 title-profile 断言和已有测试用例的 metadata 更新。如果严格拆分，可只 stage 该文件中与 title-profile 相关的变更行。但该文件当前 diff 的主要新增内容（titleProfile 断言 + metadata JSON 更新）全部属于 title-profile 主题，旧断言更新属于配套适配，建议整体放入此提交。

**建议 commit message：**

```
feat(admin): 展示文章标题画像

AdminArticleController 列表与详情端点新增 titleProfile 字段；
新增 AdminArticleTitleProfile DTO 承载 sourceTitle / anchorTitle / representativeTitle / titleGenerationMode；
解析失败时优雅降级为 LEGACY_UNSET。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

### 9.3 提交二：admin UI / 人工确认队列摘要

**staged 文件清单（11 个）：**

```
src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java
src/main/resources/static/admin/admin.css
src/main/resources/static/admin/index.html
src/main/resources/static/admin/management.js
src/main/resources/static/admin/modules/admin-runtime-part-02.js
src/main/resources/static/admin/modules/management-history-part.js
src/test/java/com/xbk/lattice/api/admin/AdminPageControllerTests.java
src/test/java/com/xbk/lattice/api/admin/AdminProcessingTaskControllerTests.java
src/test/java/com/xbk/lattice/api/admin/AdminUploadControllerTests.java
src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java
src/test/resources/admin/management-js-runtime-test.js
```

**建议 commit message：**

```
feat(admin): 优化治理工作台诊断 UI 与处理历史

重新设计处理历史与资料源诊断面板布局（toolbar + filter bar 合并）；
处理历史列表 IIFE 化并增强状态展示、名称格式化与详情跳转；
Dashboard 摘要卡片支持人工确认草稿计数与文案切换；
JS runtime 测试 harness 外置为独立文件。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

## 10. 当前状态

- 未 stage
- 未 commit
- 未 push
- 仅新增本验证报告
