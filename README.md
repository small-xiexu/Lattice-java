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

## 从零到一：完整上手流程

### 第一步：确认前置条件

| 依赖 | 最低版本 | 验证命令 |
|------|----------|----------|
| Docker + Docker Compose | 24+ | `docker compose version` |
| JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |

确保 5432（PostgreSQL）和 6379（Redis）端口未被占用：

```bash
lsof -i :5432
lsof -i :6379
```

---

### 第二步：获取代码

```bash
git clone <仓库地址>
cd lattice-java
```

---

### 第三步：启动依赖服务

```bash
docker compose up -d
```

等待约 15 秒，确认两个服务都健康：

```bash
docker compose ps
```

期望输出中 `STATUS` 列显示 `healthy`：

```
NAME                   STATUS
lattice_b1_postgres    Up (healthy)
lattice_b3_redis       Up (healthy)
```

如果状态是 `starting`，再等几秒后重新检查。

---

### 第四步：创建数据库 Schema

```bash
docker exec lattice_b1_postgres psql -U postgres -d ai-rag-knowledge \
  -c "CREATE SCHEMA IF NOT EXISTS lattice;"
```

成功输出：`CREATE SCHEMA`

---

### 第五步：构建应用

```bash
mvn package -DskipTests
```

构建成功后，`target/lattice-java-1.0-SNAPSHOT.jar` 存在即可。

---

### 第六步：启动应用

设置环境变量并启动（全部复制粘贴执行）：

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

