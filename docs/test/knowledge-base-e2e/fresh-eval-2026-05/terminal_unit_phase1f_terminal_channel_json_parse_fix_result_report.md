# Terminal Unit Phase 1F: Terminal Channel JSON Parse Fix Result Report

修复时间：2026-05-31
执行人：agentA
修复类型：最小修复 — JSONB 文本格式导致的 channel 识别失败

---

## 1. 本轮根因

PostgreSQL `jsonb::text` 输出带空格格式化：

```json
{"channel": "fact_card_terminal_fts", "keyPath": "...", ...}
```

而此前 `isTerminalUnitChannelHit` 使用紧凑格式的字符串包含匹配：

```java
metadataJson.contains("\"channel\":\"fact_card_terminal_fts\"")
```

两种格式不兼容，导致 fused_rank=1 的 terminal unit 在 selector 的 `filterFallbackEvidenceHits` 和 conclusion builder 的 `buildTerminalUnitExactConclusionLines` 中均未被识别为 terminal unit。下游所有基于 channel 的判断全部失效。

---

## 2. 修改文件与最小变更

| 文件 | 变更 | 行数 |
|---|---|---|
| `TerminalUnitHitMetadataSupport.java` | **新增** package-private 小工具，用 `ObjectMapper.readTree` 解析 metadata JSON 的 `channel` 字段，值与 `CHANNEL_FACT_CARD_TERMINAL_FTS` 比较 | ~25 行 |
| `AnswerFallbackEvidenceSelector.java` | `isTerminalUnitChannelHit` 从脆弱 `contains` 改为委托到 `TerminalUnitHitMetadataSupport` | -7 / +3 |
| `AnswerFallbackConclusionBuilder.java` | 同上 | -7 / +2 |

**唯一变量**：channel 识别方式从字符串 `contains` 改为结构化 JSON 解析。

---

## 3. 为什么不是业务特判

| 检查项 | 说明 |
|---|---|
| JSON 解析是通用方法 | `ObjectMapper.readTree` + `node.path("channel").asText("")` |
| channel 值比较用已有常量 | `RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS`（值为 `"fact_card_terminal_fts"`） |
| 不含业务词/字段名/case id | 不读取任何 eval 数据 |
| fail-closed | JSON 解析异常、channel 缺失、metadata 为空 → 全部返回 false |

---

## 4. 为什么 Fail-Closed 不会误放行非 Terminal Hit

| 条件 | 结果 |
|---|---|
| `hit == null` | false |
| `evidenceType != FACT_CARD` | false |
| `metadataJson == null` 或 blank | false |
| `ObjectMapper.readTree` 解析异常 | false（catch → return false） |
| `channel` 字段缺失或为空 | `node.path("channel").asText("")` → `""` ≠ `"fact_card_terminal_fts"` → false |
| `channel` = `"fact_card_fts"`（不是 terminal） | false |
| `channel` = `"fact_card_terminal_fts"` | **true** |

非 terminal unit hit（ARTICLE、SOURCE、CONTRIBUTION、GRAPH）的 metadata 中不含 `channel: fact_card_terminal_fts`，必然返回 false。

---

## 5. Redline / Git Diff / 定向测试

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| redline | **BLOCKER=0** |
| `AnswerFallbackConclusionBuilderTests` | **7/0/0** |
| `AnswerFallbackEvidenceSelectorTests` | **11/0/0** |
| 定向组合 | **18/0/0 — BUILD SUCCESS** |

---

## 6. 下一步

AgentD clean schema runtime 复验：
1. 验证 FQ6/FG2 terminal unit 在 selector 和 conclusion builder 中被正确识别为 terminal unit channel
2. 验证 conclusion 输出 `keyPath = value` exact line
3. FQ3/FQ4/FG1 保护回归
4. FQ7/FQ11 保护回归

---

## 合规声明

- 本轮仅修改 channel 识别方式（contains → JSON parse）
- 未修改 token 匹配、selector 排序、conclusion 顺序、question type、fallback outcome
- 未修改 compiler、materializer、retrieval、RRF、citation
- 未修改测试文件
- 未修改 config、schema、prompt、redline
- 不含业务词、字段名、文件名、case id 硬编码
- 未读取 hidden eval
- 未清库、未重建、未导入、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
- 新增文件：1（TerminalUnitHitMetadataSupport.java）
