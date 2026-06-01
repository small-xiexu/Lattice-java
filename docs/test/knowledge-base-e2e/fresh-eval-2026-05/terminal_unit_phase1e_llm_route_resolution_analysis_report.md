# Terminal Unit Phase 1E-2 LLM Route Resolution 只读归因报告

## 1. 结论

根因是 `LlmFactCardTerminalUnitFieldAliasEnricher` 使用无 scope 的 `llmGateway.routeResolution("compile", "field-alias-enricher")`，该路径只返回 bootstrap fallback，不读取 `agent_model_bindings`，因此 DB 中已存在且已冻结到快照的 `field-alias-enricher` binding 没有被 alias enricher 使用。

## 2. 当前链路复盘

实际路径如下：

```text
compile job
  -> initialize_job
     -> ExecutionLlmSnapshotService.freezeSnapshots(compile_job, jobId, compile)
        -> 读取 agent_model_bindings，已能冻结 field-alias-enricher binding
  -> persist_source_file_chunks
     -> SourceIngestSupport.persistSourceFileChunks(...)
        -> rebuildFactCards(sourceFileId)
           -> FactCardGenerationService.rebuildForSourceFile(sourceFileId)
              -> materializeTerminalUnits(savedFactCardRecord)
                 -> fieldAliasEnricher.enrich(records, factCardRecord)
                    -> isLlmRouteAvailable()
                       -> llmGateway.routeResolution("compile", "field-alias-enricher")
                          -> resolveBootstrapRoute(...)
                             -> ExecutionLlmSnapshotService.bootstrapRoute(...)
                                -> compile bootstrap fallback, bindingId=null, snapshotBacked=false
                       -> bindingId == null && !snapshotBacked => false
                    -> return original records
```

关键点：

- `InitializeJobNode.freezeSnapshotsFailOpen(...)` 已在图开始阶段冻结 compile scene 快照，源码位置：`src/main/java/com/xbk/lattice/compiler/graph/node/InitializeJobNode.java:112-138`。
- `PersistSourceFileChunksNode` 调用 `sourceIngestSupport.persistSourceFileChunks(...)` 时只传 raw sources 和 source file ids，没有把 `state.getJobId()` 传给 fact card rebuild 链路，源码位置：`src/main/java/com/xbk/lattice/compiler/graph/node/PersistSourceFileChunksNode.java:46-52`。
- `SourceIngestSupport.rebuildFactCards(...)` 只调用 `factCardGenerationService.rebuildForSourceFile(sourceFileId)`，没有 scope 参数，源码位置：`src/main/java/com/xbk/lattice/compiler/service/SourceIngestSupport.java:391-400`。
- `FactCardGenerationService.materializeTerminalUnits(...)` 只调用 `fieldAliasEnricher.enrich(records, factCardRecord)`，没有 compile job scope，源码位置：`src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationService.java:176-185`。

## 3. Bean / 注入判断

`LlmFactCardTerminalUnitFieldAliasEnricher` 具备 Spring 创建条件：

- 类是 `public`，带 `@Service`，位于 `com.xbk.lattice.compiler.service` 包下，属于 `com.xbk.lattice` 应用扫描范围。源码位置：`src/main/java/com/xbk/lattice/compiler/service/LlmFactCardTerminalUnitFieldAliasEnricher.java:32-34`、`src/main/java/com/xbk/lattice/LatticeApplication.java:8-11`。
- 构造器是 `public`，依赖 `LlmGateway`、`CompilerPromptProvider`、`FactCardTerminalUnitMaterializer`，这些依赖均是现有 Spring 管理组件或已在运行门禁中编译通过。源码位置：`src/main/java/com/xbk/lattice/compiler/service/LlmFactCardTerminalUnitFieldAliasEnricher.java:69-77`。
- 定向测试已有反射级断言，确认该实现是 public `@Service` 并实现接口。源码位置：`src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricherTests.java:209-228`。

`FactCardGenerationService` 能注入该 bean：

- 构造器参数为 `@Autowired(required = false) FactCardTerminalUnitFieldAliasEnricher fieldAliasEnricher`，源码位置：`src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationService.java:80-94`。
- 生产代码中唯一 Spring bean 实现是 `LlmFactCardTerminalUnitFieldAliasEnricher`；`NoOp` 是接口内部类，没有 `@Service`，不会自动注册。

