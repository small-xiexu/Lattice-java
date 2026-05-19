# Compile Job 进度展示语义收口运行时验证报告

- 验证时间：2026-05-19 14:32–14:40 +0800
- 执行者：agentD（只验证，不修代码）
- 分支：`codex/qa-polish`
- 参照修复报告：`compile_job_progress_display_semantics_fix_result_report.md`

---

## 1. Redline 扫描

| 项目 | 值 |
|------|-----|
| 脚本 | `bash scripts/scan-redline.sh special_cases_report.md` |
| BLOCKER | **0**（存量，非本轮新增） |
| REVIEW | 1860（存量） |
| ALLOWLIST | 244（存量） |

---

## 2. 本轮是否修改代码

**否。** 本轮不修改任何文件。

验证对象为已合入的展示语义收口修复：
- `AdminCompileReviewSummaryService.buildStepDetail()` — 不再把审查步骤、审查模式、模型路由和计数字段拼入默认 `progressSteps.detail`
- `AdminCompileReviewSummaryService.hasPositiveCount()` / `hasNoReviewIssue()` — 仅用于选择用户可见文案
- `AdminProcessingTaskPresentationResolver.resolveSpecificStateLabel()` — `generate_synthesis_artifacts` 文案改为"正在整理知识库概览"

---

## 3. API 验证：progressSteps.detail 不暴露内部字段

### 3.1 验证方法

调用 `GET /api/v1/admin/processing-tasks?limit=10`，检查所有任务卡中 `progressSteps[].detail` 是否包含内部审计字段。

### 3.2 验证结果

共 7 条任务卡（含新创建的 smoke job `43274d6e`），`progressSteps` 分布如下：

| step key | label | detail 示例 |
|----------|-------|-------------|
| TASK_SUBMITTED / TASK_RECEIVED | 资料接收 | （空） |
| COMPILE_NEW_ARTICLES | 内容生成 | （空） |
| REVIEW_ARTICLES | 质量检查 | "质量检查后需要人工确认" / "已根据检查结果修正内容" / "未发现需要修复的问题" |
| FINALIZE_JOB | 写入知识库 | "入库完成" / （空） |

**`progressSteps.detail` 中未出现以下任何内部字段：**
- `review_articles`
- `reviewMode`
- `model_route`
- `acceptedCount`
- `pendingReviewCount`
- `needsHumanReviewCount`

各任务 detail 均为简洁中文文案，无字段拼接痕迹。

---

## 4. API 验证：compileReviewSummary 审计字段保留

### 4.1 验证方法

同一 API 调用，检查每个任务的 `compileReviewSummary` 对象是否完整保留。

### 4.2 验证结果

所有 7 条任务均包含完整的 `compileReviewSummary`，保留字段包括：

| 字段 | 示例值 |
|------|--------|
| reviewStepPresent | true |
| reviewStepName | "review_articles" |
| reviewAgentRole | "ReviewerAgent" |
| requestedReviewMode | "LLM" |
| reviewRoute | "compile.reviewer.gpt-5-5-chat-1" |
| reviewModeLabel | "LLM 审查" |
| acceptedCount | 0 / 1 |
| pendingReviewCount | 0 |
| needsHumanReviewCount | 0 / 1 / 6 / 7 |
| fixStepPresent | true |
| fixStepName | "fix_review_issues" |
| fixAttemptCount | 1 |
| fixRoute | "compile.fixer.gpt-5-5-chat-1" |
| fixDisplayMessage | "已触发自动修复" |
| reviewDisplayWarning | null |

**审计字段完整，无丢失。**

---

## 5. 代码级验证：generate_synthesis_artifacts 标签

`AdminProcessingTaskPresentationResolver.java:650`：

```java
if (AdminProcessingTaskStep.GENERATE_SYNTHESIS_ARTIFACTS.getCode().equals(normalizedStep)) {
    return "正在整理知识库概览";
}
```

标签已从旧文案改为"正在整理知识库概览"。由于 smoke job 为单 markdown 文件，不触发合成产物步骤，该标签未出现在本轮 API 响应中，但从代码确认修改到位。

---

## 6. 步骤标签一致性

所有任务的 `progressSteps[].label` 均为统一中文标签：

| step key | label |
|----------|-------|
| TASK_SUBMITTED / TASK_RECEIVED | 资料接收 |
| COMPILE_NEW_ARTICLES | 内容生成 |
| REVIEW_ARTICLES | 质量检查 |
| FINALIZE_JOB | 写入知识库 |

无步骤标签遗漏或回退。

---

## 7. 新增风险评估

| 风险 | 级别 | 说明 |
|------|------|------|
| 无 | — | 本轮未发现展示语义相关的新增风险。 |

---

## 8. 总结

| 验证项 | 结果 |
|--------|------|
| progressSteps.detail 不暴露内部审计字段 | ✅ 通过（7/7 任务） |
| compileReviewSummary 审计字段保留 | ✅ 通过（7/7 任务） |
| 步骤标签为统一中文文案 | ✅ 通过 |
| detail 为简洁用户可见中文 | ✅ 通过 |
| generate_synthesis_artifacts 标签改为"正在整理知识库概览" | ✅ 代码确认 |
| redline BLOCKER=0 | ✅ 通过 |
| 本轮未修改代码 | ✅ 通过 |

---

## 9. 下一步建议

展示语义收口验证通过。建议进入 **pre-commit quality review**，检查代码规范、测试覆盖，准备合并。
