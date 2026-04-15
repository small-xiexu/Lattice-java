# Lattice

企业内部知识库后端，专为 AI 客户端（Claude Desktop / Cursor）设计。

把你的内部文档、配置规范、系统设计编译进知识库，让 AI 在开发对话中直接查询、纠错、确认——无需浏览器，无需搜索框，AI 就是界面。

---

## 解决什么问题

你是否遇到过这些情况：

- 问 ChatGPT / Claude "我们系统的支付超时重试是怎么配置的"，AI 一无所知
- 新同事入职，每天都在问"这个错误码是什么意思"
- 写代码时不确定某个内部服务的接口规范，要去翻 Confluence
- 系统出故障，on-call 工程师花了半小时才找到对应的 Runbook

**根本问题**：你的 AI 助手只有公开知识，没有你团队的私有知识。

Lattice 把这部分知识补齐：

```
你的内部知识（配置文档、Runbook、设计规范）
        │
        ▼  编译一次
  ┌─────────────────────┐
  │  Lattice 知识库     │
  └─────────────────────┘
        │  MCP 协议
        ▼
  Claude Desktop / Cursor
        │
        ▼
  工程师直接在对话里得到答案
```

---

## 典型使用场景

### 场景一：查配置规范

团队有一套复杂的分布式系统，各服务的超时、重试、熔断参数分散在多份文档里。

```
工程师（在 Cursor 里）：payment 服务的超时重试参数是多少？

Claude：（自动调用 Lattice）
         根据知识库：
         - 超时时间：timeout=5s
         - 重试次数：retry=3
         - 重试间隔：interval=30s
         来源：payment/config.md
```

### 场景二：查错误码

系统有几百个内部错误码，on-call 工程师在凌晨看到报警不知道是什么意思。

```
工程师：错误码 5001 是什么，怎么处理？

Claude：（自动调用 Lattice）
         5001 = 支付网关连接超时。
         处理建议：检查网关服务健康状态，
         参考 Runbook：ops/payment-gateway-runbook.md
```

### 场景三：新人答疑

新同事入职，不断问架构相关问题，老同事反复解答。

```
新同事：为什么订单服务不直接调用库存服务，要走消息队列？

Claude：（自动调用 Lattice）
         根据架构决策记录（ADR-012）：
         直接同步调用在促销峰值期间导致过三次级联故障，
         2023 年切换为消息队列解耦。详见 adr/ADR-012.md
```

### 场景四：答案有误时纠正

```
工程师：数据库连接池大小是多少？

Claude：（调用 Lattice）默认是 10。

工程师：不对，去年已经改成 20 了。

Claude：（调用 lattice_query_correct）已记录纠正。要确认这条修订吗？

工程师：确认。

Claude：（调用 lattice_query_confirm）已确认，修订已沉淀为知识贡献。
```

---

## 与普通 RAG 知识库的区别

市面上已经有很多 RAG 方案，Lattice 的不同之处：

| 对比项 | 普通 RAG | Lattice |
|--------|----------|---------|
| **知识输入** | 扔进去原始文档（PDF/Word/网页） | 结构化概念文件，显式定义知识点 |
| **检索方式** | 向量相似度（embedding） | 全文检索 + 引用词精确匹配，结果可预测 |
| **LLM 依赖** | 查询时必须调用 LLM 生成答案 | 答案生成为本地逻辑，无 LLM 调用成本 |
| **答案错了怎么办** | 重新导入文档，等待重新索引 | 直接在对话里 correct → confirm，立即生效 |
| **知识积累** | 静态，文档更新才能更新知识 | 每次确认的问答对写入 contributions，知识持续积累 |
| **使用界面** | 通常有搜索框 / 聊天界面 | 无独立界面，AI 客户端就是界面 |
| **基础设施复杂度** | 需要 embedding 模型 + 向量数据库 | PostgreSQL + Redis，无向量依赖 |

