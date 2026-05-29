# Terminal Unit Phase 1B Ranking Fix Result Report

验证时间：2026-05-29
验证人：agentA
验证对象：terminal unit FTS 检索结果内字段意图 lexical rerank

## 1. 结论

本轮在 terminal unit FTS 检索结果内部新增通用 lexical rerank（`FactCardTerminalUnitIntentReranker`），使 terminalKey / fieldLabel / fieldAliases / keyPath 与 query token 更好对齐，减少同卡 sibling value_text 抢排。

本轮未修改 fallback、citation、vector、schema；未 stage、未 commit、未 push。

## 2. 修改文件清单

### 新增文件

| 文件 | 职责 |
|---|---|
| `src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitIntentReranker.java` | Terminal unit FTS hit 字段意图重排器 |
| `src/test/java/com/xbk/lattice/query/service/FactCardTerminalUnitIntentRerankerTests.java` | Reranker 单元测试（10 个） |

### 修改文件

| 文件 | 变更说明 |
|---|---|
| `src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchService.java` | 注入 reranker，search() 返回前调用 `reranker.rerank(hits, question)` |
| `src/test/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchServiceTests.java` | 重构 fixture 构造方法（支持多字段 metadata），新增多 hit rerank 集成测试 |

### 未修改（遵守禁令）

- `AnswerGenerationFallback*`、`AnswerFallback*`、`QueryResponseCitation*`、`FactCardVector*`：未修改
- `schema.sql`、`FactCardTerminalUnitMaterializer.java`、`FactCardGenerationService.java`：未修改
- `KnowledgeSearchService.java`、`RetrievalStrategyResolver.java`、`RrfFusionService.java`、`QueryGraph*`：未修改
- `config/synonyms.yaml`、`config/rules.yaml`、`prompt`、`scripts/scan-redline.sh`、`redline allowlist`：未修改
- `docs/模型绑定配置参考.md`：未读取、未修改

## 3. Rerank 算法说明

### 3.1 算法流程

```
1. 提取 query tokens（QueryTokenExtractor：ASCII / path / number / 中文 N-gram）
2. 解析每个 hit 的 metadata JSON → 构建 HitProfile（terminalKey / fieldLabel / fieldAliases / keyPath / parentPath / value / valueType / displayText）
3. 对每个 hit 计算 adjustedScore：
   adjustedScore = originalFtsScore
                 + fieldMatchCount × 1.0     （字段 token 精确匹配）
                 + min(valueMatchCount, 5) × 0.1  （value 匹配，封顶 0.5）
                 + numericBonus（query 有数值问法 + valueType=number/version → +0.5）
4. 同 parentPath sibling 分组：
   - 若组内存在 terminalKeyMatchCount > 0 的 hit → 仅给这些 hit +6.0 sibling boost
   - terminalKeyMatchCount 只计 terminalKey / fieldLabel / fieldAliases，不含 keyPath
5. 无字段信号 + 无数值意图 → 保持原始顺序
6. 按 adjustedScore 降序稳定排序
```

### 3.2 权重设计

| 信号 | 权重 | 说明 |
|---|---|---|
| 字段 token 精确匹配 | +1.0 / token | terminalKey / fieldLabel / aliases / keyPath 与 query token 精确相等 |
| value token 匹配 | +0.1 / token（封顶 0.5） | value/displayText 命中只作实体定位，不压过字段意图 |
| 数值 valueType 加成 | +0.5 | query 含通用数值问法（"多少/最大/最小/最长/最短/上限/下限"）且 valueType=number/version |
| Sibling 字段优先 | +6.0 | 同 parentPath 组内，仅 terminalKey/fieldLabel/aliases 精确匹配 query token 的 hit 获得 |

### 3.3 关键设计决策

1. **精确匹配不包含子串**：避免 "iota" 把 "iota_limit" 和 "iota_name" 都匹配（污染 sibling 区分度）。
2. **Sibling boost 只看 terminalKey**：keyPath 是共享前缀，sibling 内应靠 terminalKey/fieldLabel/aliases 区分。
3. **Value 匹配封顶**：长中文 value 文本匹配大量 N-gram token 时封顶 0.5，避免价值累加压过字段意图。
4. **无信号不重排**：query 无字段 token 且无数值意图时，保留原始 FTS 顺序。

## 4. 为什么不是 Fresh Eval 特判

- 重排器只使用 `terminalKey / fieldLabel / fieldAliases / keyPath / parentPath / value / valueType / displayText`，全部来自 metadata JSON（源内容派生），不读取题面、文件名、case id、答案值。
- Query 侧 token 全部来自 `QueryTokenExtractor.extract()`（ASCII / path / number / 中文 N-gram），不新增业务词。
- 数值问法信号使用 "多少/最大/最小/最长/最短/上限/下限" 等通用中文疑问词，不涉及具体字段语义（没有 "押金→deposit_amount" 这类映射）。
- 所有测试 fixture 使用 synthetic 数据（alpha_limit / beta_count / gamma_max_retry / renewal_period / theta_size 等），不使用 fresh eval 字段或答案值。

