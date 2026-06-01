# B17 Query Domain + Evidence Domain 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B17 — `query/domain`（9）+ `query/evidence/domain`（14）= **23 个类**

---

## 一、拆分建议：B17a + B17b（按风险拆分）

| 子批次 | 候选数 | 范围 | @Data 数 | 风险 |
|---|---|---|---|---|
| **B17a** | **18** | query/domain 全 9 个 + evidence 全 9 个枚举 | **0** | 低：全部不可变/enum，仅 @Getter + Javadoc |
| **B17b** | **5** | evidence 中 5 个 @Data 可变对象 | **5** | 中：@Data 降级，含业务方法保护 |

---

## 二、B17a — 不可变对象 + 枚举（18 个，0 @Data）

### 2.1 query/domain（9 个：3 枚举 + 5 不可变 + 1 枚举）

| # | 类 | 类型 | 字段 | 特殊方法 | 处置 |
|---|---|---|---|---|---|
| 1 | `AnswerOutcome` | Enum | 5 值 | — | 枚举值 Javadoc |
| 2 | `GenerationMode` | Enum | 3 值 | — | 枚举值 Javadoc |
| 3 | `ModelExecutionStatus` | Enum | 4 值 | — | 枚举值 Javadoc |
| 4 | `QueryAnswerPayload` | 不可变 | 6 final | **5 static factory**（llm/ruleBased/fallback×2/failedFallback）+ 2 构造器 | @Getter + Javadoc |
| 5 | `QueryRewritePayload` | 不可变 | 6 final | **toAnswerPayload()** 业务方法 | @Getter + Javadoc |
| 6 | `ReviewIssue` | 不可变（@JsonCreator） | 3 final | — | @Getter + Javadoc |
| 7 | `ReviewResult` | 不可变（@JsonCreator） | 3 final | **5 static factory**（passed/issuesFound/parseRescued/parseFailed/timeoutFallback） | @Getter + Javadoc |
| 8 | `ReviewStatus` | Enum | 5 值 | — | 枚举值 Javadoc |
| 9 | `ReviewerPayload` | 不可变 | 6 final | 引用 `PromptCacheWritePolicy` | @Getter + Javadoc |

**可删除 getter**：6+6+3+3+6 = **24 个**，全部简单字段访问，全部可用 @Getter 替代。

**不可破坏的方法**：

| 类 | 方法 | 原因 |
|---|---|---|
| `QueryAnswerPayload` | `llm()` / `ruleBased()` / `fallback()` ×2 / `failedFallback()` | 5 个 static factory，多处调用 |
| `QueryAnswerPayload` | 2 个构造器（5P→6P telescoping） | 构造入口 |
| `QueryRewritePayload` | `toAnswerPayload()` | 领域转换方法，不可删除 |
| `ReviewResult` | `passed()` / `issuesFound()` / `parseRescued()` / `parseFailed()` / `timeoutFallback()` | 5 个 static factory，编码状态机语义 |

### 2.2 evidence 枚举（9 个）⚠️ 部分含业务方法

| # | 枚举 | 值数 | 特殊方法 | Javadoc 现状 |
|---|---|---|---|---|
| 10 | `AnswerShape` | 6 | `fromValue()` static factory | **已有值级 Javadoc**（ENUM/COMPARE/SEQUENCE/STATUS/POLICY/GENERAL） |
| 11 | `EvidenceAnchorSourceType` | 4 | — | 无 |
| 12 | `EvidenceAnchorValidationStatus` | 4 | — | 无 |
| 13 | `FactCardReviewStatus` | 5 | `databaseValue()` + `fromValue()` | 无（枚举值有 databaseValue 需要说明） |
| 14 | `FactCardType` | 5 | `fromValue()` | 无 |
| 15 | `FactValueType` | 5 | — | 无 |
| 16 | `FindingSupportLevel` | 2 | — | 无 |
| 17 | `ProjectionCitationFormat` | 2 | — | 无 |
| 18 | `ProjectionStatus` | 3 | — | 无 |

**AnswerShape 是唯一已有值级 Javadoc 的枚举**，可作为 B17 枚举注释的参照标准。

**FactCardReviewStatus 特殊**：每个值有 `databaseValue` 字段（如 VALID→"valid"），`fromValue()` 支持双向解析。枚举值 Javadoc 需说明对应的 databaseValue 和行为语义。

---

## 三、B17b — 5 个 @Data 可变对象（需降级）

