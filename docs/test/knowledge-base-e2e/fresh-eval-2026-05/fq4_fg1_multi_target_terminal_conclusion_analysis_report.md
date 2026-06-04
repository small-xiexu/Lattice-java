# FQ4/FG1 多目标 Terminal Conclusion 完整性 — 只读分析报告

分析时间：2026-06-04
执行人：agentB（治理/链路分析 Agent）
类型：只读根因分析与通用修复设计，无代码修改

---

## 1. 结论：当前 FQ4/FG1 是否可标记完整 PASS

**否。** 当前 runtime gate 报告的 "FQ4: PASS / FG1: PASS" 仅代表 sibling 误选问题已修复（winner 选对了字段），但**不能标记为完整 PASS**。

| 题号 | 问题 | 当前 API 回答 | 遗漏 | 判定 |
|------|------|-------------|------|------|
| FQ4 | "常规设备和大型设备的押金分别是多少？" | `equipment_types[0].deposit_amount = 100` | `equipment_types[2].deposit_amount = 1000` | **PARTIAL_ANSWER（回答漏点）** |
| FG1 | "精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？" | `equipment_types[1].late_fee_per_day = 20` | `equipment_types[0].late_fee_per_day = 5` | **PARTIAL_ANSWER（回答漏点）** |

两题均只返回了一个 target entity 的值，遗漏了问题中明确询问的第二个 entity。

---

## 2. 根因分类

### 失败类型：**证据已召回但回答漏点 / 多目标 terminal unit 聚合缺失**

具体子类型：`buildTerminalUnitExactConclusionLines` 的单 bestCandidate 返回策略无法覆盖多目标问题。

### 排除的类型

| 失败类型 | 排除证据 |
|----------|----------|
| 检索未召回 | `deposit_amount=1000` 在 builder 候选池 cand#5（ftmc=5）；`late_fee_per_day=5` 在 builder 候选池 cand#5（ftmc=3） |
| 编译抽取缺失 | terminal unit 数据完整入库（deposit_amount: 100/500/1000; late_fee_per_day: 5/20/50） |
| Rerank 排序低 | 两题的遗漏候选均 qf=true，已通过 query-focused 筛选 |
| Enricher 未生效 | 中文别名已生成（"押金金额"、"逾期日费"等），0 条 401 错误 |
| Candidate supply 截断 | 两题的遗漏候选均在 builder 候选池中，未被 limit 截断 |
| Sibling tie-break | FQ4 的 winner 已正确从 `approval_required` 切换为 `deposit_amount=100`；FG1 的 winner 保持 `late_fee_per_day=20` |

---

## 3. 关键源码链路说明

### 3.1 单 bestCandidate 返回位置

**文件**：`AnswerFallbackConclusionBuilder.java`，第 326-391 行

`buildTerminalUnitExactConclusionLines` 方法的核心逻辑：

```java
// 第 343-379 行：遍历所有 fallbackHits
for (QueryArticleHit fallbackHit : fallbackHits) {
    if (!isTerminalUnitChannelHit(fallbackHit)) continue;
    // ...计算 qf, ftmc, atmc, fs...
    if (!qf) continue;
    // 单 bestCandidate 选择：
    if (fieldTokenMatchCount > bestFieldTokenMatchCount
        || (fieldTokenMatchCount == bestFieldTokenMatchCount
            && aliasTokenMatchCount > bestAliasTokenMatchCount)
        || (...)) {
        bestCandidate = fallbackHit;  // ← 只保留一个
    }
}

// 第 387-390 行：只返回一条结论行
return List.of("Confirmed evidence: "
    + bestExactLine + " " + support.joinConclusionCitations(List.of(bestCandidate)));
```

**关键观察**：整个方法的设计目标就是选出**唯一最佳候选**并返回**单行结论**。没有任何逻辑考虑"可能有多个候选都正确且应该一起返回"。

### 3.2 调用链位置

