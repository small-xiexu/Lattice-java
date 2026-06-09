# Java 代码语义索引增强 — 能力调研与分阶段设计方案

设计时间：2026-06-09
执行人：agentB（治理/归因 Agent）
类型：只读调研与方案设计，不修改任何代码

---

## 1. 当前能力结论

### 1.1 已具备的能力

| 能力 | 当前状态 | 实现位置 |
|------|:---:|------|
| Java 源码解析（`.java` 文件 AST） | ✅ 已实现 | `AstGraphExtractService`：JavaParser 3.27.1 + SymbolSolver |
| 实体抽取（CLASS/INTERFACE/ENUM/METHOD） | ✅ 已实现 | 4 种实体类型 |
| 事实抽取（package/annotation/signature/http_mapping） | ✅ 已实现 | 4 种事实谓词 |
| 关系抽取（declares_method/extends/implements/calls） | ✅ 已实现 | 4 种关系边类型 |
| CODE_LIGHT 模式（跳过 writer/reviewer/fixer） | ✅ 已实现 | `BuildLightweightArticlesNode`：article=源码原文 |
| Graph 检索通道（LIKE 匹配） | ✅ 已实现 | `GraphSearchService`：mention → 实体名称 LIKE 搜索 |
| CODE_STRUCTURE 意图 → graph 权重提升 | ⚠️ 已定义但未触发 | `QueryIntentClassifier` 永远不返回 CODE_STRUCTURE |
| Spring `@Mapping` 注解提取 | ✅ 已实现 | `http_mapping` 事实谓词，覆盖所有 `*Mapping` 注解 |
| CONFIGURATION 意图 → graph 权重配置 | ✅ 已实现 | `RetrievalStrategyResolver` 通道权重配置 |

### 1.2 数据库实际统计

| 表 | 数量 | 说明 |
|------|:---:|------|
| `graph_entities` | 40 | CLASS(12) + INTERFACE(6) + METHOD(22) |
| `graph_facts` | 58 | signature(22) + package(18) + annotation(14) + http_mapping(4) |
| `graph_relations` | 73 | calls(46) + declares_method(22) + extends(3) + implements(2) |

**当前数据来自 Java Codebase Public Eval 的 payment-service-mini fixture（~20 个 Java 文件）。**

---

## 2. 主要缺口清单

| # | 缺口 | 影响 | 优先级 |
|---|------|------|:---:|
| 1 | **CODE_STRUCTURE 意图从不触发** | `QueryIntentClassifier` 永远不返回 CODE_STRUCTURE，导致 graph 通道权重提升永不生效 | P0 |
| 2 | **Graph 搜索只有 LIKE，无 FTS** | 对驼峰命名（`UserService` → token "user"+"service"）的召回依赖 LIKE 子串匹配，性能差、精度低 | P0 |
| 3 | **无 Spring 语义解析** | `@Controller`/`@Service`/`@Repository` 只是普通 annotation fact，无法区分层级角色；`@Autowired` 依赖注入未被提取为关系 | P1 |
| 4 | **无 MyBatis XML 解析** | Mapper XML 中的 SQL（`<select>/<insert>/<update>/<delete>` + `id` + `resultMap`）未被解析为结构化事实 | P1 |
| 5 | **无 Mapper 接口 ↔ XML 关联** | MyBatis 接口方法（如 `PaymentOrderMapper.selectByOrderId`）和 XML 中的 SQL 语句之间无显式关系边 | P1 |
| 6 | **无 Controller→Service→Mapper 调用链** | 当前 `calls` 关系只在同文件内解析。跨文件的 Controller→Service→Mapper 调用需要 SymbolSolver 解析，但当前未提取为显式关系 | P1 |
| 7 | **无 YAML/properties 配置项提取** | `application.yml` 中的 `library.credit.deduct-1-7-days: 2` 未被提取为结构化事实。配置 key 在所有源文件中不可检索（除非出现在 Java 代码的 `@Value` 注解中） | P1 |
| 8 | **无 pom.xml 依赖提取** | `pom.xml` 中的依赖（groupId/artifactId/version）未被提取。用户无法查询"项目用了哪个版本的 MyBatis" | P2 |
| 9 | **Graph 无结构化查询能力** | 无法查询"UserService 的所有 public 方法"、"哪些类 implements PaymentService"、"@RestController 标注的所有类" | P2 |
| 10 | **无增量 AST 重建** | 项目更新后需要全量重编译。没有基于文件 hash 的增量 AST 更新机制 | P2 |
| 11 | **Graph 检索返回格式不利于 LLM 消费** | `factsBlock` 是分号拼接的中文文本，LLM 难以从中提取精确的调用链关系 | P2 |

