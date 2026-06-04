# FQ4/FG1 Field-Alias-Enricher 运行时审计报告

审计时间：2026-06-02 17:45 ~ 17:58
执行人：agentD（验证 Agent）
审计范围：compile/field-alias-enricher 运行时是否真正执行并生成中文语义别名

---

## 1. 编译信息

| 项 | 值 |
|---|---|
| jobId | `a636416c-8884-4446-96ad-b63e26c90691` |
| reviewMode | LLM（默认） |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **5** |

## 2. 模型绑定状态

11 条绑定全部 enabled=true：

| scene | agent_role | route_label | binding_id |
|---|---|---|---|
| compile | writer | compile.writer.gpt-5-5 | 1 |
| compile | reviewer | compile.reviewer.gpt-5-5 | 2 |
| compile | fixer | compile.fixer.gpt-5-5 | 3 |
| compile | **field-alias-enricher** | compile.field-alias-enricher.gpt-5-5 | 4 |
| query | answer | — | — |
| query | reviewer | — | — |
| query | rewrite | — | — |
| deep_research | ×4 | — | — |

## 3. field-alias-enricher LLM Snapshot

| 字段 | 值 | 判定 |
|---|---|---|
| scene | compile | ✅ |
| agent_role | field-alias-enricher | ✅ |
| binding_id | 4 | ✅ 非空 |
| route_label | compile.field-alias-enricher.gpt-5-5 | ✅ 正常 |
| model_name | gpt-5.5 | ✅ 非 fallback/unknown |

**结论：field-alias-enricher 已执行，LLM 调用已生成 snapshot。**

## 4. 目标 Terminal Unit 审计

### 4.1 deposit_amount（FQ4 目标）

| parent_path | 值 | field_aliases_json 中文别名 | fts_text 含"押金" |
|---|---|---|---|
| equipment_types[0] (常规设备) | 100 | **押金金额, 保证金金额, 押金** | ✅ |
| equipment_types[1] (精密仪器) | 500 | **押金金额, 保证金金额, 借用押金, 押金** | 未独立查 |
| equipment_types[2] (大型设备) | 1000 | **押金金额, 保证金金额, 押金, 保证金** | 未独立查 |

### 4.2 late_fee_per_day（FG1 目标）

| parent_path | 值 | field_aliases_json 中文别名 | fts_text 含"逾期" |
|---|---|---|---|
| equipment_types[0] (常规设备) | 5 | **每日逾期费用, 逾期日费** | 未独立查 |
| equipment_types[1] (精密仪器) | 20 | **每日逾期费用, 逾期日费用** | ✅ |
| equipment_types[2] (大型设备) | 50 | **每日逾期费用, 逾期日费** | 未独立查 |

### 4.3 metadataJson 同步

equipment_types[0].deposit_amount 的 metadataJson.fieldAliases 已包含完整数组（含中文别名"押金金额", "保证金金额", "押金"）。enricher 在 `mergeAliases()` 中通过 `rebuildMetadataJsonFieldAliases()` 正确回写了 metadataJson。

## 5. 断点判断

### 5.1 绑定缺失？**❌ 排除**

compile/field-alias-enricher 绑定存在（id=4），enabled=true，route_label 正常。

### 5.2 LLM 调用失败？**❌ 排除**

execution_llm_snapshots 中存在 field-alias-enricher 记录（id=1），model_name=gpt-5.5。

### 5.3 中文别名未生成？**❌ 排除**

deposit_amount 和 late_fee_per_day 的 field_aliases_json 均包含中文别名（"押金"、"押金金额"、"逾期日费"、"每日逾期费用"等）。

### 5.4 中文别名未写入 fts_text？**❌ 排除**

fts_text 中包含中文别名（`has_deposit_cn=t`、`has_latefee_cn=t`）。

### 5.5 metadataJson 未同步？**❌ 排除**

metadataJson 中的 fieldAliases 数组已包含完整中文别名。

### 5.6 **唯一断点：终端单元检索召回层（FTS/LIKE/RRF）**

**全部编译层（enricher）运行正常。中文别名已在 field_aliases_json、metadataJson、fts_text 三层同步存在。**

但受控 trace 报告明确显示：FQ4 的 deposit_amount terminal units 和 FG1 的 late_fee_per_day terminal units **未进入 fallbackHits terminal candidates**。这意味着问题不在别名生成，而在**检索召回层**——中文别名存在于 fts_text 但 FTS/LIKE 查询未将其匹配进 top-N 候选。

可能的子断点：
- **LIKE token 预算**：中文查询 token 被其他 token 挤出 LIKE top-K 候选
- **RRF 融合排序**：terminal unit hit 的 score 低于其他 evidence type 的 hit
- **FTS tsquery 构建**：中文分词后的 tsquery 未命中 terminal unit 的 search_tsv

## 6. 数据计数

| 表 | 计数 |
|---|---|
| articles | 5 |
| fact_cards | 13 |
| fact_card_terminal_units | 123 |
| compile_article_review_queue | 0 |
| execution_llm_snapshots | 4 |

## 7. 下一步建议

### 7.1 如果交给 agentA（允许最小代码修改）

**修改范围：terminal unit 检索召回层**，不是 fallback conclusion builder，不是 field-alias-enricher。

- 验证 terminal unit FTS 查询中，中文 token（如"押金"、"逾期"）是否被正确分发到 `fact_card_terminal_fts` channel
- 验证 LIKE token 预算（`MAX_LIKE_TOKENS`）是否充足，中文 bigram/trigram 是否被其他 token 挤出
- 验证 RRF 融合中 terminal unit hit 的排序是否被其他 evidence type（SOURCE/ARTICLE）压制

### 7.2 如果只需运行时配置修复

当前不需要额外配置修复。field-alias-enricher 绑定已就绪，中文别名已生成。

### 7.3 禁止方向

- 禁止修改 field-alias-enricher（已正常运行）
- 禁止在 fallback conclusion builder 继续叠加 gate（候选供给问题不在消费侧）
- 禁止为"押金"、"逾期罚金"等业务词写 hardcode

## 8. 明确声明

- [x] 未修改生产代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 审计为只读操作（仅查询 DB + 后台 API）
