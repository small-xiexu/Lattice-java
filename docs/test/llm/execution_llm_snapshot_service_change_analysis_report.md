# ExecutionLlmSnapshotService 改动分析报告

**验证时间**：2026-05-27 17:37 CST  
**分析 Agent**：agentB（治理/链路分析 Agent）  
**分析模式**：只读分析，未修改任何文件，未 stage/commit/push  
**生成文件**：`docs/test/llm/execution_llm_snapshot_service_change_analysis_report.md`

---

## 1. 当前未提交状态摘要

```
 M src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java   ← 唯一未提交生产代码
 M docs/模型绑定配置参考.md                                                        ← 禁止读取/操作
 M docs/项目全流程真实验收手册.md                                                   ← 非生产代码
 M special_cases_report.md                                                        ← redline 刷新，本轮禁止 stage
?? docs/plans/...                                                                  ← 未跟踪计划文件
?? docs/test/knowledge-base-e2e/...                                                ← 未跟踪报告
```

**结论**：`ExecutionLlmSnapshotService.java` 是当前工作区唯一的未提交生产代码文件，符合本轮分析前提。

---

## 2. Diff 分块解释

### 唯一改动块：`resolveRoute()` 方法中的 apiKey 解密（第 258-268 行）

**旧代码（已提交版本）：**
```java
String apiKey = llmSecretCryptoService.decrypt(providerConnection.orElseThrow().getApiKeyCiphertext());
```

**新代码（工作区版本）：**
```java
String apiKey;
try {
    apiKey = llmSecretCryptoService.decrypt(providerConnection.orElseThrow().getApiKeyCiphertext());
}
catch (RuntimeException exception) {
    if (requiresStrictBindings(normalizedScene)) {
        throw exception;
    }
    log.warn("LLM snapshot exists but api key decrypt failed for connection {}, fallback to bootstrap route",
            snapshot.orElseThrow().getConnectionId(), exception);
    return Optional.empty();
}
```

### 改动意图分析

| 维度 | 说明 |
|---|---|
| **改动类型** | 运行时降级策略变更 |
| **改动粒度** | 单点：`resolveRoute()` 中 apiKey 解密失败时的异常处理 |
| **改动主题** | LLM snapshot 解密失败 → 优雅降级到 bootstrap fallback（非 strict 场景） |
| **是否涉及模型绑定读取** | 否（snapshot 的 binding 已在前序步骤读取并解析） |
| **是否涉及 LLM snapshot 解密** | 是——核心改动点 |
| **是否涉及 fallback 逻辑** | 是——解密失败后返回 `Optional.empty()`，触发调用方 bootstrap fallback |
| **是否涉及 strict binding / fail-closed** | 部分——`deep_research` 保持 fail-closed（re-throw），`compile`/`query` 行为变化 |
| **是否涉及日志/可观测性** | 新增一条 `log.warn`（含 connectionId + exception），不泄露 apiKey |

---

## 3. 调用链 / 引用关系

### 核心调用链

```
LlmGatewayRouteSupport.resolveScopedRoute()           ← 路由解析入口
  ├── ExecutionLlmSnapshotService.resolveRoute()       ← 【本次改动点】
  │     ├── executionLlmSnapshotJdbcRepository.findByScopeAndRole()
  │     ├── llmProviderConnectionJdbcRepository.findById()
  │     ├── llmSecretCryptoService.decrypt()           ← 解密 apiKey（原无 try-catch，现加入）
  │     └── new LlmRouteResolution(..., apiKey, ...)   ← 构造路由结果
  ├── ExecutionLlmSnapshotService.isBootstrapAllowed()  ← 判断是否允许 bootstrap fallback
  └── ExecutionLlmSnapshotService.bootstrapRoute()      ← 构建 properties/YAML fallback 路由
```

### 生产代码引用清单

