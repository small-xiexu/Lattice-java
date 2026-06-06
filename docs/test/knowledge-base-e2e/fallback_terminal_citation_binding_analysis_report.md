# FALLBACK Terminal Unit Citation Binding — 只读根因分析报告

分析时间：2026-06-05
执行人：agentB（治理/链路分析 Agent）
类型：只读根因分析，无代码修改

---

## 1. 当前现象复述

**来源**：`recall_citation_metrics_collection_report.md`（2026-06-05 agentD 专项采集）

PE2 FALLBACK 模式的 `citationCheck.coverageRate` 表现不一致：

| 题号 | 答案 | mode | covRate | verified | demoted | claims |
|------|------|------|:---:|:---:|:---:|:---:|
| FQ3 | `max_borrow_days=7` | FALLBACK | **1.0** | 1 | 0 | 1 |
| FQ5 | `api_endpoint=https://...` | FALLBACK | **1.0** | 1 | 0 | 1 |
| FQ6 | `version=v2.3.1` | FALLBACK | **1.0** | 1 | 0 | 1 |
| **FG1** | `late_fee_per_day=20+5` | FALLBACK | **0.5** | 1 | 1 | 2 |
| **FQ4** | `deposit_amount=100+1000` | FALLBACK | **0.0** | 0 | 2 | 2 |
| **FG2** | `max_concurrent_requests=50` | FALLBACK | **0.0** | 0 | 1 | 1 |

**关键现象**：答案全部正确（人工验证通过），但 citation 验证覆盖率从 0.0 到 1.0 不等。所有 citation 均指向同一源文件 `equipment-borrowing-policy.yaml`，validation_status 全部为 `RULE`（非 LLM），source_type 全部为 `SOURCE_FILE`。

---

## 2. 数据库只读证据（根因定位关键）

从 `lattice.query_answer_citations` 表直接查询每一条 citation 的验证结果：

### 2.1 VERIFIED 案例（cov=1.0）

| claim | overlap | matched_excerpt | reason |
|-------|:---:|------|------|
| `borrowing_system.api_endpoint = https://...` | 0.8462 | `borrowing_system:` | source_near_complete_overlap_verified |
| `borrowing_system.version = v2.3.1` | 0.7500 | `borrowing_system:` | source_near_complete_overlap_verified |
| `equipment_types[1].max_borrow_days = 7` | 0.6667 | `version: "v2.3.1"` | source_near_complete_overlap_verified |
| `equipment_types[1].late_fee_per_day = 20` | 0.6667 | `version: "v2.3.1"` | source_near_complete_overlap_verified |

### 2.2 DEMOTED 案例（cov=0.0 或 0.5 中的失败项）

| claim | overlap | matched_excerpt | reason |
|-------|:---:|------|------|
| `equipment_types[0].deposit_amount = 100` | 0.5000 | `max_concurrent_requests: 50` | source_insufficient_overlap |
| `equipment_types[2].deposit_amount = 1000` | 0.5000 | `api_endpoint: "https://..."` | source_insufficient_overlap |
| `equipment_types[0].late_fee_per_day = 5` | 0.5000 | `max_concurrent_requests: 50` | source_insufficient_overlap |
| `borrowing_system.max_concurrent_requests = 50` | 0.6000 | `borrowing_system:` | source_insufficient_overlap |

### 2.3 核心发现

**CitationValidator 将 terminal unit 的 flat key=value claim（如 `equipment_types[0].deposit_amount = 100`）与原始 YAML 源文件的嵌套内容做逐句 token 重叠比对。匹配到的 excerpt 与 claim 实际指向的字段不一致。**

例如：
- Claim 是 `deposit_amount = 100`，validator 在源文件中匹配到的"最佳"句子是 `max_concurrent_requests: 50`（重叠分 0.5）
- Claim 是 `late_fee_per_day = 5`，匹配到的"最佳"句子也是 `max_concurrent_requests: 50`（重叠分 0.5）

这不是 validator 的 bug——它正确地在源文件中找到了与 claim token 重叠度最高的句子。问题在于：**terminal unit 的 flat key=value 格式与原始 YAML 的嵌套结构在 token 层面存在系统性的语义失配**。验证通过与否取决于 claim token 与源文件中某个随机行的**偶然重叠度**是否超过阈值（0.6667），而非取决于是否找到了正确的证据行。

---

## 3. 相关源码链路

### 3.1 Citation 构造链

