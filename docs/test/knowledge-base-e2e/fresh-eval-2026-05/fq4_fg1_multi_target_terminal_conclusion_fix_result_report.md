# FQ4/FG1 多目标 Terminal Conclusion 聚合修复 — 修复结果报告

修复时间：2026-06-04
执行人：agentA（代码执行 Agent）
前置分析：`fq4_fg1_multi_target_terminal_conclusion_analysis_report.md`（agentB）
前置 runtime gate：`fq4_tie_break_runtime_gate_report.md`（agentD）

---

## 1. 根因确认

**FQ4 和 FG1 当前不是完整 PASS。**

断点在 `AnswerFallbackConclusionBuilder.buildTerminalUnitExactConclusionLines`（第 326-391 行）的**单 bestCandidate 返回策略**。整个方法的设计目标就是选出唯一最佳候选并返回单行结论，没有任何逻辑考虑"可能有多个候选都正确且应该一起返回"。

### FQ4 现场（来自 agentD runtime gate）

```
Winner:  equipment_types[0].deposit_amount = 100  (ftmc=5, atmc=3, fs=9.0)
遗漏:    equipment_types[2].deposit_amount = 1000 (ftmc=5, atmc=2, fs=3.0)
```

`cand#5` 的 `ftmc=5`、`qf=true`、`terminalKey` 与 winner 相同但 `parentPath` 不同——完全应该被包含，但被算法丢弃。

### FG1 现场（来自 agentD runtime gate）

```
Winner:  equipment_types[1].late_fee_per_day = 20 (ftmc=5, atmc=3, fs=5.0)
遗漏:    equipment_types[0].late_fee_per_day = 5  (ftmc=3, atmc=3, fs=6.0)
```

`cand#5` 的 `ftmc=3 >= max(1, 5/2)=2`、`qf=true`、同 `terminalKey` 不同 `parentPath`，完全应该被包含。

### 上游链路均正常

```
compile → Materializer → Enricher → DB
  → Query → FTS Search → Reranker → RRF → Fallback Evidence Selector
    → Conclusion Builder (候选池中多个正确候选)
      → buildTerminalUnitExactConclusionLines (只选一个) ← 唯一断点
```

### 本轮不处理的事项

- retrieval / reranker / candidate supply — 已排除
- enricher bootstrap guard — 已修复
- qf / ftmc / atmc 计算语义 — 不变
- sibling tie-break — 已修复（atmc 排序）
- fallback outcome 判定 — 不变

---

## 2. 修改文件与修改范围

**文件**：`src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

**修改范围**：
- 新增 import：`java.util.LinkedHashMap`、`java.util.Map`
- 修改 `buildTerminalUnitExactConclusionLines` 方法：在 bestCandidate 选择后追加 Phase 2 附加候选收集
- 新增 7 个辅助方法/类：
  - `extractTerminalKey(QueryArticleHit)` — 从 metadataJson 提取 terminalKey
  - `extractParentPath(QueryArticleHit)` — 从 metadataJson 提取 parentPath
  - `extractMetadataTextField(QueryArticleHit, String)` — 通用 metadataJson 字段提取
  - `entityContextMatchesQuery(QueryArticleHit, List<String>)` — 实体上下文匹配
  - `buildEntityContextHaystack(QueryArticleHit)` — 构建实体上下文文本
  - `deduplicateByParentPath(List<CandidateProfile>)` — parentPath 去重
  - `selectTopAdditionalCandidates(List<CandidateProfile>, int)` — top-N 选取
  - `CandidateProfile` — private static final 内部类

**未修改**：`qf`、`ftmc`、`atmc`、`fusedScore` 的计算语义完全不变。Phase 1 的 bestCandidate 选择逻辑完全不变。

---

## 3. 关键实现说明

### 3.1 两阶段算法

```
Phase 1（不变）：遍历所有 fallbackHits，按 ftmc → atmc → fusedScore 选择 bestCandidate

Phase 2（新增，仅在 bestCandidate != null 时执行）：
  a. 从 bestCandidate 的 metadataJson 提取 winnerTerminalKey 和 winnerParentPath
  b. 二次遍历所有 fallbackHits，收集满足全部条件的附加候选：
     - terminal unit channel hit
     - 不是 bestCandidate 本身
     - exactLine 非空
     - qf=true
     - terminalKey == winnerTerminalKey
     - parentPath != winnerParentPath
     - fieldTokenMatchCount >= max(1, bestFieldTokenMatchCount / 2)
     - entityContextMatchesQuery（entity context 命中 queryTokens）
  c. 按 parentPath 去重（同 parentPath 只保留 ftmc 最高的）
  d. 按 ftmc desc, atmc desc 排序，截取 top 4
  e. 返回多条 "Confirmed evidence: exactLine citation"