---

## 3. 当前代码链路引用

| 组件 | 文件 | 关键行 |
|------|------|:---:|
| AST 抽取入口 | `ExtractAstGraphNode.java` | 51-72 |
| AST 抽取核心 | `AstGraphExtractService.java` | 83-280 |
| 实体/事实/关系类型定义 | `AstEntityType.java`, `AstEntity.java`, `AstFact.java`, `AstRelation.java` | — |
| Graph 持久化 | `GraphEntityJdbcRepository.java`, `GraphEntityMapper.xml` | — |
| Graph 搜索 | `GraphSearchService.java` | 90-238 |
| Graph 通道权重 | `RetrievalStrategyResolver.java` | 140-155 |
| 意图分类 | `QueryIntentClassifier.java` | 35-53 |
| CODE_LIGHT 路由 | `CompileGraphConditions.java` | 23-34, 42-58 |
| CODE_LIGHT 文章构建 | `BuildLightweightArticlesNode.java` | 50-148 |
| contentProfile 规范化 | `CompileExecutionRequest.java` | 160-174 |

---

## 4. 技术选型建议

### 4.1 推荐：继续使用 JavaParser + SymbolSolver

| 方案 | 优势 | 劣势 | 推荐度 |
|------|------|------|:---:|
| **JavaParser + SymbolSolver（当前）** | 已在项目中集成（3.27.1）；纯 Java，无原生依赖；类型解析能力足够覆盖 Spring/MyBatis 场景；社区活跃 | 大项目解析速度较慢；对 Java 17+ 新语法支持有延迟 | ⭐⭐⭐⭐⭐ |
| tree-sitter-java | 极快、增量解析；容错性强 | 需要引入原生库（JNI/JNA）；无类型解析/符号解析；需要自建语义层 | ⭐⭐⭐ |
| Eclipse JDT | 完整的 Java 编译器前端；类型解析最精确 | 依赖过重（>> 50MB）；API 复杂；与 Spring Boot 项目集成繁琐 | ⭐⭐ |
| Spoon | 基于 Eclipse JDT；API 更友好；支持源码级变换 | 依赖重；类型解析依赖 Eclipse JDT | ⭐⭐ |
| OpenRewrite | 专为代码重构设计；Lossless Semantic Tree | 不是为"代码问答/检索"设计；需要额外适配层 | ⭐ |

**推荐理由**：JavaParser 3.27.1 已在项目中稳定运行，当前 40 实体/58 事实/73 关系的规模证明核心链路可靠。后续增强只需要扩展提取规则（新增事实谓词、关系类型），不需要替换解析器。引入新解析器会增加依赖复杂度和维护成本，收益不足以抵消成本。

### 4.2 不需要新增的技术

- **Neo4j / 图数据库**：当前 `graph_entities/facts/relations` 三张 PostgreSQL 表 + GIN 索引已满足当前规模（< 10K 实体）。Neo4j 的图遍历能力在"代码问答 + 全文检索"场景下不是瓶颈——LLM 的 context window 才是。
- **Elasticsearch**：现有 PostgreSQL FTS（`search_tsv`）+ LIKE 已足以支撑 graph 检索。如果未来实体规模超过 100K，再评估 ES 的必要性。

---

## 5. 推荐架构

```
INTERNAL_MIRROR (contentProfile=CODE_LIGHT)
  │
  ├─ ingest_sources          → 所有文件入库（.java/.xml/.yml/.properties/pom.xml）
  ├─ persist_source_files    → source_files 表（保留原始内容和路径）
  ├─ persist_source_chunks   → source_file_chunks 表（全文检索索引）
  │
  ├─ extract_ast_graph       → AST 解析（当前仅 .java）
  │   ├─ Phase 1: 补全 Spring 语义标注、MyBatis 接口标注
  │   ├─ Phase 2: 新增 MyBatis XML 解析、YAML/properties 配置提取
  │   └─ Phase 2: 新增 pom.xml 依赖提取
  │   → graph_entities / graph_facts / graph_relations 表
  │
  ├─ analyze_batches         → 概念分析
  ├─ merge_concepts          → 合并去重
  │
  ├─ [CODE_LIGHT 分支]
  │   └─ build_lightweight_articles → 每文件一篇 article（content=源码原文）
  │       → articles 表（article_chunks 也同步生成）
  │
  ├─ [DOCUMENT 分支]         → 仅对 README/docs 等文档类文件
  │   └─ compile_new_articles → writer/reviewer/fixer
  │
  ├─ refresh_vector_index    → chunk vector
  └─ finalize_job
```

