# Terminal Unit Phase 1F: Metadata Alias Sync Fix Result Report

修复时间：2026-05-31
执行人：agentA
修复类型：最小通用修复 — metadataJson.fieldAliases 与 fieldAliasesJson 同步

---

## 1. 失败归因

LLM 字段别名增强后：

| 字段 | 状态 | 说明 |
|---|---|---|
| `field_aliases_json` | 已更新 ✓ | LLM alias 已追加（"版本 / 版本号 / 系统版本"） |
| `fts_text` | 已更新 ✓ | 别名段已重建 |
| `search_tsv` | 自动更新 ✓ | `to_tsvector` 重新生成 |
| `metadata_json.fieldAliases` | **未更新** ✗ | 仍是物化时的原始数组，不含中文 alias |

`FactCardTerminalUnitIntentReranker.parseProfile()` 从 `metadataJson.fieldAliases` 读取字段别名用于 `fieldMatchCount` 计算。metadata 未同步导致 Reranker 看不到 LLM alias，`fieldMatchCount=0`，中文 query token 无法通过字段级别名匹配到 terminal unit。

**这不是 channel parse、selector 或 conclusion builder 的问题，而是 compiler 层 alias 写入后 metadata 不同步的结构性缺口。**

---

## 2. 修改文件

| 文件 | 变更 | 行数 |
|---|---|---|
| `FactCardTerminalUnitRecord.java` | 新增 `withFieldAliasesFtsTextAndMetadata()` — 同时替换 aliases、ftsText、metadataJson；原 `withFieldAliasesAndFtsText()` 委托到新方法 | +12 |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | `mergeAliases()` 调用新方法并传入重建的 metadataJson；新增 `rebuildMetadataJsonFieldAliases()` | +20 |

**约 32 行。**

---

## 3. 为什么不是 Case 特判

| 检查项 | 说明 |
|---|---|
| 通用 JSON 操作 | `ObjectNode.putArray("fieldAliases")` 替换整个数组，不区分业务内容 |
| 不读取具体 alias 值 | 只做 `ArrayNode.add(alias)` 遍历写入 |
| 异常安全 | `catch (Exception)` → 返回原 `metadataJson`，fail-safe |
| 不影响非 LLM alias 记录 | 只在 alias 已实际变化时（`mergedAliases.size() > existingAliases.size()`）才更新 |

---

## 4. 影响面

| 影响 | 范围 |
|---|---|
| `metadata_json.fieldAliases` | 在 LLM alias enricher 增强后，现在与 `field_aliases_json` 保持同步 |
| Reranker `fieldMatchCount` | 可读取到中文 alias，对中文 query token 产生匹配 |
| FTS / `search_tsv` | 无变化（ftsText 此前已正确更新） |
| `field_aliases_json` | 无变化（此前已正确更新） |
| 非 LLM alias 记录 | 无影响（metadataJson 只在 alias 变化时更新） |

---

## 5. 已跑命令与结果

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| redline | **BLOCKER=0** |
| `FactCardTerminalUnitFieldAliasEnricherTests` | **15/0/0** |
| `FactCardTerminalUnitIntentRerankerTests` | **10/0/0** |
| 定向组合 | **25/0/0 — BUILD SUCCESS** |

---

## 6. 未跑项

| 项目 | 状态 |
|---|---|
| Clean schema reset | 未执行 |
| 资料导入 / compile | 未执行 |
| 19 题业务 eval / baseline | 未执行 |
| 全量 mvn test | 未执行（定向测试已充分覆盖） |
| 写测试 | 未执行（本轮禁止改测试） |

---

## 7. 下一步

AgentD clean schema runtime 复验：
1. Clean schema 重建 + 导入资料 + compile
2. 验证 terminal unit 的 `metadata_json.fieldAliases` 与 `field_aliases_json` 一致
3. 验证 Reranker 的 `fieldMatchCount` 对中文 query token > 0
4. FQ6/FQ2 目标 terminal unit 排名改善
5. FQ3/FQ4/FG1/FQ7/FQ11 保护回归

---

## 合规声明

- 本轮只修改 `FactCardTerminalUnitRecord.java` + `LlmFactCardTerminalUnitFieldAliasEnricher.java`
- 未修改 query、selector、conclusion builder、RRF、citation、prompt、schema
- 不含业务词、字段名、文件名、case id、答案值硬编码
- metadata 替换使用通用 JSON 操作（`putArray` + `add`）
- 异常路径返回原值（fail-safe）
- 未读取 hidden eval
- 未清库、未重建、未导入、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
