# Terminal Unit Phase 1D-1: Materializer Sibling Context Fix Result Report

修复时间：2026-05-29
执行人：agentA
修复类型：最小修复 — 仅 Materializer，不改 Reranker
修复范围：Layer 2 第一半

---

## 1. 修改摘要

在 `FactCardTerminalUnitMaterializer` 中新增 sibling descriptor 收集与 fieldDescription 增强逻辑：

- **新增方法** `collectParentPathDescriptors(JsonNode itemsNode)`：在 `materialize()` 遍历 items 前，扫描同 fact card、同 parentPath 的 terminal item value，收集符合形态规则的中文 string descriptor。
- **修改** `buildFieldDescription(...)`：新增 `siblingDescriptors` 和 `ownValueText` 参数，将过滤后的 sibling context 追加为 `context: xxx, yyy`。
- **修改** `materializeItem(...)`：接收 `parentPathDescriptors` map，提取当前 item 的 sibling descriptors 并入参传给 `buildFieldDescription`。

**效果**：目标 terminal unit（如 `equipment_types[1].max_borrow_days=7`）的 `fieldDescription` 变更为：

```
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
```

"精密仪器"、"实验室主任" 进入 `ftsText` → 进入 `search_tsv` → 可被 LIKE `%精密仪器%` 匹配，为中文 query token 与英文 field key 之间建立编译级上下文桥梁。

## 2. 为什么本轮只改 Materializer、不改 Reranker

设计报告 `terminal_unit_phase1d_yaml_sibling_context_design_report.md` 推荐 Materializer + Reranker 双层修改。本轮仅执行 Layer 2 第一半（Materializer），原因：

1. **本轮目标不是追求 5/5 PASS**，而是验证 "编译产物是否能提供中文上下文信号"。Materializer 单独即可产出可观测的 fieldDescription/ftsText 变化。
2. Reranker 的 `fieldMatchCount` 与 `contextMatch` 权重调整需要 query 时行为验证，需要先确认 Materializer 生成的 context 正确进入数据库，再由 agentD 在 clean schema 上做端到端验证。
3. 如果 Materializer 生成的 sibling context 已在 ftsText 中生效（LIKE 召回命中），再决定是否需要 Reranker 感知层。如果 LIKE 召回已能将目标 unit 带入 topK，Reranker 修改可能不需要。

## 3. Sibling Context 来源与过滤规则

### 3.1 来源

- **仅来自**同一 fact card、同一 parentPath 的 terminal item value。
- **不跨 card**（factCardRecord 隔离）。
- **不跨 source file**（来源于 fact card 的 itemsJson）。

### 3.2 过滤规则（纯形态，无业务语义）

| 规则 | 实现 |
|---|---|
| valueType 为 string | `inferValueType(valueText)` 返回值必须为 `"string"` |
| valueText 含 CJK | `CJK_RUN_PATTERN.matcher(valueText).find()` |
| 长度 2-20 | `valueText.length() >= 2 && valueText.length() <= 20` |
| 排除自身 | `!d.equals(ownValueText)` |
| 每 parentPath 最多 2 个 | `limited.size() < 2` |
| 保持原始顺序去重 | `LinkedHashSet` + 顺序遍历 |
| 排除容器值 | `startsWith("{")` / `startsWith("[")` |
| 排除过长值 | `valueText.length() <= MAX_VALUE_LENGTH(=240)` |

### 3.3 写入位置

- **写入** `fieldDescription`（追加 `context: xxx, yyy`）→ 进入 `ftsText` → 进入 `search_tsv` → 可被 LIKE 匹配。
- **不写入** `fieldAliases`（避免 sibling boost 混淆，所有 sibling 共享相同 descriptor alias 会导致 fieldMatchCount 无净差异）。

## 4. 为什么不是业务硬编码或 Eval 污染