**Lattice 不是万能的**：如果你的知识是大量非结构化文档（几千页 PDF），普通 RAG 更合适。Lattice 适合知识结构相对清晰、可以被显式定义为"概念 + 规则"的场景。

---

## 技术栈

| 组件 | 选型 |
|------|------|
| 运行时 | JDK 21 + Spring Boot 3.5 |
| AI 集成 | Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.0 |
| MCP 协议 | Spring AI MCP Server WebMVC（Streamable HTTP） |
| 数据库 | PostgreSQL 16 + pgvector |
| 缓存 / WAL | Redis 7 |
| 数据库迁移 | Flyway |
| 构建工具 | Maven 3 |

---

## 快速启动

### 1. 启动依赖服务

```bash
docker compose up -d
```

启动 PostgreSQL 16（端口 `5432`，库名 `ai-rag-knowledge`）和 Redis 7（端口 `6379`）。

### 2. 创建 Schema

```bash
docker exec lattice_b1_postgres psql -U postgres -d ai-rag-knowledge \
  -c "CREATE SCHEMA IF NOT EXISTS lattice;"
```

### 3. 配置环境变量

```bash
export SPRING_PROFILES_ACTIVE=jdbc
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice"
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export SPRING_FLYWAY_ENABLED=true
export SPRING_FLYWAY_SCHEMAS=lattice
export SPRING_FLYWAY_DEFAULT_SCHEMA=lattice
export SPRING_AI_OPENAI_API_KEY=any-non-empty-value
export SPRING_AI_ANTHROPIC_API_KEY=any-non-empty-value
```

> **注意**：当前版本答案生成和审查均为本地逻辑，无真实 LLM 调用，API Key 填任意非空字符串即可。

### 4. 启动应用

```bash
mvn package -DskipTests
java -jar target/lattice-java-1.0-SNAPSHOT.jar
```

应用启动后监听 `http://localhost:8080`，MCP 端点为 `http://localhost:8080/mcp`。

---

## 第一步：把知识编译进知识库

### 组织知识目录

创建一个本地目录，按模块分子目录存放知识文件：

```
knowledge-source/
├── payment/
│   └── analyze.json       ← 支付模块知识
├── order/
│   └── analyze.json       ← 订单模块知识
└── ops/
    ├── error-codes.md     ← 普通文本也支持
    └── runbook.md
```

### 编写 analyze.json

这是推荐的结构化格式，直接定义知识点，无需 LLM 解析：

```json
{
  "concepts": [
    {
      "id": "payment-timeout",
      "title": "Payment Timeout 配置",
      "description": "支付服务超时与重试策略",
      "snippets": ["retry=3", "timeout=5s", "interval=30s"],
      "sections": [
        {
          "heading": "超时规则",
          "content": [
            "连接超时：timeout=5s",
            "最大重试次数：retry=3",
            "重试间隔：interval=30s"
          ],
          "sources": ["payment/config.md#timeout"]
        }
      ]
    },
    {
      "id": "payment-error-codes",
      "title": "支付错误码",
      "description": "支付服务业务错误码含义",
      "snippets": ["5001", "5002", "支付网关"],
      "sections": [
        {
          "heading": "错误码列表",
          "content": [
            "5001：支付网关连接超时，检查网关服务健康状态",
            "5002：余额不足，提示用户充值"
          ]
        }
      ]
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 概念唯一标识，自动归一化为小写连字符 |
| `title` | 是 | 概念标题 |
| `description` | 否 | 简短描述，影响检索命中 |
| `snippets` | 否 | 关键词片段，提升精确匹配召回率 |
| `sections` | 否 | 结构化章节，内容越详细答案越准 |

> 普通 `.md` / `.txt` 文件也可以直接放入目录，会以文件名作为概念 ID、文件内容作为知识正文导入，适合快速迁移已有文档。

### 触发编译

```bash
curl -X POST http://localhost:8080/api/v1/compile \
  -H "Content-Type: application/json" \
  -d '{"sourceDir": "/path/to/knowledge-source"}'
