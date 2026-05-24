# 当前处理任务质量检查阶段小字文案冲突修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

## 测试结论

全部 17 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `AdminCompileReviewSummaryServiceTests`: 3 个用例通过（含新增 `shouldShowInProgressSemanticsWhenTaskIsRunning`）
- `AdminProcessingTaskControllerTests`: 6 个用例通过
- `ManagementJsRuntimeTests`: 8 个用例通过

## 根因

`AdminCompileReviewSummaryService.buildStepDetail()` 仅根据审查数据状态（计数、fix 步骤是否存在）决定展示文案，完全不考虑任务本身的 RUNNING/SUCCEEDED/FAILED 状态。当任务仍在 RUNNING 时，fix 步骤可能已被触发（数据中 `fixStepPresent=true`），该方法直接返回终态结论"已根据检查结果修正内容"，与步骤标题"正在检查内容质量"形成语义冲突。

**触发链路**：

1. 任务处于 RUNNING 状态，当前步骤为 `review_articles`
2. review 步骤已发现 fixable issue 并触发 `fix_review_issues` → 数据中 `fixStepPresent=true`
3. `buildStepDetail()` 看到 `fixStepPresent=true`，返回"已根据检查结果修正内容"
4. 前端同时显示：步骤标题"正在检查内容质量" + 小字"已根据检查结果修正内容"——进度中与已完成语义冲突

## 改动点

### 1. `AdminCompileReviewSummaryService.java` — `buildStepDetail` 增加状态感知（核心修复）

```java
// 修复前 — 仅根据数据状态决定文案
public String buildStepDetail(AdminCompileReviewSummaryResponse summary) {
    ...
    if (summary.isFixStepPresent()) {
        return "已根据检查结果修正内容";
    }
    ...
}

// 修复后 — RUNNING 时优先展示进行中语义
public String buildStepDetail(AdminCompileReviewSummaryResponse summary, String displayStatus) {
    ...
    if (AdminProcessingTaskDisplayStatus.RUNNING.matches(displayStatus)) {
        if (summary.isFixStepPresent()) {
            return "已发现待修复问题，正在自动修正";
        }
        return "正在检查内容质量";
    }
    // 非 RUNNING 状态沿用既有终态逻辑
    ...
}
```

| 场景 | 修复前 | 修复后 |
|---|---|---|
| RUNNING + review 阶段 | "未发现需要修复的问题"（终态） | "正在检查内容质量"（进行中） |
| RUNNING + fix 已触发 | "已根据检查结果修正内容"（终态） | "已发现待修复问题，正在自动修正"（进行中） |
| SUCCEEDED + 无问题 | "未发现需要修复的问题" | "未发现需要修复的问题"（不变） |
| SUCCEEDED + fix 完成 | "已根据检查结果修正内容" | "已根据检查结果完成修正"（措辞微调） |
| SUCCEEDED + 需人工确认 | "质量检查后需要人工确认" | "质量检查后需要人工确认"（不变） |

### 2. `AdminProcessingTaskService.java` — `enrichReviewStepDetail` 传递状态（2 处）

- `enrichReviewStepDetail()` 新增 `String displayStatus` 参数，透传给 `buildStepDetail()`
- `toSourceSyncTask()` 调用处传入 `displayStatus`
- `toStandaloneCompileTask()` 调用处传入 `derivedStatus`

### 3. `AdminCompileReviewSummaryServiceTests.java` — 测试更新

- 既有测试调用补充 `displayStatus` 参数（`SUCCEEDED.getCode()`）
- 断言 `fixedSummary` 终态文案更新为"已根据检查结果完成修正"
- 新增 `shouldShowInProgressSemanticsWhenTaskIsRunning` 测试：
  - RUNNING + 无 fix 步骤 → "正在检查内容质量"
  - RUNNING + fix 已触发 → "已发现待修复问题，正在自动修正"

## 是否改动后端

**是**，仅修改后端 service 层：

- `src/main/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryService.java`（`buildStepDetail` 方法签名 + 逻辑）
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java`（`enrichReviewStepDetail` 透传参数，2 处调用点）
- `src/test/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryServiceTests.java`（测试适配 + 新增）

前端代码、数据库、API 接口定义均未触碰。

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.admin.service.AdminCompileReviewSummaryServiceTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests,com.xbk.lattice.api.admin.ManagementJsRuntimeTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. 触发一次编译任务，在"当前处理任务"中观察步骤 3（质量检查）的小字文案
2. review 进行中（fix 尚未触发）时，小字应显示"正在检查内容质量"
3. review 发现 issue 并触发 fix 后，小字应切换为"已发现待修复问题，正在自动修正"
4. 任务完成后，小字应显示终态文案（"未发现需要修复的问题"/"已根据检查结果完成修正"/"质量检查后需要人工确认"）
5. WAIT_CONFIRM 状态的小字"质量检查后需要人工确认"无回归
6. 其他步骤（编译、持久化、向量等）的文案无回归