```
AnswerFallbackMarkdownBuilder.buildEvidenceMarkdown()
  → appendEvidenceConclusion()
    → support.buildEvidenceConclusionLines(question, fallbackHits, queryTokens, queryArticleHits)
      → AnswerGenerationFallbackConclusionSupport.buildEvidenceConclusionLines()
        → answerFallbackConclusionBuilder.buildEvidenceConclusionLines()
          → buildGeneralFallbackConclusionLines()       [第 214 行]
            → buildTerminalUnitExactConclusionLines()   [第 255 行] ← 断点
```

### 3.3 上游链路均正常

```
compile → Materializer → Enricher → DB (field_aliases_json, fts_text 含中文别名)
  → Query → FTS Search → Reranker → RRF Fusion → Fallback Evidence Selector
    → Conclusion Builder (候选池中有多个正确候选)
      → buildTerminalUnitExactConclusionLines (只选一个) ← 唯一断点
```

---

## 4. FQ4/FG1 Runtime 证据引用

来源：`fq4_tie_break_runtime_gate_report.md`

### 4.1 FQ4 现场

```
Query: equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？

cand#3 el=equipment_types[0].deposit_amount = 100   qf=true ftmc=5 atmc=3 fs=9.0  ← WINNER
cand#5 el=equipment_types[2].deposit_amount = 1000  qf=true ftmc=5 atmc=2 fs=3.0  ← 遗漏！

API: Confirmed evidence: equipment_types[0].deposit_amount = 100
```

`cand#5` 的 `ftmc=5`（与 winner 相同），`qf=true`，完全应该被包含在回答中。但它被 single-best-candidate 算法丢弃了，只因为 `atmc=2 < 3` 且 `fs=3.0 < 9.0`。

### 4.2 FG1 现场

```
Query: equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？

cand#5 el=equipment_types[0].late_fee_per_day = 5   qf=true ftmc=3 atmc=3 fs=6.0  ← 遗漏！
cand#6 el=equipment_types[1].late_fee_per_day = 20  qf=true ftmc=5 atmc=3 fs=5.0  ← WINNER

API: Confirmed evidence: equipment_types[1].late_fee_per_day = 20
```

`cand#5` 的 `ftmc=3`（略低于 winner 的 5），`qf=true`，对应的是问题中"常规设备"的逾期罚金。它被丢弃只因为 `ftmc=3 < 5`。

### 4.3 关键结构信号

两个现场共享同一个结构模式：

| 信号 | FQ4 | FG1 |
|------|-----|-----|
| 遗漏候选的 terminalKey | `deposit_amount` | `late_fee_per_day` |
| winner 的 terminalKey | `deposit_amount` | `late_fee_per_day` |
| terminalKey 相同？ | **是** | **是** |
| parentPath 不同？ | `equipment_types[0]` vs `[2]` | `equipment_types[0]` vs `[1]` |
| 遗漏候选 qf | true | true |
| 遗漏候选 ftmc | 5（与 winner 相同） | 3（winner=5） |

**通用结构信号**：同一 `terminalKey` 在多个 `parentPath` 下出现，且每个候选都通过 `qf=true`，说明用户问题询问的是同一个字段在不同实体上的值。

---

## 5. 通用修复设计

### 5.1 设计原则

- **零 case 特判**：不写入 FQ4、FG1、deposit_amount、late_fee_per_day、常规设备、大型设备、精密仪器等任何样例专属分支
- **纯结构信号**：只使用 terminalKey、parentPath、fieldTokenMatchCount、qf 等通用字段
- **向后兼容**：单目标问题行为不变（只返回一条结论行）
- **最小改动面**：只修改 `buildTerminalUnitExactConclusionLines` 一个方法的返回逻辑

### 5.2 核心思路

当前算法选出 winner 后立即返回单行。修改为：选出 winner 后，再从剩余 qf-passing 候选中收集**同 terminalKey + 不同 parentPath + 足够 fieldTokenMatchCount** 的候选，一并返回。

### 5.3 算法概要

