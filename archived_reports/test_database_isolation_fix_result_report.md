# 测试数据库隔离修复结果报告

生成时间：2026-05-13

---

## 1. Redline 结果

| 项目 | 值 |
|---|---|
| BLOCKER | **0** |
| REVIEW | **1827** |
| ALLOWLIST | **219** |

exit code=0，门禁通过。

---

## 2. 修改文件清单

### 2.1 测试配置

| 文件 | 修改内容 |
|---|---|
| `src/test/resources/application.properties` | 新增 3 行：`spring.datasource.url` / `username` / `password`，指向 `ai-rag-knowledge-test` |

### 2.2 测试类（删除硬编码 datasource）

修改了 **55 个测试 Java 文件**，每个文件删除 3 行硬编码：

```
"spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
"spring.datasource.username=postgres",
"spring.datasource.password=postgres",
```

修改的文件完整列表：

| # | 文件 |
|---|---|
| 1 | `src/test/java/com/xbk/lattice/LatticeApplicationTests.java` |
| 2 | `src/test/java/com/xbk/lattice/vault/VaultSyncServiceTests.java` |
| 3 | `src/test/java/com/xbk/lattice/vault/VaultExportServiceTests.java` |
| 4 | `src/test/java/com/xbk/lattice/vault/snapshot/VaultSnapshotServiceTests.java` |
| 5 | `src/test/java/com/xbk/lattice/api/compiler/CompileControllerTests.java` |
| 6 | `src/test/java/com/xbk/lattice/api/query/PendingQueryControllerTests.java` |
| 7 | `src/test/java/com/xbk/lattice/api/query/QueryControllerTests.java` |
| 8 | `src/test/java/com/xbk/lattice/api/query/StructuredTableQueryRegressionTests.java` |
| 9 | `src/test/java/com/xbk/lattice/api/admin/AdminVectorIndexControllerTests.java` |
| 10 | `src/test/java/com/xbk/lattice/api/admin/AdminOverviewControllerTests.java` |
| 11 | `src/test/java/com/xbk/lattice/api/admin/AdminUploadControllerTests.java` |
| 12 | `src/test/java/com/xbk/lattice/api/admin/AdminProcessingTaskControllerTests.java` |
| 13 | `src/test/java/com/xbk/lattice/api/admin/AdminCompileReviewConfigControllerTests.java` |
| 14 | `src/test/java/com/xbk/lattice/api/admin/AdminQueryRetrievalAuditControllerTests.java` |
| 15 | `src/test/java/com/xbk/lattice/api/admin/AdminQueryRetrievalConfigControllerTests.java` |
| 16 | `src/test/java/com/xbk/lattice/api/admin/AdminSourceControllerTests.java` |
| 17 | `src/test/java/com/xbk/lattice/api/admin/AdminSourceCredentialControllerTests.java` |
| 18 | `src/test/java/com/xbk/lattice/api/admin/AdminChunkRebuildControllerTests.java` |
| 19 | `src/test/java/com/xbk/lattice/api/admin/AdminPageControllerTests.java` |
| 20 | `src/test/java/com/xbk/lattice/api/admin/AdminRepoSnapshotControllerTests.java` |
| 21 | `src/test/java/com/xbk/lattice/api/admin/AdminManagementControllerTests.java` |
| 22 | `src/test/java/com/xbk/lattice/api/admin/AdminGovernanceApiIntegrationTests.java` |
| 23 | `src/test/java/com/xbk/lattice/api/admin/AdminCompileFailureRegressionTests.java` |
| 24 | `src/test/java/com/xbk/lattice/api/admin/AdminFactCardControllerTests.java` |
| 25 | `src/test/java/com/xbk/lattice/api/admin/AdminCompileJobControllerTests.java` |
| 26 | `src/test/java/com/xbk/lattice/api/admin/AdminVectorConfigControllerTests.java` |
| 27 | `src/test/java/com/xbk/lattice/api/admin/LlmConfigCenterIntegrationTests.java` |
| 28 | `src/test/java/com/xbk/lattice/api/admin/DocumentParseConfigIntegrationTests.java` |
| 29 | `src/test/java/com/xbk/lattice/infra/persistence/ArticleUsageStatsJdbcRepositoryTests.java` |
| 30 | `src/test/java/com/xbk/lattice/infra/persistence/AnswerFeedbackJdbcRepositoryTests.java` |
| 31 | `src/test/java/com/xbk/lattice/infra/persistence/SourceFileJdbcRepositoryTests.java` |
| 32 | `src/test/java/com/xbk/lattice/infra/persistence/ArticleChunkJdbcRepositoryTests.java` |
| 33 | `src/test/java/com/xbk/lattice/infra/persistence/ArticleReviewAuditJdbcRepositoryTests.java` |
| 34 | `src/test/java/com/xbk/lattice/infra/persistence/SourceFileChunkJdbcRepositoryTests.java` |
| 35 | `src/test/java/com/xbk/lattice/infra/persistence/ArticleJdbcRepositoryTests.java` |
| 36 | `src/test/java/com/xbk/lattice/infra/persistence/DeepResearchBaselineSchemaTests.java` |
| 37 | `src/test/java/com/xbk/lattice/infra/persistence/ArticleSnapshotJdbcRepositoryTests.java` |
| 38 | `src/test/java/com/xbk/lattice/infra/persistence/ContributionJdbcRepositoryTests.java` |
| 39 | `src/test/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepositoryTests.java` |
| 40 | `src/test/java/com/xbk/lattice/infra/persistence/VectorJdbcRepositoryOperatorTests.java` |
| 41 | `src/test/java/com/xbk/lattice/infra/persistence/StructuredTableJdbcRepositoryTests.java` |
| 42 | `src/test/java/com/xbk/lattice/infra/persistence/FactCardJdbcRepositoryTests.java` |
| 43 | `src/test/java/com/xbk/lattice/compiler/service/CompilePipelineServiceTests.java` |
| 44 | `src/test/java/com/xbk/lattice/compiler/service/CompilePipelineVectorIndexingTests.java` |
| 45 | `src/test/java/com/xbk/lattice/compiler/service/CompileJobLeaseManagerIntegrationTests.java` |
| 46 | `src/test/java/com/xbk/lattice/compiler/service/FactCardGenerationServiceTests.java` |
| 47 | `src/test/java/com/xbk/lattice/compiler/service/StateGraphCompileOrchestratorTests.java` |
| 48 | `src/test/java/com/xbk/lattice/documentparse/service/DocumentParseRouterIntegrationTests.java` |
| 49 | `src/test/java/com/xbk/lattice/governance/repo/RepoSnapshotServiceTests.java` |
| 50 | `src/test/java/com/xbk/lattice/source/service/SourceDomainIntegrationTests.java` |
| 51 | `src/test/java/com/xbk/lattice/query/service/NonCouponComplexDocumentRegressionTests.java` |
| 52 | `src/test/java/com/xbk/lattice/query/service/RetrievalAuditServiceTests.java` |
| 53 | `src/test/java/com/xbk/lattice/query/service/RetrievalAuditQueryServiceTests.java` |
| 54 | `src/test/java/com/xbk/lattice/query/service/JdbcSearchCapabilityServiceTests.java` |
| 55 | `src/test/java/com/xbk/lattice/query/deepresearch/DeepResearchAuditPersistenceServiceTests.java` |