java -jar target/lattice-java-1.0-SNAPSHOT.jar
```

> **关于 API Key**：当前版本答案生成和审查均为本地逻辑，无真实 LLM 调用，填任意非空字符串即可。

看到以下日志说明启动成功：

```
Started LatticeApplication in X.XXX seconds
```

Flyway 会在首次启动时自动执行 V1–V5 建表迁移，无需手动建表。

---

### 第七步：验证应用健康

新开一个终端，执行健康检查：

```bash
curl http://localhost:8080/actuator/health
```

期望响应：

```json
{"status":"UP"}
```

---

### 第八步：准备第一批知识文件

新建一个目录作为知识源：

```bash
mkdir -p /tmp/my-lattice-kb/payment
```

创建第一个知识文件 `/tmp/my-lattice-kb/payment/analyze.json`：

```json
{
  "concepts": [
    {
      "id": "payment-timeout",
      "title": "支付超时配置",
      "description": "支付服务超时与重试策略",
      "snippets": ["retry=3", "timeout=5s", "interval=30s"],
      "sections": [
        {
          "heading": "超时规则",
          "content": [
            "连接超时：timeout=5s",
            "最大重试次数：retry=3",
            "重试间隔：interval=30s"
          ]
        }
      ]
    },
    {
      "id": "payment-error-codes",
      "title": "支付错误码",
      "description": "支付服务业务错误码含义",
      "snippets": ["5001", "5002", "支付网关", "余额不足"],
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

---

### 第九步：编译知识库

```bash
curl -s -X POST http://localhost:8080/api/v1/compile \
  -H "Content-Type: application/json" \
  -d '{"sourceDir": "/tmp/my-lattice-kb"}' | python3 -m json.tool
```

期望响应：

```json
{
    "persistedCount": 2,
    "jobId": "9da6c973-8789-41c7-8aae-b9d85cf72a76"
}
```

`persistedCount: 2` 表示成功写入了 2 个概念（`payment-timeout` 和 `payment-error-codes`）。

---

### 第十步：验证查询（不需要 AI 客户端）

在连接 Claude Desktop 之前，先用 HTTP 接口确认查询链路正常：

```bash
curl -s -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"question": "支付超时重试参数是多少"}' | python3 -m json.tool
```

期望响应包含 `answer` 和 `queryId`：

```json
{
    "answer": "retry=3",
    "sources": [...],
    "queryId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "reviewStatus": "PASSED"
}
```

查询成功说明编译结果可被检索。

---

### 第十一步：连接 Claude Desktop

**编辑配置文件**（macOS）：

```
~/Library/Application Support/Claude/claude_desktop_config.json
```

如果文件不存在，新建它：

```json
{
  "mcpServers": {
    "lattice": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

**重启 Claude Desktop**，等待约 5 秒。

**验证连接**：在对话框输入 `ping`，Claude 应该调用 `lattice_ping` 并回复 `pong`。

对话框底部工具栏出现 Lattice 图标，说明 5 个 MCP 工具已全部注册。

---

### 第十二步：第一次 AI 对话

在 Claude Desktop 对话框里直接问：

```
我们系统 payment 服务超时了，重试间隔是多少？
```

Claude 会自动调用 `lattice_query` 并给出答案。

如果答案有误：

```
你说的不对，重试间隔已经改成 60s 了
```

Claude 会调用 `lattice_query_correct` 记录纠正，再问：

```
对的，确认这条修订
```

Claude 调用 `lattice_query_confirm`，修订永久沉淀为 `contributions` 记录。

---

## Cursor 接入

在 Cursor 的 MCP 设置页面（Settings → MCP）添加 Server：

- **Name**：lattice
- **URL**：`http://localhost:8080/mcp`

保存后在 Cursor 的 AI 对话里即可使用，用法与 Claude Desktop 相同。

---

## 知识文件格式详解

### 推荐：结构化 JSON

适合已知知识结构的场景，检索命中率最高：

```json
{
  "concepts": [
    {
      "id": "概念唯一标识",
      "title": "概念标题",
      "description": "一句话描述，影响检索召回",
      "snippets": ["关键词1", "关键词2", "精确匹配词"],
      "sections": [
        {
          "heading": "章节标题",
          "content": [
            "条目一",
            "条目二"
          ],
          "sources": ["可选，显式指定来源文件引用"]
        }
      ]
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 全局唯一，自动归一化为小写连字符（`Payment Timeout` → `payment-timeout`） |
| `title` | 是 | 概念标题，展示在来源里 |
| `description` | 否 | 简短描述，影响全文检索命中 |
| `snippets` | 否 | 精确匹配片段，提升引用词检索召回率 |
| `sections` | 否 | 结构化章节，内容越详细答案越准确 |

### 也支持：普通文本文件

`.md`、`.java`、`.yml`、`.xml`、`.vue`、`.js` 直接扔进目录即可，系统会以文件内容整体作为知识片段导入。适合快速迁移已有文档：

```
knowledge-source/
├── payment/
│   └── analyze.json       ← 推荐，结构化
├── adr/
│   ├── ADR-012.md         ← 支持，全文导入
│   └── ADR-013.md
└── ops/
    └── runbook.md
```

---

## 分组规则与目录结构

目录的**第一层子目录名**就是分组键（group key），同组内的文件会被合并为一批进行分析：

```
knowledge-source/
├── payment/          ← group: "payment"，所有文件合并分析
│   ├── timeout.json
│   └── gateway.md
├── order/            ← group: "order"
│   └── status.md
└── config.yml        ← 根目录文件，归入 defaultGroup
```

也可以在 `application.yml` 配置显式分组规则覆盖默认行为：

```yaml
lattice:
  compiler:
    grouping-rules:
      - pattern: "payment/**"
        group-key: payment-system
      - pattern: "**/*-runbook.md"
        group-key: ops-runbook
    default-group: misc
    ingest-max-chars: 8192    # 单文件最大读取字符数（超出截断）
    batch-max-chars: 40000    # 同组内分批阈值
```

---

## 接口说明

### 管理接口（ops / CI 调用）

供运维人员或 CI 流水线调用，管理知识库的编译与维护：

| 接口 | 说明 |
|------|------|
| `POST /api/v1/compile` | 触发知识库编译，指定源目录 |
| `POST /api/v1/compile/retry` | 用 jobId 恢复中断的编译任务 |

编译中断后，已落库的 article 不会重复写入。使用 `jobId` 恢复未完成部分：

```bash
curl -X POST http://localhost:8080/api/v1/compile/retry \
  -H "Content-Type: application/json" \
  -d '{"jobId": "9da6c973-8789-41c7-8aae-b9d85cf72a76"}'
```

重新编译同一目录会幂等 upsert（按 `conceptId`），不产生重复数据。

---

### MCP 工具（AI 客户端自动调用）

通过 MCP 协议暴露，由 Claude Desktop / Cursor 在对话中自动调用，工程师无需直接操作：

| 工具 | 触发场景 |
|------|----------|
| `lattice_query` | 向知识库提问，返回答案 + 来源 + queryId |
| `lattice_query_pending` | 查看某个 queryId 的当前待确认状态 |
| `lattice_query_correct` | 对答案提交纠正，保持 pending 状态 |
| `lattice_query_confirm` | 确认答案，沉淀为贡献记录 |
| `lattice_ping` | 连通性探测 |

---

### 调试接口（等价于 MCP 的 HTTP 版本）

在没有 AI 客户端时用于本地调试，与 MCP 工具等价：

| 接口 | 对应 MCP 工具 |
|------|--------------|
| `POST /api/v1/query` | `lattice_query` |
| `POST /api/v1/query/{id}/correct` | `lattice_query_correct` |
| `POST /api/v1/query/{id}/confirm` | `lattice_query_confirm` |
| `POST /api/v1/query/{id}/discard` | 无对应 MCP，直接丢弃 pending |

---

## 反馈闭环

每次查询都会生成一条 `pending_query` 记录（TTL 7 天）：

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
| `article_chunks` | 文章分块（预留向量检索扩展） |
| `source_files` | 已索引的源文件记录 |
| `pending_queries` | 待确认查询，TTL 7 天 |
| `contributions` | 已确认问答贡献，永久保存 |

所有表由 Flyway 自动创建（V1–V5），首次启动无需手动建表。

---

## 配置参考

### 环境变量

| 变量名 | 是否必填 | 默认值 | 说明 |
|--------|----------|--------|------|
| `SPRING_PROFILES_ACTIVE` | 必填 | 无 | 必须设为 `jdbc` |
| `SPRING_DATASOURCE_URL` | 必填 | 无 | 需包含 `?currentSchema=xxx` |
| `SPRING_DATASOURCE_USERNAME` | 必填 | 无 | |
| `SPRING_DATASOURCE_PASSWORD` | 必填 | 无 | |
| `SPRING_FLYWAY_ENABLED` | 建议 | `false` | 首次启动设为 `true` 自动建表 |
| `SPRING_AI_OPENAI_API_KEY` | 必填 | 无 | 任意非空字符串（当前无真实调用） |
| `SPRING_AI_ANTHROPIC_API_KEY` | 必填 | 无 | 任意非空字符串（当前无真实调用） |
| `LATTICE_REDIS_HOST` | 否 | `127.0.0.1` | |
| `LATTICE_REDIS_PORT` | 否 | `6379` | |

### 编译参数

```yaml
lattice:
  compiler:
    ingest-max-chars: 8192      # 单文件最大采集字符数（超出截断）
    batch-max-chars: 40000      # 分批最大字符数
    default-group: defaultGroup # 无法匹配规则时的兜底分组
    grouping-rules:             # 显式分组规则（可选）
      - pattern: "payment/*"
        group-key: payment
```

### 缓存与 WAL

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `LATTICE_QUERY_CACHE_TTL_SECONDS` | `3600` | 查询缓存 TTL（秒） |
| `LATTICE_COMPILER_WAL_TTL_SECONDS` | `86400` | 编译 WAL TTL（秒） |

本地开发可切换为内存缓存，无需 Redis：

```yaml
lattice:
  query:
    cache:
      store: in-memory  # 生产环境改为 redis
```

---

## 常见问题

**Q：启动报 `Connection refused` 连接数据库失败**

确认 PostgreSQL 容器已健康：`docker compose ps`。确认 `SPRING_DATASOURCE_URL` 中的主机和端口正确。

**Q：编译后 `persistedCount: 0`**

检查 JSON 文件是否合法：`cat your-file.json | python3 -m json.tool`。确认 JSON 根节点有 `concepts` 数组，且每个概念有 `id` 和 `title` 字段。`id` 归一化后不能为 `default`。

**Q：查询返回空答案**

确认编译时 `persistedCount > 0`。检查问题里是否包含知识文件中 `snippets` 或 `description` 里出现过的词。引用词精确匹配区分大小写。

**Q：Claude Desktop 看不到 Lattice 工具**

确认配置文件路径和 JSON 格式正确，重启 Claude Desktop，确认应用在 `8080` 端口正常运行：`curl http://localhost:8080/actuator/health`。

**Q：`lattice_query` 和调试 HTTP 接口结果不一致**

两者共用同一条查询链路（`QueryFacadeService`），结果应该一致。不一致通常是 Redis 缓存导致，重启应用或等 TTL 过期。

---

## 运行测试

```bash
# 全量集成测试（需要 PostgreSQL + Redis 在线）
mvn test

# 仅单元测试（无需外部依赖）
mvn test -Dtest="AnalyzeNodeTests,CrossGroupMergeNodeTests,ReviewResultParserTests,\
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
│   └── query/               # 查询调试接口（MCP 工具的 HTTP 等价）
├── compiler/
│   └── service/             # 编译五节点
│       ├── IngestNode.java        # 扫描目录，读取文件
│       ├── GroupNode.java         # 按路径规则分组
│       ├── BatchSplitNode.java    # 超大组切批次
│       ├── AnalyzeNode.java       # 提取结构化概念
│       ├── CrossGroupMergeNode.java   # 跨组合并同概念
│       └── CompilePipelineService.java
├── query/
│   └── service/             # 查询服务
│       ├── QueryFacadeService.java    # 查询入口，协调各子服务
│       ├── FtsSearchService.java      # 全文检索
│       ├── RefKeySearchService.java   # 引用词精确匹配
│       ├── RrfFusionService.java      # RRF 融合排序
│       ├── AnswerGenerationService.java  # 本地答案生成
│       ├── PendingQueryService.java   # pending_query 管理
│       └── ReviewerAgent.java         # 答案质量审查
├── mcp/
│   ├── LatticeMcpTools.java   # 4 个业务 MCP 工具
│   └── PingTool.java          # 连通性探测工具
└── infra/
    └── persistence/           # JDBC Repository 层
```
