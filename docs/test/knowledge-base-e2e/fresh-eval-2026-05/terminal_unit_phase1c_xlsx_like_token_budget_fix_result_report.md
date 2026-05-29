# Terminal Unit Phase 1C: LIKE Token 预算与排序修复结果报告

验证时间：2026-05-29
验证人：agentA
验证对象：LexicalSearchTokenBudget 中文 LIKE token 预算与排序策略修复

## 1. 结论

本轮修改 `LexicalSearchTokenBudget` 的两个参数，解决了中文查询中有效 CJK bigram 被长 N-gram 和结构化 token 挤出 LIKE 候选集的问题。

**未 stage、未 commit、未 push。**

## 2. 修改摘要

### 2.1 修改文件

| 文件 | 变更 |
|---|---|
| `src/main/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudget.java` | 2 处修改 |
| `src/test/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudgetTests.java` | 新增 4 个测试，更新 1 个测试断言 |

### 2.2 修改详情

**修改 1 — MAX_LIKE_TOKENS (line 19):**

```java
// Before:
private static final int MAX_LIKE_TOKENS = 8;

// After:
private static final int MAX_LIKE_TOKENS = 32;
```

**修改 2 — CJK token 评分公式 (line 103):**

```java
// Before:
if (isCjkToken(token)) {
    return token.length() >= 2 ? 220 + Math.min(token.length(), 20) : 0;
}

// After:
if (isCjkToken(token)) {
    return token.length() >= 2 ? 230 - Math.min(token.length(), 8) : 0;
}
```

**效果对照：**

| CJK token 长度 | 旧评分 | 新评分 | 排序变化 |
|---|---|---|---|
| 2 (bigram) | 222 | **228** | 最低 → 最高 |
| 3 (trigram) | 223 | **227** | 中等 → 次高 |
| 4 (quadgram) | 224 | **226** | 最高 → 再次 |
| 8+ | 228 | **222** | 最高 → 最低 |

干净的 2-char CJK bigram 现在在 LIKE 候选排序中优先级最高，避免了 quadgram 中相邻字符污染 LIKE 匹配的问题。

## 3. 通用性论证：为什么不是特判

### 3.1 不涉及任何业务词

代码中不存在任何 fresh eval 业务词的判断：
- 不判断 token 是否为文件名
- 不判断 token 是否为 "xlsx" / "csv" / "yaml" 等扩展名
- 不判断 CJK token 的内容是否为 "存储"、"保管"、"化学品" 等业务词
- 不区分 XLSX query vs CSV query vs YAML query

### 3.2 纯通用 token 形态规则

两个修改仅依赖 token 的**形态属性**（长度、字符集），不依赖 token 的**语义内容**：

- `MAX_LIKE_TOKENS = 32`: 纯预算参数。控制 LIKE 条件的最大数量，与 token 内容无关。
- `230 - Math.min(length, 8)`: CJK token 评分仅依赖 `token.length()`——字符数。bigram=228, trigram=227, quadgram=226。不读取 token 中的具体字符。

### 3.3 可复现

任何包含以下特征的查询都会同等受益：
- 中文查询中包含结构化标识符 token（如文件名 "xxx.pdf"、路径 "/a/b/c"）
- 查询中有较长 CJK 片段产生大量 N-gram
- 关键的匹配 token 是短 CJK bigram

## 4. 影响面说明

### 4.1 LexicalSearchTokenBudget 的复用范围

`LexicalSearchTokenBudget.selectLikeTokens()` 被以下检索服务间接调用：

| 调用链 | 影响 |
|---|---|
| `FactCardJdbcRepository.searchLexical()` → fact card FTS | LIKE token 预算从 8 → 32, CJK bigram 优先 |
| `FactCardTerminalUnitJdbcRepository.searchLexical()` → terminal unit FTS | 同上 |
| `SourceChunkJdbcRepository.searchLexical()` → source chunk FTS | 同上 |
| `SourceFileJdbcRepository.searchLexical()` → source file FTS | 同上 |
| `RefKeySearchJdbcRepository.searchLexical()` → refkey FTS | 同上 |

### 4.2 性能影响评估

- **LIKE 条件数**：从最多 8 个 → 最多 32 个。每个 LIKE 条件是对已建立索引的 `tsvector`/`text` 列的简单字符串匹配。PostgreSQL 处理 32 个 OR LIKE 的开销可忽略（微秒级）。
- **SQL 长度**：每个 LIKE token 约 10-20 字节（含 `%token%`），32 个增加约 500 字节 SQL，远低于 PostgreSQL 的 SQL 长度限制。
- **无新增 JOIN / 子查询 / 复杂表达式**。

### 4.3 兼容性

- `selectLikeTokens()` 的返回值格式不变（`List<String>`）
- 调用方完全无感知（接口签名不变）
- 如果 LIKE token 不足 32 个，按实际数量返回

## 5. 测试结果