**共删除 165 处硬编码**（55 文件 x 3 行）。

---

## 3. 测试默认 datasource 最终指向

```
jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge-test?currentSchema=lattice
username=postgres
password=postgres
```

配置位置：`src/test/resources/application.properties`（line 9-11）。

Spring Boot 属性优先级：`application.yml` 默认值 → `application.properties` 覆盖 → `@SpringBootTest(properties)` 最高。由于所有测试类的 datasource 硬编码已被删除，`application.properties` 的值生效。

---

## 4. 是否创建/使用 ai-rag-knowledge-test

**是。** 新数据库已创建：

```sql
CREATE DATABASE "ai-rag-knowledge-test" OWNER postgres;
```

Schema 通过 `pg_dump --schema-only` 从 `ai-rag-knowledge` 复制，无数据污染。

---

## 5. Redline BLOCKER / REVIEW / ALLOWLIST

| 项目 | 修复前 | 修复后 |
|---|---|---|
| BLOCKER | 0 | **0** |
| REVIEW | 1827 | **1827** |
| ALLOWLIST | 219 | **219** |

无变化，无新增 BLOCKER。

---

## 6. mvn test 结果

| 指标 | 修复前 | 修复后 |
|---|---|---|
| Tests run | 811 | **811** |
| Failures | 0 | **0** |
| Errors | 0 | **0** |
| BUILD | SUCCESS | **SUCCESS** |