```
阶段 1（不变）：遍历所有 fallbackHits，找到 bestCandidate（现有逻辑完全保留）

阶段 2（新增）：如果 bestCandidate != null：
  a. 提取 winnerTerminalKey（从 bestCandidate 的 metadataJson 中）
  b. 二次遍历 qf-passing 候选：
     - 跳过 bestCandidate 本身
     - 跳过 terminalKey 与 winner 不同的候选
     - 跳过 parentPath 与 winner 相同的候选（同 entity 去重）
     - 跳过 fieldTokenMatchCount < minThreshold 的候选
  c. 将符合条件的候选加入 additionalCandidates 列表
  d. 按 fieldTokenMatchCount desc, aliasTokenMatchCount desc 排序
  e. 返回多条 "Confirmed evidence" 行：
     - 第 1 行：bestCandidate 的 exactLine + citation
     - 第 2..N 行：每个 additionalCandidate 的 exactLine + citation
```

### 5.4 最小阈值设计

`minThreshold` 用于防止无关 sibling 被误纳入。建议：

```java
int minThreshold = Math.max(1, bestFieldTokenMatchCount / 2);
```

含义：只有 token 匹配数不低于 winner 一半的候选才被纳入。理由：
- FQ4：winner ftmc=5，minThreshold=2，遗漏候选 ftmc=5 >= 2 ✓
- FG1：winner ftmc=5，minThreshold=2，遗漏候选 ftmc=3 >= 2 ✓
- 无关 sibling（如 `approval_required` ftmc=0 或 `type` ftmc=2）：可能被阈值过滤

### 5.5 terminalKey 和 parentPath 提取

从 `QueryArticleHit.metadataJson` 中提取（已有 JSON 解析基础设施在 `buildFieldLevelHaystack`、`buildFieldAliasesHaystack` 等方法中）：

- `terminalKey`：`metadataJson.path("terminalKey").asText("")`
- `parentPath`：`metadataJson.path("parentPath").asText("")`

这两个字段在 terminal unit 的 metadataJson 中已经存在（由 `FactCardTerminalUnitMapper.xml` 的 `searchLexical` SQL 填充）。

### 5.6 返回格式

```
Confirmed evidence: equipment_types[0].deposit_amount = 100 [→ equipment-borrowing-policy.yaml]
Confirmed evidence: equipment_types[2].deposit_amount = 1000 [→ equipment-borrowing-policy.yaml]
```

每条结论行保持现有格式（`exactLine + citation`），只是从 1 行变为 N 行。

---

## 6. 风险与保护用例

### 6.1 单目标问题保护

| 场景 | 候选情况 | 预期行为 | 是否受影响 |
|------|----------|----------|-----------|
| "精密仪器的逾期罚金是多少？" | 只有 `equipment_types[1].late_fee_per_day=20` 有高 ftmc，其他 parentPath 的同 terminalKey 候选 ftmc 低于阈值 | 只返回 1 行 | **不受影响** |
| "设备的 API 地址是什么？" | 只有一个 `api_endpoint` terminal unit（所有 entity 共享同一值） | 只返回 1 行（同 terminalKey 但同 parentPath 去重） | **不受影响** |
| FQ3 "精密仪器的单次最长借用天数是多少？" | `max_borrow_days` 在多个 parentPath 下存在，但只有精密仪器相关候选有高 ftmc | 只返回 1 行 | **不受影响** |

**保护机制**：`minThreshold = max(1, bestFtmc/2)` 确保只有与 winner 足够相关的候选才被纳入。单目标场景下，其他 entity 的同名字段 token 匹配数通常远低于 winner。

### 6.2 误纳入无关 sibling 的风险

| 风险 | 缓解措施 |
|------|----------|
| 同 terminalKey 但完全不相关的 entity 被纳入 | `qf=true` + `ftmc >= minThreshold` 双重过滤 |
| 同 parentPath 的重复候选被纳入 | `parentPath` 去重 |
| 大量候选导致结论行爆炸 | 可设上限（如最多 5 条），但当前数据集规模小，暂不必要 |

