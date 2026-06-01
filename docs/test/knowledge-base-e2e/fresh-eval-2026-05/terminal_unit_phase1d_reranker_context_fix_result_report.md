# Terminal Unit Phase 1D-2: Reranker Context Match Fix Result Report

修复时间：2026-05-29
执行人：agentA
修复类型：最小修复 — 仅 Reranker 读取 fieldDescription context 参与排序

---

## 1. 修改摘要

在 `FactCardTerminalUnitIntentReranker` 中新增 fieldDescription context 感知能力，使 Reranker 能从 terminal unit metadata 的 `fieldDescription` 字段读取 Phase 1D-1 写入的中文 sibling context，并作为低权重排序信号参与重排。

**5 处代码修改：**

| # | 修改点 | 说明 |
|---|---|---|
| 1 | 新增常量 `CONTEXT_TOKEN_WEIGHT = 0.3` | 低于 `FIELD_TOKEN_WEIGHT(1.0)`，高于 `VALUE_TOKEN_WEIGHT(0.1)` |
| 2 | `HitProfile` 新增 `fieldDescription` + `contextMatchCount` 字段 | 存储 metadata 中的 fieldDescription 及上下文命中计数 |
| 3 | `parseProfile()` 读取 `fieldDescription` | 从 metadataJson 解析 fieldDescription |
| 4 | 新增 `countContextMatches()` + `extractContextTokens()` | 从 fieldDescription 的 `context: ...` 段提取 CJK n-gram token，与 query token 做集合匹配 |
| 5 | `adjustedScore` + early return 修改 | `adj += contextMatchCount * 0.3`；`anySignal` 纳入 `contextMatchCount > 0` |

## 2. Context Match 权重与理由

| 信号 | 权重 | 理由 |
|---|---|---|
| Field token match (terminalKey/fieldLabel/aliases/keyPath) | **1.0** | 明确的字段意图信号，精度最高 |
| **Context match (fieldDescription context)** | **0.3** | 间接中文上下文信号——"精密仪器"命中 context 只说明该 unit 与描述该实体的字段同组，不如明确命中 `max_borrow_days` 精准 |
| Value match (valueText/displayText) | **0.1** | 纯值文本命中，可能只是巧合 |

**层级关系**：`fieldMatch(1.0) > contextMatch(0.3) > valueMatch(0.1) > numericBonus(0.5, 独立维度)`

以 FQ3 "精密仪器的单次最长借用天数是多少" 为例：

| Unit | fieldMatch | contextMatch | valueMatch | numericBonus | 净调整 |
|---|---|---|---|---|---|
| max_borrow_days=7 **(目标)** | 0 | 1+ ("精密仪器") | 0 | +0.5 | **≥+0.8** |
| type=精密仪器 (sibling) | 0 | 0 | 1+ ("精密仪器") | 0 | **≤+0.5** |

目标 unit 的 contextMatch(0.3) + numericBonus(0.5) = +0.8 > sibling 的 valueMatch(0.1×n)。在原始 FTS score 差距 < 0.3 时，目标 unit 可翻盘。

## 3. 为什么这是通用排序信号

| 检查项 | 状态 | 说明 |
|---|---|---|
| 无中文字段语义映射 | **通过** | context 文本来自 Phase 1D-1 Materializer 的纯形态规则产出 |
| 无文件名/文档标题判断 | **通过** | 不读取 cardId/sourceFileName |
| 无 case id/题面/答案判断 | **通过** | 不读取任何 eval 数据 |
| 无业务词白名单 | **通过** | `extractContextTokens()` 只解析 `context: ...` 段的 CJK n-gram，不区分业务词 |
| context 来源只读 metadata | **通过** | 从 `metadataJson.fieldDescription` 字段读取，不做额外判断 |
| 权重层级通用 | **通过** | fieldMatch > contextMatch > valueMatch，是精度递减的通用排序信号 |
| 不污染 fieldAliases | **通过** | `extractContextTokens()` 只解析 `context: ...` 段，不接触 fieldAliases |
| 测试使用 synthetic 数据 | **通过** | 所有测试用例的 context 值均为 synthetic 中文词 |

## 4. 测试结果

### 4.1 git diff --check

```
无输出（通过）
```

### 4.2 Redline 扫描

```
BLOCKER=0, REVIEW=2063, ALLOWLIST=259
```

