# Terminal Unit Phase 1E: LLM Field Alias Enricher 可行性审计报告

审计时间：2026-05-29
审计人：agentB（只读架构审计）
审计范围：LLM 字段 alias 生成方案实现可行性

---

## 1. 一句话结论

**Phase 1E LLM alias Enricher 可行，建议拆分为 1E-1（interface + fake + Materializer ftsText 重建）+ 1E-2（LLM runtime + 集成）两轮实现。唯一阻塞点：`FactCardTerminalUnitRecord` 字段为 private final，无 setter，不能原地修改，需要 Materializer 暴露 ftsText 重建方法或新增 copy-with 构造路径。**

---

## 2. 审计发现明细

### 2.1 LLM 调用入口 — 可行

**现有基础设施**：`LlmGateway.generateText(scene, agentRole, purpose, systemPrompt, userPrompt)`

**现有调用模式**（来自 `CompileArticleNode.java`）：

```java
// Writer 调用
llmGateway.generateText("compile", "writer", "compile-article", systemPrompt, userPrompt)
// Title 生成调用
llmGateway.generateText("compile", "writer", "compile-title", systemPrompt, userPrompt)
```

**Enricher 建议 route**：

```java
llmGateway.generateText("compile", "field-alias-enricher", "enrich-field-aliases", systemPrompt, userPrompt)
```

- **scene**：`"compile"` — 与现有 compile 路径一致，复用 compile 的 LLM 绑定配置
- **agentRole**：`"field-alias-enricher"` — 新增 role，需在模型绑定配置中新增绑定
- **purpose**：`"enrich-field-aliases"` — 供审计和 debugging

**降级策略**：如果 `field-alias-enricher` role 未绑定，`LlmGateway` 的 `resolveBootstrapRoute` 会走 fallback 逻辑（取决于实现）。最坏情况：`generateText` 抛异常 → Enricher catch → 静默降级，不阻塞 compile。符合 fail-closed 要求。

**建议**：1E-1 使用 fake/stub 避免 LLM 绑定依赖，1E-2 再引入真实 LLM 调用。

### 2.2 Prompt 加载 — 可行

**现有模式**（`CompilerPromptProvider`）：

```
prompts/compiler/writer.md           → writerPrompt()
prompts/compiler/reviewer.md         → reviewerPrompt()
prompts/compiler/fixer.md            → fixerPrompt()
prompts/compiler/shared-grounding-rules.md → {{shared-grounding-rules}} 占位符替换
```

**新增文件**：`src/main/resources/prompts/compiler/field-alias-enricher.md`

**Pattern 一致**：
- 同目录、同格式（Markdown）
- 同样支持 `{{shared-grounding-rules}}` 占位符替换
- `CompilerPromptProvider` 新增 `fieldAliasEnricherPrompt()` 方法（~5 行）
- 构造器中加载，启动时校验文件存在且非空

**Prompt 内容红线约束**：
- 禁止写入"最长借用天数"等具体业务词作为示例
- 禁止提及具体文件名、case id、eval 题面
- 只能描述通用任务："为英文字段名生成中文检索别名"
- 输入字段描述只能描述结构（terminalKey, keyPath, siblingKeys, valueType）

### 2.3 集成点 — 可行但需注意

**当前调用路径**（`FactCardGenerationService.java`）：

```java
// line 106-108: rebuildForSourceFile()
for (FactCardRecord factCardRecord : factCardRecords) {
    FactCardRecord savedFactCardRecord = factCardJdbcRepository.upsert(factCardRecord);
    materializeTerminalUnits(savedFactCardRecord);  // <-- 集成点
}

// line 171-178: materializeTerminalUnits()
private void materializeTerminalUnits(FactCardRecord factCardRecord) {
    List<FactCardTerminalUnitRecord> terminalUnitRecords =
            factCardTerminalUnitMaterializer.materialize(factCardRecord);    // 1. 物化
    factCardTerminalUnitJdbcRepository.upsertAll(terminalUnitRecords);      // 2. 持久化
}
```

**Enricher 插入位置**：在 Materializer 返回 records 之后、Repository upsert 之前：

```java
private void materializeTerminalUnits(FactCardRecord factCardRecord) {
    List<FactCardTerminalUnitRecord> terminalUnitRecords =
            factCardTerminalUnitMaterializer.materialize(factCardRecord);
    // [新增] LLM alias 增强
    if (fieldAliasEnricher != null) {
        terminalUnitRecords = fieldAliasEnricher.enrich(terminalUnitRecords, factCardRecord);
    }
    factCardTerminalUnitJdbcRepository.upsertAll(terminalUnitRecords);
}
```

### 2.4 Source Content 输入 — 部分可行