| # | 类 | 字段 | @Data 风险 | 业务方法 | 处置 |
|---|---|---|---|---|---|
| 1 | `AnswerProjection` | 8 | 无业务方法冲突，但 status 有默认值 | — | @Data→@Getter @Setter |
| 2 | `AnswerProjectionBundle` | 2 | **answerMarkdown 大文本参与 toString()** | — | @Data→@Getter @Setter |
| 3 | `EvidenceAnchor` | 11 | **equals/hashCode 冲突**（identitySignature 定义域身份，@Data 的 11 字段 equals 不一致）；**quoteText 大文本参与 toString()** | `identitySignature()` / `hasReusableIdentity()` + 3 private normalize | @Data→@Getter @Setter |
| 4 | `FactFinding` | 12 | **equals/hashCode 冲突**（expectedFactKey/mergeIdentity 定义域身份）；**List anchorIds 参与 equals** | `expectedFactKey()` / `matchesFrozenFactKey()` / `mergeIdentity()` / `canEnterLedger()` / `isBlank()` | @Data→@Getter @Setter |
| 5 | `ProjectionCandidate` | 8 | 无业务方法冲突 | — | @Data→@Getter @Setter |

### 3.1 @Data 降级理由详述

**EvidenceAnchor**（最严重）：
- 11 字段 @Data equals/hashCode 包含 `double retrievalScore`、`quoteText`、`contentHash`、`validationStatus` 等
- 领域方法 `identitySignature()` 定义了锚点的真实身份：由 sourceType+sourceId+chunkId+lineRange+quoteText 组成
- 现象：两个锚点引用同一 source 但 retrievalScore 不同，@Data equals 判定不相等，但领域语义上它们是同一锚点
- toString() 会输出 `quoteText`（可能是长证据文本）
- **降级**：@Data → @Getter @Setter，保留 2 个领域方法 + 3 个 private normalize 方法

**FactFinding**（严重）：
- 12 字段 @Data equals/hashCode 包含 `double confidence`、`List<String> anchorIds`
- 领域方法 `mergeIdentity()` 定义了合并判定身份：`(factKey, valueText, unit)` 三元组
- `canEnterLedger()` 定义了入账条件：factKey 匹配 + 有 anchor
- toString() 会输出 `claimText`（可能很长）
- **降级**：@Data → @Getter @Setter，保留 5 个领域方法

**AnswerProjectionBundle**（中等）：
- `answerMarkdown` 是完整 Markdown 答案（可能极长）
- @Data toString() 会输出完整答案文本
- **降级**：@Data → @Getter @Setter

**AnswerProjection / ProjectionCandidate**（低）：
- 纯数据载体，无业务方法冲突
- 但 @Data 的 equals/hashCode/toString 对可变对象不必要
- **降级**：@Data → @Getter @Setter

---

## 四、排除清单

| 排除 | 理由 |
|---|---|
| `query/service/*` | 服务层 |
| `query/retrieval/*` | 检索层 |
| `query/answer/fallback/citation` 相关服务 | 主链行为 |
| `query/deepresearch/domain` + graph state | B18 |
| `governance/domain` | B19 |
| `infra/persistence/*` | 明确排除 |

---

## 五、字段风险汇总

### 大文本/大内容字段（不应参与 toString）

| 字段 | 所属类 | 说明 |
|---|---|---|
| `answerMarkdown` | QueryAnswerPayload, QueryRewritePayload, AnswerProjectionBundle | 完整 Markdown 答案，可能极长 |
| `quoteText` | EvidenceAnchor | 证据引用原文，可能很长 |
| `claimText` | FactFinding | 事实声明文本，可能较长 |
| `userFacingRewriteHints` | ReviewerPayload | List<String> 面向用户的重写提示 |
| `issues` | ReviewResult, ReviewerPayload | 审查问题列表 |

### 不应修改的领域行为

| 方法 | 语义 | 禁止原因 |
|---|---|---|
| `QueryAnswerPayload.fallback()` | LLM 调用失败/降级时的兜底答案构造 | 答案主链 fallback 逻辑 |
| `ReviewResult.passed()/timeoutFallback()` | 审查状态机工厂方法 | 审查主链状态流转 |
| `EvidenceAnchor.identitySignature()` | 锚点身份计算 | 证据去重/合并核心算法 |
| `FactFinding.mergeIdentity()` | finding 合并判定 | 证据合并核心算法 |
| `FactFinding.canEnterLedger()` | 入账条件判定 | 证据平面写入控制 |

---

## 六、给 agentA 的下一轮提示词草案（B17a）