仍无法直接确认当前本地运行实例已创建该 bean，因为本轮检查时 `127.0.0.1:18082` 未运行，无法读取 actuator/beans。结合源码和 runtime wiring gate，当前更可信的判断是：bean 创建和注入条件已满足，runtime smoke 的阻塞点不应继续归因到 bean 可见性。

## 4. Route Resolution 根因

无 scope route 的实际分支：

- `LlmGateway.routeResolution(scene, agentRole)` 直接调用 `resolveBootstrapRoute(scene, agentRole)`，源码位置：`src/main/java/com/xbk/lattice/compiler/service/LlmGateway.java:413-415`。
- `generateText(scene, agentRole, purpose, ...)` 同样调用 `resolveBootstrapRoute(scene, agentRole)`，源码位置：`src/main/java/com/xbk/lattice/compiler/service/LlmGateway.java:226-234`。
- `resolveBootstrapRoute(...)` 在存在 `ExecutionLlmSnapshotService` 时调用 `executionLlmSnapshotService.bootstrapRoute(...)`，源码位置：`src/main/java/com/xbk/lattice/compiler/service/LlmGatewayRouteSupport.java:181-206`。
- `ExecutionLlmSnapshotService.bootstrapRoute(...)` 不查询 `agent_model_bindings`；它只按 role 区分 reviewer 与非 reviewer。`field-alias-enricher` 不是 reviewer，因此走 compile bootstrap fallback，`bindingId=null`、`snapshotBacked=false`。源码位置：`src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java:304-360`。

读取 `agent_model_bindings` 的路径在 `freezeSnapshots(...)`：

- `freezeSnapshots(...)` 调用 `agentModelBindingJdbcRepository.findEnabledByScene(normalizedScene)`，源码位置：`src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java:115-182`。
- Mapper 查询条件是 `scene = #{scene} and enabled = true`，源码位置：`src/main/resources/com/xbk/lattice/llm/infra/mapper/AgentModelBindingMapper.xml:68-74`。

因此，`field-alias-enricher` binding 已存在并能被冻结成 snapshot，但无 scope 的 `routeResolution("compile", "field-alias-enricher")` 不会走这条查询。

`isLlmRouteAvailable()` 的判断：

- `routeResolution == null` 返回 false。
- `bindingId == null && !snapshotBacked` 返回 false。
- `modelName` 为空、`fallback`、`unknown` 返回 false。
- 源码位置：`src/main/java/com/xbk/lattice/compiler/service/LlmFactCardTerminalUnitFieldAliasEnricher.java:149-167`。

这确实会拒绝 LlmGateway 语义上的合法 bootstrap fallback；但对 alias enricher 来说，这是合理 fail-closed：避免没有模型中心 binding 的情况下用通用 compile bootstrap 静默生成检索 alias。当前不应通过删除该判断来修。

## 5. DB / API 绑定状态

只读 DB 查询结论：

| 项 | 状态 |
|---|---|
| `agent_model_bindings` | 存在 `id=11, scene=compile, agent_role=field-alias-enricher` |
| binding enabled | true |
| primary model profile | `id=1, model_code=gpt-5.5, model_name=gpt-5.5, model_kind=CHAT, enabled=true` |
| provider connection | `id=1, connection_code=local_openai, provider_type=openai_compatible, base_url=http://127.0.0.1:8888, enabled=true` |
| API key | 仅确认存在脱敏 mask，未读取、未输出 ciphertext 或完整 key |
| execution snapshots | 多个 compile job scope 已冻结 `field-alias-enricher` snapshot，`binding_id=11`、`model_name=gpt-5.5` |

这说明 DB 配置本身不是阻塞点。`id=11` 没有被 `routeResolution("compile", "field-alias-enricher")` 使用，是因为该方法没有 scope，只走 bootstrap fallback；不是因为 binding disabled、profile disabled 或 connection disabled。

API 状态：

- 运行报告已确认 API 白名单通过，重复创建返回唯一约束冲突而不是 `agentRole与scene不匹配`。
- 源码中 compile role 白名单包含 `field-alias-enricher`，位置：`src/main/java/com/xbk/lattice/api/admin/AdminLlmConfigController.java:506`。
- 本轮只读尝试访问本地 `18082` 时服务未运行，因此未重复调用 API。

