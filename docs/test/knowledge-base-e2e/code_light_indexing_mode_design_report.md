# CODE_LIGHT 代码轻量索引模式 — 设计报告

设计时间：2026-06-07
执行人：agentB（只读设计/归因 Agent）
类型：架构设计，不落地代码，不跑测试，不导入资料

---

## 1. 背景与问题定义

### 1.1 Dogfood 验证发现

INTERNAL_MIRROR dogfood 验证（`internal_mirror_dogfood_java_project_runtime_report.md`）使用 Lattice-java 自身（~2000 source_files）做真实 Java 项目导入，发现：

| 阶段 | 状态 | 耗时 |
|------|:---:|------|
| initialize_job | ✅ | < 1 秒 |
| ingest_sources | ✅ | < 5 秒 |
| persist_source_files | ✅ | < 5 秒 |
| persist_source_file_chunks | ✅ | ~30 秒 |
| **compile_articles（Writer+Reviewer+Fixer）** | ⏳ | **30-60 分钟** |
| persist_articles + vector_index | ⏳ | 依赖 compile 完成 |

**2000+ 源码文件全部走 writer/reviewer/fixer LLM 调用，是当前最大瓶颈。** 对于以代码检索为主要场景的内部 Java 项目，每个 `.java` 文件生成一篇 LLM 文章既不必要的（源码本身已是最精确的知识表示），也不经济的（token 成本 > 500K/项目）。

### 1.2 核心问题

不是"编译能不能跑"，而是"大型 Java 项目的默认导入方式不应把每个源码文件都当作文档来生成文章"。

---

## 2. CODE_LIGHT 的定位

### 2.1 它是新的 `contentProfile`

CODE_LIGHT 是**编译期的内容处理策略（content profile）**，不是新的 source type、不是新的 compile mode、不是新的 refresh policy。

| 概念 | 是什么 | CODE_LIGHT 是否为此 |
|------|------|:---:|
| `contentProfile` | 控制哪些编译步骤执行、每类文件如何处理 | **是** |
| `sourceType`（UPLOAD/GIT/INTERNAL_MIRROR） | 控制资料从哪里来 | 否 |
| `compileMode`（full/incremental） | 控制是全量还是增量编译 | 否 |
| `reviewMode`（LLM/RULE_BASED） | 控制审查方式 | 否 |

### 2.2 与 DOCUMENT profile 共存

| 维度 | DOCUMENT（当前默认） | CODE_LIGHT（新增） |
|------|------|------|
| 适用场景 | 文档、合同、手册、SOP | Java 后端项目代码 |
| 每文件都生成 LLM 文章 | **是** | **否**（源码文件跳过） |
| 走 writer/reviewer/fixer | **是** | **否**（核心差异） |
| source_files 入库 | 是 | 是 |
| source_file_chunks 入库 | 是 | 是 |
| AST graph | 仅 `.java` | 仅 `.java` |
| fact cards | 是 | 否 |
| vector index | 是（article vector） | 是（chunk vector） |
| 搜索能力 | 文章 + chunk + source | chunk + source + AST |
| citation | 指向 article | **指向真实源码路径** |

---

## 3. 现状链路分析

### 3.1 当前编译图节点（20 个）

```
initialize_job → ingest_sources → persist_source_files → persist_source_file_chunks
→ extract_ast_graph → group_sources → split_batches → analyze_batches
→ merge_concepts → [compile_new_articles → review_articles → fix_review_issues] → persist_articles
→ rebuild_article_chunks → refresh_vector_index → generate_synthesis_artifacts
→ capture_repo_snapshot → finalize_job
```

### 3.2 CODE_LIGHT 应保留的节点

| 节点 | 保留 | 理由 |
|------|:---:|------|
| `initialize_job` | ✅ | LLM 快照冻结仍需要 |
| `ingest_sources` | ✅ | 源码文件扫描入库 |
| `persist_source_files` | ✅ | 源码文件和路径元数据 |
| `persist_source_file_chunks` | ✅ | 源码全文检索的 chunk 索引 |
| `extract_ast_graph` | ✅ | Java 类/方法/注解/endpoint 结构化索引 |
| `group_sources` + `split_batches` | ✅ | 仍需要分批处理 |
| `analyze_batches` | ✅（简化） | 只做文件分类，不做 LLM 分析 |
| `merge_concepts` | ✅ | 合并去重 |
| `persist_articles` | ✅（输入变化） | 接受 lightweight article 而非 LLM article |
| `rebuild_article_chunks` | ✅ | 和 persist 一起 |
| `refresh_vector_index` | ✅ | chunk vector 索引 |
| `finalize_job` | ✅ | 状态收尾 |