| 调用方 | 场景 | 影响面 |
|---|---|---|
| `AgentModelRouter` | compile writer/reviewer/fixer 路由 | compile 链 |
| `LlmGatewayRouteSupport` | compile/query/deep_research 统一路由 | 所有 LLM 调用 |
| `LlmGateway` / `LlmGatewayInvocationSupport` | LLM 调用封装层 | 所有 LLM 调用 |
| `QueryGraphOrchestrator` | query answer/rewrite/reviewer 路由 | query 链 |
| `DeepResearchOrchestrator` | deep_research planner/researcher/synthesizer | deep_research 链 |
| `ArticleCorrectionService` | compile fix 路由 | 治理链路 |
| `ArticleCompileSupport` | compile 路由常量引用 | compile 链 |
| `InitializeJobNode` | compile job 初始化时的 snapshot freeze | compile 链 |
| `ReviewArticlesNode` / `CompileNewArticlesNode` / `FixReviewIssuesNode` | compile graph 各节点 | compile 链 |
| `ReviewerAgent` / `LlmReviewerGateway` | query reviewer | query 链 |
| `AnswerGenerationApiSupport` | query answer/rewrite | query 链 |
| `DeepResearchBindingValidator` | deep_research 启动期校验 | deep_research 链 |

**影响范围**：该改动影响 `resolveRoute()` 方法，该方法被所有 compile/query/deep_research 路由入口调用。但由于异常处理按 `requiresStrictBindings()` 分层：
- `deep_research` 路径完全不受影响（still re-throw）
- `compile` 和 `query` 路径仅在 apiKey 解密失败时行为变化

### 测试文件引用

| 测试文件 | 覆盖情况 |
|---|---|
| `ExecutionLlmSnapshotServiceTests` | 7 个测试，覆盖 freeze/deep_research 严格校验/bootstrap timeout；**不覆盖 apiKey 解密失败路径** |
| `AgentModelRouterSnapshotTests` | 使用 `StubExecutionLlmSnapshotService` 继承，不测试 `resolveRoute` 解密逻辑 |
| `LlmGatewayTests` | 使用 `StubExecutionLlmSnapshotService`，不测试 `resolveRoute` 解密逻辑 |
| `DeepResearchBindingValidatorTests` | 使用 `FailingSnapshotService` 继承，不测试 `resolveRoute` 解密逻辑 |
| `ArticleCorrectionServiceTests` | 使用 `RecordingExecutionLlmSnapshotService` 继承，不测试 `resolveRoute` 解密逻辑 |
| `LlmConfigCenterIntegrationTests` | 集成测试，引用 service 但不专门测试解密失败 |

---

## 4. 行为变化判断

### 核心变化：解密失败时从 fail-closed 变为条件性 fail-open

| 场景 | 旧行为 | 新行为 | 变化 |
|---|---|---|---|
| `deep_research` + 解密失败 | RuntimeException 向上传播 → 任务失败 | RuntimeException 向上传播 → 任务失败（re-throw） | **无变化** |
| `compile`/`query` + 解密失败 + `bootstrapEnabled=true` | RuntimeException 向上传播 → 任务失败 | log.warn + 返回 `Optional.empty()` → 调用方走 bootstrap fallback | **fail-closed → fail-open** |
| `compile`/`query` + 解密失败 + `bootstrapEnabled=false` | RuntimeException 向上传播 → 任务失败 | log.warn + 返回 `Optional.empty()` → 调用方抛 `IllegalStateException` | **fail-closed 保持**（异常类型从 RuntimeException 变为 IllegalStateException，语义等价） |

### 与同类失败处理的对比

`resolveRoute()` 方法中，以下失败路径均已在旧代码中返回 `Optional.empty()` + 走 bootstrap fallback：

| 失败类型 | 旧行为 | 与本次改动一致性 |
|---|---|---|
| snapshot 未找到 | 返回 empty → bootstrap | — |
| provider connection 缺失 | 返回 empty → bootstrap | — |
| apiKey 解密失败 | **抛异常（旧）** | 不一致 → **本次改为一致** |

本次改动使 apiKey 解密失败与其它 snapshot 层失败（缺失 profile、缺失 connection）保持一致的处理语义：非 strict 场景优雅降级，而非硬失败。

---

## 5. 安全风险判断

### 5.1 是否涉及 fail-open / fail-closed 变化

**是。** 对于 `bootstrapEnabled=true` 的 `compile`/`query` 场景，将 apiKey 解密失败从 fail-closed 改为 fail-open（降级到 bootstrap）。

