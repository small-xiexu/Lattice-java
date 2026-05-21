# Compile Review Query Visibility Filter Verification Design

## 结论

Query visibility hard filter 的验证不应依赖主库现状，也不建议跑 baseline。最小可归因验证应放在 Maven 集成测试使用的 `ai-rag-knowledge-test` 库中完成：用合成 article 同时构造 `passed/ACTIVE` 正例、非 passed 反例、非 ACTIVE 反例，逐条验证 5 条 article-backed 通道。

当前 agentA 已补上 article chunk lexical、article vector、article chunk vector 的 `pending` 不可见断言，并恢复全量 `mvn test=812 / 0 / 0`。但提交前验证仍需确认两类覆盖：`ArticleFtsSearchMapper` / `RefKeySearchMapper` 的直接覆盖，以及 `lifecycle != ACTIVE` 的负向覆盖。

## 1. Redline

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1852 |
| ALLOWLIST | 239 |

说明：本轮运行 `bash scripts/scan-redline.sh special_cases_report.md`，扫描结果写入 `special_cases_report.md`。未修改 redline 脚本或 allowlist。

## 2. 当前 Diff 摘要

`git diff --stat` 显示当前工作区主要包含 agentA 的 5 个 mapper hard filter、2 个 repository fixture 测试修复，以及 `special_cases_report.md` 刷新：

| 文件类别 | 当前状态 |
|---|---|
| 5 个 article-backed mapper XML | 增加 `review_status='passed'` 与 `lifecycle='ACTIVE'` hard filter |
| `ArticleChunkJdbcRepositoryTests` | 命中 fixture 改为 `passed/ACTIVE`，补 `pending` 不可见断言 |
| `VectorJdbcRepositoryOperatorTests` | article vector / chunk vector 命中 fixture 改为 `passed/ACTIVE`，补 `pending` 不可见断言 |
| source/source_chunk mapper | 无 diff |
| fact card mapper / policy | 无 diff |

本报告只新增验证设计，不修改源码、测试、配置、脚本、题集或数据库。

## 3. 5 条通道的验证入口

| 通道 | 最小验证入口 | 当前 SQL 位置 | 验证重点 |
|---|---|---|---|
| Article FTS | `ArticleFtsSearchMapper.search(...)`，或经 `FtsSearchService.search(...)` | `src/main/resources/com/xbk/lattice/query/service/mapper/ArticleFtsSearchMapper.xml` | `search_tsv` 命中时只返回 `passed/ACTIVE` |
| RefKey article search | `RefKeySearchMapper.search(...)`，或经 `RefKeySearchService.search(...)` | `src/main/resources/com/xbk/lattice/query/service/mapper/RefKeySearchMapper.xml` | 原 `OR` 条件不能绕过 hard filter |
| Article chunk lexical | `ArticleChunkJdbcRepository.searchLexical(...)` | `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkMapper.xml` | chunk 正文 / title / concept 命中均不能绕过 hard filter |
| Article vector | `ArticleVectorJdbcRepository.searchNearestNeighbors(...)` | `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleVectorMapper.xml` | 同 embedding 的非 passed / 非 ACTIVE article 不返回 |
| Article chunk vector | `ArticleChunkVectorJdbcRepository.searchNearestNeighbors(...)` | `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkVectorMapper.xml` | 同 embedding 的非 passed / 非 ACTIVE chunk 不返回 |

## 4. 已有测试覆盖是否足够

| 通道 | 现有覆盖 | 缺口 |
|---|---|---|
| Article chunk lexical | 已有 `passed/ACTIVE` 正例；已有 `pending/ACTIVE` 负例 | 还缺 `needs_human_review`、`rejected`、`lifecycle != ACTIVE` 负例 |
| Article vector | 已有 `passed/ACTIVE` 正例；已有 `pending/ACTIVE` 负例 | 还缺 `needs_human_review`、`rejected`、`lifecycle != ACTIVE` 负例 |
| Article chunk vector | 已有 `passed/ACTIVE` 正例；已有 `pending/ACTIVE` 负例 | 还缺 `needs_human_review`、`rejected`、`lifecycle != ACTIVE` 负例 |
| Article FTS | 未看到专门的 repository / mapper 断言 | 需要直接覆盖正例、非 passed 负例、非 ACTIVE 负例 |
| RefKey article search | 未看到专门的 repository / mapper 断言 | 需要直接覆盖正例、非 passed 负例、非 ACTIVE 负例，尤其验证 `OR` 括号不会绕过 filter |

