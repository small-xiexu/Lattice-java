# B9a: api/admin Query Feedback DTO 契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B9a（B9 第 1 子批次，6/11 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminQueryFeedbackCreateRequest.java` | Request | `@Getter/@Setter`，删除 8 getter+8 setter，8 字段 Javadoc，禁止 @Data |
| `AdminQueryFeedbackHandleRequest.java` | Request | `@Getter/@Setter`，删除 2 getter+2 setter，2 字段 Javadoc，禁止 @Data |
| `AdminQueryFeedbackResponse.java` | Response | 类级 `@Getter`，删除 15 getter，15 字段 Javadoc，禁止 @Data |
| `AdminQueryFeedbackListResponse.java` | Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminQueryFeedbackDetailResponse.java` | Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminQueryFeedbackAuditResponse.java` | Response | 类级 `@Getter`，删除 9 getter，9 字段 Javadoc，禁止 @Data |

---

## 2. @Data 风险处置

**全部 6 个类均禁止 @Data**：

| 类 | 风险字段 | 后果 |
|---|---|---|
| `CreateRequest` | `question`, `answerSummary`, `comment`, `reportedBy` | toString() 输出用户查询内容、答案、反馈文本、提交人 |
| `HandleRequest` | `handledBy`, `comment` | toString() 输出处理人、处理意见 |
| `FeedbackResponse` | 同上 + `resolutionComment` | toString() 输出完整反馈内容和处理记录 |
| `FeedbackAuditResponse` | `comment`, `operatedBy`, `metadataJson` | toString() 输出审计意见、操作人、大 JSON |

---

## 3. 各类详细变更

### 3.1 AdminQueryFeedbackCreateRequest（8 字段）

| 字段 | 注释要点 |
|---|---|
| `queryId` | 关联查询会话标识，用于回溯原始问答上下文 |
| `question` | 用户原始问题，可能含 PII，禁止 toString() |
| `answerSummary` | 答案摘要，禁止 toString() |
| `feedbackType` | positive/negative/correction，驱动分类和优先级 |
| `comment` | 用户反馈说明，不可控输入，禁止 toString() |
| `articleKeys` | 关联文章唯一键列表，帮助定位问题文章 |
| `sourcePaths` | 关联来源路径列表 |
| `reportedBy` | 反馈提交人，禁止 toString() |

### 3.2 AdminQueryFeedbackResponse（15 字段）

关键标注：
- `status`：pending/resolved/dismissed，驱动前端处理标签和操作按钮
- `resolutionComment`：处理结果说明，禁止 toString()
- `handledBy`/`handledAt`：null=尚未处理

### 3.3 AdminQueryFeedbackAuditResponse（9 字段）

状态流转说明：`previousStatus → nextStatus`（如 pending→resolved）。`action` 可选值：create/resolve/dismiss。

---

## 4. Lombok 统计

| 类 | 注解 | 替代 |
|---|---|---|
| `AdminQueryFeedbackCreateRequest` | `@Getter/@Setter` | 8 getter + 8 setter |
| `AdminQueryFeedbackHandleRequest` | `@Getter/@Setter` | 2 getter + 2 setter |
| `AdminQueryFeedbackResponse` | `@Getter` | 15 getter |
| `AdminQueryFeedbackListResponse` | `@Getter` | 2 getter |
| `AdminQueryFeedbackDetailResponse` | `@Getter` | 2 getter |
| `AdminQueryFeedbackAuditResponse` | `@Getter` | 9 getter |
| **合计** | | **38 getter + 10 setter** |

---

## 5. 测试与验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh /tmp/b9a_special_cases_report.md: (clean, BLOCKER=0)
```

未发现 api/admin 层 feedback 测试类。已验证编译 + redline 通过。未修改 docs/模型绑定配置参考.md、special_cases_report.md。

---

## 6. B9 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B9a** | **已完成** | **6** |
| B9b | 待开始 | 5 (retrieval audit) |

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 6 个目标文件 | 通过 |
| 全部类禁止 @Data | 通过 |
| 构造器签名/逻辑未改 | 通过 |
| 未修改 docs/模型绑定配置参考.md | 通过 |
| 未修改 special_cases_report.md | 通过 |
| 未混入 B9b/B10 | 通过 |
| 未 stage/commit/push | 通过 |