### 3.3 CODE_LIGHT 应跳过的节点

| 节点 | 跳过 | 理由 |
|------|:---:|------|
| `compile_new_articles` | ✅ | **核心跳过**——不调 Writer LLM |
| `review_articles` | ✅ | 不调 Reviewer LLM |
| `fix_review_issues` | ✅ | 不调 Fixer LLM |
| `generate_synthesis_artifacts` | ✅ | 代码项目不需要知识库概览 |
| `capture_repo_snapshot` | ✅ | 代码项目不需要仓库快照 |

---

## 4. 推荐方案

### 4.1 核心思路

在 `merge_concepts` 之后新增条件分支：如果 `contentProfile == CODE_LIGHT`，路由到新节点 `build_lightweight_articles`（直接从 source chunks 构建最小 article），绕过 writer/reviewer/fixer，直接进入 `persist_articles`。

### 4.2 新节点：`BuildLightweightArticlesNode`

**职责**：为每个 merged concept（源码文件）构建最小 `ArticleRecord`，满足 `PersistArticlesNode` 的输入契约。

**构建逻辑**（通用，不写任何类名/方法名/文件名特判）：

| ArticleRecord 字段 | CODE_LIGHT 取值来源 |
|------|------|
| `conceptId` | merged concept 的 conceptId |
| `title` | 源文件相对路径（如 `src/main/java/.../HelloController.java`） |
| `content` | 源文件的 chunk_text 原文（不经过 LLM 改写） |
| `articleKey` | 由 conceptId 派生 |
| `reviewStatus` | 直接设为 `passed`（代码文件不需要内容审查） |
| `lifecycle` | `ACTIVE` |
| `analysisMode` | `CODE_LIGHT` |
| `metadataJson` | 包含 `contentProfile: "CODE_LIGHT"`, 源文件路径, 文件类型 |

### 4.3 条件路由

在 `merge_concepts` 之后新增条件边：

```
merge_concepts → [条件判断]
  → contentProfile == CODE_LIGHT → build_lightweight_articles → persist_articles
  → contentProfile == DOCUMENT → compile_new_articles → review_articles → ...（现有路径）
```

### 4.4 `contentProfile` 的传播链

```
CompileExecutionRequest.contentProfile   (API 传入)
  → CompileJobRecord.contentProfile       (DB 持久化)
    → CompileGraphState.contentProfile    (图状态)
      → 条件路由判断
      → BuildLightweightArticlesNode
      → ArticleRecord.metadataJson
```

### 4.5 文件类型处理矩阵

| 文件类别 | CODE_LIGHT 处理 | DOCUMENT 处理 |
|------|------|------|
| `.java` | source chunk + AST graph + lightweight article | source chunk + AST graph + LLM article |
| mapper `.xml` | source chunk + lightweight article | source chunk + LLM article |
| `application.yml/.properties` | source chunk + lightweight article | source chunk + LLM article |
| `pom.xml` / `build.gradle` | source chunk + lightweight article | source chunk + LLM article |
| `README.md` / `*.md` | **可选走 DOCUMENT**（配置化） | source chunk + LLM article |
| 设计文档 / 接口文档 | **可选走 DOCUMENT**（配置化） | source chunk + LLM article |
| test source | source chunk（不生成 article） | source chunk（不生成 article） |
| generated source | **跳过**（不导入） | **跳过**（不导入） |
| `target/` / `build/` | **排除**（物化阶段过滤） | **排除** |

### 4.6 CODE_LIGHT 处理流程图

```
INTERNAL_MIRROR source (contentProfile=CODE_LIGHT)
  │
  ├─ initialize_job ✅
  ├─ ingest_sources ✅ (扫描镜像目录)
  ├─ persist_source_files ✅ (源码文件元数据入库)
  ├─ persist_source_file_chunks ✅ (源码全文检索索引)
  ├─ extract_ast_graph ✅ (仅 .java → 类/方法/注解/endpoint/调用关系)
  ├─ group_sources + split_batches ✅
  ├─ analyze_batches ✅ (简化：只做文件分类，不打 LLM)
  ├─ merge_concepts ✅
  │
  ├─ [条件分支: contentProfile=CODE_LIGHT]
  │   └─ build_lightweight_articles 🆕
  │       为每个源码文件构建最小 ArticleRecord:
  │         title = 文件路径
  │         content = chunk_text 原文
  │         reviewStatus = passed
  │         metadataJson.contentProfile = CODE_LIGHT
  │
  ├─ persist_articles ✅ (接受 lightweight article)
  ├─ rebuild_article_chunks ✅
  ├─ refresh_vector_index ✅ (chunk vector)
  │
  ├─ compile_new_articles ❌ 跳过
  ├─ review_articles ❌ 跳过
  ├─ fix_review_issues ❌ 跳过
  ├─ generate_synthesis_artifacts ❌ 跳过
  ├─ capture_repo_snapshot ❌ 跳过
  │
  └─ finalize_job ✅
```