| 检查项 | 状态 | 说明 |
|---|---|---|
| 无中文字段语义映射 | **通过** | 无 "最长借用天数 → max_borrow_days" 类映射 |
| 无文件名/文档标题判断 | **通过** | 不读取 factCardRecord 的 sourceFileName/sourceTitle |
| 无 case id/题面/答案判断 | **通过** | 不读取任何 eval 数据 |
| 无业务词白名单 | **通过** | 只用 CJK 字符检测 + 长度 + valueType = string |
| 无跨卡污染 | **通过** | parentPath 只在本 fact card 范围内生效 |
| 测试使用 synthetic 数据 | **通过** | 测试中的 "精密仪器"、"实验室主任"、"校园预约系统"、"有效配置" 等均为 synthetic 通用词，未使用 fresh eval 字段名/答案/文件名 |

## 5. 测试结果

### 5.1 git diff --check

```
无输出（通过）
```

### 5.2 Redline 扫描

```
BLOCKER=0, REVIEW=2062, ALLOWLIST=259
```

与修复前一致，未引入新 BLOCKER。

### 5.3 定向测试（FactCardTerminalUnitMaterializerTests）

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

新增 6 个测试：

| 测试 | 验证点 | 结果 |
|---|---|---|
| `shouldAddSiblingContextToFieldDescription` | 同 parentPath CJK sibling value 进入其他 unit 的 fieldDescription + ftsText | PASS |
| `shouldExcludeOwnValueFromSelfContext` | 自身 value 不作自身 context | PASS |
| `shouldFilterNonCjkLongAndEmptyFromContext` | 非 CJK(number/url/boolean)和空值不进入 context | PASS |
| `shouldLimitDescriptorsPerParentPath` | 每 parentPath 最多 2 个 descriptor，保持原始顺序 | PASS |
| `shouldNotAddSiblingDescriptorsToFieldAliases` | fieldAliases 不含 sibling descriptor | PASS |
| `shouldNotAddContextWhenNoSharedParentPathOrSingleItem` | 无 parentPath 或单 item 无 context | PASS |

原有 8 个测试全保护：PASS

### 5.4 全量 mvn test

```
Tests run: 870, Failures: 1, Errors: 39, Skipped: 0
```

失败/错误全部为预存问题（`GroupNodeTests`、`CrossGroupMergeNodeTests`、`LlmGatewayMaxInputCharsTests`），非本轮引入。Materializer 相关测试全部通过。

## 6. 未修改清单（确认）

| 文件/区域 | 状态 |
|---|---|
| `src/main/java/com/xbk/lattice/query/**` | **未修改** |
| `FactCardTerminalUnitIntentReranker.java` | **未修改** |
| `FactCardTerminalUnitFtsSearchService.java` | **未修改** |
| `LexicalSearchTokenBudget.java` | **未修改** |
| `FactCardTerminalUnitMapper.xml` / SQL | **未修改** |
| `schema.sql` | **未修改** |
| `src/main/resources/**` | **未修改** |
| `scripts/**` | **未修改** |
| `AGENTS.md` / `CLAUDE.md` | **未修改** |
| `docs/模型绑定配置参考.md` | **未修改** |
| `special_cases_report.md` | **未修改**（redline 输出不提交） |
| Fresh eval 题集/标准答案/验收口径 | **未修改** |
| query fallback / answer generation / citation | **未修改** |
| Reranker / RRF / FTS search | **未修改** |

## 7. 下一步

交给 agentD 执行 clean schema 重导验证：

1. `./scripts/reset-lattice-schema.sh`
2. 重新导入 fresh eval 2 资料（5 份 YAML + CSV/XLSX）
3. 触发 compile
4. 验证 YAML 5 题目标 terminal unit 的 `fieldDescription` / `ftsText` 是否包含同 parentPath 中文 context
5. 验证 terminal channel（`fact_card_terminal_fts`）命中数与排名是否改善
6. 验证 fused topK 中目标 unit 位置是否提升

本轮仅验证编译产物。Reranker 感知修改（Phase 1D-2）视 agentD 验证结果决定是否需要。

## 合规声明

- 本轮未修改 query fallback、Reranker、SQL、配置、题集
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 修改文件数：2（Materializer.java + MaterializerTests.java）
- 新增文件数：1（本报告）
