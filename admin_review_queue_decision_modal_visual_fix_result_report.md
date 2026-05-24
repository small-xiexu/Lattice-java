# Review Queue 审核决策弹窗视觉重做报告

**日期**: 2026-05-24

**分支**: `codex/qa-polish`

## 测试结论

全部 17 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `AdminCompileReviewSummaryServiceTests`: 3 个用例通过
- `AdminProcessingTaskControllerTests`: 6 个用例通过
- `ManagementJsRuntimeTests`: 8 个用例通过（决策面板断言已更新覆盖新结构）

## 选了方案 A（modal 重做）还是方案 B（审核抽屉）

**选了方案 A — modal 重做**。理由：

1. 当前 review queue 页面已是左列表 + 右详情的两栏布局，再加上右侧抽屉会造成三栏嵌套，在窄屏上不可用
2. 现有 modal 已有 ESC 关闭、背景点击关闭、键盘导航等完成度较高的交互
3. 抽屉方案需要在 DOM 中持久化一个新面板，与现有的 `review-queue-detail` 面板产生定位冲突
4. 对本轮目标（视觉层级重排）而言，modal 方案成本低、风险小，且能做到"决策面板"的视觉感受

## 为什么上一版仍然"像表单弹窗"

上一版（`admin_review_queue_triage_and_modal_ux_fix_result_report.md` 中描述的版本）已经改进了信息架构，但仍有三个问题让它不够像审核决策面板：

1. **四段平权，没有主视觉**：四个 section（为什么需要你确认 / 核对清单 / 来源摘要 / 操作记录）使用完全相同的视觉样式（相同的 section 容器、相同的标题样式、相同的底色），没有任何一个段落在视觉上比其他的更突出。用户眼睛扫过去，所有信息看起来同等重要。

2. **"来源摘要"分散了注意力**：风险信息和来源信息分属两个独立 section，用户需要自己把"高风险 + 来源不一致"和"来源文件：卡券三期.md"在脑内合并，而不是一眼看到完整的审核上下文。

3. **"操作记录"与其他 section 视觉权重相同**：操作人输入框所在 section 的边框、圆角、底色、标题样式与"为什么需要你确认"完全一样。在视觉上，"填操作人"和"看风险摘要"是同等重要的操作——这不符合审核场景的真实优先级。

## 改动点

### 三段式决策面板（替代四段平权 section）

```
旧版（4 段平权）：
  ┌─ 为什么需要你确认 ─┐  ← 与其他 section 样式相同
  └────────────────────┘
  ┌─ 核对清单 ─────────┐  ← 与其他 section 样式相同
  └────────────────────┘
  ┌─ 来源摘要 ─────────┐  ← 分散了注意力
  └────────────────────┘
  ┌─ 操作记录 ─────────┐  ← 视觉权重与前三个完全相同
  └────────────────────┘

新版（3 段分层）：
  ┌─ 审核摘要（主视觉）─────────────────────────────┐
  │  草稿标题（粗体，15px）                          │
  │  [高风险]  badge    2 个待确认问题                │
  │  [来源不一致] [概念偏差]  ← 问题类型 tag           │
  │  ──────────────────────────────────────────── │
  │  来源  docs/test.md              2026-05-20     │
  │  请核对以下内容后决定是否入库。                    │
  │  ↑ 渐变底色 + 暖调，明显的视觉焦点                │
  └────────────────────────────────────────────────┘

  ┌─ 核对清单 ─────────────────────────────────────┐
  │  ▎是否超出源文范围                      ← high  │
  │  ▎是否新增源文未提供的主题或结论        ← high  │
  │  ▎是否存在概念偏差                     ← medium │
  │  ▎来源是否足以支撑正文                 ← medium │
  │  ↑ 浅绿底色，区别于审核摘要                      │
  └────────────────────────────────────────────────┘

  ┌─ 操作记录（底部次要区）──────────────────────────┐
  │  操作人  [admin___________]                      │
  │  备注    [_______________]                       │
  │  ↑ 浅色、小字、窄间距，明显被降级                 │
  └────────────────────────────────────────────────┘
```

### 1. `compile-review-queue.js` — `buildReviewActionModalHtml` 重写