```
AnswerFallbackConclusionBuilder.buildTerminalUnitExactConclusionLines()
  → support.joinConclusionCitations(List.of(hit))
    → resolveConclusionCitationLiteral(hit, [hit])
      → hit.evidenceType == FACT_CARD
        → resolveFactCardSourceCitationLiteral(hit, [hit])
          → 在 candidateHits 中找 SOURCE 类型命中（但列表只有 FACT_CARD hit）
          → 找不到 → 返回 ""
        → 回退到 resolveSourceCitationLiteral(hit)
          → 使用 hit.getSourcePaths() → "[→ equipment-borrowing-policy.yaml]"
```

**关键文件**：
- `AnswerFallbackConclusionBuilder.java` 第 387-391 行 — citation 拼接
- `AnswerCitationResolver.java` 第 118-139 行 — `resolveConclusionCitationLiteral()`
- `AnswerGenerationFallbackComparisonSupport.java` 第 261-274 行 — `joinConclusionCitations()`

### 3.2 Citation 验证链

```
CitationCheckService.check(answerMarkdown, projectionBundle)
  → CitationExtractor.extractClaims(answerMarkdown)
    → 解析 "## 证据" 节中的 bullet line "- Confirmed evidence: X = Y [→ file]"
    → 产生 ClaimSegment（claimText="Confirmed evidence: X = Y", citation="[→ file]"）
  → for each ClaimSegment.citations:
      → CitationValidator.validate(citation, claimSegment)
        → 按 targetKey 查找 source_file
        → 提取 claimText 中的 hard-fact token（数字、snake_case、Latin 词等）
        → 在 source_file 内容中逐句计算 token overlap
        → overlap >= 阈值 → VERIFIED
        → overlap < 阈值 → DEMOTED（reason: source_insufficient_overlap）
  → coverageRate = coveredClaimCount / totalClaimCount
```

**关键文件**：
- `CitationCheckService.java` 第 60-126 行 — `check()` 和 coverageRate 计算
- `CitationValidator.java` 第 69-262 行 — `validate()` 和 overlap 评分
- `CitationExtractor.java` 第 36-81 行 — `extractClaims()`

### 3.3 coverageRate 口径

```java
// CitationCheckService.java 第 111 行
double coverageRate = claimSegments.isEmpty() ? 0.0D 
    : coveredClaimCount * 1.0D / claimSegments.size();
```

- **分子**：`coveredClaimCount` — 至少有一个 VERIFIED 或 SKIPPED-with-no-hard-fact citation 的 claim 数
- **分母**：`claimSegments.size()` — 从 answer markdown 中提取的总 claim 数
- DEMOTED 不计入 covered，直接拉低 coverageRate

---

## 4. FQ3/FQ5/FQ6 vs FQ4/FG1/FG2 差异对比

| 维度 | FQ3/FQ5/FQ6 (cov=1.0) | FQ4/FG1/FG2 (cov=0.0–0.5) |
|------|------|------|
| 结论行数 | 1 条 | 1–2 条 |
| citation 格式 | `[→ equipment-borrowing-policy.yaml]` | 完全相同 |
| validation_status | VERIFIED | DEMOTED |
| overlap_score | 0.67–0.85 | 0.50–0.60 |
| matched_excerpt | 与 claim 无关的源文件行（如 `borrowing_system:`、`version: "v2.3.1"`） | 与 claim 无关的源文件行（如 `max_concurrent_requests: 50`） |
| 验证通过原因 | 偶然高重叠度（碰巧超过 0.6667 阈值） | 重叠度不足（低于阈值） |

**差异本质不是代码逻辑不同，而是 token overlap 的随机性。** FQ3/FQ5/FQ6 的 claim token 偶然与源文件中某些行产生了更高的 token 重叠分，碰巧过了阈值。FQ4/FG1/FG2 的 claim token 没有这种偶然性。

---

## 5. 根因判断

### 根因：**CitationValidator 对 FACT_CARD / terminal unit 证据的验证策略与证据格式不匹配**

具体来说：

1. **Terminal unit conclusion 的 citation 指向源文件**（`[→ equipment-borrowing-policy.yaml]`），validator 在**原始源文件**中搜索匹配句子
2. **源文件是嵌套 YAML 结构**（如 `equipment_types:` → `- type: 常规设备` → `deposit_amount: 100`）
3. **Claim 文本是 flat key=value 格式**（如 `equipment_types[0].deposit_amount = 100`）
4. **两者在 token 层面的重叠是偶然的、不可靠的**——validator 在源文件中找到的"最佳匹配"句子往往与 claim 指向的实际字段无关
5. **验证通过与否取决于偶然重叠度是否超过 0.6667 阈值**，而非取决于是否找到了正确的证据

