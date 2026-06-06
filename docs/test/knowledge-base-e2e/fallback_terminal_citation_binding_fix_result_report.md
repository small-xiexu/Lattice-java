# FALLBACK Terminal Citation Binding — 修复结果报告

修复时间：2026-06-05
执行人：agentA（代码执行 Agent）
前置分析：`fallback_terminal_citation_binding_analysis_report.md`（agentB）
前置采集：`recall_citation_metrics_collection_report.md`（agentD）

---

## 1. 根因复述

FALLBACK terminal unit 结论的 citation 指向源文件（`[→ equipment-borrowing-policy.yaml]`），但 `CitationValidator` 只在原始源文件中逐句做 token overlap。原始 YAML 的嵌套结构与 terminal unit 的 flat key=value 格式存在系统性语义失配，验证通过与否取决于 token 偶然重叠度是否超过阈值，而非是否找到了正确证据。

示例：claim `equipment_types[0].deposit_amount = 100` 在 `equipment-borrowing-policy.yaml` 中匹配到的最佳句子是 `max_concurrent_requests: 50`（重叠分 0.5），而非 `deposit_amount: 100`。

terminal unit 的结构化证据（displayText、valueText）已存储在 `fact_card_terminal_units` 表中，但 validator 不查询这个数据源。

---

## 2. 修改文件

| 文件 | 修改类型 |
|------|----------|
| `src/main/java/com/xbk/lattice/query/citation/CitationValidator.java` | 生产代码 |
| `src/test/java/com/xbk/lattice/query/citation/CitationValidatorTests.java` | 新增 4 个测试 |
| 6 个其他测试文件 | 构造器参数适配（新增第三参数 `null`） |

---

## 3. 最小 diff 摘要

### 3.1 `CitationValidator.java`

**新增依赖**：`FactCardTerminalUnitJdbcRepository`

**新增方法**：
- `validateAgainstTerminalUnitEvidence()` — 查询与 source file 关联的 terminal unit 记录，用 displayText/valueText/normalizedValue 构建 evidence text，计算 overlap。高重叠时返回 VERIFIED，否则返回 null 回退现有路径。
- `buildTerminalUnitEvidenceText()` — 将 terminal unit 记录的 displayText、valueText、normalizedValue 拼接为验证用文本。

**SOUCE_FILE 分支插入点**：在 `sourceFileRecord` 获取后、`hasDirectEvidenceLineMatch` 之前，插入 terminal unit evidence 检查。

### 3.2 验证路径优先级

```
1. source file lookup (existing)
2. → terminal unit evidence check (NEW)
     - query fact_card_terminal_units by sourceFileId
     - build evidence text from displayText + valueText
     - overlap >= 1.0 or high-confidence partial → VERIFIED
     - otherwise → null (fall through)
3. → direct line match (existing)
4. → source rule overlap (existing)
5. → context window verification (existing)
6. → DEMOTED (existing)
```

---

## 4. terminal unit evidence 验证路径说明

### 4.1 为什么能消除偶然性

terminal unit 的 `displayText` 是 Materializer 在 compile 期生成的 flat key=value 格式（如 `equipment_types[0].deposit_amount = 100`），与 claim 文本在 token 层面高度一致。验证不再依赖原始源文件中某一行与 claim 的偶然 token 重叠。

### 4.2 通用性保证

| 检查项 | 判定 |
|--------|:---:|
| 是否检测具体字段名？ | 否 |
| 是否检测具体文件名？ | 否 |
| 是否检测具体题号？ | 否 |
| 匹配信号 | sourceFileId + hard fact token overlap |
| 对所有 terminal unit 一视同仁？ | 是 |

### 4.3 回退路径

- terminal unit 仓库不可用 → 返回 null，走现有 source file overlap
- 该 source file 没有 terminal unit → 返回 null，走现有路径
- terminal unit evidence 重叠不足 → 返回 null，走现有路径
- 非 SOURCE_FILE 类型 citation → 不触发此路径

---

## 5. 为什么不是 case 特判

- 触发条件：citation.sourceType == SOURCE_FILE（通用类型判断）
- 验证逻辑：硬事实 token overlap（与现有逻辑相同的通用算法）
- 数据来源：`fact_card_terminal_units` 表（通用数据结构，所有事实卡类型共有）
- 不引入任何文件名、字段名、题号、答案值判断

---

## 6. 测试结果

### 定向测试

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

新增 4 个测试：
- `shouldVerifySourceCitationByTerminalUnitEvidence` — terminal unit 证据验证通过
- `shouldNotVerifyTerminalUnitEvidenceWhenValueMismatch` — 值不匹配时回退 DEMOTED
- `shouldFallbackToSourceOverlapWhenNoTerminalUnitExists` — 无 terminal unit 时走原有路径
- `shouldNotCrossMatchTerminalUnitsFromDifferentSourceFile` — 跨 source file 不误匹配

