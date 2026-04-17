# Spring AI Alibaba Graph 技术验证记录

## 1. 验证目的

本记录用于回答设计审查中的三个阻塞问题：

1. 当前依赖版本是否真的支持条件边
2. 当前依赖版本是否真的支持生命周期监听
3. `OverAllState` 是否至少可以承载中等体量对象集合并跨节点读取

本记录只证明“能力存在且基础行为可用”，不代表可以把大对象长期常驻在图状态中。

---

## 2. 本地依赖版本

项目 `pom.xml` 声明：

- `spring-ai-alibaba.version = 1.1.2.0`

本机本地 Maven 仓库实际验证 jar：

- `/Users/sxie/maven/repository/com/alibaba/cloud/ai/spring-ai-alibaba-graph-core/1.1.2.0/spring-ai-alibaba-graph-core-1.1.2.0.jar`

---

## 3. API 签名验证

通过 `javap` 验证 `StateGraph`，确认存在以下方法：

- `addConditionalEdges(String, AsyncCommandAction, Map<String, String>)`
- `addConditionalEdges(String, AsyncEdgeAction, Map<String, String>)`
- `addConditionalEdges(String, AsyncEdgeActionWithConfig, Map<String, String>)`
- `addParallelConditionalEdges(String, AsyncMultiCommandAction, Map<String, String>)`

通过源码验证 `CompileConfig.Builder`，确认存在：

- `withLifecycleListener(GraphLifecycleListener listener)`

通过 `javap` 验证 `OverAllState`，确认其本质仍是：

- `Map<String, Object>` 风格状态容器

结论：

- 条件边 API 在当前版本中存在
- 生命周期监听挂载点在当前版本中存在
- 状态对象能存放任意 `Object`

---

## 4. 最小 Demo 验证

本地临时验证文件：

- `/tmp/GraphConditionalDemo.java`

验证覆盖点：

1. 条件边根据 `hasEnhancements / hasCreates` 分支到不同节点
2. `GraphLifecycleListener.before/after` 被稳定触发
3. `OverAllState` 承载 `List<DemoConcept>`，并在后续节点读取到完整集合

执行命令：

```bash
mvn -q -s .codex/maven-settings.xml -Dmdep.outputFile=/tmp/lattice.cp -DincludeScope=runtime dependency:build-classpath
CP=$(cat /tmp/lattice.cp)
javac -cp "$CP" /tmp/GraphConditionalDemo.java
java -cp "/tmp:$CP" GraphConditionalDemo
```

关键输出：

```text
=== no-change branch ===
result=nothing_to_do
observedConceptCount=512
observedPayloadSize=512
=== create branch ===
result=compile_new_articles
observedConceptCount=512
observedPayloadSize=512
=== lifecycle events ===
before:prepare
after:prepare
before:nothing_to_do
after:nothing_to_do
before:prepare
after:prepare
before:compile_new_articles
after:compile_new_articles
```

结论：

- 条件边可以正常路由
- 两个分支都已被实际执行
- 生命周期监听已被实际触发
- `OverAllState` 至少可以承载 512 个对象组成的集合并跨节点读取

---

## 5. 对设计的直接约束

虽然最小 Demo 已通过，但本记录同时得出两个反向结论：

1. `OverAllState` 能放大对象，不等于应该长期放大对象
2. 当前验证只覆盖“基础 API 可用”，不覆盖“大规模编译稳定性”

因此正式设计仍采用：

- `CompileGraphState / QueryGraphState` 只保留轻量字段
- 大对象进入 `CompileWorkingSetStore / QueryWorkingSetStore`
- Phase 0 必须把最小验证固化到仓内测试，而不是依赖 `/tmp` 临时文件

相关设计决策在主方案中的落点：

- 轻状态字段与循环计数器：设计方案第 `6.1`、`6.1.1` 节
- `CompileWorkingSetStore / QueryWorkingSetStore` 接口：设计方案第 `12.3.1`、`12.3.2` 节
- 事务边界与补偿策略：设计方案第 `12.4` 节
- `Facade / Registry` 职责边界：设计方案第 `5.2`、`11.1` 节
- `ArticleReviewEnvelope -> ArticleRecord` flatten 关系：设计方案第 `9.2` 节
- `LocalReviewerGateway` 替换策略：设计方案第 `9.3`、`15.6` 节

---

## 6. 尚未被此记录覆盖的事项

以下事项仍需在正式实现中验证：

1. 条件边在多层循环中的稳定性
2. 生命周期监听与 `compile_job_steps` 落库结合后的性能
3. 大型源码目录下工作集存储的容量与清理策略
4. 节点失败后从指定步骤恢复的正确性
5. 规则审查器与真实 LLM 审查器的分支一致性

本记录只能作为进入 Phase 1 的准入依据之一，不能替代正式测试。
