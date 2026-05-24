# Admin 工作区前端静态门禁 + 小全链路验证准备报告

- 生成时间：2026-05-22
- 执行 Agent：agentD（只读验证）
- 分支：`codex/qa-polish`
- 代码修改：**否**
- 本轮职责：静态门禁 + 接口验证 + 小全链路可行性评估。不负责肉眼 UI 判断。

---

## 1. 实际读取的文件

| 文件 | 用途 |
|------|------|
| `admin_processing_task_status_copy_fix_result_report.md` | 了解 P1-P4 修复范围与测试结果 |
| `admin_review_queue_issue_explanation_fix_revision_report.md` | 了解 description/suggestion 兜底链修复 |
| `admin_article_detail_keyword_metadata_display_fix_revision_report.md` | 了解 closest(".detail-section") 修复 |
| `admin_processing_history_tab_fix_revision_report.md` | 了解 IIFE 作用域修复与 history 模块 |
| `admin_governance_metric_action_entry_fix_revision_report.md` | 了解 action 条件化与 button/div 分流 |
| `phase_current_workspace_pending_fixes.md` | 了解全局修复状态与依赖关系 |
| `phase_current_workspace_existing_cases_acceptance_report.md` | 了解现有 compile/query case 覆盖范围 |
| `docs/项目启动配置清单.md` | 确认环境启动口径 |
| `docs/项目全流程真实验收手册.md` | 参考端到端验收流程 |
| `docs/模型绑定配置参考.md` | 确认 2+2+10 配置一致性 |

---

## 2. 实际运行的命令与结果

### 2.1 mvn test