```

响应：

```json
{
  "persistedCount": 3,
  "jobId": "9da6c973-8789-41c7-8aae-b9d85cf72a76"
}
```

`persistedCount` 表示本次新写入的 article 数量。相同 `conceptId` 的内容会幂等覆盖，重复编译不会产生重复数据。

---

## 第二步：连接 AI 客户端

### Claude Desktop

编辑配置文件（macOS：`~/Library/Application Support/Claude/claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "lattice": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

重启 Claude Desktop 后，在对话界面底部工具栏可看到 Lattice 工具已接入。

### Cursor

在 Cursor 的 MCP 设置页添加 server，URL 填 `http://localhost:8080/mcp`。

### 验证连接

在 AI 对话里问：`lattice ping`，Claude 应调用 `lattice_ping` 工具并返回 `pong`。

---

## 接口说明

Lattice 的接口分为两层，职责不同：

### 管理接口（ops / CI 调用）

这部分接口供运维人员或 CI 流水线调用，管理知识库的编译与维护。**不通过 AI 客户端调用。**

| 接口 | 说明 |
|------|------|
| `POST /api/v1/compile` | 触发知识库编译，指定源目录 |
| `POST /api/v1/compile/retry` | 恢复中断的编译任务 |

**编译触发示例：**

```bash
curl -X POST http://localhost:8080/api/v1/compile \
  -H "Content-Type: application/json" \
  -d '{"sourceDir": "/data/knowledge-source"}'
```

**中断恢复：** 编译中断后，已落库的 article 不会重复写入。使用 `jobId` 重试未完成部分：

```bash
curl -X POST http://localhost:8080/api/v1/compile/retry \
  -H "Content-Type: application/json" \
  -d '{"jobId": "9da6c973-8789-41c7-8aae-b9d85cf72a76"}'
```

---

### MCP 工具（AI 客户端调用）

这部分能力通过 MCP 协议暴露，由 Claude Desktop / Cursor 在对话中自动调用。**工程师无需直接操作。**

| 工具 | 用途 |
|------|------|
| `lattice_query` | 向知识库提问，返回答案 + 来源 + queryId |
| `lattice_query_pending` | 查看某个 queryId 的当前待确认状态 |
| `lattice_query_correct` | 对答案提交纠正，保持 pending 状态 |
| `lattice_query_confirm` | 确认答案，沉淀为贡献记录 |
| `lattice_ping` | 连通性探测 |

---

### 调试接口（不作为正式集成入口）

以下 HTTP 接口在无 AI 客户端时用于调试，等价于 MCP 工具的 HTTP 版本：

| 接口 | 对应 MCP 工具 |
|------|--------------|
| `POST /api/v1/query` | `lattice_query` |
| `POST /api/v1/query/{id}/correct` | `lattice_query_correct` |
| `POST /api/v1/query/{id}/confirm` | `lattice_query_confirm` |
| `POST /api/v1/query/{id}/discard` | 无对应 MCP 工具 |

---

## 反馈闭环

每次查询都会生成一条 `pending_query` 记录（TTL 7 天）。

```
查询 → 返回 answer + queryId
  │
  ├── 答案正确 ──→ confirm ──→ 写入 contributions（永久保存）
  │
  └── 答案有误 ──→ correct（可多次）──→ confirm ──→ 写入修订后的 contributions
                                        └─ 或 discard（直接丢弃，不沉淀）
```

`contributions` 表是知识质量的证明——每一条都是经过人工确认的正确问答对，可作为后续评测、Fine-tuning 或审计的数据来源。

---

## 查询链路

查询请求经过以下链路处理：

