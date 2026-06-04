# FQ4/FG1 Terminal Channel Limit 根因分析报告

分析时间：2026-06-02
分析人：Codex 项目架构师
类型：只读 SQL 复算，无代码修改

## 1. 结论

FQ4/FG1 的当前断点不是 field-alias-enricher、metadataJson、fallback conclusion builder，也不是 LIKE token 预算被挤出。

最新根因是：**terminal unit channel 的数据库侧原始排序被 value/context 命中主导，目标字段命中在每通道 limit=10 之前被截断，导致后续 reranker、RRF、fallback conclusion builder 都没有机会消费目标 terminal unit。**

## 2. 已排除项

| 假设 | 结论 | 证据 |
|---|---|---|
| field-alias-enricher 未运行 | 排除 | `execution_llm_snapshots` 有 compile/field-alias-enricher 快照；目标字段中文别名已入库 |
| 中文别名未写入 terminal unit | 排除 | `field_aliases_json`、`metadataJson.fieldAliases`、`fts_text` 均包含中文别名 |
| LIKE token 预算不足 | 排除 | FQ4 的 `押金`、FG1 的 `逾期`、`罚金` 均在 top32 LIKE token 内 |
| conclusion builder 字段级排序单独有错 | 排除为单独根因 | 目标字段未进入 `fallbackHits`，消费侧无法选择不存在的候选 |

## 3. 当前库状态

只读查询 `ai-rag-knowledge.lattice`：

| 表 | 数量 |
|---|---:|
| articles | 5 |
| fact_cards | 13 |
| fact_card_terminal_units | 123 |
| compile_article_review_queue | 0 |

目标 terminal unit 均存在：

| terminal_key | values | 中文别名 |
|---|---|---|
| deposit_amount | 100 / 500 / 1000 | 押金金额、保证金金额、借用押金、押金 |
| late_fee_per_day | 5 / 20 / 50 | 每日逾期费用、逾期日费、逾期日费用 |

## 4. Mapper 等价复算

当前容器没有 `jiebacfg`，真实服务会回退 `simple`。按 `FactCardTerminalUnitMapper.searchLexical` 等价 LIKE/FTS 评分复算：

### FQ4

问题：`equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？`

| rank | key_path | value | score |
|---:|---|---|---:|
| 1 | `[14].设备类型` | 常规设备 | 81 |
| 2 | `[24].设备类型` | 常规设备 | 81 |
| 3 | `equipment_types[0].type` | 常规设备 | 68 |
| 4 | `equipment_types[2].type` | 大型设备 | 68 |
| 13 | `equipment_types[0].approval_required` | 设备管理员 | 21 |
| 14 | `equipment_types[0].deposit_amount` | 100 | 15 |
| 15 | `equipment_types[2].deposit_amount` | 1000 | 15 |

### FG1

问题：`equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？`

| rank | key_path | value | score |
|---:|---|---|---:|
| 1 | `[4].设备类型` | 精密仪器 | 88 |
| 2 | `[14].设备类型` | 常规设备 | 81 |
| 3 | `[24].设备类型` | 常规设备 | 81 |
| 4 | `equipment_types[1].type` | 精密仪器 | 70 |
| 5 | `equipment_types[0].type` | 常规设备 | 68 |
| 15 | `equipment_types[0].late_fee_per_day` | 5 | 15 |
| 17 | `equipment_types[1].late_fee_per_day` | 20 | 15 |

## 5. 机制解释

`FactCardTerminalUnitFtsSearchService.search(question, limit)` 把 API topK 的 `limit` 原样传给 terminal unit repository。当前问题的 runtime topK 为 10，因此 mapper 只返回数据库侧 top10。目标字段排在 14/15/17 位，未进入 Java 侧 reranker。

数据库侧 score 对 `display_text`、`value_text`、`fts_text` 的多 token LIKE 命中累加较高，设备类型/value/context 命中会压过字段别名命中。中文字段别名已经生成，但 `deposit_amount` / `late_fee_per_day` 的字段别名得分不足以抵消设备类型 value 命中的累积分。

## 6. 下一步建议

交给 agentA 做一个最小功能修复，范围限定在 terminal unit retrieval candidate supply：

- `FactCardTerminalUnitFtsSearchService` 可先扩大数据库原始候选窗口，再交给 Java reranker 后截回原 limit。
- `FactCardTerminalUnitIntentReranker` 需让字段意图命中在 terminal unit channel 内显式优先于 value/context-only 命中，避免 type/value sibling 长期压制字段查值。

这属于同一个根因：terminal unit channel 在字段查值场景下的候选供给排序不稳定。不要继续改 fallback conclusion builder。

## 7. 禁止方向

- 禁止为 `押金`、`逾期罚金`、`deposit_amount`、`late_fee_per_day` 写生产逻辑特判。
- 禁止修改题集、测试断言、prompt、redline 脚本或 allowlist。
- 禁止继续在 `AnswerFallbackConclusionBuilder` 叠加 gate。
- 禁止把 FS2/FS4b/S2 搜索问题混入本轮。