这不是 CitationValidator 的 bug——它正确地执行了"在目标文件中搜索最佳匹配句子"的职责。问题在于：**terminal unit 的结构化证据（display_text、value_text、field_label、field_aliases）存储在 `fact_card_terminal_units` 表和 `QueryArticleHit.content/metadataJson` 中，但 validator 不会查询这些数据源**。

### 排除项

| 候选根因 | 判定 | 理由 |
|----------|:---:|------|
| conclusion line 缺 citation | **排除** | 所有 terminal unit 结论行均携带 `[→ file]` citation |
| citation 格式错误 | **排除** | `[→ path]` 格式正确，CitationExtractor 能正确解析 |
| projection white-list 缺失 | **排除** | validation_status 全部为 `RULE`（非 projection 路径），validated_by 不是 projection |
| 源文件不存在 | **排除** | targetKey 指向的源文件存在，validator 能读取 |
| 多目标输出导致 claim 解析错误 | **排除** | 单目标 FG2 同样 cov=0.0；多目标 FG1 有 1/2 通过 |
| 答案文本无 hard-fact token | **排除** | 所有 claim 均包含数字和 snake_case token |

---

## 6. 候选修复方案对比

| # | 方案 | 层面 | 改动面 | 是否消除偶然性 | 副作用 |
|---|------|:---:|:---:|:---:|------|
| **A** | **CitationValidator 增加 terminal unit 证据查找路径**：当 citation 指向源文件且 claim 来自 FACT_CARD 时，同步查询 `fact_card_terminal_units` 表，用 `display_text`、`value_text`、`field_label` 做 token overlap | 查询期 | 中（CitationValidator + 新增 TerminalUnitEvidenceLookup） | **是** | 需新增 DB 查询；每次 citation 验证多一次查询 |
| B | 将 terminal unit 的 `display_text` 写入源文件的索引/内容中 | 编译期 | 中（Materializer + search_tsv） | 部分 | 污染源文件内容语义；影响 FTS 检索质量 |
| C | 将 terminal unit 结论的 citation 改为直接引用 terminal unit 自身 identity（如 `[[terminal-unit:...]]`），validator 新增该 target 类型 | 查询期 | 大（需新增 citation target 类型 + validator 分支） | **是** | 需改 CitationExtractor、CitationValidator、projection builder |
| D | 对 FALLBACK 模式设置 citation_coverage 不参与 Answer Accuracy 判定 | 口径层 | 小 | 否（绕过问题） | 掩盖真实 citation 质量；违反质量工程原则 |
| E | 降低 CitationValidator 的 overlap 阈值（如从 0.6667 降到 0.5） | 查询期 | 极小 | 否（降低门槛让更多误匹配通过） | 增加假阳性 citation；降低整体 citation 质量 |

### 推荐方案：A

**CitationValidator 增加 terminal unit 证据查找路径**，是唯一能从根本上消除偶然性的方案。当验证一个指向源文件的 citation 时，如果 claim 包含结构化 key=value 模式（如 `field_path = value`），额外查询 `fact_card_terminal_units` 表，用 terminal unit 的 `display_text`、`value_text` 与 claim 做精确/高重叠匹配。

### 为什么不是 case 特判

| 检查项 | 判定 |
|--------|:---:|
| 是否检测具体字段名（如 deposit_amount）？ | 否 |
| 是否检测具体文件名？ | 否 |
| 是否检测 eval 题号？ | 否 |
| 匹配信号是什么？ | claim 文本格式（`key_path = value`）和 citation target 类型（SOURCE_FILE） |
| 对所有 terminal unit 是否一视同仁？ | **是**——所有 FACT_CARD 来源的 citation 均受益 |

---

## 7. 潜在副作用

| 副作用 | 评估 | 缓解 |
|--------|------|------|
| 每次 FACT_CARD citation 验证多一次 DB 查询 | 性能影响小（citation 验证本身不是高频操作） | 可在 CitationValidator 中做批量预加载 |
| 可能让 LLM 模式的 FACT_CARD citation 也走 terminal unit 验证 | LLM 模式的 FACT_CARD citation 指向 fact_card 本身而非 terminal unit | 通过检查 claim 文本是否匹配 `key = value` 模式来限定 |
| 与其他 citation 验证路径的交互 | 新增路径是附加的，不影响现有 source/article 验证 | fallback 逻辑：terminal unit 匹配失败时回退到现有 source file 验证 |

