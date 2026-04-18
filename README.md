# Lattice

面向 AI 客户端的企业知识库与 MCP 后端。

当前项目的单一职责很明确：

- 把结构化或半结构化知识编译进知识库
- 通过 HTTP API 和 MCP 工具暴露查询、治理、反馈、导出能力
- 不再按“大而全平台”视角扩展

---

## 当前入口

本项目现在建议按下面 3 份文档进入：

- [项目启动配置清单](/Users/sxie/xbk/Lattice-java/.codex/%E9%A1%B9%E7%9B%AE%E5%90%AF%E5%8A%A8%E9%85%8D%E7%BD%AE%E6%B8%85%E5%8D%95.md)
- [项目全流程真实验收手册](/Users/sxie/xbk/Lattice-java/docs/%E9%A1%B9%E7%9B%AE%E5%85%A8%E6%B5%81%E7%A8%8B%E7%9C%9F%E5%AE%9E%E9%AA%8C%E6%94%B6%E6%89%8B%E5%86%8C.md)
- [Spring AI Alibaba Graph 完整接入设计方案](/Users/sxie/xbk/Lattice-java/.codex/Spring%20AI%20Alibaba%20Graph%20%E5%AE%8C%E6%95%B4%E6%8E%A5%E5%85%A5%E8%AE%BE%E8%AE%A1%E6%96%B9%E6%A1%88.md)

如果你只想知道“怎么启动”：

- 先看启动配置清单

如果你想知道“这个项目真实跑到了哪一步”：

- 先看全流程真实验收手册

---

## 当前真实状态

基于 **2026-04-18** 这一轮真实验收，项目已经跑通：

- 真实环境启动
- 后台 LLM 配置中心
- 复杂知识源全量编译
- 增量编译
- Query 问答
- CLI remote / standalone 调用
- `correct -> confirm` / `discard`
- Admin 治理接口
- MCP 工具调用
- Vault 导出
- 文章快照、历史与回滚

本轮真实验收中的关键结果：

- 最新补验实例运行在 `18082`，旧 `18081` 不再作为本轮收口依据
- 全量编译成功，`persistedCount=9`
- 曾专项验证过 `compile.reviewer` 与 `query.reviewer` 可按绑定切到 Claude `claude-sonnet-4-6`，但当前日常后台验收默认优先走 OpenAI 绑定
- 复杂 Query 在 HTTP、CLI remote、CLI standalone 都返回 `reviewStatus=PASSED`
- CLI remote `compile/status/search/query/vault-export` 与 standalone `status/query` 已真实补验
- MCP 工具数 `29`
- MCP 已在 `18082` 上通过 raw HTTP 真实跑通 `initialize / tools/list / lattice_status / lattice_query`
- Query `execution_llm_snapshots` 已真实冻结 `answer / reviewer / rewrite`
- confirmed contribution 已真实导出到 Vault，CLI remote 导出 `writtenFiles=13`
- 后台现已支持向量配置保存、索引状态查看与手动全量重建

本轮仍未完全打通的能力：

- 向量检索在真实 embedding 网关下仍未完全打通
- Admin 文章纠错接口
- 整库 repo diff / repo rollback

这些问题和原因已经写进：

- [项目全流程真实验收手册](/Users/sxie/xbk/Lattice-java/docs/%E9%A1%B9%E7%9B%AE%E5%85%A8%E6%B5%81%E7%A8%8B%E7%9C%9F%E5%AE%9E%E9%AA%8C%E6%94%B6%E6%89%8B%E5%86%8C.md)

---

## 项目能力面

### 编译面

- 目录型知识源编译
- Graph 驱动的 compile / review / fix 回环
- source file / chunk / article / article chunk 持久化
- repo snapshot 捕获

### 查询面

- FTS / SOURCE / ARTICLE / CONTRIBUTION 融合问答
- Query Graph
- pending query 闭环
- query scene 复用统一 LLM 配置中心与快照路由

### 治理面

- quality
- coverage / omissions
- lint
- article lifecycle
- article snapshot / history / rollback
- Vault export

### MCP 面

- `lattice_query`
- `lattice_search`
- `lattice_get`
- `lattice_status`
- `lattice_snapshot`
- `lattice_history`
- `lattice_rollback`
- `lattice_query_correct`
- `lattice_query_confirm`
- `lattice_query_discard`
- 以及其他治理工具

---

## 快速启动

### 1. 准备依赖

- JDK `21`
- Docker
- PostgreSQL
- Redis

### 2. 创建 schema

默认开发可使用 `lattice`，真实验收建议用独立 schema。

示例：

```bash
docker exec vector_db psql -U postgres -d ai-rag-knowledge \
  -c "CREATE SCHEMA IF NOT EXISTS lattice;"
```

### 3. 启动应用

```bash
mvn -s .codex/maven-settings.xml spring-boot:run
```

