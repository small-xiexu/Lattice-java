# B17 Query Domain + Evidence Domain Checkpoint 门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B17a + B17b — query/domain（9）+ query/evidence/domain（14）
状态：**PASS — 所有核查项通过，无阻塞问题**

---

## 1. 当前 Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| 生产代码（B17a query/domain） | 9 | 4 枚举 Javadoc + 5 不可变 @Getter |
| 生产代码（B17a evidence 枚举） | 8 | 8 枚举 Javadoc（AnswerShape 已有 Javadoc 未改） |
| 生产代码（B17b evidence 可变） | 5 | @Data→@Getter/@Setter 降级 |
| **生产代码合计** | **22**（+ AnswerShape = 23 候选类） | — |
| 计划文档（治理台账） | 1 | 台账更新（B17→已完成），"当前下一步"已修正 |
| 模型绑定配置参考 | 1 | **已知 out-of-scope dirty（不得纳入）** |
| special_cases_report | 1 | **已知 out-of-scope dirty（不得纳入）** |
| 批次报告（untracked） | 3 | 可纳入 |

### 1.2 生产代码详细清单

#### B17a: query/domain（9 个）

| 文件 | 类型 | diff 规模 | 变更 |
|---|---|---|---|
| `AnswerOutcome.java` | 枚举 | +9/-? | 5 值 Javadoc |
| `GenerationMode.java` | 枚举 | +7/-? | 3 值 Javadoc |
| `ModelExecutionStatus.java` | 枚举 | +8/-? | 4 值 Javadoc |
| `ReviewStatus.java` | 枚举 | +14/-? | 5 值 Javadoc |
| `QueryAnswerPayload.java` | 不可变 | +201/-? | @Getter, 6 getter 删除, 5 static factory 保留 |
| `QueryRewritePayload.java` | 不可变 | +105/-? | @Getter, 6 getter 删除, toAnswerPayload() 保留 |
| `ReviewIssue.java` | 不可变 | +45/-? | @Getter, 3 getter 删除, @JsonCreator 保留 |
| `ReviewResult.java` | 不可变 | +96/-? | @Getter, 3 getter 删除, 5 static factory 保留 |
| `ReviewerPayload.java` | 不可变 | +88/-? | @Getter, 6 getter 删除 |

#### B17a: evidence 枚举（8 个，AnswerShape 已有 Javadoc 跳过）

| 文件 | 枚举值 | diff | 特殊方法保留 |
|---|---|---|---|
| `EvidenceAnchorSourceType.java` | 4 | +8/-? | — |
| `EvidenceAnchorValidationStatus.java` | 4 | +8/-? | — |
| `FactCardReviewStatus.java` | 5 | +25/-? | databaseValue()/fromValue() |
| `FactCardType.java` | 5 | +15/-? | fromValue() |
| `FactValueType.java` | 5 | +9/-? | — |
| `FindingSupportLevel.java` | 2 | +6/-? | — |
| `ProjectionCitationFormat.java` | 2 | +6/-? | — |
| `ProjectionStatus.java` | 3 | +7/-? | — |

#### B17b: evidence 可变对象（5 个 @Data 降级）

| 文件 | 字段 | diff | 降级 | 领域方法保留 |
|---|---|---|---|---|
| `AnswerProjection.java` | 8 | +25/-? | @Data→@Getter/@Setter | projectionStatus 默认 ACTIVE |
| `AnswerProjectionBundle.java` | 2 | +13/-? | @Data→@Getter/@Setter | answerMarkdown 大文本标注 |
| `EvidenceAnchor.java` | 11 | +73/-? | @Data→@Getter/@Setter | identitySignature/hasReusableIdentity+3 normalize |
| `FactFinding.java` | 12 | +75/-? | @Data→@Getter/@Setter | expectedFactKey/matchesFrozenFactKey/mergeIdentity/canEnterLedger/isBlank |
| `ProjectionCandidate.java` | 8 | +25/-? | @Data→@Getter/@Setter | — |

---

## 2. 核查项逐项结果

### 2.1 生产代码仅限 B17 23 个候选类

| 包 | 预期 | 实际修改 | 匹配 |
|---|---|---|---|
| `query/domain/` | 9 | 9 | ✅ |
| `query/evidence/domain/` | 14 | 13（AnswerShape 已有 Javadoc 未改） | ✅ |
| **合计** | **23** | **22 修改 + 1 已就绪** | ✅ |

### 2.2 越界检查

```
git diff --name-only -- query/service/ query/retrieval/ query/deepresearch/
→ (无输出)
```
query 主链服务和 B18 范围均未修改。

**结果：PASS。**

### 2.3 B17b @Data 降级

```
rg -n "@Data" <5个evidence可变对象>
→ (无输出)
```