**核心设计**：CODE_LIGHT 编译负责两件事——(a) `extract_ast_graph` 产出的结构化代码图谱，(b) `build_lightweight_articles` 产出的源码原文文章。两者互补：图谱回答"UserService 有哪些方法"、"谁调用了 processRefund"，文章回答"UserService 的 processRefund 方法具体怎么实现"。

---

## 6. 分阶段落地方案

### Phase 1：补全 Java AST 结构化索引 + 修复 CODE_STRUCTURE 意图

**目标**：让现有的 AST 提取能力被 Query 主链正确消费。

| 任务 | 修改范围 | 验证方式 |
|------|------|------|
| 1.1 `QueryIntentClassifier` 返回 CODE_STRUCTURE | `QueryIntentClassifier.java` | 代码类问题（含类名/方法名/注解名）→ 意图 = CODE_STRUCTURE |
| 1.2 Graph 搜索增加 FTS（`search_tsv`） | `GraphEntityMapper.xml` + `GraphSearchService.java` | LIKE→FTS 迁移，entity name 的 token 级别精确匹配 |
| 1.3 Spring 语义标注 | `AstGraphExtractService.java` | `@Controller`/`@Service`/`@Repository` → 新增 `predicate="spring_stereotype"` 事实 |
| 1.4 `@Autowired` 字段 → 依赖关系 | `AstGraphExtractService.java` | `@Autowired private XxxService xxxService` → 新增 `edgeType="injects"` 关系 |
| 1.5 `@Value` 字段 → 配置引用关系 | `AstGraphExtractService.java` | `@Value("${library.credit.enabled}")` → 新增 `edgeType="config_ref"` 关系 |

**允许修改范围**：`QueryIntentClassifier.java`, `AstGraphExtractService.java`, `GraphSearchService.java`, `GraphEntityMapper.xml`

**验证**：Java Codebase Public Eval fixture → 验证 graph 通道对代码类问题的召回改善

---

### Phase 2：补 MyBatis / Config / Maven 跨文件关系

**目标**：让 XML mapper、YAML 配置、pom.xml 也被结构化提取，并与 Java 代码建立关系。

| 任务 | 修改范围 | 验证方式 |
|------|------|------|
| 2.1 MyBatis XML 解析 | 新增 `MyBatisXmlExtractor` | 解析 `<mapper namespace="...">`, `<select>/<insert>/<update>/<delete>` (id + resultMap) → 实体+事实 |
| 2.2 Mapper 接口 ↔ XML 关联 | `AstGraphExtractService` + `MyBatisXmlExtractor` | Mapper 接口的全限定名 = XML namespace → `edgeType="maps_to"` |
| 2.3 YAML/properties 配置项提取 | 新增 `ConfigKeyExtractor` | `library.credit.*` 配置段 → 实体+事实 |
| 2.4 `@Value` ↔ 配置项关联 | `AstGraphExtractService` + `ConfigKeyExtractor` | 代码中的 `@Value("${...}")` → 配置项实体 → `edgeType="config_ref"` |
| 2.5 pom.xml 依赖提取 | 新增 `MavenPomExtractor` | `groupId/artifactId/version/scope` → 实体+事实 |

**允许修改范围**：新增 3 个 Extractor 类 + `ExtractAstGraphNode` 路由（按文件类型分发）

**验证**：PE6 fixture（含 MyBatis XML + YAML 配置 + pom.xml）→ 验证跨文件关系可检索

---

### Phase 3：增强 Graph Retrieval 和 Code Query Routing

**目标**：让 graph channel 的检索精度和结构化查询能力达到可用水平。

| 任务 | 修改范围 | 验证方式 |
|------|------|------|
| 3.1 Graph 通道 FTS 权重优化 | `RetrievalStrategyResolver.java` | CODE_STRUCTURE 下 graph 通道权重从 1.60→2.00 |
| 3.2 `factsBlock` 格式优化 | `GraphSearchService.java` | 改为 JSON 格式（而非分号拼接中文）→ LLM 更容易消费 |
| 3.3 Graph hit 的 citation 增强 | `GraphSearchService.java` | 每个 graph hit 的 `sourcePaths` 包含精确行号 → citation 验证可用 |
| 3.4 结构化查询原语 | `GraphEntityJdbcRepository.java` | 新增 `findByAnnotation(String)`, `findCallersOf(String)` 等方法 |

**允许修改范围**：`RetrievalStrategyResolver.java`, `GraphSearchService.java`, `GraphEntityJdbcRepository.java`, `GraphEntityMapper.xml`

**验证**：PE6 → 代码类问题的 Answer Accuracy >= 80%

---

### Phase 4：增量索引、大项目性能基准