### 6.3 Citation 准确性

每条结论行独立携带自己的 citation（`support.joinConclusionCitations(List.of(candidate))`），不会因为多行输出而导致 citation 混淆。

### 6.4 Answer Outcome 影响

当前两题的 `answerOutcome` 分别为 `PARTIAL_ANSWER`（FQ4）和 `SUCCESS`（FG1）。修复后：
- FQ4 应保持 `PARTIAL_ANSWER`（因为问题仍未被 LLM 完全回答，只是 fallback 证据更完整）
- FG1 从 `SUCCESS` 变为 `PARTIAL_ANSWER`（更诚实地反映 fallback 模式）

outcome 由 `AnswerGenerationFallbackOutcomeSupport` 独立判断，不受 conclusion 行数影响。

### 6.5 与现有 compare/contrast 路径的关系

`buildEvidenceConclusionLines` 中已有的 `extractComparisonOptions` + `buildComparisonFallbackConclusionLines` 路径（第 61-72 行）当前被禁用（`extractComparisonOptions` 返回空列表）。本轮修复不涉及此路径，不重新启用它。

---

## 7. 建议交给 agentA 的最小代码修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
修复 AnswerFallbackConclusionBuilder.buildTerminalUnitExactConclusionLines 的单 bestCandidate
返回策略，使其能返回多条结论行以覆盖多目标问题。

修改范围：
- 只修改 src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java
- 只修改 buildTerminalUnitExactConclusionLines 方法（第 326-391 行）
- 不改其他方法、不改其他文件

修改要求：
1. 在现有 bestCandidate 选择循环之后，新增"同 terminalKey 不同 parentPath"的附加候选收集逻辑
2. 从 bestCandidate 的 metadataJson 中提取 terminalKey 和 parentPath
3. 二次遍历所有 qf-passing 候选，收集满足以下条件的附加候选：
   - terminalKey 与 winner 相同
   - parentPath 与 winner 不同
   - fieldTokenMatchCount >= max(1, bestFieldTokenMatchCount / 2)
4. 将附加候选按 fieldTokenMatchCount desc, aliasTokenMatchCount desc 排序
5. 返回多条 "Confirmed evidence: exactLine citation" 行而非单行
6. 新增一个私有 helper 方法 extractTerminalKey(QueryArticleHit) 从 metadataJson 提取 terminalKey

通用性要求：
- 不写入 FQ4、FG1、deposit_amount、late_fee_per_day、精密仪器、常规设备、大型设备 等任何样例专属字符串
- 不检测 "分别是"、"分别" 等中文模式
- 纯结构信号驱动：terminalKey + parentPath + fieldTokenMatchCount
- 单目标问题行为不变（仍只返回 1 行）

禁止事项：
- 禁止修改 qf/ftmc/atmc 计算逻辑
- 禁止修改 isTerminalHitQueryFocused
- 禁止修改 countFieldLevelTokenMatches / countFieldAliasTokenMatches
- 禁止修改 extractTerminalUnitExactLine
- 禁止修改 AnswerFallbackMarkdownBuilder
- 禁止修改 FactCardTerminalUnitFtsSearchService
- 禁止修改 FactCardTerminalUnitIntentReranker
- 禁止修改 tests、scripts、prompt、config、题集
- 禁止提交 commit

验证要求（交给 agentD）：
1. redline BLOCKER=0
2. mvn test 全量通过
3. FQ4 API 回答包含 deposit_amount=100 和 deposit_amount=1000 两条结论
4. FG1 API 回答包含 late_fee_per_day=20 和 late_fee_per_day=5 两条结论
5. FQ3（单目标）保护回归：仍只返回一条 max_borrow_days 结论
6. FQ6（单目标）保护回归：不受影响
```

---

## 8. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未跑 mvn test / redline / baseline
- [x] 未读取 hidden eval
- [x] 所有结论基于源码只读分析 + runtime gate 报告交叉验证
- [x] 通用修复设计中无 case 特判
- [x] 修复提示词草案中无样例专属字符串
