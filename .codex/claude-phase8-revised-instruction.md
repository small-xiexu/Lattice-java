# Claude 执行指令：Phase 8 前置收口 + 模型池配置中心

以下指令**优先级高于** [task1-phase8-prerequisites.md](/Users/sxie/xbk/Lattice-java/.codex/task1-phase8-prerequisites.md) 和 [task2-phase8-implementation.md](/Users/sxie/xbk/Lattice-java/.codex/task2-phase8-implementation.md) 中任何冲突描述。

## 目标

按以下顺序完成：

1. 先完成 Phase 8 前置收口（结构重构）
2. 前置收口所有测试通过后，再进入 Phase 8 模型池与 Agent 绑定配置中心实现

禁止跳过 Task 1 直接进入 Task 2。

---

## 一、数据库边界

本项目数据库按**全新重构**处理，不需要考虑任何历史数据库迁移兼容、在线升级兼容、数据回填兼容。

执行规则：

- 可以直接重写现有 Flyway 基线与后续版本文件，使**空库初始化**后即得到最终结构
- 不需要为了兼容旧库保留“增量补丁式”迁移策略
- 不需要编写历史数据 backfill、兼容列、兼容索引、兼容视图
- 验收口径是：空数据库执行 Flyway 后结构正确、应用启动与测试通过

如果你认为最清晰的做法是直接调整：

- `src/main/resources/db/migration/V1__baseline_schema.sql`

这是允许的。

本项目按 0 到 1 单基线处理，不保留独立 `V2/V3` 迁移脚本，也**不需要**为“旧库已执行 V1/V2/V3”这种场景做兼容设计。

---

## 二、Task 1 必做项

你必须先完成以下收口项：

1. 收缩 `StateGraphCompileOrchestrator`
规则：
- `execute(...)` 只负责注入最小初始字段
- 不允许与 `initialize_job` 双重初始化配置快照

2. 拆分 `CompileGraphDefinitionFactory`
规则：
- `CompileGraphDefinitionFactory` 只保留图定义、节点注册、条件边声明
- 节点执行逻辑必须下沉到 `compiler/graph/node/` 独立类
- 不能继续维持 God Class 结构

3. 继续瘦身 `CompilePipelineService`
规则：
- 不能继续作为 Graph 背后的超级枢纽
- 节点应优先依赖更窄职责的 support/service，而不是全部继续依赖 `CompilePipelineService`
- 可以保留 `CompilePipelineService` 作为过渡委托层，但不能让它继续承载主要实现复杂度

4. 清理 `CompileArticleNode.compile(...)` 遗留审查链路
规则：
- 至少显式 `@Deprecated`
- 更优方案是直接删除并修正调用方

5. 清理 `CompileOrchestrationModes` 历史死代码
规则：
- 最终口径以 `state_graph` 为唯一正式模式
- 文档与代码必须一致

Task 1 的硬性验收：

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

Task 1 全绿后，才能进入 Task 2。

---

## 三、Task 2 的关键覆盖修正

Task 2 不是只做表和页面。你必须确保“快照冻结”真正进入运行时调用链，而不是只把快照写进数据库。

### 1. 快照驱动必须打通到实际 Agent 调用

仅实现以下内容**不算完成**：

- `execution_llm_snapshots` 表
- `freezeSnapshots(...)`
- `AgentModelRouter.routeFor(scopeId, scene, agentRole)`

你还必须把 `scopeId / scene / llmBindingSnapshotRef` 或等价上下文透传到实际调用链，至少覆盖：

- 编译侧 `WriterAgent / ReviewerAgent / FixerAgent`
- `WriterTask / ReviewTask / FixTask`
- `ArticleReviewerGateway`
- `ReviewFixService`
- 任何仍直接调用 `llmGateway.compile(...) / review(...)` 的编译主链路关键点

目标是：

- 真正的模型选择由快照驱动
- 不是“冻结了快照，但实际调用仍读 properties”

### 2. `AgentModelRouter` 必须按“重写”处理

当前 `AgentModelRouter` 只是对 `LlmGateway.compileRoute()/reviewRoute()/fixRoute()` 的薄转发。

