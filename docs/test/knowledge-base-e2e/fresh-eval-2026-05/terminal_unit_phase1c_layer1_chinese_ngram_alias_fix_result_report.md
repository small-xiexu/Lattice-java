# Terminal Unit Phase 1C Layer 1: 中文 N-gram Alias 物化修复结果报告

验证时间：2026-05-29
验证人：agentA
验证对象：在 FactCardTerminalUnitMaterializer 中新增中文 fieldLabel/表头/列名的 N-gram alias 生成能力

## 1. 结论

本轮实现了 Phase 1C Layer 1：在 `FactCardTerminalUnitMaterializer` 中新增 `addChineseNgramAliases` 方法，对源文件自带的 fieldLabel / keyPath 中的中文片段生成 bigram + trigram 别名。使中文 query token 能在 terminal unit FTS 检索中匹配到中文命名的字段。

**本轮只做 Layer 1，没有做 Layer 2（sibling context），没有做 Layer 3（LLM alias）。**

未 stage、未 commit、未 push。

## 2. 修改文件清单

### 修改文件

| 文件 | 变更说明 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializer.java` | 新增 `BRACKET_CONTENT_PATTERN`、`CJK_RUN_PATTERN` 两个编译时常量；新增 `addChineseNgramAliases()` 方法；在 `buildFieldAliases()` 中对 fieldLabel 和 keyPath 调用该方法 |
| `src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializerTests.java` | 新增 4 个测试：中文 N-gram 生成、单字跳过、英文不退化、超长文本不生成噪声 |

### 未修改（遵守禁令）

- `FactCardTerminalUnitIntentReranker.java`、`FactCardTerminalUnitFtsSearchService.java`、`QuerySemanticRules.java`、`lattice-query-semantic.yml`：未修改
- `FactCardGenerationService.java`、`schema.sql`：未修改
- 任何 query/answer/fallback/citation/vector/prompt/scripts/redline allowlist：未修改
- fresh eval 题集、标准答案、hidden eval、模型私有配置：未读取、未修改

## 3. 实现细节

### 3.1 新增常量

```java
private static final Pattern BRACKET_CONTENT_PATTERN = Pattern.compile("[（(][^）)]*[）)]");
private static final Pattern CJK_RUN_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]{2,}");
```

- `BRACKET_CONTENT_PATTERN`：匹配中英文括号及其内容，用于移除 "(天)"、"（单位）" 等括号注释
- `CJK_RUN_PATTERN`：匹配 2+ 连续 CJK 字符组成的纯中文片段，天然跳过单字和英文/数字

### 3.2 `addChineseNgramAliases` 方法

```java
private void addChineseNgramAliases(Set<String> aliases, String value) {
    // 1. 移除括号内容: "维护周期(天)" → "维护周期"
    String cleaned = BRACKET_CONTENT_PATTERN.matcher(value).replaceAll("");
    // 2. 提取 2+ 连续 CJK 字符的纯中文片段
    Matcher cjkRunMatcher = CJK_RUN_PATTERN.matcher(cleaned);
    while (cjkRunMatcher.find()) {
        String cjkRun = cjkRunMatcher.group();
        // 3. 只处理 2-8 字片段
        if (cjkRun.length() < 2 || cjkRun.length() > 8) continue;
        // 4. 保留完整片段
        aliases.add(cjkRun);
        // 5. 生成 bigram
        for (int i = 0; i + 2 <= cjkRun.length(); i++)
            aliases.add(cjkRun.substring(i, i + 2));
        // 6. 生成 trigram
        for (int i = 0; i + 3 <= cjkRun.length(); i++)
            aliases.add(cjkRun.substring(i, i + 3));
    }
}
```

### 3.3 调用点

在 `buildFieldAliases()` 中，现有别名生成逻辑之后追加：

```java
addChineseNgramAliases(aliases, fieldLabel);
addChineseNgramAliases(aliases, keyPath);
```

### 3.4 算法特性

| 特性 | 实现 |
|---|---|
| 来源 | 仅从 fieldLabel / keyPath 中已有的中文片段生成 |
| N-gram 类型 | bigram (2-gram) + trigram (3-gram) + 完整片段 |
| 跳过单字 | CJK_RUN_PATTERN 要求 `{2,}`，天然跳过单字 CJK 字符 |
| 长度限制 | 只处理 2-8 个中文字符的片段，超长句子跳过 |
| 括号处理 | "维护周期(天)" → 移除 "(天)" → 提取 "维护周期" |
| 不读 sibling value | 未使用 items_json 中其他 item 的 value |
| 不读 eval 数据 | 未读取题面、case id、expected answer、query 日志 |

## 4. 硬编码业务映射审计

**不存在任何中英文映射表。**

搜索结果：
```bash
rg -n "押金|deposit_amount|逾期|late_fee_per_day|最长借用|max_borrow_days|最大并发|max_concurrent_requests" \
  FactCardTerminalUnitMaterializer.java
