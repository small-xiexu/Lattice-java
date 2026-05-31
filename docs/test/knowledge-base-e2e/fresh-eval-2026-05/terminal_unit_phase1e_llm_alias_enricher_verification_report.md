# Terminal Unit Phase 1E-2 LLM Alias Enricher 验证报告

验证时间：2026-05-29
验证人：agentD
验证对象：Phase 1E-2 真实 LLM Field Alias Enricher 代码级验证

## 1. 验证结论

**PASS** — 改动范围正确、红线合规、fail-closed 覆盖完整、全部门禁通过。无业务词映射、无 eval 污染。

## 2. 改动范围核验

### 2.1 Tracked 文件

| 文件 | 类型 | 归属 | 说明 |
|---|---|---|---|
| `FactCardTerminalUnitRecord.java` | 修改 | 1E-1 骨架 | `withFieldAliasesAndFtsText` copy-with |
| `FactCardTerminalUnitMaterializer.java` | 修改 | 1E-1 骨架 | `rebuildFtsText` + `parseAliasesFromJson` |
| `FactCardGenerationService.java` | 修改 | 1E-1 骨架 | `@Autowired(required = false)` Enricher |
| `CompilerPromptProvider.java` | 修改 | **1E-2** | 加载 `fieldAliasEnricherPrompt` |
| `terminal_unit_phase1_implementation_plan.md` | 修改 | 1E-2 checkpoint | 实施计划状态回写 |
| `模型绑定配置参考.md` | 修改 | **无关本地脏改动** | 本地配置变更，未纳入验证交付 |
| `special_cases_report.md` | 修改 | **redline 输出** | 脚本自动覆盖 |

### 2.2 Untracked 文件

| 文件 | 类型 | 归属 | 说明 |
|---|---|---|---|
| `FactCardTerminalUnitFieldAliasEnricher.java` | **新增** | 1E-1 骨架 → 1E-2 升级 | 接口 + NoOp → 新增 `LlmFactCardTerminalUnitFieldAliasEnricher` @Service |
| `FactCardTerminalUnitFieldAliasEnricherTests.java` | **新增** | 1E-2 | 12 个测试（6 个 1E-1 + 6 个 1E-2） |
| `field-alias-enricher.md` | **新增** | **1E-2** | LLM prompt 文件 |
| `terminal_unit_phase1e_llm_alias_enricher_fix_result_report.md` | 新增 | 1E-2 agentA 报告 | — |
| 其他 untracked（phase1d_reranker_* 等） | 已有 | 前轮遗留 | 不在本轮交付范围 |

### 2.3 范围确认

1E-2 仅新增/修改 5 个文件（1E-1 已验证骨架 + 本轮 Enricher 实现 + Prompt + 测试 + 计划回写）。**未修改 query/fallback/Reranker/citation/SQL/schema。** `docs/模型绑定配置参考.md` 和 `special_cases_report.md` 为无关脏改动/redline 输出。

## 3. 红线与污染核验

### 3.1 Java 主链

| 检查项 | 结果 | 证据 |
|---|---|---|
| 英文→中文硬编码映射表 | **无** | 0 matches for "精密仪器/借用/押金/逾期" 等业务词 |
| Map.of 业务映射 | **无** | 5 个 `Map.of()` 均为 fail-closed 空 map 返回 |
| if/switch 字段特判 | **无** | 0 matches for `if.*key.*equals` / `switch.*key` |
| 文件名判断 | **无** | 无 sourceFileName/sourceTitle 读取 |
| eval 题面/case id/答案值 | **无** | 无 FQ*/FG* 引用，无 expectedAnswer |

### 3.2 Prompt（field-alias-enricher.md）

| 检查项 | 结果 | 证据 |
|---|---|---|
| fresh eval 业务词 | **无** | 0 matches (精密仪器/常规设备/押金/逾期 等) |
| 文件名/文档名 | **无** | 0 matches (equipment-borrowing/chemical-storage 等) |
| case id / expected answer | **无** | 明确规则："不要使用文件名、查询问题、评测信息、案例编号或期望答案" |
| 答案模板 | **无** | 纯字段别名生成任务，不涉及业务结论 |

Prompt 内容为通用任务描述：为英文字段名生成中文检索别名，输入仅限 terminal unit 结构信息（terminalKey/fieldLabel/keyPath/valueType 等），输出严格 JSON。

### 3.3 测试数据

| 检查项 | 结果 | 证据 |
|---|---|---|
| 中性 synthetic 数据 | **是** | 42 matches: "甲类对象/指标参数/group_alpha/target_metric/sample_limit" |
| eval 业务词 | **无** | 0 matches (FQ3/FQ4/equipment-borrowing/精密仪器 等) |

### 3.4 LLM 输入边界

| 检查项 | 结果 |
|---|---|
| 文件名 | 不输入 |
| query 日志 | 不输入 |
| eval 题面 | 不输入 |
| case id | 不输入 |
| expected answer | 不输入 |

LLM 输入仅包含：terminalKey、fieldLabel、keyPath、parentPath、pathSegments、valueType、displayText、siblings（同 parentPath 键值摘要）、raw（itemsJson 原始行）。

## 4. Fail-closed 核验

### 4.1 应对场景与测试覆盖