## 6. 修复方案对比

| 方案 | 做法 | 影响面 | 风险 | 对 writer/reviewer/fixer/query/deep_research 的影响 |
|---|---|---|---|---|
| alias enricher 使用 scoped route | 将 compile jobId 传入 fact card terminal unit alias enrich，`field-alias-enricher` 使用 `routeResolutionFor(jobId, compile, role)` 与 `generateTextWithScope(...)` | 限定在 compile source chunk -> fact card -> terminal unit alias 链路 | 需要补 overload 和少量调用链传参；非 job 场景继续 fail-closed | 不改 LlmGateway 通用解析；不改 writer/reviewer/fixer；不触碰 query/deep_research |
| LlmGateway 增加 unscoped runtime binding resolution | 在 `routeResolution(scene, role)` 或 `resolveBootstrapRoute(...)` 中先查 DB binding | LlmGateway 全局 | 会改变所有无 scope `generateText/routeResolution` 行为，legacy compile、governance、测试可能一起变 | 可能影响 writer/reviewer/fixer 的无 scope 调用；query/deep_research 需额外防回归 |
| ExecutionLlmSnapshotService.bootstrapRoute 支持读取 runtime binding | 在 bootstrapRoute 内读取 `agent_model_bindings` 并构造非 snapshot route | LLM snapshot/route 核心服务 | 概念上混淆 bootstrap fallback 与 runtime binding；会让 bootstrapRoute 语义变宽 | 影响所有调用 bootstrapRoute 的场景，尤其 compile writer/reviewer/fixer |
| 允许 field-alias-enricher 使用 bootstrap fallback | 删除或放宽 `bindingId == null && !snapshotBacked` 判断 | alias enricher 本类 | 可以触发真实 LLM，但绕过模型中心 binding，无法证明使用 id=11；配置治理弱化 | 不影响其他角色，但会把 alias 生成变成 properties/bootstrap 驱动，不推荐 |

## 7. 推荐最小修复

推荐方案：**alias enricher 使用 scoped route，并把 compile jobId 只沿 terminal unit alias enrich 链路向下传递**。

理由：

- 现有 compile graph 已在 `initialize_job` 阶段冻结了包含 `field-alias-enricher` 的 snapshot；DB 也证明 snapshot 中已有 `binding_id=11`。
- 只要把 `state.getJobId()` 传到 fact card rebuild / alias enricher，`routeResolutionFor(jobId, "compile", "field-alias-enricher")` 就能命中已有 snapshot。
- 该方案不改变 `LlmGateway.routeResolution(...)` 和 `ExecutionLlmSnapshotService.bootstrapRoute(...)` 的全局语义，避免影响 writer/reviewer/fixer、query、deep_research。
- 保留 `bindingId == null && !snapshotBacked` fail-closed 判断，snapshot 缺失、解密失败或配置不可用时仍不会静默用 bootstrap 生成 alias。

建议修复点只放在 compile fact card terminal unit alias 链路：

- `PersistSourceFileChunksNode`：把 `state.getJobId()` 传给 source chunk 持久化 / fact card rebuild。
- `SourceIngestSupport`：新增带 scope/jobId 的 overload，旧方法保留。
- `FactCardGenerationService`：新增带 scope/jobId 的 `rebuildForSourceFile(...)` / `materializeTerminalUnits(...)` overload，旧方法保留。
- `FactCardTerminalUnitFieldAliasEnricher` / `LlmFactCardTerminalUnitFieldAliasEnricher`：新增 scoped enrich 路径；有 scope 时调用 `routeResolutionFor` + `generateTextWithScope`，无 scope 时保持当前 fail-closed 行为。

## 8. 给 agentA 的强约束提示词