```
唯一命中为 `inferValueType` 中的 `return "version";`（第 614 行），这是通用值类型推断逻辑，返回字符串 `"version"` 表示值形态为版本号格式，与 `"number"`、`"boolean"`、`"string"` 同级。**不是业务字段映射。**

代码中不存在：
- `if (key.equals("deposit_amount")) aliases.add("押金")`
- `Map.of("deposit", "押金", "borrow", "借用")`
- 任何将英文字段名映射到中文语义的硬编码

## 5. alias 来源分析

| 来源 | 是否使用 | 说明 |
|---|---|---|
| fieldLabel 中文片段 | **是** | 如 CSV/XLSX 中文列头 "维护周期(天)" → 提取 "维护周期" |
| keyPath 中文片段 | **是** | 如路径中有中文段 |
| 括号内内容 | **排除** | "(天)" 被 BRACKET_CONTENT_PATTERN 移除 |
| sibling value | **否** | Layer 2 范围，本轮不实现 |
| LLM 生成 | **否** | Layer 3 范围，本轮不实现 |
| 硬编码映射表 | **否** | 红线禁止 |
| eval 题面/答案 | **否** | 红线禁止 |

## 6. 验证结果

### 6.1 Redline 扫描

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：exit=0，**BLOCKER=0**，REVIEW=2059，ALLOWLIST=259

### 6.2 定向测试

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
  -Dtest=FactCardTerminalUnitMaterializerTests test
```

结果：**Tests run: 8, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

| # | 测试 | 覆盖点 | 状态 |
|---|---|---|---|
| 1 | shouldMaterializeKeyValueListItems | 原有：scalar assignment 展开 | PASS |
| 2 | shouldMaterializePathAwareItems | 原有：path-aware 保留 parentPath/keyPath | PASS |
| 3 | shouldFilterEmptyContainerAndLongTextItems | 原有：空值/容器/过长文本跳过 | PASS |
| 4 | shouldBuildAliasesFromSourcePathAndGenericSplittingOnly | 原有：英文别名不含手动提示 | PASS |
| 5 | shouldGenerateChineseNgramAliasesFromChineseFieldLabel | **新增**："维护周期(天)" → 含 "维护周期""维护""周期""维护周""护周期"，排除 "天" | PASS |
| 6 | shouldNotGenerateNgramAliasesFromSingleCjkChar | **新增**：单字 CJK "单" 不生成 N-gram | PASS |
| 7 | shouldNotDegradeEnglishFieldLabelAliases | **新增**：英文 fieldLabel 别名逻辑不退化 | PASS |
| 8 | shouldNotGenerateNgramAliasesForVeryLongChineseText | **新增**：>8 字中文文本不生成子串 | PASS |

### 6.3 全量 Maven Test

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：**Tests run: 951, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

基线：Phase 1B + redline fix + config binding fix 全量为 947。本轮新增 4 个 Materializer 测试，总数 951。

## 7. 是否仍只保留一个变量

是。本轮只有一个可归因变量：在 `FactCardTerminalUnitMaterializer.buildFieldAliases()` 中新增中文 N-gram alias 生成。未修改 Reranker、FTS Search、QuerySemanticRules、YAML 配置、schema 或任何 query/fallback 层代码。

## 8. 示例：中文 fieldLabel 的 alias 变化

以 fieldLabel = "维护周期(天)" 为例（synthetic fixture）：

**Before (Phase 1A):**
```
["维护周期(天)", "维护周期(天)", "维护周期 天", ...]
```
仅完整字符串及其标点替换变体，无子串。

**After (Phase 1C Layer 1):**
```
["维护周期(天)", "维护周期(天)", "维护周期 天", ..., "维护周期", "维护", "护周", "周期", "维护周", "护周期"]
```
新增中文 bigram + trigram + 完整片段。中文 query token（如 "维护周期"、"周期"、"维护"）现在能精确匹配到 fieldAliases → Reranker 的 `fieldMatchCount` 可以增加 → terminal unit 排序提升。

## 9. 明确未 Stage、未 Commit、未 Push

本轮所有变更仅在 working tree 中，未执行 `git add`、`git commit`、`git push`。