- **审核摘要合并**：原"为什么需要你确认"和"来源摘要"合并为单一的 `.review-decision-summary` 块
  - 渐变底色 + 径向高光，在弹窗中形成唯一的视觉焦点
  - 风险 badge（大号，14px 700 weight）、问题数（紧挨 badge）、问题类型 tag（独立行）
  - 来源信息以紧凑单行展示（`来源  |  docs/test.md  |  2026-05-20`），用分隔线隔开
  - 底部一句话提示（"请核对以下内容后决定是否入库/驳回"），替代旧版独立的 toast 式 hint
- **核对清单增强**：独立 `.review-decision-checklist` 块
  - 浅绿底色区分于审核摘要
  - 每项增加 `data-risk="high|medium"` 属性
  - 高风险项：左侧红色 3px 竖线 + 红色方点标记
  - 中风险项：左侧黄色 3px 竖线 + 黄色方点标记
  - 新增"是否存在概念偏差"检查项
- **操作记录降级**：`.review-decision-record` 块
  - 更浅的底色、更细的边框、更小的 label 字号（11px）
  - 完全去除 section 标题（不再有"操作记录"大标题）
  - 靠底部的自然位置让它成为"扫过就填"的次要区

### 2. `admin.css` — 样式全面替换

| 旧 CSS 类 | 新 CSS 类 | 视觉变化 |
|-----------|-----------|----------|
| `.review-modal-section` (四段共用) | `.review-decision-summary` + `.review-decision-checklist` + `.review-decision-record` | 三段各有独立视觉 |
| `.review-modal-section-title` (大写标题) | 移除，`decision-checklist-title` 仅用于核对清单 | 审核摘要和操作记录不再有 uppercase 标题 |
| `.review-modal-summary-row` | `.decision-summary-badges` + `.decision-issue-tags` + `.decision-source-row` | 拆分为 badge 行 / tag 行 / 来源行三个明确区域 |
| `.review-modal-description-row` | `.decision-source-row` | 来源信息改为紧凑单行（label + file + time） |
| `.review-modal-checklist li` (纯文字) | `li` + `li[data-risk="high\|medium"]` | 增加彩色左边框 + 彩色方点标记 |
| `.review-modal-section` (操作记录) | `.review-decision-record` + `.decision-record-field` | 边框更淡、字号更小、无标题 |

### 3. `ManagementJsRuntimeTests.java` — 决策面板断言更新

替换约 45 行旧断言，新增覆盖：

- **审核摘要区**：验证 `review-decision-summary` 块存在，包含风险 badge、问题数、问题类型 tag、来源行、上下文提示
- **来源合并不再独立**：验证 `来源摘要` 和 `为什么需要你确认` 两个旧 section 不再独立出现
- **核对清单增强**：验证 `data-risk` 属性存在于清单项，新增"概念偏差"检查项
- **操作记录降级**：验证 `review-decision-record` 在 DOM 中位于 `review-decision-summary` 之后
- **驳回路径**：验证驳回 modal 中决策摘要和核对清单完整保留

## 是否改动后端

**否**。仅修改：

- `src/main/resources/static/admin/compile-review-queue.js`（`buildReviewActionModalHtml` 重写）
- `src/main/resources/static/admin/admin.css`（样式从四段平权替换为三段分层）
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`（决策面板断言更新）

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests,com.xbk.lattice.admin.service.AdminCompileReviewSummaryServiceTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. 打开确认入库弹窗，第一眼看到的是审核摘要（大号风险 badge + 问题类型 + 来源文件），而不是操作人输入框
2. 审核摘要区有明显的视觉区分（渐变底色），是弹窗中最突出的块
3. 核对清单有浅绿底色，每项有彩色左边线标记（高风险红色、中风险黄色）
4. 操作人输入框位于弹窗底部，视觉权重明显低于审核摘要
5. 操作记录区没有"操作记录"大标题，呈现为简洁的轻量字段
6. 来源信息和风险信息在同一块内，不需要跨 section 拼凑
7. 底部按钮层级：取消（左）→ 驳回（中，危险次按钮）→ 确认入库（右，主按钮）
8. 驳回 modal 中驳回原因的上方辅助提示保留
9. ESC 关闭、背景点击关闭、inline 驳回按钮切换到驳回 modal 均正常
10. 已有功能无回归：筛选、分组、列表卡片、详情排序、键盘导航
