# Terminal Unit Phase 1E-1 Alias Enricher Interface 验证报告

验证时间：2026-05-29
验证人：agentD
验证对象：Phase 1E-1 Interface + NoOp 集成骨架修复

## 1. 验证结论

**PASS** — Phase 1E-1 骨架改动范围正确、默认行为不变、无业务词映射、无 fake alias、全部门禁通过。

## 2. 改动范围核验

### 2.1 改动文件清单

| 文件 | 状态 | 类型 | 是否符合 1E-1 允许范围 |
|---|---|---|---|
| `FactCardTerminalUnitFieldAliasEnricher.java` | **新增** | 接口 + NoOp 内部类 | ✓ |
| `FactCardTerminalUnitFieldAliasEnricherTests.java` | **新增** | 6 个单测 | ✓ |
| `FactCardTerminalUnitRecord.java` | 修改 | 新增 `withFieldAliasesAndFtsText()` copy-with 方法 | ✓ |
| `FactCardTerminalUnitMaterializer.java` | 修改 | 新增 `rebuildFtsText()` + `parseAliasesFromJson()` 工具方法 | ✓ |
| `FactCardGenerationService.java` | 修改 | `@Autowired(required = false)` 注入 Enricher + 集成调用点 | ✓ |

### 2.2 未修改文件（确认）

| 文件/区域 | 状态 |
|---|---|
| `FactCardTerminalUnitIntentReranker.java` | **已回退到 HEAD，无 diff** |
| `FactCardTerminalUnitFtsSearchService.java` | 未修改 |
| `LexicalSearchTokenBudget.java` | 未修改 |
| `QuerySemanticRules.java` / `lattice-query-semantic.yml` | 未修改 |
| `FactCardTerminalUnitMapper.xml` / `schema.sql` | 未修改 |
| `AnswerGeneration*` / fallback / citation | 未修改 |
| `FactCardTerminalUnitIntentReranker.java` | 未修改（已确认回退到 HEAD） |

### 2.3 非代码文件变更

| 文件 | 状态 | 说明 |
|---|---|---|
| `terminal_unit_phase1_implementation_plan.md` | 修改 | 实施计划 checkpoint 更新（合规） |
| `special_cases_report.md` | 修改 | redline 脚本输出（合规） |

## 3. 红线核验

| 检查项 | 结果 |
|---|---|
| 命令 | `bash scripts/scan-redline.sh special_cases_report.md` |
| 退出码 | 0 |
| BLOCKER | **0** |
| REVIEW | 2062 |
| ALLOWLIST | 259 |

与 Phase 1D-1 基线一致，无新增命中。

## 4. 测试结果

| 检查项 | 命令 | 结果 |
|---|---|---|
| git diff --check | `git diff --check` | **通过**（无输出） |
| 定向测试 | `mvn test -Dtest=FactCardTerminalUnitFieldAliasEnricherTests,FactCardTerminalUnitMaterializerTests` | **Tests run: 20, Failures: 0, Errors: 0, Skipped: 0** |
| 全量 mvn test | `mvn test` | **Tests run: 977, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |

定向测试明细（20 个）：

| 测试类 | 数量 | 说明 |
|---|---|---|
| `FactCardTerminalUnitMaterializerTests` | 14 | 原有（含 Phase 1D-1 sibling context 测试），全部通过 |
| `FactCardTerminalUnitFieldAliasEnricherTests` | 6 | 新增，覆盖 NoOp/rebuildFtsText/copy-with/边界 |

新增 6 个测试覆盖：
- NoOp 原样返回 records
- test-only fake 追加 alias → ftsText 重建
- ftsText 别名段精确替换
- copy-with 27 个字段透传
- 空 alias 安全处理
- null ftsText 安全处理

## 5. 合规检查

### 5.1 无 fake alias

**通过。** `FactCardTerminalUnitFieldAliasEnricher.java` 只含接口定义 + NoOp 实现。NoOp 直接 `return records`，不追加任何 alias。Materializer 的 `rebuildFtsText` 仅在调用方显式传入 newAliases 时使用，不自动生成。

### 5.2 无业务词映射

**通过。** 全量扫描 enricher 文件，0 命中以下业务词：
- "精密仪器"、"借用"、"押金"、"逾期"、"最长借用"、"最大并发"
- "equipment"、"borrowing_system"、"deposit_amount"、"late_fee"、"max_borrow"、"max_concurrent"
- "化学"、"维护"、"存储"

代码中不存在任何 `Map.of("英文字段", "中文别名")` 或 if/switch 中文字段映射。

### 5.3 未修改 query/fallback/reranker/citation/SQL/prompt/model binding

**通过。**
- `FactCardTerminalUnitIntentReranker.java`：已确认回退到 HEAD，无 diff
- `AnswerGeneration*` / fallback / citation：未修改
- SQL / mapper / schema：未修改
- prompt 文件：未新增、未修改
- `lattice-query-semantic.yml`：未修改
- `docs/模型绑定配置参考.md`：未修改

### 5.4 默认行为不变

**通过。** `@Autowired(required = false)` 确保无 Enricher bean 时 `fieldAliasEnricher == null`，执行路径与 Phase 1D-1 完全一致：

```
无 Enricher bean:
  materializeTerminalUnits()
    → materialize()      // 物化（与 Phase 1D-1 相同）
    → fieldAliasEnricher == null → SKIP  // 跳过 enrich
    → upsertAll()        // 持久化（与 Phase 1D-1 相同）
```

fieldAliasesJson 不变，ftsText 不变。

### 5.5 测试数据无 eval 污染

**通过。** 所有新增测试使用中性 synthetic 数据：
- "甲类对象"、"指标参数"、"group_alpha"、"target_metric"
- 不包含任何 fresh eval 字段名、文件名、答案值、case id

### 5.6 无 LLM 调用

**通过。** 生产代码无 `LlmGateway`、`RestTemplate`、`HttpClient` 或任何网络调用。NoOp 直接返回 records。

## 6. 风险与下一步

### 6.1 当前风险评估

| 风险 | 等级 | 说明 |
|---|---|---|
| 骨架无功能效果 | 无风险 | 这是 1E-1 的设计目标——只铺骨架，不产生 alias |
| copy-with 字段遗漏 | 低 | `withFieldAliasesAndFtsText` 的注册字段列表已通过测试验证 27 字段透传 |
| ftsText 重建错误 | 低 | `rebuildFtsText` 的别名段精确替换逻辑已通过测试验证 |
| Enricher 注入失败 | 无风险 | `required = false` + null check 双重保护 |

### 6.2 下一步建议

**全部验证项通过，建议交回项目架构师判断是否进入 Phase 1E-2。**

1E-2 允许的内容（不在本轮验证范围）：
- 新增 `field-alias-enricher.md` prompt 文件
- 实现 `LlmFieldAliasEnricher` @Service
- 注册 `field-alias-enricher` model role 绑定
- LLM 成功/失败/超时场景单测

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- 未修改 redline 规则、allowlist、AGENTS.md、CLAUDE.md
- 未修改 fresh eval 题集、标准答案、验收口径
- `docs/模型绑定配置参考.md` 未修改
- 未 stage、未 commit、未 push
- 未清库、未重建 schema、未导入资料、未跑业务 eval
- 未连接真实 LLM、未生成任何 alias
- 本轮新增报告：本文件