REVIEW +1（2062→2063），新增命中为 `contextTokens.contains(...)` — 这是 `Set.contains()` Java 集合方法，非字符串内容匹配，属于 false positive。无新增 BLOCKER。

### 4.3 定向测试（FactCardTerminalUnitIntentRerankerTests）

```
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

**新增 6 个测试：**

| 测试 | 验证点 | 结果 |
|---|---|---|
| `shouldRankContextMatchedNumericTargetAboveValueOnlyStringSibling` | context + numeric bonus 帮助 number 型目标超过 value-only string sibling | PASS |
| `shouldPrioritizeFieldAliasMatchOverContextMatch` | field alias match(1.0) 权重高于 context match(0.3) | PASS |
| `shouldHandleMissingOrEmptyFieldDescription` | fieldDescription null/空/无 context → 不报错，安全降级 | PASS |
| `shouldPreserveOriginalOrderWhenNoSignalsAtAll` | 无任何信号 + 无 numeric intent → 保持原顺序（early return） | PASS |
| `shouldAllowRerankWhenOnlyContextSignalPresentNoNumericIntent` | 仅有 context signal 也激活重排（不再 early return） | PASS |
| （修正）原有 9 个测试 | 全部保留通过 | PASS |

### 4.4 全量 mvn test

fork-based 全量测试受预存 `ClassNotFoundException` Surefire fork 问题影响（同 Phase 1D-1 报告记录）。Reranker 相关测试全部通过。Phase 1D-1 clean verification 已确认全量 `971/0/0/0`。

## 5. 未修改清单（确认）

| 文件/区域 | 状态 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/**` | **未修改** |
| `FactCardTerminalUnitMaterializer.java` | **未修改** |
| `src/main/java/com/xbk/lattice/documentparse/**` | **未修改** |
| `src/main/java/com/xbk/lattice/infra/persistence/**` | **未修改** |
| `src/main/resources/**` | **未修改** |
| `Mapper XML / SQL / schema.sql` | **未修改** |
| `Query fallback / AnswerGeneration* / citation` | **未修改** |
| `FactCardTerminalUnitFtsSearchService.java` | **未修改** |
| `LexicalSearchTokenBudget.java` | **未修改** |
| `QuerySemanticRules.java` / `lattice-query-semantic.yml` | **未修改** |
| `scripts/**` | **未修改** |
| `AGENTS.md` / `CLAUDE.md` | **未修改** |
| `docs/模型绑定配置参考.md` | **未修改** |
| `special_cases_report.md` | **未修改**（redline 输出不提交） |
| Fresh eval 题集/标准答案/验收口径 | **未修改** |

## 6. 与 Phase 1D-1 的关系

```
Phase 1D-1 (Materializer):  写入 sibling context → fieldDescription → ftsText
Phase 1D-2 (Reranker):       读取 fieldDescription → contextMatch → adjustedScore
```

两层互补：
- **Materializer 单独**：使目标 unit 可被 LIKE 召回（ftsText 含中文 context），但不能排序提权
- **Reranker 单独**：可读取 fieldDescription 做排序，但依赖 Materializer 先写入 context
- **组合**：LIKE 召回 + context match 排序 → 目标 unit 同时获得召回和排序提升

## 7. 下一步

交给 agentD 执行 clean schema 重导验证（需清库重建以获取 Phase 1D-1 的 Materializer 产出 + Phase 1D-2 的 Reranker 行为）：

1. `./scripts/reset-lattice-schema.sh`
2. 重新导入 5 份 fresh eval 资料
3. 触发 compile（产出含 sibling context 的 fieldDescription）
4. 重点验证 YAML 5 题：
   - FQ3 max_borrow_days=7 的 fused rank 是否从 6 提升
   - FG2 max_concurrent_requests=50 的 fused rank 是否从 4 提升
   - FQ4 deposit_amount=100 是否进入 fused topK
   - FG1 late_fee_per_day=20 是否进入 fused topK
   - FQ6 version=v2.3.1 是否进入 fused topK
5. 验证 FQ7/FQ11 保护不退化
6. 输出完整 Fresh Eval 指标

## 合规声明

- 本轮未修改 Materializer、query fallback、SQL、配置、题集
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 修改文件数：2（Reranker.java + RerankerTests.java）
- 新增文件数：1（本报告）