Phase 8 里必须把它升级为真正的路由组件：

- 输入：`scopeId + scene + agentRole`
- 输出：命中的 `routeLabel / model binding`
- fallback：仅在本地无数据库配置或快照不存在时才回退到 properties

### 3. `LlmGateway` 缓存键必须纳入快照维度

至少纳入以下之一：

- `bindingId`
- `snapshotVersion`
- `routeLabel`

不能继续只用 `modelName + prompt` 作为缓存键，否则会出现快照版本污染。

### 4. 成本估算不能继续硬编码 provider 判断

`estimateCostUsd()` 不允许继续靠 `modelName.contains("anthropic")` 这类逻辑长期存在。

本轮要求：

- 至少把定价逻辑抽成可配置映射或独立策略
- 不要求一步做到完美计费平台
- 但不能继续把 provider 定价硬写死在网关方法里

---

## 四、密钥安全规则

这一条非常重要：

**V1 也不允许把 API Key 明文存数据库。**

以下做法禁止：

- `api_key_ciphertext` 字段实际存明文
- 用“Phase 9 再加密”作为理由延期
- Admin API 返回任何密文字段或明文字段

最低要求：

- 落库前加密
- 页面只显示 mask
- 更新时允许“留空不覆盖”
- 日志、异常、步骤摘要禁止输出明文 key

如果需要新增应用级密钥配置来做加解密，这是允许且推荐的。

---

## 五、Admin API 与前端路径风格

Admin LLM 配置接口必须沿用当前项目风格：

- 前缀统一使用 `/api/v1/admin/...`

不要新开一套 `/admin/api/...` 风格路径。

推荐：

- `/api/v1/admin/llm/connections`
- `/api/v1/admin/llm/models`
- `/api/v1/admin/llm/bindings`

---

## 六、`fallback_model_profile_id` 的 V1 语义

V1 必须明确为：

- `fallback_model_profile_id` 只做数据结构预留
- 不参与运行时自动 fallback
- “Claude 不可用时切别的模型”在 V1 指**管理员手动改绑定**
- 修改后只影响新任务

不要偷偷实现半套自动 fallback。

如果要做自动 fallback，必须额外补：

- 故障检测规则
- 决策链记录
- 日志口径
- 计费口径

本轮默认不做。

---

## 七、`execution_llm_snapshots` 唯一约束

V1 必须明确：

- 同一 `scope_type + scope_id + scene + agent_role` 只允许一条生效快照

也就是说，数据库层面应有唯一约束。

`snapshot_version` 在 V1 可以固定为 `1`，仅为后续增强预留。

---

## 八、Query 侧范围

本轮 Phase 8 **只覆盖 compile 侧**。

强制规则：

- Query 侧模型池、Agent 绑定、快照冻结、调用链透传明确延后
- 不要写成“如果顺手做了 Query 节点就一起做”
- 不要在本轮实现中半做 Query 侧基础设施
- 文档里必须明确标注：`query` 侧 Phase 8 能力 deferred
- 测试与验收范围也只按 compile 侧口径统计

只有在 compile 侧 Phase 8 完整稳定后，才进入 Query 侧的独立后续任务。

---

## 九、完成后必须同步更新文档

完成 Task 1 后：

- 回写 [Spring AI Alibaba Graph 完整接入设计方案.md](/Users/sxie/xbk/Lattice-java/.codex/Spring%20AI%20Alibaba%20Graph%20完整接入设计方案.md)
- 明确 Phase 8 前置收口已完成

完成 Task 2 后：

- 回写同一设计文档中的 Phase 8 验收项
- 保证文档口径与代码现状一致
- 删除或修正文档中任何仍暗示“旧库兼容/明文密钥/自动 fallback 已实现”的描述

---

## 十、统一验收标准

必须满足：

1. Task 1 测试全通过
2. Task 2 测试全通过
3. Admin 配置中心接口不返回明文或密文字段
4. 快照真正驱动实际 Agent 调用链
5. 新任务受绑定变更影响，旧任务不漂移
6. 文档、代码、测试三者口径一致

统一验收命令：

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```