```
问题
 │
 ├─ Redis 缓存命中？──→ 直接返回
 │
 ▼  未命中
 FTS 全文检索（PostgreSQL tsvector）
 + 引用词精确匹配（referential_keywords @>）
 │
 ▼  RRF 融合排序
 取 Top-K 结果
 │
 ▼  本地答案生成
 基于 Top-1 article token 匹配生成答案，无 LLM 调用
 │
 ▼  本地规则审查
 LocalReviewerGateway（当前版本：非空输入一律 PASSED）
 │
 ▼  创建 pending_query，生成 queryId
 │
 ▼  返回 answer + sources + articles + queryId + reviewStatus
```

---

## 数据库表结构

| 表名 | 用途 |
|------|------|
| `articles` | 编译后的知识文章，每个 `conceptId` 唯一一条 |
| `article_chunks` | 文章分块，预留向量检索扩展，当前版本暂未启用 |
| `source_files` | 已索引的源文件记录 |
| `pending_queries` | 待确认查询，TTL 7 天 |
| `contributions` | 已确认问答贡献，永久保存 |

所有表由 Flyway 自动创建（V1–V5），首次启动无需手动建表。

---

## 配置参考

### 数据库与 Redis

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `SPRING_DATASOURCE_URL` | 无，必填 | 需包含 `?currentSchema=xxx` |
| `SPRING_DATASOURCE_USERNAME` | 无，必填 | |
| `SPRING_DATASOURCE_PASSWORD` | 无，必填 | |
| `LATTICE_REDIS_HOST` | `127.0.0.1` | |
| `LATTICE_REDIS_PORT` | `6379` | |

### 编译参数

```yaml
lattice:
  compiler:
    ingest-max-chars: 8192      # 单文件最大采集字符数
    batch-max-chars: 40000      # 分批最大字符数
    default-group: defaultGroup # 无法匹配规则时的兜底分组
    grouping-rules:             # 显式分组规则（可选）
      - pattern: "payment/*"
        group-key: payment
```

### 缓存与 WAL

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `LATTICE_QUERY_CACHE_TTL_SECONDS` | `3600` | 查询缓存 TTL（秒） |
| `LATTICE_COMPILER_WAL_TTL_SECONDS` | `86400` | 编译 WAL TTL（秒） |

```yaml
lattice:
  query:
    cache:
      store: redis       # 生产环境
      # store: in-memory  # 测试 / 本地开发，无需 Redis
```

---

## 运行测试

```bash
# 全量测试（需要 PostgreSQL + Redis 在线）
mvn -s .codex/maven-settings.xml test

# 仅单元测试（无需外部依赖）
mvn -s .codex/maven-settings.xml test \
  -Dtest="AnalyzeNodeTests,CrossGroupMergeNodeTests,ReviewResultParserTests,\
ReviewerAgentTests,QueryFacadeServiceCacheTests,LatticeMcpToolsTest,\
IngestNodeTests,GroupNodeTests,BatchSplitNodeTests"
```

当前测试数：**47 个**，全部通过。

---

## 项目结构

```
src/main/java/com/xbk/lattice/
├── api/
│   ├── compiler/            # 编译管理接口（ops / CI 调用）
│   └── query/               # 查询调试接口 + MCP 工具的 HTTP 等价接口
├── compiler/
│   └── service/             # 编译五节点
│       ├── IngestNode.java
│       ├── GroupNode.java
│       ├── BatchSplitNode.java
│       ├── AnalyzeNode.java
│       ├── CrossGroupMergeNode.java
│       └── CompilePipelineService.java
├── query/
│   └── service/             # 查询服务
│       ├── QueryFacadeService.java
│       ├── FtsSearchService.java
│       ├── RefKeySearchService.java
│       ├── RrfFusionService.java
│       ├── AnswerGenerationService.java
│       ├── PendingQueryService.java
│       └── ReviewerAgent.java
├── mcp/
│   ├── LatticeMcpTools.java  # 4 个业务 MCP 工具
│   └── PingTool.java
└── infra/
    └── persistence/          # JDBC Repository 层
```
