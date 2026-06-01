# Terminal Unit Phase 1E-1: Interface + NoOp 集成骨架修复结果报告

修复时间：2026-05-29
执行人：agentA
修复类型：安全骨架 — 无 LLM、无 fake alias、默认行为不变

---

## 1. 修改摘要

为 Phase 1E LLM 字段 alias 生成铺平基础架构，本轮只做骨架，不接真实 LLM。

| 文件 | 变更 | 类型 |
|---|---|---|
| `FactCardTerminalUnitRecord.java` | 新增 `withFieldAliasesAndFtsText(newAliasesJson, newFtsText)` — 仅替换两个字段，其余 27 字段透传 | 修改 |
| `FactCardTerminalUnitMaterializer.java` | 新增 `rebuildFtsText(record, newAliases)` + `parseAliasesFromJson(json)` — ftsText 中精确替换旧别名段为新别名段 | 修改 |
| `FactCardTerminalUnitFieldAliasEnricher.java` | 新增接口 + `NoOp` 内部类 — NoOp 原样返回 records | **新增** |
| `FactCardGenerationService.java` | 注入可选 Enricher（`@Autowired(required = false)`），在 `materializeTerminalUnits` 中 Materializer 后、upsert 前调用 | 修改 |
| `FactCardTerminalUnitFieldAliasEnricherTests.java` | 6 个单测，覆盖 NoOp、fake 注入、ftsText 重建、copy-with 透传、空/null 安全 | **新增** |

## 2. 为什么 1E-1 不接真实 LLM

1. **LLM 绑定未就绪**：`field-alias-enricher` role 尚未在模型绑定配置中注册。
2. **Prompt 文件未编写**：`field-alias-enricher.md` 需要在仔细审计后单独创建。
3. **分轮降低风险**：1E-1 验证骨架正确性（集成点、record copy、ftsText 重建），1E-2 再引入 LLM 调用。
4. **默认行为不变**：NoOp + `required = false` 确保无 Enricher bean 时系统完全不受影响。

## 3. 为什么生产代码没有 fake alias / 业务词映射

| 检查项 | 状态 | 说明 |
|---|---|---|
| `FactCardTerminalUnitFieldAliasEnricher.java` | 只含接口定义 + NoOp | NoOp 直接 `return records`，不做任何 alias 修改 |
| `FactCardTerminalUnitMaterializer.java` | 只新增 `rebuildFtsText` 工具方法 | 仅在调用方显式传入 newAliases 时使用，不自动生成 alias |
| `FactCardGenerationService.java` | `fieldAliasEnricher` 默认为 null | 无 Enricher bean → 跳过 enrich → 行为完全不变 |
| 无业务词 | 全量扫描通过 | Java 代码无 "借用/押金/逾期/并发/版本号" 等业务词 |
| 无中文字段映射 | 全量扫描通过 | 无 `Map.of("max_borrow_days", ...)` 或 if/switch 映射 |

## 4. 默认行为不变的证据

```
无 Enricher bean (fieldAliasEnricher == null):
  materializeTerminalUnits()
    → materialize()         // 物化
    → if (fieldAliasEnricher != null) → SKIP  // 跳过
    → upsertAll()           // 持久化

与 Phase 1D-1 完全一致的执行路径。fieldAliasesJson 不变，ftsText 不变。
```

## 5. 测试结果

### 5.1 git diff --check

```
无输出（通过）
```

### 5.2 Redline 扫描

```
BLOCKER=0, REVIEW=2062, ALLOWLIST=259
```

与 Phase 1D-2 回退后一致，无新增命中。

### 5.3 定向测试

```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

| 测试类 | 数量 | 变化 |
|---|---|---|
| `FactCardTerminalUnitMaterializerTests` | 14 | 无变化（含 Phase 1D-1 6 个 sibling context 测试） |
| `FactCardTerminalUnitFieldAliasEnricherTests` | 6 | **新增** |

**新增 6 个测试明细：**

| 测试 | 验证点 |
|---|---|
| `shouldReturnRecordsUnchangedWithNoOpEnricher` | NoOp 原样返回 records |
| `shouldAppendAliasAndRebuildFtsTextWithFakeEnricher` | test-only fake 追加 alias → fieldAliasesJson 更新 → ftsText 包含新 alias |
| `shouldReplaceOnlyAliasSegmentInFtsText` | ftsText 重建只替换别名段，其余保留 |
| `shouldPreserveAllFieldsExceptAliasesAndFtsTextInCopyWith` | copy-with 后 27 个非 alias/ftsText 字段完全透传 |
| `shouldHandleEmptyAliasesInRebuildFtsText` | 空别名列表安全处理 |
| `shouldNotThrowOnNullFtsTextInRebuild` | null ftsText 不抛异常 |

所有测试使用中性 synthetic 数据（"甲类对象"、"指标参数"、"group_alpha"、"target_metric"），不涉及 fresh eval 字段名/文件名/答案值。

### 5.4 全量 mvn test

```
Tests run: 977, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

全量 977/0/0/0 干净通过。较 Phase 1D-1 基线（971）增加 6 个测试（1E-1 Enricher tests）。

## 6. 未修改清单

| 文件/区域 | 状态 |
|---|---|
| `FactCardTerminalUnitIntentReranker.java` | **未修改**（已回退到 HEAD） |
| `FactCardTerminalUnitFtsSearchService.java` | **未修改** |
| `LexicalSearchTokenBudget.java` | **未修改** |
| `QuerySemanticRules.java` / `lattice-query-semantic.yml` | **未修改** |
| `FactCardTerminalUnitMapper.xml` / `schema.sql` | **未修改** |
| `AnswerGeneration*` / fallback / citation | **未修改** |
| `src/main/resources/**` | **未修改** |
| `scripts/**` | **未修改** |
| `AGENTS.md` / `CLAUDE.md` | **未修改** |
| `docs/模型绑定配置参考.md` | **未修改** |
| `special_cases_report.md` | **未修改** |
| Fresh eval 题集/标准答案/验收口径 | **未修改** |

## 7. 下一步：Phase 1E-2

1E-2 才允许：
- 新增 `field-alias-enricher.md` prompt 文件
- 实现 `LlmFieldAliasEnricher` @Service（调用 `LlmGateway.generateText`）
- 注册 `field-alias-enricher` model role 绑定
- 补充 LLM 成功/失败/超时场景单测

## 合规声明

- 本轮未连接真实 LLM、未生成任何 alias
- 生产代码无 fake alias、无业务词映射
- 未修改 query fallback、Reranker、SQL、配置、题集
- 未读取 hidden eval
- 未 stage、未 commit、未 push
- 修改文件：3（Record + Materializer + FactCardGenerationService）
- 新增文件：2（Enricher 接口 + 测试）
- 新增报告：1（本报告）
