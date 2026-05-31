# Terminal Unit Phase 1G: Terminal Candidate Precision Fix Result Report

修复时间：2026-05-31
执行人：agentA
修复类型：最小修复 — conclusion builder 多候选中按 score 选择最佳 terminal unit

---

## 1. 失败归因

FQ6 中 `borrowing_system` 下有两个 terminal unit 都通过 `isTerminalHitQueryFocused`：
- `name = 校园实验室设备预约系统`（valueText 直接含 "预约系统"）
- `version = v2.3.1`（metadata alias 含 "版本号"）

`buildTerminalUnitExactConclusionLines` 按 `fallbackHits` 遍历顺序选择**第一个** query-focused terminal unit。`name` 在列表中的遍历顺序先于 `version`（name 的 valueText "校园实验室设备预约系统" 直接命中 "预约系统" token，在 fallback scoring 中可能得分不同），导致 name 被选中。

但 `version` 的 fused_rank（RRF 融合排名）= 1，`name` 的 fused_rank = 5。`version` 才是更贴字段意图的目标 terminal unit。

**这不是 channel 识别、metadata sync 或 Reranker 的问题。这是 conclusion builder 在多个 query-focused terminal unit 之间选择逻辑的 precision 问题。**

---

## 2. 修改点

**文件**：`AnswerFallbackConclusionBuilder.java`

**方法**：`buildTerminalUnitExactConclusionLines()`

**修改前**：遍历 `fallbackHits`，返回第一个满足三重约束的 terminal unit。

**修改后**：遍历所有 `fallbackHits`，收集满足三重约束的候选，按 `QueryArticleHit.getScore()` 选最高分者。

```java
// 旧：first-match
for (QueryArticleHit fallbackHit : fallbackHits) {
    if (channel && exactLine && queryFocused) {
        return List.of("Confirmed evidence: " + exactLine);
    }
}

// 新：best-score
QueryArticleHit bestCandidate = null;
for (...) {
    if (channel && exactLine && queryFocused) {
        if (fallbackHit.getScore() > bestScore) {
            bestCandidate = fallbackHit;
        }
    }
}
```

**约 +12 行。**

---

## 3. QueryArticleHit Score 可用性判断

| 属性 | 判断 |
|---|---|
| `getScore()` 方法存在 | ✓ `QueryArticleHit:331` |
| `fusedRank` 字段 | 不存在 — `QueryArticleHit` 是 fused 后的 hit，`getScore()` 承载融合后的 score |
| fallbackHits 中的 score | 已通过 `sortFallbackEvidenceHits` → `scoreQuestionFocusedFallbackHit` 排序。`getScore()` 返回原始 FTS/RRF score |
| 是否可用于选最佳 terminal unit | ✓ 对同 channel、同 query 的 terminal unit，score 代表检索/融合层面的相关性排序 |

---

## 4. 为什么不是 Case 特判

| 检查项 | 说明 |
|---|---|
| 不含字段名判断 | 不读取 `terminalKey`、`keyPath` 的具体值 |
| 不含业务词 | 无 `version`、`name`、`borrowing_system` 等硬编码 |
| 选择逻辑通用 | `getScore() > bestScore` 对所有 terminal unit candidate 生效 |
| 不影响其他 evidence type | ARTICLE/SOURCE 继续走各自 conclusion 路径 |

---

## 5. 已跑命令与结果

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| redline | **BLOCKER=0** |
| `AnswerFallbackConclusionBuilderTests` | **7/0/0** |
| `AnswerFallbackEvidenceSelectorTests` | **11/0/0** |
| 定向组合 | **18/0/0 — BUILD SUCCESS** |

---

## 6. 未跑项

| 项目 | 状态 |
|---|---|
| Clean schema reset | 未执行 |
| 资料导入 / compile | 未执行 |
| 19 题业务 eval / baseline | 未执行 |
| 全量 mvn test | 未执行（定向测试已覆盖修改范围） |

---

## 7. 下一步

AgentD clean schema runtime 复验：
1. FQ6 — 验证 conclusion 输出 `borrowing_system.version = v2.3.1`（而非 name）
2. FG2 — 保护回归（已 PASS）
3. FQ3/FQ4/FG1 — 保护回归
4. FQ7/FQ11 — 保护回归

---

## 合规声明

- 本轮只修改 `AnswerFallbackConclusionBuilder.java` 一个方法
- 选择逻辑仅基于 `getScore()` 通用排序信号
- 不含业务词、字段名、文件名、case id、答案值硬编码
- 未修改 selector、Reranker、metadata sync、channel parse、RRF
- 未修改测试文件
- 未读取 hidden eval
- 未清库、未重建、未导入、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