**阻塞点**：`materializeTerminalUnits` 方法只接收 `FactCardRecord`，不接收 source chunk。`FactCardRecord.itemsJson` 包含结构化提取后的数据，**不包含原始源文件文本**。

**两种输入策略**：

| 策略 | 输入范围 | 可行性 | 风险 |
|---|---|---|---|
| **A) 字段结构输入** | terminalKey, keyPath, parentPath, sibling keys, sibling values, valueType | **立即可行**，不需改数据流 | LLM 只能从字段名推测语义（如 `max_borrow_days` → "最大借用天数"），对语义不明确的字段（如 `quota`）效果有限 |
| **B) 字段结构 + 源内容** | A + source chunk text / itemsJson raw fields | 需要修改 `rebuildForSourceFile` 数据流，将 source chunks 传递到 `materializeTerminalUnits` | 增加方法签名变更范围 |

**推荐**：**1E-1 使用策略 A**（字段结构输入），1E-2 仍用策略 A。策略 B 留作 1E-3 增强（如果 1E-2 验证后字段名翻译效果不足）。

**合规确认**：策略 A 不涉及文件名、eval 题面、query 日志。只传字段路径和兄弟字段值。

**支持证据**：现有的 writer.md / reviewer.md prompt 都是基于 chunk text 做结构化提取，Enricher 只需要字段结构。`itemsJson` 中每个 item 已经包含 `raw` 字段（源文本行），可用于提供字段上下文。

### 2.5 数据模型 — 需要 Materializer 协作

**关键发现**：`FactCardTerminalUnitRecord` 是普通 class，但字段全为 `private final`（29 个字段），无 setter。

```java
public class FactCardTerminalUnitRecord {
    private final String fieldAliasesJson;  // 无 setter
    private final String ftsText;           // 无 setter
    // ... 27 more private final fields
}
```

**影响**：设计报告中的伪代码 `record.setFieldAliasesJson(...)` 不可行。

**解决方案（推荐方案 B）**：

| 方案 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| **A) Enricher 新建完整 record** | 用 25+ 参数的构造器复制所有字段 | 不修改 Materializer | 构造器过长，容易出错，且需访问所有字段 |
| **B) Materializer 暴露 ftsText 重建方法** | Enricher 构建新的 `fieldAliasesJson`，Materializer 提供方法从旧 record + 新 aliases 重建 ftsText，Enricher 用 Materializer 的私有构造逻辑创建新 record | 职责清晰 | 需要 Materializer 暴露一个方法 |
| **C) 新增 copy-with 方法** | 在 `FactCardTerminalUnitRecord` 上新增 `withFieldAliasesAndFtsText(newAliasesJson, newFtsText)` 方法 | 最干净 | 修改 persistence 层 |

**推荐方案 B + C 混合**：
1. `FactCardTerminalUnitMaterializer` 新增 public 方法：`String rebuildFtsText(FactCardTerminalUnitRecord record, List<String> newAliases)` — 基于旧 record 的字段 + 新 aliases 重建 ftsText
2. `FactCardTerminalUnitRecord` 新增 package-private copy 方法：仅修改 `fieldAliasesJson` 和 `ftsText` 两个字段，其余字段透传

**或更简单的方案**：Enricher 返回 `List<Pair<FactCardTerminalUnitRecord, String>>`（原 record + 新 fieldAliasesJson），由 `materializeTerminalUnits` 负责合并和重建。

### 2.6 红线合规 — 严格可控

| 检查项 | 状态 | 控制措施 |
|---|---|---|
| LLM prompt 不包含文件名 | **可控** | Enricher 不接收 sourceFileId，不使用 `factCardRecord.getSourceFileId()` 反查文件名 |
| LLM prompt 不包含 eval 题面 | **可控** | prompt 只包含字段结构，不包含任何 query 文本 |
| LLM input 不包含 query 日志 | **天然合规** | compile 阶段完全无 query 上下文 |
| 生成的 alias 不写入 Java 代码 | **可控** | alias 只写入数据库 `field_aliases_json` |
| 不硬编码映射表 | **可控** | 无任何 `Map.of("max_borrow_days", ...)` |
| hidden eval 不接触 | **天然合规** | Enricher 输入全部来自 compile 数据流 |
| prompt 不含业务域预设 | **可控** | prompt 只描述"为英文字段名生成中文别名"的通用任务，不举例业务词 |

**建议审计**：1E-2 完成后，agentD 应检查 YAML 5 题 target unit 的 alias 内容，确认无 eval 题面 wording 污染。

### 2.7 Schema 变更 — 不需要

所有目标列已存在。`field_aliases_json` 内容增加，`fts_text` 相应重建，`search_tsv` 由 upsert 时 PostgreSQL 自动通过 `to_tsvector('simple', fts_text)` 重新生成。

---

## 3. 推荐实现拆分