### 5.1 Redline 扫描

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

**结果：exit=0，BLOCKER=0。**

### 5.2 定向测试

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
  -Dtest=LexicalSearchTokenBudgetTests,FactCardTerminalUnitFtsSearchServiceTests test
```

**结果：Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**

| # | 测试 | 覆盖点 | 状态 |
|---|---|---|---|
| 1 | shouldNormalizeTokensByCaseAndOrder | 原有：小写去重 | PASS |
| 2 | shouldSelectBoundedHighSignalTokensForLikeConditions | 更新：新预算 32，结构化 token 优先 | PASS |
| 3 | shouldEnforceMaxLikeTokenCap | **新增**：50 个 token 截断至 32 | PASS |
| 4 | shouldRankCjkBigramAboveTrigramAndQuadgram | **新增**：bigram(228) > trigram(227) > quadgram(226) | PASS |
| 5 | shouldNotCrowdOutCjkBigramWhenStructuredTokensPresent | **新增**：结构化 token + CJK quadgram 不挤出 bigram | PASS |
| 6 | shouldScoreNumericTokensCorrectly | **新增**：数字 token 评分正确 | PASS |
| 7-9 | FactCardTerminalUnitFtsSearchServiceTests (3 tests) | 原有：FTS 搜索功能不退化 | PASS |

### 5.3 全量 Maven Test

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

**结果：Tests run: 965, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**

基线：Phase 1C Extractor Fix 全量为 961。本轮新增 4 个 LexicalSearchTokenBudget 测试，总数 965。

## 6. 测试数据审计

所有新增测试使用以下 synthetic 数据，不包含任何 fresh eval 业务词：

| 测试 | 使用的 token | 避免的词 |
|---|---|---|
| shouldEnforceMaxLikeTokenCap | 一甲、一乙、...、十戊（50 个纯 CJK bigram） | 存储、保管、化学品、维护等级、危险等级 |
| shouldRankCjkBigramAboveTrigramAndQuadgram | 甲乙、甲乙丙、甲乙丙丁、状态值、配置项、处理结果 | 同上 |
| shouldNotCrowdOutCjkBigramWhenStructuredTokensPresent | example-document.xlsx、项目一甲、...、字段 | 同上 |
| shouldScoreNumericTokensCorrectly | 1、42、100、9999 | 无关 |

未使用任何 fresh eval 题面、case id、expected answer、文件名（除通用 example-document.xlsx）、列名。

## 7. 未修改清单

| 文件/区域 | 说明 |
|---|---|
| `FactCardTerminalUnitMaterializer.java` | 未修改 |
| `FactCardTerminalUnitIntentReranker.java` | 未修改 |
| `FactCardTerminalUnitFtsSearchService.java` | 未修改 |
| `FactCardGenerationService.java` / `FactCardGeneration*Support.java` | 未修改 |
| `ExcelTextExtractor.java` / `CsvTextExtractor.java` | 未修改 |
| `QueryTokenExtractor.java` | 未修改 |
| `RetrievalStrategyResolver.java` | 未修改 |
| `KnowledgeSearchService.java` / `QueryGraphDefinitionBaseSupport.java` | 未修改 |
| `AnswerGeneration*` / fallback 相关 | 未修改 |
| `FactCardTerminalUnitMapper.xml` / 所有 mapper SQL | 未修改 |
| `schema.sql` | 未修改 |
| `lattice-query-semantic.yml` / 所有配置文件 | 未修改 |
| `scripts/scan-redline.sh` / allowlist | 未修改 |
| 题集、资料包、标准答案、hidden eval | 未修改、未读取 |

## 8. 下一步建议

1. **agentD clean schema 重导验证**：
   - 清库、重导 5 份 fresh eval 资料、重新编译
   - 验证 FQ7 的 `fact_card_terminal_fts` 是否从 0 hit 恢复到 >0 hit
   - 验证 FQ11 (CSV) 的 6 hits 不退化
   - 验证 YAML 5 题行为不变
   - 验证全量 19 题 fresh eval 无新增回归

2. **保护回归** (agentD):
   - Q6 terminal field alias
   - S2 chunk/anchor identity

3. **提交顺序建议**:
   ```
   Commit 1: Phase 1B (Reranker + numericIntent + config)
   Commit 2: Phase 1C Layer 1 (中文 N-gram alias)
   Commit 3: Phase 1C Extractor Fix (XLSX/CSV 结构化行)
   Commit 4: Phase 1C LIKE Token Budget Fix (本轮)
   ```

## 合规声明

- 本轮仅修改 2 个文件：`LexicalSearchTokenBudget.java` + 对应测试
- 未 stage、未 commit、未 push
- 未修改 query/answer/fallback/citation/compiler/extractor/SQL/config/scripts
- 未读取 hidden eval
- 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
- 测试数据使用 synthetic 通用 CJK token（一甲、甲乙等），不使用业务词