---

## 5. 与 DOCUMENT 模式对比

| 维度 | DOCUMENT | CODE_LIGHT |
|------|------|------|
| source_files | ✅ | ✅ |
| source_file_chunks | ✅ | ✅ |
| AST graph（.java） | ✅ | ✅ |
| Writer LLM 调用 | 每文件 1 次 | **0 次** |
| Reviewer LLM 调用 | 每文件 1-2 次 | **0 次** |
| Fixer LLM 调用 | 0-N 次 | **0 次** |
| 每文件 LLM token 成本 | ~2000-8000 | **0** |
| article 生成 | LLM 文章 | **源码原文作为 article content** |
| fact cards | ✅ | ❌ |
| article vector | ✅ | ✅（chunk vector） |
| 2000 文件编译耗时 | **30-60 分钟** | **< 2 分钟**（预估） |
| citation 指向 | LLM 文章 | **真实源码路径** |
| search 召回 | article FTS + chunk FTS + vector | chunk FTS + source FTS + AST + vector |
| query 取证 | 从 LLM 文章取证 | **从 source chunk 原文 + AST 结构化索引取证** |

---

## 6. 对 Query / Search / Citation 的影响

### 6.1 搜索

CODE_LIGHT 不影响现有搜索通道。source_file_chunks 已在 `article_chunk_fts` 和 `chunk_vector` 通道中可检索。AST graph 在 `graph` 通道中可检索。citation 验证指向真实源码路径（`source_files.file_path`），验证逻辑不变。

### 6.2 问答

没有 LLM article 时，query 从以下来源取证：
- `source_file_chunks`（全文检索 + FTS）
- AST `graph_entities` / `graph_facts`（结构化代码索引）
- `source_files`（文件级检索）

citation 引用真实源码路径（如 `src/main/java/.../HelloController.java:42`），不是生成文章。

### 6.3 Citation 验证

`CitationValidator` 现有的 source file overlap 验证路径可直接用于 CODE_LIGHT 的 citation——citation 指向 `source_files` 表中的真实文件，validator 用 claim hard fact token 与 source file content 做 overlap 计算。不需要 terminal unit evidence 路径。

---

## 7. 对 Eval 的影响

- DOCUMENT profile 的 public eval（PE1-PE4）**不受影响**——CODE_LIGHT 是独立 profile，默认不启用
- 需要新增 **Java Codebase Public Eval**（建议作为 PE5），验证 CODE_LIGHT 下的代码问答能力
- 代码 eval 题集应覆盖：类名查询、方法签名查询、注解查询、配置 key 查询、endpoint 查询、mapper SQL 查询、跨文件引用、包路径搜索

---

## 8. 风险与边界

| 风险 | 控制 |
|------|------|
| 不能为代码题集写类名/方法名/endpoint 特判 | CODE_LIGHT 只改编译期行为，不改 query 主链；代码 question 中的类名只是普通文本 token |
| 不得改 query 主链去"认识某个项目" | query/retrieval/rerank/citation 主链零修改 |
| 不得牺牲 DOCUMENT 编译质量 | DOCUMENT 路径完全保留，CODE_LIGHT 是新增条件分支 |
| 不得恢复旧 SERVER_DIR | CODE_LIGHT 不涉及 source type 变更 |
| 不得读取真实公司私有代码 | 代码 eval 使用合成 Java 项目 fixture |
| lightweight article 的 reviewStatus 直接设为 passed | 代码源码文件不需要内容审查，源码原文没有"编造"风险；文件名级安全过滤已在物化阶段完成 |

---

## 9. 分阶段落地建议

### Phase 1：最小闭环（MVP）