判断：当前测试已能解释原 3 个 repository fixture 失败，并覆盖一部分 hard filter 行为；但作为 query visibility 提交前 gate，还不完整。agentD 验证时如果没有 FTS / RefKey / lifecycle 负向覆盖，结论应写为“基础测试通过，但 hard filter 验证不完整”。

## 5. Passed / ACTIVE 正向验证方案

每条 article-backed 通道都构造一个中性合成 article：

| 字段 | 建议 |
|---|---|
| `review_status` | `passed` |
| `lifecycle` | `ACTIVE` |
| 检索内容 | 使用无业务含义的唯一 token，避免污染红线判断 |
| vector 通道 | 正例与负例使用相同 embedding，确保差异只来自 visibility filter |
| 断言 | 返回结果非空，并且只包含正例 article / chunk |

正向验证只证明 filter 没误伤正常 article；它不能替代负向验证。

## 6. Pending / Needs Human Review / Rejected 负向验证方案

每条 article-backed 通道都应在同一测试内构造同查询条件的反例：

| 反例状态 | 数据形态 | 预期 |
|---|---|---|
| `pending` | `ACTIVE`，内容 / token / embedding 与正例同样可命中 | 不返回 |
| `needs_human_review` | `ACTIVE`，内容 / token / embedding 与正例同样可命中 | 不返回 |
| `rejected` | `ACTIVE`，内容 / token / embedding 与正例同样可命中 | 不返回 |

断言方式：结果中的 `conceptId/articleKey/chunkId` 不包含任何反例。不要只断言 size，因为 rerank 或 score 改动可能改变数量；应直接断言反例标识不存在。

## 7. Lifecycle 非 ACTIVE 负向验证方案

每条 article-backed 通道都应至少构造一个 `review_status='passed'` 但 `lifecycle != 'ACTIVE'` 的反例：

| 字段 | 建议 |
|---|---|
| `review_status` | `passed` |
| `lifecycle` | 使用当前系统可写出的非 ACTIVE 值，例如 `archived` 或 `ARCHIVED` |
| 检索内容 | 与正例同 token / 同 embedding |
| 预期 | 不返回 |

这个用例和非 passed 负例不同：它证明生命周期门禁独立生效，能挡住已过审但被归档、废弃或非激活的历史 article。

## 8. Source / Source Chunk 不受影响验证方案

source/source_chunk 不应套 article `review_status`，验证目标是“不被本轮 SQL 改动误伤”。

| 验证项 | 建议 |
|---|---|
| diff 检查 | `git diff -- SourceFileMapper.xml SourceFileChunkMapper.xml` 应为空 |
| 定向测试 | 运行 `SourceFileJdbcRepositoryTests`、`SourceFileChunkJdbcRepositoryTests`、`SourceSearchServiceTests` |
| 通过标准 | source lexical / source chunk lexical 仍能返回原有命中 |

当前 surefire 产物显示 `SourceFileChunkJdbcRepositoryTests=5 / 0 / 0`、`SourceSearchServiceTests=2 / 0 / 0`。agentD 仍应在最终验证轮重跑，而不是只引用旧产物。

## 9. Fact Card 不受影响验证方案

fact card 有自己的 review usage policy，不应直接套 article `passed/ACTIVE`。

| 验证项 | 建议 |
|---|---|
| diff 检查 | `git diff -- FactCardMapper.xml FactCardVectorMapper.xml` 应为空 |
| 定向测试 | 运行 `FactCardJdbcRepositoryTests`、`FactCardFtsSearchServiceTests`、`FactCardVectorSearchServiceTests`、`FactCardReviewUsagePolicyTests` |
| 通过标准 | fact card lexical / vector / review usage policy 原有断言通过；`low_confidence`、`needs_human_review`、`conflict` 等策略不因 article filter 改变 |

当前 surefire 产物显示上述 fact card 测试均为 0 failure。agentD 最终仍需重跑定向或全量测试确认。