### 全量 mvn test

```
Tests run: 1014, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 7. redline 结果

`BLOCKER=0`

---

## 8. 潜在副作用和保护点

| 副作用 | 评估 | 缓解 |
|--------|------|------|
| 每次 SOURCE_FILE citation 验证多一次 DB 查询 | 性能影响小（citation 验证非高频操作） | 查询按 sourceFileId，走主键索引 |
| 与 LLM 模式 citation 的交互 | 不影响（LLM 模式的 citation 不指向 SOURCE_FILE） | SOURCE_FILE 触发条件限定了范围 |
| 非 terminal unit 的 SOURCE_FILE 验证 | 无 terminal unit 时返回 null，完全走现有路径 | 行为不变 |

---

## 9. 仍需 agentD runtime gate 验证

### 验证目标

| 验证项 | 通过标准 |
|--------|----------|
| FQ4 citation | `citationCheck.coverageRate` 从 0.0 提升到 1.0 |
| FG1 citation | `citationCheck.coverageRate` 从 0.5 提升到 1.0 |
| FG2 citation | `citationCheck.coverageRate` 从 0.0 提升到 1.0 |
| FQ3/FQ5/FQ6 保护 | 保持 cov=1.0（无回归） |
| LLM 模式保护 | FQ1/FQ2/FQ7/FQ8 等 cov 不低于修复前 |
| Answer Accuracy 保护 | 全量 PE2 无 PASS→FAIL 回归 |
| Hallucination | 仍为 0 |

### 验证步骤

1. 清库（`bash scripts/reset-lattice-schema.sh`）
2. 重新导入 Public Eval 2 资料并编译
3. 查询 PE2 全部 FALLBACK 题目，采集 citationCheck 数据
4. 对比修复前后的 coverageRate

---

## 10. 明确声明

- [x] 只修改了 `CitationValidator.java` 生产代码 + 测试文件
- [x] 未修改 `AnswerFallbackConclusionBuilder` / `AnswerCitationResolver` / `CitationExtractor`
- [x] 未修改 `schema.sql` / `Mapper XML` / `FactCardTerminalUnitRecord`
- [x] 未修改 `scripts` / `prompt` / `config` / `题集`
- [x] 未降低现有 overlap 阈值
- [x] 已使用 `sourceFileId` 约束，不跨 source file 误匹配
- [x] 不匹配时回退现有路径，不强制 VERIFIED
- [x] 未写入任何具体字段名、文件名、题号、答案值
- [x] 未提交 commit
- [x] redline `BLOCKER=0`
- [x] mvn test `1014/0/0/0, BUILD SUCCESS`

---

## 11. 补充修订：逐条 terminal unit 验证，禁止跨 unit 拼接

**修订时间**：2026-06-05

### 为什么需要修订

上一轮实现将同一 source file 下所有 terminal units 的 displayText/valueText/normalizedValue 拼接为一个大 evidenceText，然后整体计算 overlap。这导致：
- key/path token 来自 terminal unit A；
- value token 来自 terminal unit B；
- 拼接后 overlap 足够高；
- 但没有任何单条 terminal unit 真正同时覆盖 key/path 和 value。

### 修改摘要

1. **`validateAgainstTerminalUnitEvidence` 重构为逐条 unit 验证**：遍历每条 terminal unit，独立构建 evidence text，独立计算 overlap。只有同一条 unit 通过时才会 VERIFIED。
2. **新增 `isKeyValueClaim` guard**：仅对含 `=` 且两侧均有非空文本的 claim 启用 terminal unit 路径。非 key=value claim 直接返回 null，走原有 source overlap。
3. **新增 `claimValueMatchesUnit` 双层 value 检查**：
   - 第一层：直接字符串包含（归一化 claim value 是否等于/包含于 unit valueText）
   - 第二层：hard fact token 匹配（claim value 中的事实 token 是否全在 unit 值字段中）
   - 两层结合防止 "30s" 这类不产生 hard fact token 的值空过检查
4. **删除 `buildTerminalUnitEvidenceText`（跨 unit 拼接版）**，替换为 `buildSingleUnitEvidenceText`

### 新增测试

- `shouldNotVerifyTerminalUnitEvidenceByCombiningDifferentUnits` — 同 source file 两个 unit，一个覆盖 key，一个覆盖 value，逐条验证必须不通过
- `shouldSkipTerminalUnitEvidenceForNonKeyValueClaim` — 非 key=value claim 不走 terminal unit 路径
- 扩展 `FixedTerminalUnitJdbcRepository` 返回两条 unit

### 最新定向测试结果（以本修订为准）

```
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
```

### 最新全量 mvn test 结果（以本修订为准）

```
Tests run: 1016, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### redline 结果

`BLOCKER=0`

### 仍需 agentD runtime gate 验证

验证目标与第 9 节一致。