```
交给 agentA。

本轮任务：对 B17a 的 18 个不可变对象 + 枚举做 @Getter + 领域语义 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_domain_evidence_contract_analysis_report.md

## 修改范围（18 个文件）

### query/domain 枚举（3 个，仅补 Javadoc）
1. AnswerOutcome.java — 5 值语义标注（SUCCESS→完整答案；INSUFFICIENT_EVIDENCE→证据不足；NO_RELEVANT_KNOWLEDGE→无相关知识；PARTIAL_ANSWER→部分答案；MODEL_FAILURE→模型失败）
2. GenerationMode.java — LLM/FALLBACK/RULE_BASED 标注生成路径
3. ModelExecutionStatus.java — SUCCESS/DEGRADED/FAILED/SKIPPED 标注执行状态

### query/domain 不可变对象（5 个，@Getter + Javadoc）
4. QueryAnswerPayload.java ⚠️ 5 static factory 必须保留
5. QueryRewritePayload.java ⚠️ toAnswerPayload() 必须保留
6. ReviewIssue.java（@JsonCreator 保留）
7. ReviewResult.java ⚠️ 5 static factory 必须保留
8. ReviewerPayload.java

### evidence 枚举（9 个，仅补 Javadoc）
9-17. AnswerShape（已有值级 Javadoc 做参照）、EvidenceAnchorSourceType、EvidenceAnchorValidationStatus、FactCardReviewStatus（标注 databaseValue）、FactCardType、FactValueType、FindingSupportLevel、ProjectionCitationFormat、ProjectionStatus

### query/domain 枚举（第 9 个）
18. ReviewStatus.java — PASSED/ISSUES_FOUND/PARSE_RESCUED/PARSE_FAILED/TIMEOUT_FALLBACK 标注审查状态机

## 禁止事项
- 禁止修改任何 static factory（QueryAnswerPayload 5 个、ReviewResult 5 个）
- 禁止修改 toAnswerPayload()
- 禁止修改 @JsonCreator 构造器
- 禁止修改 FactCardReviewStatus.fromValue()/databaseValue()
- 禁止修改 AnswerShape.fromValue()
- 禁止引入 @Data/@Setter

## 完成后：回写 B17a → "已完成"，输出 B17a_fix_result_report.md
```

---

## 七、给 agentA 的下一轮提示词草案（B17b）

```
交给 agentA。

本轮任务：对 B17b 的 5 个 @Data 可变证据对象做 @Data 降级 + 领域语义 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_domain_evidence_contract_analysis_report.md

## 修改范围（5 个文件，全部 @Data→@Getter @Setter）

1. AnswerProjection.java
   - @Data → @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 8 字段 Javadoc：projectionOrdinal/citationLiteral/projectionStatus(默认ACTIVE)/repairRound/repairedFromProjectionOrdinal 修复追踪

2. AnswerProjectionBundle.java
   - @Data → @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 2 字段 Javadoc：**answerMarkdown 标注大文本**

3. EvidenceAnchor.java ⚠️
   - @Data → @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 11 字段 Javadoc：**quoteText 标注大文本**；validationStatus 默认 RAW
   - **保留 identitySignature() / hasReusableIdentity() + 3 private normalize 方法**（禁止修改）

4. FactFinding.java ⚠️
   - @Data → @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 12 字段 Javadoc：**claimText 标注可能较长**
   - **保留 expectedFactKey() / matchesFrozenFactKey() / mergeIdentity() / canEnterLedger() / isBlank()**（禁止修改）

5. ProjectionCandidate.java
   - @Data → @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 8 字段 Javadoc：priority/verified/retrievalScore 候选排序依据

## 禁止事项
- 禁止修改 EvidenceAnchor 的 identitySignature/hasReusableIdentity/normalize*
- 禁止修改 FactFinding 的 expectedFactKey/matchesFrozenFactKey/mergeIdentity/canEnterLedger
- 禁止修改字段默认值（projection status ACTIVE、validation status RAW、anchorIds new ArrayList）
- 禁止修改 @NoArgsConstructor/@AllArgsConstructor

## 验收门槛
- mvn compile -pl . -q 通过
- 自查：EvidenceAnchor.identitySignature() 行为不变
- 自查：FactFinding.mergeIdentity() 行为不变

## 完成后：回写 B17b → "已完成"，输出 B17b_fix_result_report.md
```

---

## 八、审查结论

- B17 共 23 个类，按风险拆分为 **B17a（18 个不可变+枚举，0 @Data）** + **B17b（5 个 @Data 可变证据对象）**。
- **B17a** 可安全加 @Getter 删除 24 个手写 getter + 12 个枚举值 Javadoc + 5 个不可变对象字段 Javadoc。但必须保留：QueryAnswerPayload 5 个 static factory、ReviewResult 5 个 static factory、QueryRewritePayload.toAnswerPayload()、FactCardReviewStatus databaseValue/fromValue()、AnswerShape.fromValue()。
- **B17b** 5 个 @Data 需降级为 @Getter @Setter：
  - **EvidenceAnchor 和 FactFinding 最严重**：@Data equals/hashCode 与领域身份方法（identitySignature/mergeIdentity）冲突
  - **AnswerProjectionBundle**：answerMarkdown 大文本参与 toString()
  - 5 个领域方法（identitySignature/expectedFactKey/mergeIdentity/canEnterLedger 等）不可修改
- `AnswerShape` 是唯一已有值级 Javadoc 的枚举，可作为 B17a 的参照标准。