1. 新增 `contentProfile` 字段：`CompileExecutionRequest` → `CompileJobRecord` → `CompileGraphState`
2. 新增 `BuildLightweightArticlesNode`：从 source chunks 构建最小 ArticleRecord
3. 在 `merge_concepts` 后新增条件路由：CODE_LIGHT → build_lightweight_articles → persist_articles
4. 默认 `contentProfile = DOCUMENT`（向后兼容），INTERNAL_MIRROR 可显式指定 `CODE_LIGHT`
5. 验证：合成 Java 项目（20-50 文件）的 CODE_LIGHT 编译耗时 < 1 分钟

### Phase 2：结构化索引增强

1. 补 endpoint/class/method/config/mapper 的结构化元数据索引
2. 增强 AST graph 的 `graph` 通道检索权重
3. 验证：类名/方法名/配置 key 的 Recall@5 >= 90%

### Phase 3：代码问答 Eval

1. 设计 Public Eval 5（Java Codebase 题集，~30 题）
2. 覆盖：精确类名、方法签名、注解、配置 key、endpoint 路径、SQL mapper、跨文件引用、包搜索
3. 验证：Answer Accuracy >= 80%、Citation Accuracy >= 75%

### Phase 4：大项目性能与持续同步

1. 补定时轮询、删除 reconciliation、扫描限额
2. 2000+ 文件的 CODE_LIGHT 编译耗时 benchmark < 3 分钟
3. 验证：增量 sync（修改触发）< 30 秒

---

## 10. agentA MVP 最小实现提示

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
实现 CODE_LIGHT 内容处理策略的最小闭环——让 INTERNAL_MIRROR 导入的 Java 项目
在编译时跳过 writer/reviewer/fixer，直接以源码原文作为 article content 入库。

修改范围：
1. CompileExecutionRequest / CompileJobRecord / CompileGraphState — 新增 contentProfile 字段
2. CompileGraphDefinitionFactory — 新增条件路由 + BuildLightweightArticlesNode
3. BuildLightweightArticlesNode（新增） — 从 source chunks 构建最小 ArticleRecord
4. INTERNAL_MIRROR sync 入口 — 支持传入 contentProfile=CODE_LIGHT
5. 不改 query/answer/rerank/fallback/citation 主链

通用性要求：
- contentProfile 默认值为 DOCUMENT（向后兼容）
- CODE_LIGHT 对 .java/.xml/.yml/.properties/.json/.sql 等所有代码文件类型一视同仁
- lightweight article 的 title = 文件相对路径，content = chunk_text 原文
- reviewStatus 直接设为 passed
- 不写任何类名、方法名、文件名、项目名特判

禁止事项：
- 禁止修改 query/answer/rerank/fallback 主链
- 禁止修改 writer/reviewer/fixer 的现有逻辑
- 禁止修改 schema.sql
- 禁止提交 commit
```

---

## 11. agentD 验证建议

1. 使用合成 Java 项目 fixture（20-50 文件，含 Controller/Service/Mapper/Config/Entity）
2. INTERNAL_MIRROR 导入 + contentProfile=CODE_LIGHT
3. 验证：编译成功，耗时 < 1 分钟
4. 验证：source_files 和 source_file_chunks 可检索
5. 验证：AST graph 含类/方法/注解
6. 验证：搜索类名/方法名/配置 key 可命中
7. 验证：citation 指向真实源码路径
8. 对比：同项目 DOCUMENT 模式的编译耗时（验证 CODE_LIGHT 的加速比）

---

## 12. 不建议做的方案

| 方案 | 原因 |
|------|------|
| 在 query 层识别"这是代码问题"并特殊处理 | 违反红线——query 主链不得认识特定项目 |
| 把所有源码文件都生成 LLM 摘要 | 2000+ 文件 × 2000 token = 400 万 token，成本不可控 |
| 为 CODE_LIGHT 新增 source type | INTERNAL_MIRROR 已经是 source type；contentProfile 是正交维度 |
| 降低 writer/reviewer 的 LLM 调用频率（如采样） | 治标不治本——仍需要 LLM，且可能遗漏关键文件 |
| 只对 INTERNAL_MIRROR 生效，不支持 UPLOAD/GIT | CODE_LIGHT 应作为通用 contentProfile，对所有 source type 可选 |

---

## 13. 明确声明

- [x] 本轮为纯设计，未修改任何代码
- [x] 未修改测试、prompt、config、schema、scripts
- [x] 未导入资料、未清库、未跑模型
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 设计方案中不含任何类名/方法名/文件名/项目名特判
- [x] CODE_LIGHT 是通用 contentProfile，对所有代码文件类型一视同仁
- [x] 不改变 DOCUMENT profile 的现有行为