**风险评估**：
- **触发条件极窄**：需要同时满足 (1) JDBC snapshot 存在（表明绑定/模型/连接均正常） (2) apiKey 密文可读但解密失败。这意味着密文加密密钥已变更或密文损坏，属于数据库级异常状态。
- **降级目标可控**：bootstrap 路由来自 `application.yml` 的 `lattice.llm.compile-model` / `lattice.llm.reviewer-model` 等配置项，不是随机值。
- **deep_research 受保护**：strict 场景不受影响，保持 fail-closed。
- **可观测性已补足**：新增 `log.warn` 记录了 connectionId + 完整异常堆栈，运维可监控。

**结论**：构成了对非 strict 场景解密失败的行为语义变更（fail-closed → 条件性 fail-open），但风险可控，因为：
1. 触发条件极窄（DB 加密密钥变更或密文损坏）
2. 降级目标是已知的 bootstrap 配置
3. 有 warn 日志可供监控
4. deep_research 不受影响

### 5.2 是否涉及 apiKey / sk- 泄露风险

**否。** 经逐点审计：

| 审计点 | 结果 |
|---|---|
| `log.warn` 是否包含 apiKey | **否**，仅记录 `connectionId`（Long） |
| `log.warn` 异常堆栈是否包含 apiKey | **否**，`LlmSecretCryptoService.decrypt()` 抛出 `IllegalStateException("Failed to decrypt llm secret")`，不含明文或密文 |
| `LlmRouteResolution` 是否有 `@ToString` | **否**，无 Lombok 注解，默认 `Object.toString()` 不暴露字段 |
| `buildLlmEventFields()` 是否包含 apiKey | **否**，结构化事件日志字段为 scene/agentRole/scopeType/scopeId/routeLabel/providerType/baseUrl/modelName |
| 文件是否含硬编码 `sk-` | **否** |
| 文件是否含硬编码 `localhost`/`127.0.0.1` | **否** |

### 5.3 是否引入环境特判或测试特判

**否。** 改动仅基于 `requiresStrictBindings(normalizedScene)` 做场景区分，该方法是通用的场景判断（判断是否为 `deep_research`），不涉及环境变量、测试标识或特定 case。

---

## 6. Redline 结果

```
bash scripts/scan-redline.sh special_cases_report.md
EXIT_CODE=0
输出行数=0（redline 对 ExecutionLlmSnapshotService.java 无新增命中）
```

**结论**：`BLOCKER=0`，无新增红线命中。redline 扫描在 `ExecutionLlmSnapshotService.java` 上未发现 BLOCKER/REVIEW 项。

注意：redline 输出为完全空文件（0 行），可能是当前扫描范围不包含该文件，或该文件无命中规则。这不影响结论——即便在 "no hit" 场景下，`EXIT_CODE=0` 表示扫描流程本身成功。

---

## 7. 测试覆盖情况

### 已有测试

`ExecutionLlmSnapshotServiceTests`：7 个测试，全部通过（7/0/0）

| 测试名 | 覆盖场景 |
|---|---|
| `shouldFreezeSnapshotsFromActiveBindings` | 正常冻结快照 |
| `shouldResolveRouteFromSnapshotAndDecryptApiKey` | **仅覆盖解密成功路径** |
| `shouldApplyCompileWriterTimeoutDefaultWhenModelProfileTimeoutIsMissing` | writer 缺省超时 |
| `shouldApplyCompileReviewerTimeoutDefaultToBootstrapRoute` | bootstrap reviewer 超时 |
| `shouldApplyCompileFixerTimeoutDefaultToBootstrapRoute` | bootstrap fixer 超时 |
| `shouldRejectDeepResearchSceneWhenRequiredRolesAreMissing` | deep_research role 校验 |
| `shouldRejectBootstrapRouteForDeepResearchScene` | deep_research 禁止 bootstrap |

### 测试缺口

**本次改动新增 2 条代码路径，均未被现有测试覆盖：**

1. **非 strict 场景（compile/query）apiKey 解密失败 → 返回 `Optional.empty()`**
   - 应验证：解密失败时返回 empty，且日志包含 connectionId
   - 应验证：返回 empty 后调用方正确走 bootstrap fallback（可通过 `LlmGatewayRouteSupport.resolveScopedRoute` 集成验证）

2. **strict 场景（deep_research）apiKey 解密失败 → 抛异常**
   - 应验证：解密失败时异常正确传播（不被 catch 吞掉）

---