```

### 3.2 实体上下文匹配

`entityContextMatchesQuery` 构建的 haystack 包含：
- `hit.getContent()`（terminal unit 的 FTS 文本，含 fact card 实体级上下文）
- `metadataJson.parentPath`
- `metadataJson.contextPath`
- `metadataJson.displayText`
- `metadataJson.pathSegments` 数组元素

**显式排除**：`fieldAliases`、`fieldDescription`——这两个字段代表字段语义而非实体信号，会让同 terminalKey 的非目标实体（如精密仪器的 deposit_amount）误入。

### 3.3 minThreshold 设计

```java
int minThreshold = Math.max(1, bestFieldTokenMatchCount / 2);
```

- FQ4：winner ftmc=5 → 阈值=2，遗漏候选 ftmc=5 >= 2 ✓
- FG1：winner ftmc=5 → 阈值=2，遗漏候选 ftmc=3 >= 2 ✓
- 单目标问题：非目标实体的同名字段 ftmc 通常远低于 winner，被阈值过滤 ✓

### 3.4 输出爆炸防护

- 附加候选最多 4 条（通过 `selectTopAdditionalCandidates` 限制）
- 总 conclusion line 最多 5 条（1 条 bestCandidate + 4 条附加）
- parentPath 去重防止同 entity 多值重复输出

### 3.5 winnerTerminalKey 为空时的保护

当 winner 的 metadataJson 中没有 terminalKey 时，跳过 Phase 2，直接返回单行结论。单目标行为完全不变。

---

## 4. 为什么不是 case 特判

- `terminalKey`、`parentPath` 是 terminal unit 数据模型中的通用字段，对所有事实卡类型生效
- `entityContextMatchesQuery` 使用与 `isTerminalHitQueryFocused` 完全相同的通用匹配规则（子串包含 + CJK bigram 重叠）
- 不依赖任何具体业务词、文件名、字段名、文档标题、样例答案
- 不检测"分别是"、"分别"等中文问法关键词
- 对所有多实体 query + 所有 terminal unit 一视同仁地生效
- 只使用纯结构信号：terminalKey、parentPath、fieldTokenMatchCount、entity context

---

## 5. 单目标问题如何保护

| 场景 | 预期行为 | 保护机制 |
|------|----------|----------|
| FQ3 "精密仪器的单次最长借用天数是多少?" | 仍只返回 1 行 max_borrow_days | 其他 equipment_types 的 max_borrow_days 实体上下文不匹配"精密仪" |
| "精密仪器的逾期罚金是多少?" | 仍只返回 1 行 late_fee_per_day=20 | 其他 equipment_types 的实体上下文不匹配 |
| winnerTerminalKey 为空 | 仍只返回 1 行 | Phase 2 被 `winnerTerminalKey.isEmpty()` 跳过 |
| 所有附加候选 ftmc < minThreshold | 仍只返回 1 行 | Phase 2 收集到的列表为空 |
| 附加候选 entityContextMatchesQuery 全 false | 仍只返回 1 行 | 与上条相同 |

**最坏情况**：单目标问题误纳入一条无关注候选 → 输出多一条结论行。虽然略微多余，但不会产生错误答案或幻觉。当前 minThreshold + entityContextMatchesQuery 双重过滤降低了误纳入概率。

---

## 6. redline 与 mvn test 结果

| 门禁 | 结果 |
|---|---|
| redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 7. 下一步交给 agentD 的 runtime gate 建议

### 验证目标

| 题号 | 期望行为 |
|------|----------|
| FQ4 | API 回答包含 `deposit_amount = 100` 和 `deposit_amount = 1000` 两条结论 |
| FG1 | API 回答包含 `late_fee_per_day = 20` 和 `late_fee_per_day = 5` 两条结论 |
| FQ3 | 单目标保护回归：仍只返回一条 `max_borrow_days` 结论 |
| FQ6 | 单目标保护回归：不受影响 |

### 验证步骤

1. 清库（`bash scripts/reset-lattice-schema.sh`）
2. 确保 LLM 绑定就位（含 `compile/field-alias-enricher`）
3. 上传 Fresh Eval 2 资料并编译
4. 确认 enricher 生成了中文别名（0 条 401 错误）
5. 抓取 FQ4/FG1/FQ3 的 `[TU_TRACE]` 日志，观察 `additionalCandidates` / `deduped` / `selected` 计数
6. FQ4/FG1/FQ3 API 回答格式验证
7. 仅当 FQ4+FQ3 通过，再考虑跑完整 Public Eval

### 禁止事项

- 禁止在 FQ4+FG1 双多目标 PASS 之前标记修复为最终通过
- 禁止只验证最终 API 回答而不检查 trace 日志中的 additionalCandidates 计数

---

## 8. 明确声明

- [x] 只修改了 `AnswerFallbackConclusionBuilder.java` 一个文件
- [x] 未修改 `qf` 判定（`isTerminalHitQueryFocused`）
- [x] 未修改 `countFieldLevelTokenMatches` 核心语义
- [x] 未修改 `countFieldAliasTokenMatches` 核心语义
- [x] 未修改 `AnswerFallbackMarkdownBuilder`
- [x] 未修改 `FactCardTerminalUnitFtsSearchService`
- [x] 未修改 `FactCardTerminalUnitIntentReranker`
- [x] 未修改 `LlmFactCardTerminalUnitFieldAliasEnricher`
- [x] 未修改 tests、scripts、prompt、config、题集
- [x] 未写入 FQ4/FG1/deposit_amount/late_fee_per_day/常规设备/大型设备/精密仪器 等样例字符串
- [x] 未检测"分别是"/"分别"等中文问法关键词
- [x] 未放宽所有同 terminalKey sibling 输出（有 entityContextMatchesQuery + minThreshold 双重过滤）
- [x] 未提交 commit
- [x] redline `BLOCKER=0`
- [x] mvn test `995/0/0/0, BUILD SUCCESS`