## 10. 数据构造位置

| 问题 | 结论 |
|---|---|
| 是否需要写主库验证 | 不需要，也不允许 |
| 是否需要数据库只读验证 | 可选，只用于确认主库未被污染、查看当前状态分布 |
| 非 passed / 非 ACTIVE 数据放哪里 | 放 Maven 测试库 `ai-rag-knowledge-test` |
| 如何避免污染 `ai-rag-knowledge` | 只通过 `mvn test` 的 test resources 连接；主库只做 `SELECT`，不做 `INSERT/UPDATE/TRUNCATE` |

`src/test/resources/application.properties` 当前指向：

```properties
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge-test?currentSchema=lattice
```

如果后续需要补测试，优先沿用现有 repository test 的清理方式；向量类测试可复用现有 reset helper，避免手工操作主库。

## 11. AgentD 建议执行顺序

1. `git status --short --branch`
2. `git diff --stat`
3. `bash scripts/scan-redline.sh special_cases_report.md`
4. 检查 5 个 mapper diff 是否仍只包含 `review_status='passed'` / `lifecycle='ACTIVE'` hard filter，且 RefKey / chunk lexical 的 `OR` 条件被括号包住。
5. 运行 article-backed 定向测试。若 FTS / RefKey / lifecycle 负向测试尚不存在，验证结论标记为“不完整”，不要用 baseline 替代。
6. 运行 source/fact card 不受影响定向测试。
7. 运行全量 `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`。
8. 可选只读查询主库状态分布，但不写入、不清库、不重建、不重新导入。

建议定向测试集合应至少覆盖：

```bash
mvn -s .codex/maven-settings.xml \
  -Dmaven.repo.local=/Users/sxie/maven/repository \
  -Dtest=ArticleChunkJdbcRepositoryTests,VectorJdbcRepositoryOperatorTests,SourceFileJdbcRepositoryTests,SourceFileChunkJdbcRepositoryTests,SourceSearchServiceTests,FactCardJdbcRepositoryTests,FactCardFtsSearchServiceTests,FactCardVectorSearchServiceTests,FactCardReviewUsagePolicyTests \
  test
```

注意：上面命令覆盖当前已有测试，但不等于完整 hard filter gate。完整 gate 还需要包含 Article FTS、RefKey、`needs_human_review`、`rejected`、`lifecycle != ACTIVE` 的直接断言。

## 12. 是否建议 AgentD 跑 Baseline

默认不建议。

理由：

- 本轮改动是 deterministic SQL visibility gate，不是 prompt、rerank、生成或模型链路改动。
- 当前 clean 库中的 article-backed 数据预期为 `passed/ACTIVE`，对现有正常数据应是 no-op。
- baseline 容易引入检索/LLM 波动，不适合作为确认 SQL hard filter 的第一证据。
- 如果 redline、定向测试、全量 `mvn test` 都通过，后续是否跑 baseline 应由用户另行指定。

## 13. 验证通过标准

| Gate | 通过标准 |
|---|---|
| Redline | `BLOCKER=0` |
| 5 条 article-backed 通道 | `passed/ACTIVE` 正例可返回；`pending`、`needs_human_review`、`rejected`、`lifecycle != ACTIVE` 反例不可返回 |
| Source/source_chunk | mapper 无 diff；定向测试通过 |
| Fact card | mapper / policy 无 diff；定向测试通过 |
| 全量测试 | `mvn test` 0 failure / 0 error，测试数可随新增覆盖增加 |
| 主库安全 | 不写 `ai-rag-knowledge`，不清库，不重建，不重新导入 |

## 14. 下一轮最小动作

建议下一轮交给 agentD 做验证轮：按本报告先跑 redline、定向测试和全量 `mvn test`，默认不跑 baseline。若发现 Article FTS / RefKey / lifecycle 非 ACTIVE 覆盖缺失，agentD 应在验证报告中退回“测试覆盖不完整”，由 agentA 另起最小测试补充轮处理，不扩大生产代码改动。

## 15. 本轮修改说明

本轮是否修改代码：否。

本轮是否修改测试、配置、脚本、题集、数据库：否。

本轮仅新增本报告；redline 运行允许刷新 `special_cases_report.md`。