## 5. Redline 结果

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：exit=0，**BLOCKER=0**

## 6. 定向测试结果

### Reranker 测试

命令：
```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitIntentRerankerTests test
```

结果：**Tests run: 10, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

| # | 测试 | 覆盖点 |
|---|---|---|
| 1 | `shouldPrioritizeFieldIntentHitOverValueOnlyHitWithinSameParent` | 同 sibling 中 terminalKey 命中 query token 的 hit 排在 value-only 命中的 hit 前 |
| 2 | `shouldNotLetNumericValueTypeOverrideExplicitFieldTokenMatch` | valueType=number 不能压过明确字段 token 匹配 |
| 3 | `shouldGiveSmallNumericBonusWhenQueryHasNumericIntentNoFieldTokens` | 数值问法 + number valueType 小幅加权 |
| 4 | `shouldPreserveOriginalOrderWhenMetadataIsMissing` | metadata 缺失 → 保持原始顺序 |
| 5 | `shouldNotRerankWhenNoFieldIntentSignalAndNoNumericIntent` | 无字段信号 + 无数值意图 → 不重排 |
| 6 | `shouldReturnSingleHitUnchanged` | 单 hit 原样返回 |
| 7 | `shouldHandleEmptyAndNullInputs` | 空列表 / null 安全 |
| 8 | `shouldTreatFieldAliasesMatchAsFieldIntent` | fieldAliases 命中计入字段意图 |
| 9 | `shouldTreatKeyPathMatchAsFieldIntent` | keyPath 匹配区分 sibling 内 terminalKey 命中 |
| 10 | `shouldGiveVersionSameNumericBonusAsNumber` | version 与 number 享有相同小幅加权 |

### 搜索服务测试

命令：
```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitFtsSearchServiceTests test
```

结果：**Tests run: 3, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

| # | 测试 | 覆盖点 |
|---|---|---|
| 1 | `shouldReturnQueryHitWithTerminalUnitIdentity` | unit identity 不变 |
| 2 | `shouldExposeDisplayTextAndDescriptionWithoutFullItemsJson` | content 不含整张 items_json |
| 3 | `shouldReturnRerankedResultsWithFieldIntentFirst` | **新增**：验证 search() 返回已按字段意图重排 |

## 7. 全量 Maven Test 结果

命令：
```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：**Tests run: 947, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

基线：Phase 1A 全量为 936（之前有 3 个 skip 已消除）。本轮新增 11 个测试（reranker 10 + 搜索服务 1 个新测试），总数 947。

## 8. 硬编码扫描结果

命令：
```bash
git diff -- src/main/java src/test/java | rg -n "FQ3|FQ4|FQ6|FG1|FG2|equipment-borrowing-policy|押金|逾期|最长借用|最大并发|v2\.3\.1|1000|8080|Kubernetes|readiness|liveness|apiKey|sk-[A-Za-z0-9]"
```

结果：**NO_MATCHES_FOUND**

未跟踪新文件同样扫描，无命中。无 fresh eval 题面、case id、文件名、答案值、业务词或密钥泄露到生产代码或测试。

## 9. 是否需要 AgentD 服务级验证

**建议执行，但非代码层准入条件。**

理由：
- 本轮未修改 schema、未清库、未重建，不需要 agentD 重导数据。
- 代码层验证已全部通过（redline BLOCKER=0、定向测试 13/0/0、全量 947/0/0、硬编码扫描 clean）。
- rerank 逻辑是通用 lexical 层，不依赖特定数据库内容，单元测试已覆盖核心路径。
- 但 rerank 效果（是否改善 FQ3/FQ4/FQ6/FG1/FG2 目标 terminal unit 进入 topK）需要在 agentD 的真实库和 fresh eval query 上验证。

验证范围建议：
1. 在现有 Phase 1A clean schema 库上直接运行 fresh eval query（无需重建、重导）。
2. 观察 `fact_card_terminal_fts` channel 的 topK 中目标 terminal unit（max_borrow_days、deposit_amount、version、late_fee_per_day、max_concurrent_requests）是否排在同卡 sibling value_text 前面。
3. 如 rerank 效果不足，agentD 反馈具体排序 trace，agentA 针对性调整权重。

## 10. 残余风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 精确匹配对纯中文 query + 纯英文 field key 场景效果有限 | 当 query 中文 N-gram 无法匹配英文字段名时，rerank 依赖数值问法信号和 valueType 加成，不能直接做中英字段语义对齐 | 已按约束禁止硬编码中文字段语义；数值/版本问法是通用降级策略 |
| Value 匹配封顶 0.5 可能过于保守 | 某些场景下 value 匹配确有信息量 | 封顶值可调；当前以字段意图优先为第一目标 |
| Sibling boost +6.0 是强信号 | 如果 terminalKeyMatchCount 的假阳性（alias 过于宽泛），可能误提升 | alias 只来自源内容派生和通用拆词，不在 Java 主链新增中文 alias |

## 11. 明确未 Stage、未 Commit、未 Push

本轮所有变更仅在 working tree 中，未执行 `git add`、`git commit`、`git push`。