```text
你现在接手本项目的 agentA：代码执行 Agent。

开始前必须先读取：
1. AGENTS.md
2. docs/quality-progress-and-lessons.md
3. docs/multi-agent-model-routing-guide.md
4. docs/项目启动配置清单.md
5. docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1_implementation_plan.md
6. docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1e_llm_route_resolution_analysis_report.md

本轮目标：
只修一个根因：Terminal Unit Phase 1E-2 alias enricher 未使用 compile job scoped snapshot，导致 `compile + field-alias-enricher` DB binding 已存在但 `routeResolution("compile","field-alias-enricher")` 只走 bootstrap fallback。实现最小 scoped route 修复，让 field alias enricher 在 compile job 中使用已冻结的 `binding_id=11` snapshot。

允许修改代码：
- 允许修改 `src/main/java/com/xbk/lattice/compiler/graph/node/PersistSourceFileChunksNode.java`
- 允许修改 `src/main/java/com/xbk/lattice/compiler/service/SourceIngestSupport.java`
- 允许修改 `src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationService.java`
- 允许修改 `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java`
- 允许修改 `src/main/java/com/xbk/lattice/compiler/service/LlmFactCardTerminalUnitFieldAliasEnricher.java`
- 如必须补定向测试，允许只修改与本修复直接相关的 `src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricherTests.java`、`src/test/java/com/xbk/lattice/compiler/service/FactCardGenerationServiceTests.java` 或已有 compile graph source chunk 节点测试。

禁止修改范围：
- 禁止修改 `src/main/java/com/xbk/lattice/query/**`
- 禁止修改 AnswerGeneration、fallback、citation、reranker、RRF、vector、Deep Research 主链
- 禁止修改 `src/main/resources/db/schema.sql`
- 禁止修改 `src/main/resources/prompts/**`
- 禁止修改 `src/main/resources/static/**`
- 禁止修改 `scripts/**`
- 禁止修改 `docs/模型绑定配置参考.md`
- 禁止修改 fresh eval 题集、标准答案、case id、验收口径
- 禁止修改 redline 规则、allowlist、AGENTS.md、CLAUDE.md
- 禁止 stage、commit、push
- 禁止清库、重建 schema、导入资料
- 禁止通过 DB INSERT/UPDATE/DELETE 修配置

实现要求：
1. 不修改 `LlmGateway.routeResolution(...)`、`resolveBootstrapRoute(...)`、`ExecutionLlmSnapshotService.bootstrapRoute(...)` 的全局语义。
2. 不删除 `isLlmRouteAvailable()` 中 `bindingId == null && !snapshotBacked` 的 fail-closed 判断。
3. 只在 field alias enricher 有 compile job scope 时使用 `routeResolutionFor(scopeId, "compile", "field-alias-enricher")` 和 `generateTextWithScope(...)`。
4. 旧无 scope 调用必须保留兼容，并继续 fail-closed；不得让 alias enricher 静默使用 bootstrap fallback。
5. 不改变 writer/reviewer/fixer 的路由行为。
6. 不改变 query/deep_research 路由行为。
7. 不新增任何业务词、文件名、题面、答案片段、case id、中文字段语义硬编码。

允许跑测试：
- 必须先运行 `bash scripts/scan-redline.sh special_cases_report.md`
- 必须运行定向测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitFieldAliasEnricherTests,FactCardGenerationServiceTests test`
- 如修改 compile graph node 相关测试，补跑对应定向测试类
- 最后运行全量：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`

禁止跑：
- 禁止跑 19 题业务 eval / baseline
- 禁止清库、重建、导入资料
- 禁止调用写配置 API 或写 DB

输出报告：
`docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1e_scoped_alias_route_fix_result_report.md`

报告必须写明：
- 改动文件与唯一根因
- scoped route 如何从 compile jobId 传到 alias enricher
- 如何证明没有改变 writer/reviewer/fixer/query/deep_research
- redline、定向测试、全量 mvn test 结果
- runtime smoke 不由 agentA 执行，修复后交回 agentD 复跑
```

## 9. 计划台账回写

已补写 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1_implementation_plan.md` 两条 checkpoint：

1. `runtime wiring gate 复跑 PASS`：记录 `ChatClientRegistryTests` 404 未复现，全量 `mvn test=985/0/0/0` 通过。
2. `runtime smoke rerun BLOCKED`：阻塞点记录为 route resolution 未命中有效 binding，`compile + field-alias-enricher` DB binding 存在但无 scope 路由只走 bootstrap fallback。

本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`，未 stage、commit、push，未清库、未重建 schema、未导入资料，未跑 19 题业务 eval / baseline，未输出完整密钥。
