# B17a: Query Domain + Evidence 枚举/不可变对象契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B17a（B17 第 1 子批次，18/23 类）

---

## 1. 修改文件清单

### query/domain 枚举（4 个，仅 Javadoc）
| 文件 | 枚举值 | 变更 |
|---|---|---|
| `AnswerOutcome.java` | 5 | 枚举值 Javadoc（SUCCESS/INSUFFICIENT_EVIDENCE/NO_RELEVANT_KNOWLEDGE/PARTIAL_ANSWER/MODEL_FAILURE） |
| `GenerationMode.java` | 3 | 枚举值 Javadoc（LLM/FALLBACK/RULE_BASED） |
| `ModelExecutionStatus.java` | 4 | 枚举值 Javadoc（SUCCESS/DEGRADED/FAILED/SKIPPED） |
| `ReviewStatus.java` | 5 | 枚举值 Javadoc（PASSED/ISSUES_FOUND/PARSE_RESCUED/PARSE_FAILED/TIMEOUT_FALLBACK） |

### query/domain 不可变对象（5 个，@Getter + Javadoc）
| 文件 | 字段 | 删除 getter | 保留方法 |
|---|---|---|---|
| `QueryAnswerPayload.java` | 6 | 6 | 2 构造器 + 5 static factory |
| `QueryRewritePayload.java` | 6 | 6 | toAnswerPayload() |
| `ReviewIssue.java` | 3 | 3 | @JsonCreator |
| `ReviewResult.java` | 3 | 3 | 5 static factory |
| `ReviewerPayload.java` | 6 | 6 | — |

### evidence/domain 枚举（8 个，仅 Javadoc）
| 文件 | 枚举值 | 特殊方法保留 |
|---|---|---|
| `EvidenceAnchorSourceType.java` | 4 | — |
| `EvidenceAnchorValidationStatus.java` | 4 | — |
| `FactCardReviewStatus.java` | 5 | databaseValue()/fromValue() |
| `FactCardType.java` | 5 | fromValue() |
| `FactValueType.java` | 5 | — |
| `FindingSupportLevel.java` | 2 | — |
| `ProjectionCitationFormat.java` | 2 | — |
| `ProjectionStatus.java` | 3 | — |

**AnswerShape** 已有值级 Javadoc（6 个枚举值 + fromValue()），本轮不重写。

---

## 2. Lombok 统计

| 类 | @Getter | 删除 getter |
|---|---|---|
| `QueryAnswerPayload` | 1 | 6 |
| `QueryRewritePayload` | 1 | 6 |
| `ReviewIssue` | 1 | 3 |
| `ReviewResult` | 1 | 3 |
| `ReviewerPayload` | 1 | 6 |
| **合计** | **5** | **24** |

---

## 3. 关键保留

| 类 | 方法 | 数量 |
|---|---|---|
| `QueryAnswerPayload` | static factory: llm/ruleBased/fallback×2/failedFallback | 5 |
| `QueryRewritePayload` | toAnswerPayload() | 1 |
| `ReviewResult` | static factory: passed/issuesFound/parseRescued/parseFailed/timeoutFallback | 5 |
| `FactCardReviewStatus` | databaseValue()/fromValue() | 2 |
| `FactCardType` | fromValue() | 1 |
| `AnswerShape` | fromValue() | 1 |

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
@Getter: 5/5 ✓
static factory 全保留 ✓
toAnswerPayload 保留 ✓
fromValue/databaseValue 保留 ✓
AnswerShape 已有 Javadoc 不重写 ✓
```

---

## 5. B17 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B17a** | **已完成** | 18 |
| B17b | 待开始 | 5 (@Data 可变证据对象) |

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 17 个目标文件（AnswerShape 跳过） | 通过 |
| 5 个 @Getter | 通过 |
| factory/业务方法全保留 | 通过 |
| fromValue/databaseValue 保留 | 通过 |
| 未修改 B17b 的 @Data 对象 | 通过 |
| 未 stage/commit/push | 通过 |