### 3.1 两轮拆分

| 轮次 | 范围 | 交付物 | LLM 调用 |
|---|---|---|---|
| **1E-1** | Interface + Fake + Materializer ftsText 重建 + 集成骨架 + 单测 | Enricher 接口定义、fake 实现、Materializer rebuildFtsText、`materializeTerminalUnits` 集成点、全量单测 | **无**（fake 返回硬编码 alias，只验证集成正确性） |
| **1E-2** | LLM runtime + prompt 文件 + 真实集成 + 补充单测 | `LlmGateway` 调用实现、`field-alias-enricher.md` prompt、模型绑定配置、真实 LLM 测试 | **有**（真实 LLM 调用，需新增 agentRole 绑定） |

### 3.2 1E-1 精确允许修改文件清单

| 文件 | 变更 | 行数估算 |
|---|---|---|
| `FactCardTerminalUnitMaterializer.java` | 新增 public `rebuildFtsText(record, newAliases)` 方法 | ~15 行 |
| `FactCardTerminalUnitFieldAliasEnricher.java` | **新增** Interface + Fake 实现 | ~40 行 |
| `FactCardGenerationService.java` | 注入 Enricher，在 `materializeTerminalUnits` 中插入调用 | ~10 行 |
| `FactCardTerminalUnitFieldAliasEnricherTests.java` | **新增** 单测（fake 场景） | ~60 行 |

**总计：约 125 行。**

1E-1 不涉及 LLM 调用、不涉及 prompt 文件、不涉及模型绑定。

### 3.3 1E-2 精确允许修改文件清单（追加于 1E-1 之上）

| 文件 | 变更 | 行数估算 |
|---|---|---|
| `FactCardTerminalUnitFieldAliasEnricher.java` | 新增 `LlmFieldAliasEnricher` @Service 实现（真实 LLM 调用） | ~100 行 |
| `src/main/resources/prompts/compiler/field-alias-enricher.md` | **新增** LLM system prompt | ~30 行 |
| `CompilerPromptProvider.java` | 新增 `fieldAliasEnricherPrompt()` 方法 | ~10 行 |
| `FactCardGenerationService.java` | 注入 `LlmGateway` 到 Enricher 实现 | 无需修改（通过 DI 自动装配） |
| `FactCardTerminalUnitFieldAliasEnricherTests.java` | 补充 LLM 成功/失败/超时场景测试 | ~40 行 |

**总计：约 180 行追加。**

### 3.4 精确禁止修改文件清单（1E-1 + 1E-2 共同遵守）

| 文件/区域 | 原因 |
|---|---|
| `FactCardTerminalUnitIntentReranker.java` | Phase 1B 已实现 |
| `FactCardTerminalUnitFtsSearchService.java` | 检索逻辑不变 |
| `FactCardTerminalUnitMapper.xml` | SQL 不变 |
| `QuerySemanticRules.java` / `lattice-query-semantic.yml` | 数值意图配置不变 |
| `LexicalSearchTokenBudget.java` | Phase 1C 已修复 |
| `AnswerGeneration*` / fallback / citation | **query 主链禁止修改** |
| `schema.sql` | 不需要 DDL 变更 |
| `scripts/scan-redline.sh` / allowlist | 禁止放宽 |
| `docs/模型绑定配置参考.md` | 禁止修改 |
| `special_cases_report.md` | redline 输出 |
| Fresh eval 题集/标准答案/验收口径 | 禁止修改 |

---

## 4. 关键风险与缓解

| 风险 | 等级 | 缓解 |
|---|---|---|
| `FactCardTerminalUnitRecord` 无 setter | **中** | 1E-1 先解决：Materializer 暴露 `rebuildFtsText` + record copy 路径 |
| Enricher 无 source content 访问 | **低** | 字段结构输入已足够（LLM 可从 `max_borrow_days` 推断"最大借用天数"） |
| LLM alias 质量不可控 | **低** | Fail-closed；alias 只追加不覆盖，最坏情况等同于现有行为 |
| `field-alias-enricher` role 未绑定时行为 | **低** | 同 llm review 的 fail-closed 模式：异常 catch → 静默降级 |

---

## 5. 信息不足项（0 项）

本轮审计已覆盖所有关键路径。agentD 在 1E-2 验证阶段需要补充：
- YAML 5 题 target unit 的 LLM 生成 alias 实际内容审计（防止 eval 污染）
- 但这不是 agentA 实现前需要的信息，留到验证阶段即可。

---

## 6. AgentA 下一轮完整提示词草案（1E-1: Interface + Fake + 集成骨架）

