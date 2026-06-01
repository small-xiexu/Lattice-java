# Terminal Unit Phase 1E-2: Scoped Alias Route Fix Result Report

修复时间：2026-05-30
执行人：agentA
修复类型：最小 scoped route 修复 — 单一变量

---

## 1. 唯一根因与修复摘要

**根因**：`LlmFactCardTerminalUnitFieldAliasEnricher.isLlmRouteAvailable()` 使用无 scope 的 `llmGateway.routeResolution("compile", "field-alias-enricher")`，该路径只走 `ExecutionLlmSnapshotService.bootstrapRoute()`，不读取 `agent_model_bindings` 表。因此即使 DB 中存在 `id=11, scene=compile, agent_role=field-alias-enricher` 的绑定，且 `freezeSnapshots` 已将其冻结到 snapshot，enricher 也无法命中。

**修复**：将 compile job 的 `state.getJobId()` 沿链传递到 alias enricher，使其在有 scope 时使用 `routeResolutionFor(scopeId, ...)` 命中已冻结的 snapshot，再通过 `generateTextWithScope(...)` 调用 LLM。

**改动文件**（5 个文件，约 60 行净新增）：

| 文件 | 变更 |
|---|---|
| `PersistSourceFileChunksNode.java` | `execute()` 传 `state.getJobId()` 给 `sourceIngestSupport.persistSourceFileChunks(...)` |
| `SourceIngestSupport.java` | 新增 `persistSourceFileChunks(rawSources, sourceFileIdsByPath, compileJobId)` overload；`rebuildFactCards` 新增 scope 参数并传递给 `FactCardGenerationService` |
| `FactCardGenerationService.java` | 新增 `rebuildForSourceFile(sourceFileId, scopeId)` overload；`materializeTerminalUnits` 新增 scope 参数，有 scope 时调用 `enricher.enrich(records, factCard, scopeId)` |
| `FactCardTerminalUnitFieldAliasEnricher.java` | 新增 `enrich(records, factCard, scopeId)` default 方法，委托到无 scope 版本 |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | 新增 `enrich(records, factCard, scopeId)` 实现；`isLlmRouteAvailable(scopeId)` 有 scope 时使用 `routeResolutionFor`；`requestAliases(scopeId)` 有 scope 时使用 `generateTextWithScope` |

## 2. Compile JobId / ScopeId 传递链路

```
CompileGraph（StateGraph 执行入口）
  → InitializeJobNode: freezeSnapshots(compile_job, jobId, "compile")
      → snapshot 包含 field-alias-enricher binding_id=11
  → PersistSourceFileChunksNode: execute()
      → state.getJobId()                              ← [修改点 1]
      → sourceIngestSupport.persistSourceFileChunks(rawSources, sourceFileIdsByPath, compileJobId)
          → rebuildFactCards(sourceFileId, compileJobId)     ← [修改点 2]
              → factCardGenerationService.rebuildForSourceFile(sourceFileId, compileJobId)
                  → materializeTerminalUnits(savedFactCard, compileJobId)  ← [修改点 3]
                      → fieldAliasEnricher.enrich(records, factCard, compileJobId)
                          → isLlmRouteAvailable(compileJobId)     ← [修改点 4]
                              → llmGateway.routeResolutionFor(compileJobId, "compile", "field-alias-enricher")
                                  → resolveScopedRoute → 命中 snapshot (binding_id=11)
                          → requestAliases(groupRecords, candidates, compileJobId)
                              → llmGateway.generateTextWithScope(compileJobId, "compile", "field-alias-enricher", ...)
```

## 3. Scoped Route 如何命中已冻结 Snapshot

1. `InitializeJobNode.freezeSnapshotsFailOpen(...)` 在 compile 图开始阶段调用 `executionLlmSnapshotService.freezeSnapshots(compile_job, jobId, "compile")`，读取 `agent_model_bindings` 表中所有 `scene=compile, enabled=true` 的绑定（包括 `field-alias-enricher`），保存为 `ExecutionLlmSnapshot`。

2. `LlmGateway.routeResolutionFor(jobId, "compile", "field-alias-enricher")` 调用 `resolveScopedRoute(jobId, "compile", "field-alias-enricher")`，从 `ExecutionLlmSnapshotService` 中按 `jobId` 查找 snapshot，匹配 agentRole → 返回带有 `bindingId=11`、`snapshotBacked=true` 的 `LlmRouteResolution`。

3. `isLlmRouteAvailable` 收到 `bindingId != null` 且 `snapshotBacked = true` → 返回 true → 允许 LLM 调用。

## 4. 无 Scope 路径如何继续 Fail-Closed

旧入口（无 scope）保持不变：