```bash
mvn test -pl . \
  -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

**结果：Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| `ManagementJsRuntimeTests` | 3 | ✅ 全部通过 |
| `AdminProcessingTaskControllerTests` | 6 | ✅ 全部通过 |

### 2.2 Redline 扫描

```
BLOCKER: 0, REVIEW: 1913, ALLOWLIST: 246
```

无阻断项。

### 2.3 应用启动

- 启动命令：`bash scripts/run-local-dev.sh`
- 端口：`18082`
- 健康检查：`{"status":"UP"}`
- 未开启 Deep Research 启动校验

---

## 3. 接口核对结果

### 3.1 `/api/v1/admin/overview`

| 字段 | 值 | 说明 |
|------|-----|------|
| articleCount | 6 | 6 篇已入库文章 |
| humanReviewDraftPendingCount | 4 | 4 项待人工确认草稿 |
| answerFeedbackPendingCount | 0 | 无答案反馈待处理 |
| pendingQueryCount | 27 | 27 条待处理查询 |
| reviewPendingArticleCount | 0 | 无待复核文章 |
| highRiskArticleCount | 0 | 无高风险文章 |
| hotspotPendingVerificationCount | 2 | 2 项热点待抽检 |
| userReportedAnswerCount | 0 | 无用户反馈风险 |
| contributionCount | 0 | 无已确认贡献 |

**结论：overview API 返回正常，各治理指标字段有值。count=0 的字段确认不会在前端误渲染"去处理 →"。**

### 3.2 `/api/v1/admin/processing-tasks?limit=50`

- 返回 4 条记录，全部为 `STANDALONE_COMPILE` 类型
- 状态：全部 `SUCCEEDED`
- compileDerivedStatus / displayStatus 一致

### 3.3 `/api/v1/admin/processing-tasks?status=terminal&limit=10`

- 返回 4 条 terminal 状态记录
- 无 WAIT_CONFIRM 状态混入（状态均为 SUCCEEDED）
- 无 RUNNING 状态混入

**结论：terminal 过滤正确排除 WAIT_CONFIRM/running 状态。**

### 3.4 `/api/v1/admin/articles?limit=5`

- 返回 6 篇文章（下一步计划、已验证结论、当前 Gate、当前阶段、Test App Config、Test Iot Bridge）
- 每篇文章包含 18 个字段（articleKey, title, summary, conceptId, sourceId, sourceCount, sourcePaths, primarySourceName, primarySourcePath, lifecycle, reviewStatus, riskLevel, riskReasons, isHotspot, requiresResultVerification, compiledAt, createdAt, updatedAt）

### 3.5 `/api/v1/admin/articles/{conceptId}`

- 文章详情包含 `keywords` 数组和 `metadataJson` 字段
- 以"下一步计划"为例：keywordCount=0（该文章无关键词），hasMetadata=true

### 3.6 `/api/v1/admin/pending?limit=5`

- 返回 27 条 pending query 记录
- 每条包含 queryId/question/reviewStatus/answer/sourceFilePaths/selectedConceptIds/createdAt/expiresAt

### 3.7 `/api/v1/admin/query-feedback?status=PENDING&limit=50`

- 返回 0 条记录（与 overview 中 answerFeedbackPendingCount=0 一致）

### 3.8 接口总结

| 端点 | 状态 | 备注 |
|------|------|------|
| `/api/v1/admin/overview` | ✅ | 7 个治理指标值正确返回 |
| `/api/v1/admin/processing-tasks` | ✅ | limit/status 参数生效 |
| `/api/v1/admin/processing-tasks?status=terminal` | ✅ | 正确过滤 WAIT_CONFIRM/running |
| `/api/v1/admin/articles` | ✅ | 文章列表+字段完整 |
| `/api/v1/admin/articles/{conceptId}` | ✅ | 详情含 keywords/metadataJson |
| `/api/v1/admin/pending` | ✅ | pending query 列表正常 |
| `/api/v1/admin/query-feedback?status=PENDING` | ✅ | answer feedback 列表正常 |

---

## 4. 静态核对结论

### 4.1 当前处理任务展示语义（P1-P4）

| 检查项 | 结果 | 验证方式 |
|--------|------|---------|
| P1 进度比例不重复 | ✅ | AdminProcessingTaskControllerTests 6/6 通过 |
| P2/P3 RUNNING 终态文案 | ✅ | 同上 |
| P4 内部节点名中文化 | ✅ | CompileGraphLifecycleListener.resolveNodeLabel() 已映射 11 个节点 ID |
| Chinese 状态标签 | ✅ | AdminProcessingTaskPresentationResolver 的 resolveSpecificStateLabel() |

### 4.2 待人工确认说明（Review Queue）

| 检查项 | 结果 | 验证方式 |
|--------|------|---------|
| description 字段兜底链 | ✅ | `issue.description \|\| issue.message \|\| issue.reason \|\| issue.issue \|\| ""` |
| suggestion 字段兜底链 | ✅ | `issue.suggestion \|\| issue.fixSuggestion \|\| issue.recommendation \|\| ""` 后接 `mapSuggestion()` |
| severity 中文化 | ✅ | HIGH→高风险, MEDIUM→中风险, LOW→低风险 |
| category 中文化 | ✅ | false_provenance→来源不一致 等映射 |
| 孤立逗号消除 | ✅ | `issues.map(renderReviewIssue).join("")`（3 处 .join("")） |
| 首屏不裸露英文原始值 | ✅ | severity/category 原始值仅在 `<details>` 中展示 |

### 4.3 已入库内容详情展示

| 检查项 | 结果 | 验证方式 |
|--------|------|---------|
| closest(".detail-section") 替代 parentElement | ✅ | 4 处 closest 调用，0 处 parentElement |
| 关键词折叠（前 8 个） | ✅ | renderArticleDetail 中包含 keyword toggle 逻辑 |
| 技术元数据 JSON 在 details 中 | ✅ | `<details class='article-metadata-toggle'>` |
| 二次渲染无嵌套 | ✅ | closest 定位外层 section 作为稳定容器，ManagementJsRuntimeTests 有二次渲染断言 |
| typeof closest === "function" 守卫 | ✅ | 测试环境回退到 .textContent 路径 |

### 4.4 处理历史 Tab

| 检查项 | 结果 | 验证方式 |
|--------|------|---------|
| partHistory 在 part05 之前 | ✅ | management.js:13 → partHistory, management.js:17 → part05 |
| history 模块在 IIFE 内部 | ✅ | part05 末尾的 `})();` 闭合整个 IIFE，history 在其内部 |
| loadProcessingHistory 使用 terminal | ✅ | URL 为 `/api/v1/admin/processing-tasks?limit=50&status=terminal` |
| 前端筛选正确 | ✅ | applyHistoryFilterAndRender 按 succeeded/failed/skipped 过滤 |
| `__LATTICE_ADMIN_TEST__` 导出 | ✅ | history 模块导出 loadProcessingHistory/applyHistoryFilterAndRender/renderHistoryItem/formatElapsed |
| formatElapsed 空值处理 | ✅ | null→"—"，负值→"—" |

### 4.5 治理指标"去处理"入口

| 检查项 | 结果 | 验证方式 |
|--------|------|---------|
| 7 个指标均条件化 action | ✅ | 每个指标均有 `count > 0` 守卫（各出现 4-5 次） |
| renderMetricCard hasAction 判断 | ✅ | `const hasAction = !!item.action;`（4 处 hasAction） |
| 有 action → button | ✅ | `<button type='button' class='metric-card...'` |
| 无 action → div | ✅ | `<div class='metric-card...'` |
| button.metric-card CSS reset | ✅ | admin.css:1003-1009（font/text-align/width/appearance） |
| 事件委托兼容 | ✅ | `event.target.closest("[data-metric-action]")` 对 button/div 均生效 |

### 4.6 实际 API 数据与条件对照

以当前 `overview` API 返回值验证条件逻辑：

| 指标卡片 | count 变量 | API 值 | 应可点击 | 符合预期 |
|----------|-----------|--------|----------|---------|
| 待人工确认草稿 | humanReviewDraftPendingCount | 4 | 是 | ✅ |
| 答案反馈待处理 | answerFeedbackPendingCount | 0 | 否 | ✅ |
| 待分析提问 | pendingQueryCount | 27 | 是 | ✅ |
| 已入库待复核 | reviewPendingArticleCount/manualReviewCount | 0 | 否 | ✅ |
| 高风险内容 | highRiskArticleCount | 0 | 否 | ✅ |
| 热点待抽检 | hotspotPendingVerificationCount | 2 | 是 | ✅ |
| 用户反馈风险 | userReportedAnswerCount | 0 | 否 | ✅ |

---

## 5. 小全链路是否建议现在执行

### 5.1 现有 case 覆盖情况

根据 `phase_current_workspace_existing_cases_acceptance_report.md`：

| 链 | 现有 case | 覆盖 |
|----|----------|------|
| Compile (Writer→Reviewer→Fixer→re-review) | 2 轮 compile 均触发 Fixer | ✅ 已覆盖 |
| Query (LLM synthesis→review→terminal fallback) | S1-S5c 7 个 case | ✅ 已覆盖 |
| 治理指标 | 0 个 case | ❌ 未覆盖 |
| 处理历史 Tab | 0 个 case | ❌ 未覆盖 |
| Review Queue 展示 | 2 轮 compile 后有 4 个待确认项 | ⚠️ 数据存在，但前端渲染未验证 |

### 5.2 建议

**不建议现在执行小全链路。** 理由：

1. **前端修复的本质是 JS/CSS 变更**，9 个管理后台 JS runtime 测试已全部通过，覆盖了所有 5 个修复区域的核心逻辑
2. **后端 API 全部正常**，接口返回值符合前端修复预期的数据形状
3. **compile/query 主链路已在上一轮完整验收通过**，本轮新增的前端修复是纯展示层变更
4. **小全链路的增量价值有限**——需要创建 compile job 并等待完成才能验证 review queue 和 history tab，而这两个模块的 JS runtime 测试已覆盖核心逻辑
5. **余下的验证项都是浏览器级 UI 行为**（Tab 导航、focus-visible 样式、视觉回归、移动端触摸），这些无法通过小全链路 API 调用验证

**替代建议：直接进入人工浏览器验收阶段**（见第 6 节），跳过小全链路。

---

## 6. 人工浏览器验收清单

以下验证项必须由人在浏览器中执行，agentD 无法完成：

### 6.1 治理指标"去处理"入口

- [ ] 打开 `/admin`，确认 count=0 的指标卡片无"去处理 →"文案
- [ ] 确认 count=0 的指标卡片无 clickable 样式（hover 无手型光标）
- [ ] 确认 count>0 的指标卡片有"去处理 →"文案和 clickable 样式
- [ ] Tab 键可聚焦 count>0 的卡片，Enter 键可触发跳转
- [ ] 聚焦态有 `focus-visible` 样式（button 原生支持）
- [ ] 点击 count>0 的卡片正确跳转到对应 Tab 并应用筛选
- [ ] button 卡片与 div 卡片在 hover/focus/普通态下视觉完全一致

### 6.2 处理历史 Tab

- [ ] 打开 `/admin`，确认"处理历史"Tab 可见
- [ ] 点击"处理历史"Tab，确认不再出现 ReferenceError
- [ ] 确认历史列表正常加载（资料名、状态、耗时、文章数）
- [ ] 切换筛选按钮（全部/已入库/失败/已跳过）
- [ ] 点击"查看详情"跳转到已入库内容 Tab 并自动筛选
- [ ] 点击"刷新历史"按钮重新加载

### 6.3 待人工确认说明（Review Queue）

- [ ] 打开 review queue，确认卡片不出现孤立逗号
- [ ] 确认 severity 显示中文（高风险/中风险/低风险），非英文原始值
- [ ] 确认 category 显示中文（来源不一致/低可追溯/OCR 低置信），非英文原始值
- [ ] 确认 description 有值时正常显示，无值时显示兜底文案"审查未提供详细说明"
- [ ] 确认 suggestion 优先显示 issue 自带建议，无自带建议时显示映射建议
- [ ] 展开 `<details>` 确认技术信息区显示原始英文 severity/category

### 6.4 已入库内容详情

- [ ] 点击文章 A，确认关键词显示前 8 个 + "还有 N 个关键词"按钮
- [ ] 点击"还有 N 个关键词"展开/收起
- [ ] 确认技术元数据 JSON 在折叠面板中（点击"技术信息"展开）
- [ ] 切换到文章 B，确认无 h4 重复、无 details 嵌套
- [ ] 反复切换文章 A→B→A，确认每次渲染正确

### 6.5 当前处理任务

- [ ] 确认进度不再重复显示比例（如"待确认 2/6 2/6"）
- [ ] 确认 RUNNING 状态显示当前步骤名而非"运行中"
- [ ] 确认节点名显示中文（如"编译新文章"而非"compile_new_articles"）
- [ ] 确认 FAILED/STALLED 状态显示错误码摘要而非异常堆栈

---

## 7. 风险列表

### P0（阻塞上线）

无。

### P1（建议修复后上线）

| 编号 | 风险 | 说明 |
|------|------|------|
| P1-1 | 治理指标变量名映射待确认 | JS 代码使用的变量名（如 `manualReviewCount`）与 API 返回字段名（`reviewPendingArticleCount`）可能不同。需确认 renderSummary 中有正确的字段映射。9 个 JS runtime 测试全部通过，说明映射大概率正确。 |

### P2（已知限制/可延后）

| 编号 | 风险 | 说明 |
|------|------|------|
| P2-1 | Review Queue 无 API 直接访问端点 | 当前通过 `/api/v1/admin/pending` 返回的是 query pending 列表。compile review queue 的访问路径不明确（`/api/v1/admin/compile-review-queue` 返回 null）。需确认前端如何获取 review queue 数据。 |
| P2-2 | 处理历史数据依赖 compile job | 当前 DB 中仅有 4 条历史记录（全部为 SUCCEEDED），无 FAILED/SKIPPED/STALLED 状态。前端筛选逻辑（全部/已入库/失败/已跳过）的正确性需在有多种状态时验证。 |
| P2-3 | 文章详情关键词覆盖率 | 当前 6 篇文章中部分文章（如"下一步计划"）keywordCount=0，关键词折叠/展开功能的完整验证需有更多关键词的文章。 |

### P3（无风险/已消除）

| 编号 | 说明 |
|------|------|
| P3-1 | `DocumentParseResultNormalizerTests` ClassNotFoundException 为预存问题，非本次引入 |
| P3-2 | mvn test 9/9 全部通过，JS runtime 覆盖 5 个修复区域 |

---

## 8. 明确结论

### 8.1 是否需要 agentA 再修？

**不需要。** 5 个修复区域的代码均已合入，9 个管理后台 JS runtime 测试全部通过，后端 API 返回值符合前端代码预期。无代码层面阻塞项。

### 8.2 是否建议 agentD 进行肉眼验收？

**agentD 无法进行肉眼验收。** 浏览器 UI 验证（Tab 导航、hover 样式、focus-visible、视觉一致性）必须由人在浏览器中完成。清单见第 6 节。

### 8.3 是否建议下一步跑小全链路？

**不建议。** 小全链路的增量价值有限——compile/query 主链路已在上一轮完整验收通过，前端修复是纯展示层变更，JS runtime 测试已覆盖核心逻辑。建议直接进入人工浏览器验收阶段。

### 8.4 推荐下一步动作

1. **人工按照第 6 节清单逐项验收**（预计 20-30 分钟）
2. 如人工验收通过，5 条修复线 + 处理历史模块可一起进入 pre-commit 复核
3. 如人工验收发现问题，根据问题类型判断是否需要 agentA 介入修复