```
你是 agentA，本轮任务：实现 Terminal Unit Phase 1E-1 Interface + Fake + 集成骨架。

目标：
定义 FactCardTerminalUnitFieldAliasEnricher 接口，
实现一个 hardcoded fake（无 LLM 依赖），
提供 Materializer ftsText 重建能力，
完成集成骨架和全量单测。
1E-1 不调用真实 LLM，只为 1E-2 铺路。

允许新增文件：
- src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java
- src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricherTests.java

允许修改文件：
- FactCardTerminalUnitMaterializer.java：新增 public rebuildFtsText(record, newAliases) 方法
- FactCardGenerationService.java：注入 Enricher，在 materializeTerminalUnits 中插入

禁止修改文件：
- FactCardTerminalUnitIntentReranker.java / FactCardTerminalUnitFtsSearchService.java
- QuerySemanticRules.java / lattice-query-semantic.yml
- LexicalSearchTokenBudget.java
- FactCardTerminalUnitMapper.xml / schema.sql
- AnswerGeneration* / fallback / citation
- scripts/scan-redline.sh / allowlist

实现要点：
1. Enricher 接口定义：
   - 单一方法 List<FactCardTerminalUnitRecord> enrich(List<FactCardTerminalUnitRecord> records, FactCardRecord factCard)
   - 返回修改后的 records（不能原地修改，因为 Record 字段为 final）
2. Fake 实现：
   - 仅对 fieldLabel 不含 CJK 的 record 追加 1-2 个硬编码中立 alias（如 "功能指标"、"限制数量"）
   - 别名只能基于 valueType 生成（number→"数值"、"数量"；version→"版本标识"；string→跳过）
   - 不包含任何业务域语义（不写 "借用天数"、"押金" 等）
   - Fake 别名仅用于验证集成正确性，不期望在真实 query 中产生语义匹配
3. Materializer rebuildFtsText：
   - 输入：FactCardTerminalUnitRecord + 新的 fieldAliases list
   - 输出：新的 ftsText 字符串
   - 复用现有 buildFtsText 逻辑，使用 record 的已有字段值
4. FactCardGenerationService 集成：
   - 注入 Enricher（@Autowired(required = false)，允许未配置时跳过）
   - 在 materialize() 和 upsertAll() 之间调用 enrich()
   - enrich 返回的 records 用于 upsert
5. record 重建：
   - 由于 FactCardTerminalUnitRecord 字段为 final，Enricher 需要通过构造器创建新 record
   - 仅修改 fieldAliasesJson 和 ftsText，其余字段透传
   - 需要在 FactCardTerminalUnitRecord 上新增 withUpdatedAliases 辅助方法（package-private），或在 Enricher 中直接调用完整构造器

测试要求：
- 使用 synthetic 数据，不使用 fresh eval 字段名/文件名/答案值
- 验证 CJK fieldLabel 不触发 enrich（无修改）
- 验证非 CJK fieldLabel 触发 enrich（aliases 增加、ftsText 包含新 alias）
- 验证 ftsText 包含新 alias
- 验证原有 aliases 不被覆盖（只追加）
- 验证 Enricher 为 null 时流程正常（集成不受影响）
- 验证 record 重建后其他字段不透传错误

验证命令：
1. git diff --check
2. bash scripts/scan-redline.sh special_cases_report.md
3. mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitFieldAliasEnricherTests,FactCardTerminalUnitMaterializerTests test
4. mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test

禁止 stage / commit / push。
```

---

## 7. 附录：审计数据清单

| 审计项 | 结论 | 源码位置 |
|---|---|---|
| LLM 调用入口 | 可行 — `LlmGateway.generateText(scene, agentRole, purpose, systemPrompt, userPrompt)` | `LlmGateway.java:226` |
| Prompt 加载模式 | 可行 — `CompilerPromptProvider` 从 classpath 加载 `.md` 文件 | `CompilerPromptProvider.java:38-73` |
| 集成点 | 可行 — `materializeTerminalUnits()` 中 Materializer 后、upsert 前 | `FactCardGenerationService.java:171-178` |
| Record 可变性 | **需处理** — 字段为 `private final`，无 setter | `FactCardTerminalUnitRecord.java:55,69` |
| Source content 访问 | 部分可行 — itemsJson 有结构化数据，但无原始文本 | `FactCardGenerationService.java:171` |
| Schema 变更 | 不需要 | — |
| 现有 prompt 文件清单 | writer.md, reviewer.md, fixer.md, writer-image.md, reviewer-image.md, shared-grounding-rules.md | `src/main/resources/prompts/compiler/` |
| 现有 scene/role | scene="compile", role="writer" | `CompileArticleNode.java` |

## 合规声明

- 本轮未修改任何文件
- 本轮未 stage/unstage/commit/push
- 本轮未清库/重建/重导
- 本轮未读取 hidden eval
- 本轮新增报告：`terminal_unit_phase1e_llm_alias_enricher_feasibility_report.md`