| 类 | @Getter | @Setter | @NoArgsConstructor | @AllArgsConstructor | 结果 |
|---|---|---|---|---|---|
| `AnswerProjection` | 15 | 16 | ✅ | ✅ | ✅ |
| `AnswerProjectionBundle` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `EvidenceAnchor` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `FactFinding` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ProjectionCandidate` | ✅ | ✅ | ✅ | ✅ | ✅ |

**结果：PASS。** 5 个 @Data 全部降级为 @Getter @Setter，0 @Data 残留。

### 2.4 B17a @Getter

| 类 | @Getter | 结果 |
|---|---|---|
| `QueryAnswerPayload` | 1（类级） | ✅ |
| `QueryRewritePayload` | 1（类级） | ✅ |
| `ReviewIssue` | 1（类级） | ✅ |
| `ReviewResult` | 1（类级） | ✅ |
| `ReviewerPayload` | 1（类级） | ✅ |

**结果：PASS。** 5 个不可变对象 @Getter，24 getter 删除。

### 2.5 B17a 关键 factory/业务方法保留

#### QueryAnswerPayload — 5 static factory

| 方法 | 行号 | 语义 |
|---|---|---|
| `llm()` | 48 | LLM 正常生成答案 |
| `ruleBased()` | 52 | 规则引擎生成答案 |
| `fallback()` | 56 | 降级兜底答案（无原因） |
| `fallback(String)` | 60 | 降级兜底答案（含原因） |
| `failedFallback()` | 64 | 降级失败后的最终兜底 |

**结果：PASS。** 5 个 static factory + 2 个 telescoping 构造器全部保留。

#### ReviewResult — 5 static factory

| 方法 | 行号 | 语义 |
|---|---|---|
| `passed()` | 37 | 审查通过 |
| `issuesFound()` | 38 | 发现问题 |
| `parseRescued()` | 39 | 解析抢救后 |
| `parseFailed()` | 40 | 解析失败 |
| `timeoutFallback()` | 41 | 超时降级 |

**结果：PASS。** 5 个 static factory 全部保留。

#### 其他关键方法

| 类 | 方法 | 结果 |
|---|---|---|
| `QueryRewritePayload` | `toAnswerPayload()`（第 44 行） | ✅ |
| `FactCardReviewStatus` | `databaseValue()` + `fromValue()` | ✅ |
| `FactCardType` | `fromValue()` | ✅ |

### 2.6 B17b 领域方法保留

#### EvidenceAnchor（5 个方法）

| 方法 | 用途 |
|---|---|
| `identitySignature()` | 冻结锚点身份串，用于 content hash 和去重 |
| `hasReusableIdentity()` | 判断最小 identity 前提 |
| `normalize(String)` | private 规范化工具 |
| `normalizeChunk(String)` | private chunk id 规范化 |
| `normalizeLine(Integer)` | private 行号规范化 |

**结果：PASS。** 2 个公共领域方法 + 3 个 private 规范化方法全部保留。

#### FactFinding（5 个方法）

| 方法 | 用途 |
|---|---|
| `expectedFactKey()` | 构造 factKey 公式 |
| `matchesFrozenFactKey()` | factKey 匹配校验 |
| `mergeIdentity()` | run 内 merge/conflict 判定键 |
| `canEnterLedger()` | 最小可入账条件 |
| `isBlank(String)` | private 空白判断 |

**结果：PASS。** 4 个公共领域方法 + 1 个 private 工具方法全部保留。

### 2.7 默认值保留

| 默认值 | 类 | 结果 |
|---|---|---|
| `projectionStatus = ACTIVE` | AnswerProjection | ✅ |
| `validationStatus = RAW` | EvidenceAnchor | ✅ |
| `anchorIds = new ArrayList<>()` | FactFinding | ✅ |

### 2.8 计划台账状态

| 批次 | 台账状态 | 验证 |
|---|---|---|
| B17 | 已完成（B17a 18 + B17b 5 = 23 类） | mvn compile PASS |
| B18 | 待开始 | — |

**"当前下一步"已修正**：`"B0-B16 已完成，进入 B17"` → `"B0-B17 已完成（共 203 类），进入 B18"`

**结果：PASS。**

### 2.9 报告敏感信息与 out-of-scope

| 检查项 | 结果 |
|---|---|
| `docs/模型绑定配置参考.md` | 仍为 dirty（不纳入） |
| `special_cases_report.md` | 仍为 dirty（不纳入） |
| B17 报告 API key | 无（rg 无输出） |

---

## 3. 可纳入本次 Checkpoint 的文件清单

### 3.1 计划台账（1 个）

- `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`

### 3.2 B17 生产代码（22 个修改文件）

**query/domain（9）**：AnswerOutcome, GenerationMode, ModelExecutionStatus, ReviewStatus, QueryAnswerPayload, QueryRewritePayload, ReviewIssue, ReviewResult, ReviewerPayload

**query/evidence/domain（13）**：EvidenceAnchorSourceType, EvidenceAnchorValidationStatus, FactCardReviewStatus, FactCardType, FactValueType, FindingSupportLevel, ProjectionCitationFormat, ProjectionStatus, AnswerProjection, AnswerProjectionBundle, EvidenceAnchor, FactFinding, ProjectionCandidate

### 3.3 B17 批次报告（3 个 untracked）

| 文件名 | 类型 |
|---|---|
| `query_domain_evidence_contract_analysis_report.md` | 边界审查 |
| `query_domain_evidence_immutable_enum_contract_javadoc_lombok_fix_result_report.md` | B17a 修复报告 |
| `query_evidence_mutable_contract_javadoc_lombok_fix_result_report.md` | B17b 修复报告 |

---

## 4. 必须排除的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 变更 + 计划禁令 |
| `special_cases_report.md` | 机械重扫 + 计划禁令 |

---

## 5. B17 汇总

### 5.1 B17a（18 个枚举 + 不可变对象）

| 指标 | 数量 |
|---|---|
| 枚举 | 12（4 query/domain + 8 evidence） |
| 不可变对象 | 5 |
| @Getter | 5 |
| 删除 getter | 24 |
| @Data/@Setter | **0** |
| static factory 保留 | 10（QueryAnswerPayload 5 + ReviewResult 5） |
| toAnswerPayload 保留 | 1 |
| fromValue/databaseValue 保留 | 3（FactCardReviewStatus×2 + FactCardType） |
| AnswerShape 已有 Javadoc | 无需重写 |

### 5.2 B17b（5 个 @Data 降级）

| 指标 | 数量 |
|---|---|
| @Data 降级 | 5→**0** |
| @Getter @Setter | 5 对 |
| @NoArgsConstructor/@AllArgsConstructor | 5 对保留 |
| 领域方法保留 | 10（EvidenceAnchor 5 + FactFinding 5） |
| 默认值保留 | 3 |

### 5.3 B17 合计

| 指标 | 数量 |
|---|---|
| 候选类 | 23（22 修改 + 1 已有） |
| @Getter（不可变） | 5 |
| @Getter @Setter（可变降级） | 5 |
| @Data 残留 | **0** |
| 删除 getter | 24 |
| 枚举值 Javadoc | ~60（12 枚举） |
| 领域方法保留 | 20+ |

### 5.4 编译验证

| 子批次 | mvn compile |
|---|---|
| B17a | BUILD SUCCESS |
| B17b | BUILD SUCCESS |

---

## 6. 是否可以进入 B18

**可以。** 所有核查项通过：

- [x] 22 修改文件 + AnswerShape = 23 候选类完全匹配
- [x] B17a 5 个不可变对象 @Getter（24 getter 删除）
- [x] B17a 10 个 static factory + toAnswerPayload + fromValue/databaseValue 全部保留
- [x] B17b 5 个 @Data→@Getter/@Setter 全量降级，0 残留
- [x] B17b 10 个领域方法全部保留（EvidenceAnchor 5 + FactFinding 5）
- [x] B17b 3 个默认值保留
- [x] query/service/retrieval/deepresearch 均未修改
- [x] 台账 B17→已完成，下一步 B18，"当前下一步"已修正
- [x] 2 个 out-of-scope 文件已排除 + 报告无敏感信息
- [x] mvn compile PASS

---

## 附录：全量治理进度（截至 B17）

| 阶段 | 批次 | 类数 | 处置方式 | 状态 |
|---|---|---|---|---|
| 试点 | B0 | 3 | @Getter + Javadoc | 已提交 |
| API 边界 DTO | B0.5-B10 | 95 | @Getter/@Setter + Javadoc | 已完成 |
| Controller 内部 DTO | B11 | 29 | @Data 降级 + @ToString.Exclude | 已完成 |
| Config + State | B12 | 16 | Javadoc / @Getter | 已完成 |
| Compiler Domain + AST | B13 | 14 | @Getter + @Data 降级 | 已完成 |
| DocumentParse Domain | B14 | 10 | @Getter | 已完成 |
| Source Domain | B15 | 9 | @Getter + 安全标注 | 已完成 |
| LLM Domain | B16 | 4 | @Getter + 安全标注 | 已完成 |
| Query + Evidence Domain | B17 | 23 | @Getter + @Data 降级 + 领域方法保护 | 已完成 |
| **累计** | | **203** | | **200 未提交** |
| DeepResearch/Graph | B18 | ~17 | 待定 | 待开始 |
| Governance Domain | B19 | ~5 | 待定 | 待开始 |
| 全局复扫 | B20 | — | — | 待开始 |