---

## 8. agentA 下一轮修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
修复 CitationValidator 对 FACT_CARD terminal unit citation 的验证不可靠问题。
当前 terminal unit 结论行携带 [→ source_file] citation，但 validator 只在原始
源文件中逐句搜索 token overlap。源文件的嵌套结构与 terminal unit 的 flat key=value
格式存在系统性语义失配，导致验证结果依赖偶然 token 重叠度，cov 在 0.0 到 1.0
之间随机波动。

修改范围：
- 允许修改 src/main/java/com/xbk/lattice/query/citation/CitationValidator.java
- 允许修改 src/main/java/com/xbk/lattice/infra/persistence/FactCardTerminalUnitJdbcRepository.java
  （如需新增只读查询方法）
- 允许修改 src/main/java/com/xbk/lattice/infra/persistence/mapper/FactCardTerminalUnitMapper.xml
  （如需新增 SQL）
- 不改其他文件

修改要求：
1. 在 CitationValidator.validate() 中，当 citation.targetType == SOURCE_FILE 且
   claim 文本匹配通用 key=value 模式（如 "field_path = value"）时，新增 terminal
   unit 证据查找路径
2. 通过 FactCardTerminalUnitJdbcRepository 查询与 source_file 关联的 terminal unit
   记录，用 claim 中的 value_text 或 display_text 与 terminal unit 的 value_text/
   display_text 做精确匹配或高重叠匹配
3. 如果 terminal unit 匹配成功（精确值匹配或高 overlap），返回 VERIFIED
4. 如果 terminal unit 匹配失败，回退到现有的 source file 逐句 overlap 验证
5. 保持现有 RULE 验证路径不变（非 terminal unit 的 citation 不受影响）

通用性要求：
- 不检测具体字段名（deposit_amount、late_fee_per_day 等）
- 不检测具体文件名（equipment-borrowing-policy.yaml 等）
- 不检测具体 eval 题号
- key=value 模式检测基于通用正则/格式规则
- 对所有 FACT_CARD source citation 一视同仁

禁止事项：
- 禁止修改 AnswerFallbackConclusionBuilder
- 禁止修改 AnswerCitationResolver
- 禁止修改 CitationExtractor / CitationCheckService
- 禁止修改 schema / tests / scripts / prompt / config / 题集
- 禁止降低现有 overlap 阈值
- 禁止提交 commit

redline / mvn test 要求：
- redline BLOCKER=0
- mvn test 全量通过
- CitationValidatorTests 现有测试不应有回归

验证计划（交给 agentD）：
1. PE2 FALLBACK 题目（FQ3-FQ6, FG1-FG2）全部重新查询
2. FQ4/FG2 的 citation_coverage 应从 0.0 提升到 1.0
3. FG1 的 citation_coverage 应从 0.5 提升到 1.0
4. FQ3/FQ5/FQ6 保持 cov=1.0（无回归）
5. LLM 模式题目的 citation_coverage 无回归
```

---

## 9. agentD 验证建议

| 验证项 | 方法 | 通过标准 |
|--------|------|----------|
| Redline | `bash scripts/scan-redline.sh` | BLOCKER=0 |
| mvn test | 全量 | Failures=0, Errors=0 |
| FQ4 citation | API → citationCheck.coverageRate | =1.0 |
| FG1 citation | API → citationCheck.coverageRate | =1.0 |
| FG2 citation | API → citationCheck.coverageRate | =1.0 |
| FQ3/FQ5/FQ6 保护 | API → citationCheck.coverageRate | =1.0（无回归） |
| LLM 模式保护 | FQ1/FQ2/FQ7/FQ8 等 | cov 不低于修复前 |
| Answer Accuracy 保护 | 全量 PE2 | 无 PASS→FAIL 回归 |

---

## 10. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 所有结论基于源码只读分析 + 数据库 citation 表直接查询 + 报告交叉验证
- [x] 数据库证据确凿：每条 citation 的 validation_status、overlap_score、matched_excerpt、reason 均已查证
- [x] 推荐方案为通用 key=value claim 格式 + FACT_CARD citation 类型匹配，无 case 特判
- [x] 未将具体题面、答案、文件名写入生产代码建议