---

## 7. mvn test 前后真实 ai-rag-knowledge 计数

| 表 | 测试前 | 测试后 | 变化 |
|---|---|---|---|
| `lattice.source_files` | 0 | 0 | **无变化** |
| `lattice.articles` | 1 | 1 | **无变化** |
| `lattice.article_chunks` | 1 | 1 | **无变化** |
| `lattice.knowledge_sources` | 1 | 1 | **无变化** |

> 注：测试前 `ai-rag-knowledge` 已被上一次（修复前）`mvn test` 污染为 source_files=0, articles=1。本次 `mvn test` **未进一步改变**真实库。

---

## 8. 真实库是否仍被污染

**否（本次 mvn test 未增加污染）。**

但真实库在上次 mvn test 中已被污染（仅含 `payments-docs--payment-timeout` 一条测试 article）。本次 mvn test 的写入全部发生在 `ai-rag-knowledge-test`，真实库未被触碰。

---

## 9. 测试库是否有测试数据

**是。**

`ai-rag-knowledge-test` 在 mvn test 后包含：

| 表 | count |
|---|---|
| `lattice.articles` | 1 |
| `lattice.knowledge_sources` | 1 |
| `lattice.source_files` | 0 |
| `lattice.article_chunks` | 0 |

文章内容：`payments-docs--payment-timeout` / `Payment Timeout`。

---

## 10. 是否修改生产代码

**否。**

未修改 `src/main/java/**`、`src/main/resources/application.yml`、`src/main/resources/application-local-dev.yml`。

---

## 11. 是否修改 Query / AnswerGeneration 逻辑

**否。**

仅删除了测试类 `@SpringBootTest(properties)` 中的 datasource 配置，未触碰任何业务逻辑。

---

## 12. 是否修改测试断言或跳过测试

**否。**

- 未删除任何测试方法
- 未修改任何 assert 语句
- 未添加 `@Disabled` / `@Ignore`
- mvn test 仍为 811 tests

---

## 13. 下一步建议

### 13.1 隔离已成功

测试数据库隔离已完成：
- `mvn test` → `ai-rag-knowledge-test`（独立测试库）
- 生产/本地开发 → `ai-rag-knowledge`（不受测试影响）

可以安全地在任意时候运行 `mvn test`，不会再污染真实知识库。

### 13.2 验证 Q-MQ 修复

1. **重建 v2 clean 知识库**：重新编译资料入库到 `ai-rag-knowledge`
2. **不再担心 mvn test 污染**：隔离已就绪
3. **跑 query regression**：
   ```bash
   QUERY_REGRESSION_BASE_URL=http://127.0.0.1:18082 \
   scripts/run-query-regression.sh
   ```
4. **预期 Q-MQ 结果**：`generationMode=LLM`, `modelExecutionStatus=SUCCESS`

### 13.3 可选优化（非本轮必须）

- 在 `pom.xml` 中添加 `maven-surefire-plugin` 的 `environmentVariables` 配置，通过 `SPRING_DATASOURCE_URL` 环境变量注入，彻底消除 `application.properties` 中的硬编码
- 添加 `application-test.properties` 作为独立 test profile
- 如未来需要 CI 隔离，可引入 Testcontainers
