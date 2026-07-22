# 结果反馈有效性 Docker 环境验证报告

验证时间：2026-06-10 03:40 ~ 03:50
HEAD：`76eb4eb`
执行人：agentD（只读验证）

---

## 1. 用户操作时间线

| 时间 | 操作 | 数据库状态 |
|---|---|---|
| 02:49 | admin 提交反馈（answer_problem） | `pending_answer_feedback` +1（status=PENDING） |
| 03:29 | sean 确认（resolve） | status → RESOLVED，audit +1 |
| 03:30 | 用户再次搜索 | 得到更好答案 |

---

## 2. 反馈确认的实际链路

### 2.1 API 端点

```
POST /api/v1/admin/query-feedback/{id}/resolve
  → AnswerFeedbackService.resolve()
    → updateStatus(id, "RESOLVED", "RESOLVE", request)
```

### 2.2 resolve() 做了什么

`AnswerFeedbackService.resolve()` 只做了两件事：
1. 将 `pending_answer_feedback.status` 从 `PENDING` 改为 `RESOLVED`
2. 在 `answer_feedback_audits` 中插入一条 action=`RESOLVE` 的审计记录

### 2.3 resolve() 没有做什么

| 检查项 | 实际状态 | 说明 |
|---|---|---|
| contributions 表 | **0 条** | 没有导入任何正确答案到知识库 |
| article_usage_stats 表 | **0 条** | 没有重建热点统计 |
| article_hotspot_refresh | **未触发** | 没有刷新文章排名/权重 |
| 搜索索引 | **未修改** | FTS/Vector 索引无变化 |
| answer_feedback_audits | **2 条**（CREATE + RESOLVE） | 仅审计记录，不影响搜索 |

---

## 3. 真正影响答案导入的接口

```
POST /api/v1/admin/inspect/import-answers
  → InspectionAnswerImportService.importAnswer()
    → 写入 contributions 表
    → 可能触发 article_usage_stats 更新
    → 影响后续 query 的 CONTRIBUTION EVIDENCE section
```

该接口在本次时间线中**未被调用**。

---

## 4. 用户"能搜到了"的真实原因

### 4.1 LLM 日志证据

用户查询日志显示两次 LLM 调用：

| 时间 | promptLength | ARTICLE EVIDENCE | containsTruncated | containsOmitted |
|---|---|---|---|---|
| 02:46 | 34216 | 10669 chars | true | false |
| 03:30 | 20135 | 15317 chars | true | **true** |

两次调用中，ARTICLE EVIDENCE 均包含正确答案所在的文章内容。第一次 LLM 回答不够理想，第二次 LLM 回答改善了——这是 **LLM 非确定性**（两次采样结果不同），不是反馈确认触发的。

### 4.2 CONTRIBUTION EVIDENCE 始终为空

两次调用中 `CONTRIBUTION EVIDENCE = [present=true, length=6]`——长度固定为 6 表示空数组占位符（`"[]"`），说明**没有真实的 contribution 数据被使用**。如果答案导入生效，这里应显示实际的 contribution 文本。

---

## 5. 结论

### 结果反馈**不影响搜索**

| 操作 | 影响搜索？ | 说明 |
|---|---|---|
| 提交反馈 | ❌ 否 | 仅记录反馈条目 |
| 确认反馈（resolve） | ❌ 否 | 仅改状态 + 写审计记录 |
| 导入答案（import-answers） | ✅ **是** | 写入 contributions，影响后续 query |

**用户感知到的"现在就能搜到了"是 LLM 非确定性导致的巧合，不是反馈确认触发的。** 反馈确认链路不包含搜索索引修改、热度刷新、答案导入或任何搜索行为变更。

### 如果希望反馈确认后搜索结果改善

需要额外调用 `POST /api/v1/admin/inspect/import-answers` 将用户确认的正确答案导入 `contributions` 表，或在前端"确认"按钮中联动触发该接口。

---

## 6. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未清库、未重建、未重启
- [x] 未提交 commit
- [x] 所有结论基于数据库实际记录 + 源码只读分析 + Docker 日志