**目标**：让 INTERNAL_MIRROR 的增量 sync 也能增量更新 AST graph。

| 任务 | 修改范围 | 验证方式 |
|------|------|------|
| 4.1 文件级 hash → AST 增量重建 | `ExtractAstGraphNode.java` | 文件 hash 与上次 compile 一致 → 跳过 AST 重解析 |
| 4.2 删除 reconciliation | `ExtractAstGraphNode.java` | 源文件删除 → 对应 graph 实体/事实/关系 tombstoned |
| 4.3 大项目性能基准 | 性能测试 | 1000+ Java 文件的 CODE_LIGHT 编译耗时 < 3 分钟 |

**允许修改范围**：`ExtractAstGraphNode.java` + `AstGraphExtractService.java` + `GraphEntityJdbcRepository.java`

**验证**：Lattice-java 自身作为 dogfood 项目 → 增量 rebuild 耗时 < 30 秒

---

### Phase 5：代码问答专项 Eval 与 Gate 指标

**目标**：形成可量化的代码问答质量基线。

| 任务 | 修改范围 | 验证方式 |
|------|------|------|
| 5.1 设计 PE7（代码问答专项 eval） | 题集设计 | ~20 题覆盖 Controller/Service/Mapper/Config/call-chain |
| 5.2 PE7 Runtime Gate | agentD 验证 | Answer Accuracy >= 80%, Citation Accuracy >= 70% |

---

## 7. 建议 Agent 分工

| 阶段 | agentA（代码） | agentB（设计/归因） | agentD（验证） |
|:---:|------|------|------|
| Phase 1 | 实现 CODE_STRUCTURE 意图 + Graph FTS + Spring 语义 | — | 验证 CODE_STRUCTURE 触发 + graph 通道召回改善 |
| Phase 2 | 实现 MyBatis/Config/Maven 提取器 | — | 验证 PE6 fixture 跨文件关系 |
| Phase 3 | 优化 graph 通道权重 + factsBlock + citation | — | 验证 PE6 Answer Accuracy |
| Phase 4 | 实现增量 AST + 删除 reconciliation | — | 验证 dogfood 性能 |
| Phase 5 | — | 设计 PE7 | 跑 PE7 gate |

---

## 8. 下一步给 agentA 的最小修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标（Phase 1 最小子集）：修复两个最高优先级的缺口。

修改范围：
1. QueryIntentClassifier.java — 新增 CODE_STRUCTURE 意图检测
2. AstGraphExtractService.java — 新增 spring_stereotype 事实 + injects 关系

变更 1（QueryIntentClassifier.java）：
在意图分类中增加 CODE_STRUCTURE 检测：当 question 包含类名/方法名/注解名
的 token 时（已通过 QueryTokenExtractor 提取的 CamelCase token），
返回 CODE_STRUCTURE 意图。使 graph 通道的 1.60x 权重提升生效。

变更 2（AstGraphExtractService.java）：
- 对 @Controller/@Service/@Repository/@Component 注解 → 新增
  predicate="spring_stereotype" 事实（value=注解名）
- 对 @Autowired/@Inject 字段 → 新增 edgeType="injects" 关系
  （dst=字段类型的全限定名）

通用性要求：不改任何具体类名/方法名/注解名特判。@Controller/@Service 等
通过注解名字符串通用匹配，不写死具体注解列表。

禁止修改：query/retrieval/rerank/fallback/citation 主链、GraphSearchService、
schema.sql、prompt、scripts

验证：编译 Java Codebase Public Eval fixture → 确认 graph_entities/facts/relations
表新增 spring_stereotype 事实和 injects 关系
```

---

## 9. 风险与红线

- 所有增强必须是通用 Java 语义提取，不得为具体项目/类名/包名写硬编码
- CODE_LIGHT 继续作为大项目的默认导入方式，不把每个 Java 文件送 writer/reviewer/fixer
- AST 提取的 source file 引用必须指向真实源码路径（`source_files.file_path:line`），用于 citation 验证
- Hidden eval 内容不得写入任何提取规则、prompt、或测试 fixture
- 新增的 graph 实体/事实/关系的 schema 变更必须兼容现有 graph 表结构（不需要 DDL 变更）
- 不引入新的解析器依赖（继续使用 JavaParser 3.27.1）

---

## 10. 明确声明

- [x] 未修改任何代码
- [x] 未修改 schema.sql
- [x] 未提交 commit
- [x] 数据库 graph 表实际统计已通过只读 SQL 查询获取
- [x] 技术选型保留 JavaParser 3.27.1，不引入新解析器
- [x] 5 个 Phase 均为通用能力增强，无具体项目/类名/包名硬编码
- [x] 未读取 hidden eval
