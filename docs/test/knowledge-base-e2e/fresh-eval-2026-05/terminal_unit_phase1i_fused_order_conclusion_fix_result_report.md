# Terminal Unit Phase 1I: Fused Order Conclusion Fix Result Report

修复时间：2026-05-31
执行人：agentA
修复类型：最小修复 — terminal conclusion candidate selection 改用原始 fused order

---

## 1. 为什么 Phase 1G Best-Score 不能保留

Phase 1G 使用 `QueryArticleHit.getScore()` 作为 terminal unit 候选选择信号。但 `getScore()` 在不同阶段被复用为不同语义：
- terminal unit FTS 通道内：lexical/LIKE/FTS 分数
- Reranker 后：adjusted score（不写回）
- RRF 融合后：融合分
- Fallback 内：保留旧分不更新

**clean runtime 已证明**：FQ6 中 `name`（score=105，valueText 词面匹配）> `version`（score=54，alias 间接匹配），best-score 会稳定选错。`getScore()` 不是 terminal 字段意图精度信号，不应作为收口修复。

---

## 2. 本轮如何传递原始 Fused Order

answer 入口 `buildEvidenceMarkdown(question, queryArticleHits)` 持有原始 fused 列表。传递路径：

```
AnswerFallbackMarkdownBuilder.buildEvidenceMarkdown(question, queryArticleHits)
  → appendEvidenceConclusion(builder, question, fallbackHits, queryTokens, queryArticleHits)
    → support.buildEvidenceConclusionLines(question, fallbackHits, queryTokens, queryArticleHits)
      → answerFallbackConclusionBuilder.buildEvidenceConclusionLines(question, fallbackHits, queryTokens, queryArticleHits)
        → buildGeneralFallbackConclusionLines(question, fallbackHits, queryTokens, queryArticleHits)
          → buildTerminalUnitExactConclusionLines(fallbackHits, queryTokens, queryArticleHits)
```

**旧入口兼容**：`buildEvidenceConclusionLines(question, fallbackHits, queryTokens)` 内部委托到新重载并传 `null` 作为 `queryArticleHits`。旧入口不传原始 fused 列表，回退到 `getScore()`。

---

## 3. 修改文件与最小变更

| 文件 | 变更 | 行数 |
|---|---|---|
| `AnswerFallbackConclusionBuilder.java` | `buildEvidenceConclusionLines` 新增 `queryArticleHits` 重载；`buildGeneralFallbackConclusionLines` / `buildTerminalUnitExactConclusionLines` 增加 `queryArticleHits` 参数；新增 `fusedOrderScore()` 将 fused index 转换为排序分；无 `queryArticleHits` 时回退 `getScore()` | ~25 行 |
| `AnswerGenerationFallbackConclusionSupport.java` | `buildEvidenceConclusionLines` 新增 `queryArticleHits` 重载，委托到 builder | ~8 行 |
| `AnswerFallbackMarkdownBuilder.java` | `appendEvidenceConclusion` 增加 `queryArticleHits` 参数；调用点传入原始列表 | ~5 行 |

**总计 ~38 行。**

---

## 4. Fused Order Score 计算

```java
private static double fusedOrderScore(QueryArticleHit hit, List<QueryArticleHit> queryArticleHits) {
    if (queryArticleHits == null || queryArticleHits.isEmpty()) {
        return hit.getScore();  // 旧路径兼容
    }
    int index = queryArticleHits.indexOf(hit);
    if (index < 0) {
        return -1.0D;  // 不在原始 fused 列表中 → 最低优先级
    }
    return (double) (queryArticleHits.size() - index);  // order 越小分数越高
}
```

- 有 `queryArticleHits` 时：用列表 index 转换分数（越靠前分越高）
- 无 `queryArticleHits` 时：回退 `getScore()`（测试/旧调用路径兼容）
- 候选不在 fused 列表中：返回 -1.0（最低优先）

---

## 5. 为什么不是 Case 特判

| 检查项 | 说明 |
|---|---|
| 不含字段名/业务词 | 不读取 `terminalKey`、`keyPath` 的具体值 |
| 选择逻辑通用 | 对所有 terminal unit candidate 使用同一 `fusedOrderScore` |
| fused order 来源通用 | `queryArticleHits` 是 RRF 融合后的全局列表，不依赖问题文本 |
| fallback 路径不变 | `queryArticleHits=null` 时回退到原有 `getScore()` 行为 |

---

## 6. 已跑命令与结果

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| redline | **BLOCKER=0** |
| `AnswerFallbackConclusionBuilderTests` | **7/0/0** |
| `AnswerFallbackEvidenceSelectorTests` | **11/0/0** |
| 定向组合 | **18/0/0 — BUILD SUCCESS** |

---

## 7. 未跑项

| 项目 | 状态 |
|---|---|
| Clean schema reset | 未执行 |
| 资料导入 / compile | 未执行 |
| 19 题业务 eval / baseline | 未执行 |
| 全量 mvn test | 未执行（定向测试已覆盖修改范围） |

---

## 8. 下一步

AgentD clean schema runtime 复验：
1. FQ6 — 验证 conclusion 选择 `version = v2.3.1`（fused_rank=1）而非 `name`（fused_rank=5）
2. FG2 — 保护回归（已 PASS）
3. FQ3/FQ4/FG1 — 保护回归
4. FQ7/FQ11 — 保护回归

---

## 合规声明

- 本轮只修改 `AnswerFallbackConclusionBuilder.java`、`AnswerGenerationFallbackConclusionSupport.java`、`AnswerFallbackMarkdownBuilder.java`
- 未修改 QueryArticleHit 字段、RrfFusionService、selector、Reranker、citation
- 未修改测试文件
- 未修改 config、schema、prompt、redline
- 不含业务词、字段名、文件名、case id、答案值硬编码
- 未读取 hidden eval
- 未清库、未重建、未导入、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