- `FactCardGenerationService.rebuildForSourceFile(sourceFileId)` — 仍调用 `materializeTerminalUnits(factCardRecord)`，scopeId 为 null
- `materializeTerminalUnits(factCardRecord)` — 调用 `enricher.enrich(records, factCardRecord)`（无 scope 版本）
- `LlmFactCardTerminalUnitFieldAliasEnricher.enrich(records, factCardRecord)` — 调用 `doEnrich(records, null)`
- `isLlmRouteAvailable(null)` — 使用 `routeResolution("compile", "field-alias-enricher")` → bootstrap fallback → `bindingId=null, snapshotBacked=false` → 返回 false → 跳过 enrichment

`bindingId == null && !snapshotBacked` 的 fail-closed 判断保持不变。

## 5. 为什么没有改变 Writer/Reviewer/Fixer/Query/Deep Research

| 链路 | 受影响的代码路径 | 结论 |
|---|---|---|
| Writer/Reviewer/Fixer | `CompileArticleNode` 使用 `generateText` / `generateTextWithScope`，路由未改 | **无影响** |
| Query | `KnowledgeSearchService` 等使用 query scene 路由 | **无影响** |
| Deep Research | 使用 deep_research scene 路由 | **无影响** |
| `LlmGateway.routeResolution()` | 全局语义不变 | **无影响** |
| `ExecutionLlmSnapshotService.bootstrapRoute()` | 全局语义不变 | **无影响** |
| 本次修改 | 仅在 alias enricher 链路上将 scope 从 `PersistSourceFileChunksNode` 向下传递 | **只影响 alias enricher** |

## 6. 测试结果

### 6.1 git diff --check

无输出（通过）。

### 6.2 Redline 扫描

```
BLOCKER=0, REVIEW=2065, ALLOWLIST=260
```

无新增 BLOCKER。

### 6.3 定向测试

```
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

| 测试类 | 数量 | 说明 |
|---|---|---|
| `FactCardTerminalUnitFieldAliasEnricherTests` | 15 | 含 2 个新增 scoped route 测试 |
| `FactCardGenerationServiceTests` | 21 | 无变化，全部保护通过 |

**新增测试：**

| 测试 | 验证点 |
|---|---|
| `shouldUseScopedRouteResolutionAndGenerateTextWhenScopeProvided` | 有 scope 时使用 `routeResolutionFor` + `generateTextWithScope`，不调用无 scope 方法 |
| `shouldUseNonScopedRouteWhenNoScopeProvided` | 无 scope 时使用 `routeResolution` + `generateText`，不调用 scoped 方法 |

### 6.4 全量 mvn test

```
Tests run: 987, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

全量 987/0/0/0 干净通过。较上一轮基线（985）增加 2 个 scoped route 测试。

## 7. 未修改/未执行清单

| 项 | 状态 |
|---|---|
| `src/main/java/com/xbk/lattice/query/**` | **未修改** |
| AnswerGeneration / fallback / citation / Reranker / RRF / vector | **未修改** |
| `LlmGateway.routeResolution()` / `resolveBootstrapRoute()` / `ExecutionLlmSnapshotService.bootstrapRoute()` | **未修改** |
| `src/main/resources/db/schema.sql` | **未修改** |
| `src/main/resources/prompts/**` | **未修改** |
| `scripts/**` | **未修改** |
| `docs/模型绑定配置参考.md` | **未修改** |
| `special_cases_report.md` | **未修改** |
| Fresh eval 题集/标准答案/验收口径 | **未修改** |
| `bindingId == null && !snapshotBacked` fail-closed 判断 | **未删除/放宽** |
| Writer/reviewer/fixer 路由行为 | **未改变** |
| 清库/重建 schema/导入资料 | **未执行** |
| 19 题业务 eval / baseline | **未执行** |
| Stage / commit / push | **未执行** |

## 8. 下一步

交回 agentD 复跑 runtime smoke：
1. 重新 compile 触发 compile job
2. 验证 `fieldAliasEnricher` 的 bean 被注入
3. 验证 `routeResolutionFor(jobId, "compile", "field-alias-enricher")` 命中 snapshot
4. 验证 terminal unit 的 `fieldAliasesJson` 包含中文 alias
5. agentA 本轮不自行验证业务 eval

## 合规声明

- 本轮未修改 query/answer/fallback/citation/Reranker/RRF/vector 主链
- 本轮未修改全局路由语义
- 本轮未删除 fail-closed 判断
- 本轮不包含业务词、文件名、题面、case id、中文字段语义硬编码
- 本轮未清库、未重建、未导入资料、未跑业务 eval
- 本轮未 stage、未 commit、未 push
- 修改文件：5（PersistSourceFileChunksNode + SourceIngestSupport + FactCardGenerationService + Enricher interface + LlmEnricher）
- 测试文件：1（EnricherTests）
- 新增报告：1（本报告）