如果你本机 `8080` 已被占用，建议显式改端口：

```bash
export SERVER_PORT=18082
mvn -s .codex/maven-settings.xml spring-boot:run
```

后续所有示例 URL 都默认写成 `8080`；如果你改成了 `18082`，请把下面的 HTTP / MCP 地址一并替换成对应端口。

### 4. 健康检查

```bash
curl http://127.0.0.1:8080/actuator/health
```

### 5. 配置后台 LLM 绑定

浏览器入口：

- `http://127.0.0.1:8080/admin/index.html`

后台接口：

- `http://127.0.0.1:8080/api/v1/admin/llm/connections`
- `http://127.0.0.1:8080/api/v1/admin/llm/models`
- `http://127.0.0.1:8080/api/v1/admin/llm/bindings`

当前 query 不再另起一套配置，直接复用 compile 侧统一配置中心。

当前推荐至少配置这些绑定：

- `compile.writer`
- `compile.reviewer`
- `compile.fixer`
- `query.answer`
- `query.reviewer`
- `query.rewrite`

日常后台验证建议：

- 默认优先把 compile / query 的真实回归都绑到 `openai`
- `claude` 只在“验证 Claude 路由是否仍可用”或 Anthropic 兼容性专项补验时再启用

### 5.1 向量索引后台配置与维护

后台已提供向量配置与索引运维入口：

- `GET /api/v1/admin/vector/config`
- `PUT /api/v1/admin/vector/config`
- `GET /api/v1/admin/vector/status`
- `POST /api/v1/admin/vector/rebuild`

当前已经支持：

- 在后台保存向量开关、embedding 模型、期望维度
- 保存后立即作用到运行时 embeddings 请求
- 当启用向量或修改模型 / 维度时，返回“建议执行重建向量索引”的提示
- 在后台直接查看 schema 维度、已索引文章数、最近更新时间与当前状态

如果当前 embedding 网关不可用，保存配置不会报错，但重建或检索仍可能 fail-open；这时你仍可以在后台直接看到状态与原因

### 6. 触发编译

```bash
curl -X POST http://127.0.0.1:8080/api/v1/compile \
  -H "Content-Type: application/json" \
  -d '{"sourceDir":"/your/source/dir","incremental":false}'
```

### 7. 发起查询

```bash
curl -X POST http://127.0.0.1:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"question":"支付网关总超时多少，最大重试次数多少？"}'
```

### 8. 接入 MCP

HTTP MCP 地址：

- `http://127.0.0.1:8080/mcp`

如果你改了端口，例如 `18082`，地址也要同步改成：

- `http://127.0.0.1:18082/mcp`

---

## 重要说明

### 1. Query 不再是 compile 之外的一套模型配置

当前 query 已经复用：

- `agent_model_bindings`
- `execution_llm_snapshots`
- 同一套后台配置中心
- 同一套路由冻结与运行时选路能力
- compile / query 共用同一套 `scene + agentRole + binding` 结构

### 2. 现在的 Query 不应再被描述为“纯本地逻辑、无 LLM 调用”

当前项目里：

- compile review / fix 会真实调用模型
- query `answer / reviewer / rewrite` 也会在需要时调用模型
- reviewer 已可按绑定切到 OpenAI 或 Claude，而不再只是本地规则型 pass-through
- 当前日常后台验收默认优先走 OpenAI，Claude 仅用于专项兼容性验证

因此老版本 README 里“答案生成完全无 LLM 成本”的说法已经不适用。

### 3. `.claude/t1.md` 不能删除

它现在仍然是本地真实验收的重要模型信息来源。

但要注意：

- 不要把其中密钥提交到远端
- 不要在日志或文档中明文抄出密钥

---

## 当前已确认的问题

- 当前 embedding 网关若不支持 embeddings，向量索引与向量检索仍会降级；不过后台现在已经支持配置保存、状态查看与手动重建索引
- Admin 文章纠错接口在当前网关下返回 `500`
- 整库 repo diff / repo rollback 仍未完成最新一轮真实补验
- 增量编译可用，但粒度仍偏粗
- `compile_job_steps.job_id` 与对外 `compile_jobs.job_id` 目前不一致

这些问题的详细现象、影响和建议处理方式见：

- [项目全流程真实验收手册](/Users/sxie/xbk/Lattice-java/docs/%E9%A1%B9%E7%9B%AE%E5%85%A8%E6%B5%81%E7%A8%8B%E7%9C%9F%E5%AE%9E%E9%AA%8C%E6%94%B6%E6%89%8B%E5%86%8C.md)

---

## 一句话理解当前项目

如果你把它当成：

- 一个能把复杂知识源编译成知识文章
- 能通过 HTTP 与 MCP 被 AI 客户端消费
- 能处理 pending feedback、治理、快照和导出

的后端服务，那么这个理解是对的。