| 场景 | 测试方法 | 行为 | 结果 |
|---|---|---|---|
| No Enricher bean | `shouldReturnRecordsUnchangedWithNoOpEnricher` | 原样返回 records | PASS |
| LLM 抛异常 | `shouldKeepOriginalRecordsWhenLlmThrowsException` | 原样返回 records | PASS |
| LLM 返回非 JSON | `shouldKeepOriginalRecordsWhenLlmReturnsNonJson` | 原样返回 records | PASS |
| LLM 返回空响应 | `shouldKeepOriginalRecordsWhenLlmReturnsBlankResponse` | 原样返回 records | PASS |
| Route 不可用/未绑定 | `shouldKeepOriginalRecordsWhenRouteIsUnavailable` | 原样返回 records | PASS |
| 已有 CJK alias | `shouldSkipRecordsThatAlreadyHaveCjkAliases` | 跳过，不调用 LLM | PASS |

### 4.2 Fail-closed 实现路径

```
enrich(records, factCardRecord)
  │
  ├── 1. 筛选缺少 CJK alias 的候选 record
  │     └── 已有 CJK alias → 跳过（不调 LLM）
  │
  ├── 2. routeResolution(compile, field-alias-enricher)
  │     └── 不可用/异常/null → return records（不调 LLM）
  │
  ├── 3. generateText(prompt, structuredInput)
  │     ├── 异常 → return records
  │     ├── 空响应 → return records
  │     └── 非 JSON → return records
  │
  ├── 4. 解析 aliases JSON
  │     ├── 无 aliases 对象 → return records
  │     └── 无有效 alias → return records
  │
  └── 5. 合并 alias（去重/限长/限量/过滤值）
        └── withFieldAliasesAndFtsText + rebuildFtsText
```

每个失败点都直接 `return records`，不抛异常、不降级到 fake alias、不部分合并。

### 4.3 Alias 安全过滤

| 规则 | 实现 |
|---|---|
| 必须含 CJK 字符 | `containsCjk(alias)` |
| 长度 ≤ 20 | `alias.length() > MAX_ALIAS_LENGTH` → 过滤 |
| 非空 | `!hasText(alias)` → 过滤 |
| 不等于字段值 | 过滤 valueText/normalizedValue |
| 每字段最多 5 个 | LIMIT_PER_FIELD |
| 总计最多 20 个 | 合并后截断 |
| 去重 | distinct |

## 5. 测试结果

| 检查项 | 命令 | 结果 |
|---|---|---|
| git diff --check | `git diff --check` | **通过**（无输出） |
| redline | `bash scripts/scan-redline.sh special_cases_report.md` | **BLOCKER=0**, REVIEW=2065, ALLOWLIST=260 |
| 定向测试 | `mvn test -Dtest=FactCardTerminalUnitFieldAliasEnricherTests,CompilerPromptProviderTests` | **Tests run: 25, Failures: 0, Errors: 0, Skipped: 0** |
| 全量 mvn test | `mvn test` | **Tests run: 983, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |

定向测试明细：Enricher 12 个 + CompilerPromptProvider 13 个 = 25 个。

全量测试：基线 977（1E-1），新增 6 个 Enricher 测试，总数 983。与 fix report 一致。

## 6. Runtime Smoke

**未执行。** 原因：

1. `field-alias-enricher` role 未在模型绑定中注册，且本轮禁止新增或修改模型绑定
2. 无 role binding → routeResolution 返回 null → fail-closed → NoOp 路径
3. NoOp 路径已在 1E-1 验证通过
4. 全量 LLM 路径（route → generateText → JSON parse → alias merge）已在 12 个定向测试中完整覆盖

**建议：在授权 clean schema 端到端验证前，先注册 `field-alias-enricher` role 到模型绑定，以便真实 LLM 路径可被触发。**

## 7. 风险与下一步

### 7.1 风险评估

| 风险 | 等级 | 缓解 |
|---|---|---|
| LLM 生成质量不稳定 | 中 | fail-closed + 严格 alias 过滤（CJK/长度/去重） |
| LLM 增加 compile 耗时 | 低 | 仅对缺少 CJK alias 的英文字段触发；已有 CJK 的 XLSX/CSV 路径不触发 |
| Prompt 注入 eval 语言 | 低 | Prompt 无业务示例，输入不含文件名/query/eval |
| field-alias-enricher role 未绑定 | 阻塞 | 需在 runtime 验证前注册绑定 |

### 7.2 下一步建议

**全部验证项通过，建议交回项目架构师判断下一步：**

1. **若继续 1E-2**：注册 `field-alias-enricher` role 到模型绑定 → agentD clean schema 端到端验证（需清库/重导/compile/19 题 eval）
2. **若暂缓 1E-2**：当前 Phase 1D-1 已提交（21e25e9），1E-1 骨架 + 1E-2 实现可作为一个或多个 commit 提交

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- 未修改 redline 规则、allowlist、AGENTS.md、CLAUDE.md
- 未修改 fresh eval 题集、标准答案、case id、验收口径
- `docs/模型绑定配置参考.md` 存在无关本地配置脏改动，未引用、未修改
- 未 stage、未 commit、未 push
- 未清库、未重建 schema、未导入资料、未跑业务 eval
- 未新增或修改模型绑定
- 本轮新增报告：本文件