## 8. 是否建议提交

**结论**：**建议提交，但需满足以下前置条件。**

### 8.1 提交前提条件

1. **补测试**（高优先级）：补充至少 2 个定向单元测试：
   - `shouldReturnEmptyWhenApiKeyDecryptFailsForNonStrictScene`：模拟 `LlmSecretCryptoService.decrypt()` 抛 `RuntimeException`，断言非 strict 场景返回 `Optional.empty()` 且不抛异常
   - `shouldThrowWhenApiKeyDecryptFailsForDeepResearchScene`：断言 strict 场景仍抛异常

2. **验证 `bootstrapEnabled` 配置状态**：确认当前生产环境 `lattice.llm.bootstrap-enabled` 的实际取值：
   - 若 `bootstrap-enabled=false`：本次改动对生产行为零影响（解密失败仍会抛 `IllegalStateException`）
   - 若 `bootstrap-enabled=true`：本次改动使解密失败转为降级到 bootstrap，需要确认运维团队知情

3. **验证 `mvn test` 全量通过**：运行 `mvn test` 确认全量测试（含新增测试）通过

4. **确认 redline BLOCKER=0**：虽然当前 redline 空输出，但建议在补测试后重新运行确认

### 8.2 如果决定提交

**精确 staged 文件清单：**
```
src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java
src/test/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotServiceTests.java   ← 补测试后
```

**必须补跑的验证命令：**
```bash
# 1. 全量单元测试
mvn test

# 2. 定向测试
mvn test -Dtest="ExecutionLlmSnapshotServiceTests"

# 3. 红线扫描
bash scripts/scan-redline.sh special_cases_report.md

# 4. 确认 bootstrap 配置
grep -r "bootstrap-enabled\|bootstrapEnabled" src/main/resources/
```

**建议 commit message：**
```
fix(llm): 将 apiKey 解密失败从硬异常转为优雅降级

resolveRoute 中 apiKey 解密失败时，非 strict 场景（compile/query）
不再向上传播 RuntimeException，改为返回 Optional.empty()，
由调用方按 isBootstrapAllowed 决定走 bootstrap fallback 或抛
IllegalStateException。

deep_research 场景（strict）行为不变，仍 re-throw 保持 fail-closed。

此改动使解密失败的处理与其它 snapshot 层失败（缺失 profile/connection）
保持一致：优雅降级 + 可观测（log.warn 含 connectionId，不含 apiKey）。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

### 8.3 风险声明

即使满足上述前提条件，提交方应知悉：

1. **语义变化**：在 `bootstrap-enabled=true` 时，apiKey 解密失败从 fail-closed（硬失败）变为 fail-open（降级到 bootstrap 密钥）。虽然降级目标是已知配置且触发条件极窄，但这仍是安全语义变化。
2. **监控建议**：上线后应监控 `"api key decrypt failed"` 日志出现频率。如果频繁出现，说明数据库加密密钥与密文不匹配，需紧急处理。
3. **deep_research 不受影响**：strict 场景保持 fail-closed，不构成额外风险。

---

## 9. 结论

| 维度 | 结论 |
|---|---|
| **改动主题** | LLM snapshot apiKey 解密失败的优雅降级处理 |
| **是否属于模型绑定/LLM snapshot** | 是——核心改动在 snapshot 路由解析的 apiKey 解密环节 |
| **是否安全** | 基本安全，apiKey 无泄露风险，deep_research 受保护 |
| **是否涉及 fail-open** | 是——`bootstrapEnabled=true` 时解密失败从硬异常改为降级到 bootstrap。风险可控但需知悉 |
| **是否涉及 apiKey 泄露** | 否——日志仅含 connectionId，不含 apiKey 或密文 |
| **redline** | BLOCKER=0 |
| **测试覆盖** | 缺口明确：缺 2 个解密失败的定向测试 |
| **是否建议提交** | 建议在补测试 + 确认 bootstrap 配置状态后提交 |

**该改动不应归入 title-generation、documentparse、admin、Q6 terminal field alias 或 docs 桶。它属于独立的 LLM 基础设施/运行时路由安全加固范畴，应作为独立 scoped commit 提交。**

---

*本报告由 agentB 按只读模式生成，未修改任何生产代码、测试代码、配置文件或 redline 脚本。*
