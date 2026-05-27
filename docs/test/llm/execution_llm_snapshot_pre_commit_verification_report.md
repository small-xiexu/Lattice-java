# ExecutionLlmSnapshotService apiKey 解密优雅降级验证报告

- 验证时间：2026-05-27 17:45（Asia/Shanghai）
- 验证性质：只读审计
- 验证 Agent：agentD
- 结论用途：判断 ExecutionLlmSnapshotService apiKey 解密失败优雅降级改动是否具备独立提交条件
- 约束声明：未 stage、未 commit、未 push，且未修改任何业务代码

## 1. 工作区只读盘点

### 1.1 候选文件（共 2 个）

| 文件 | 类型 | 说明 |
|---|---|---|
| `src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java` | 生产代码 | apiKey 解密失败 try-catch 优雅降级 |
| `src/test/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotServiceTests.java` | 测试代码 | 解密失败场景的 fail-open / fail-closed 覆盖 |

### 1.2 明确排除文件

以下文件虽然在工作区有改动，但不属于本桶，必须排除：

- `docs/模型绑定配置参考.md` — 私有配置，永远排除提交
- `docs/项目全流程真实验收手册.md` — 不属于本桶
- `special_cases_report.md` — redline 输出，排除
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java` — admin 层
- `src/main/java/com/xbk/lattice/api/admin/*` — admin API
- `src/main/java/com/xbk/lattice/documentparse/**` — documentparse 模块
- `src/main/resources/static/admin/**` — admin UI 静态资源
- `src/test/java/com/xbk/lattice/api/admin/**` — admin 测试
- `src/test/java/com/xbk/lattice/documentparse/**` — documentparse 测试
- `src/test/resources/admin/*` — admin 测试资源
- `docs/plans/*`（untracked）— 计划文档
- `docs/test/knowledge-base-e2e/*`（untracked）— Q6 余波报告
- `docs/test/llm/execution_llm_snapshot_service_change_analysis_report.md`（untracked）— agentB 分析报告
- `docs/test/llm/execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md`（untracked）— agentA 修复报告

## 2. 候选文件 diff 审核

### 2.1 ExecutionLlmSnapshotService.java

- 改动位置：约 line 258，`decrypt()` 调用处
- 改动量：+13 行
- 逻辑变更：
  - 原先直接调用 `llmSecretCryptoService.decrypt(providerConnection.orElseThrow().getApiKeyCiphertext())`，解密失败会直接抛出 `RuntimeException`
  - 现在包裹 try-catch：
    - 调用 `requiresStrictBindings(normalizedScene)` 判断是否为严格场景
    - **严格场景（deep_research）**：re-throw 异常，保持 fail-closed，不允许降级
    - **非严格场景（compile、query 等）**：`log.warn` 记录 `connectionId` 和异常信息，返回 `Optional.empty()` 使得调用方回退到 bootstrap 路由
- `requiresStrictBindings()` 是该类已有方法，在整个类中一致使用，本次改动只是复用已有判断逻辑
- `log.warn` 仅输出 `connectionId`，**不输出 apiKey 或 ciphertext**，日志安全
- 改动精准、自包含，不涉及其他类或模块

### 2.2 ExecutionLlmSnapshotServiceTests.java

- 改动量：+121 行
- 新增两个测试用例：
  - `shouldReturnEmptyWhenApiKeyDecryptFailsForNonStrictScene`：验证非严格场景（compile）解密失败时返回 `Optional.empty()`
  - `shouldThrowWhenApiKeyDecryptFailsForDeepResearchScene`：验证 deep_research 场景解密失败时 re-throw 相同异常
- 新增内部类 `FailingDecryptCryptoService`：覆写 `decrypt()` 方法，抛出指定异常，用于模拟解密失败场景
- 测试数据使用：
  - `dummy-ciphertext` — 测试桩密文
  - `sk-du****3456` — 掩码 API key
  - `http://localhost:8888` — mock URL
- 测试数据均为测试桩值，无真实密钥或生产环境地址

## 3. fail-open / fail-closed 行为审核

| 场景 | 行为 | 验证结论 |
|---|---|---|
| deep_research（严格） | 解密失败 re-throw 异常，任务失败 | fail-closed，符合预期 |
| compile / query（非严格） | 解密失败返回 `Optional.empty()`，回退 bootstrap 路由 | fail-open，优雅降级 |
| log.warn 日志内容 | 仅输出 `connectionId` 和异常信息 | 不泄漏 apiKey 或 ciphertext |

### 3.1 bootstrap 配置状态

`src/main/resources/config/lattice-llm.yml` 中：

```yaml
bootstrap-enabled: ${LATTICE_LLM_BOOTSTRAP_ENABLED:true}
```

默认值为 `true`。非严格场景解密失败 → `Optional.empty()` → 调用方获取不到 snapshot provider → 走 bootstrap 路由 → LLM 调用仍可正常执行。

## 4. Redline 结果

```
命令：bash scripts/scan-redline.sh special_cases_report.md
结果：BLOCKER=0
```

红线没有阻塞项，通过。

## 5. Maven 全量测试

```
命令：mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
结果：Tests run: 917, Failures: 0, Errors: 0, Skipped: 0
结论：BUILD SUCCESS
```

全量测试通过（较之前 915 增加 2 个，即本次新增的 ExecutionLlmSnapshotService 测试）。

## 6. ExecutionLlmSnapshotService 定向测试

```
命令：mvn ... -Dtest="ExecutionLlmSnapshotServiceTests" test
结果：Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
结论：BUILD SUCCESS
```

定向测试全部通过（原有 7 个 + 新增 2 个）。非严格场景测试过程中有预期内的 WARN 日志（解密失败 RuntimeException 的 stacktrace），属于正常测试行为，不影响测试结果。

## 7. 硬编码 / 过拟合 / 敏感信息扫描

### 7.1 生产代码

- `apiKey` 为局部变量名，不是硬编码密钥
- `connectionId` 仅在 `log.warn` 中使用，用于定位问题连接，不包含密钥信息
- `requiresStrictBindings` 为已有方法，逻辑一致
- 未发现真实 API 密钥、令牌或生产环境地址

### 7.2 测试代码

- `dummy-ciphertext` — 测试桩值
- `sk-du****3456` — 掩码 API key，非真实密钥
- `http://localhost:8888` — mock URL
- 未发现真实 API 密钥

### 7.3 结论

- 未发现真实 API 密钥或令牌
- 未发现生产代码中对具体样本文件名、标题、题集的逻辑特判
- 未发现 Q6/S2/Kubernetes/8080 等业务域关键词
- 日志输出不包含 apiKey 或 ciphertext

## 8. 架构边界判断

| 问题 | 结论 |
|---|---|
| 这 2 个文件是否构成独立 LLM 基础设施桶？ | **是**。核心围绕 apiKey 解密失败的优雅降级处理 |
| 是否依赖 admin UI/API？ | **否**。与 admin 零耦合 |
| 是否依赖 documentparse？ | **否** |
| 是否依赖 title-generation？ | **否** |
| 是否依赖 docs/plans？ | **否** |
| 是否与 Q6 余波有关？ | **否** |
| 是否需要同步 docs/quality-progress-and-lessons.md？ | **建议提交后更新** |
| 是否建议进入提交阶段？ | **是** |

## 9. 提交建议

### 9.1 是否建议提交

**建议提交。**

同时满足以下全部条件：

- [x] redline `BLOCKER=0`
- [x] 全量 `mvn test` 通过（917/0/0）
- [x] ExecutionLlmSnapshotService 定向测试通过（9/0/0）
- [x] fail-open（非严格场景 → Optional.empty()）与 fail-closed（deep_research → throw）均有测试覆盖
- [x] 生产代码未发现真实密钥或业务特判
- [x] log.warn 不泄漏 apiKey 或 ciphertext
- [x] 文件边界与 admin、documentparse、title-generation、docs/plans、Q6 余波、私有配置可独立拆清楚

### 9.2 精确 staged 文件清单

```
src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java
src/test/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotServiceTests.java
```

### 9.3 建议 commit message

```
fix(llm): apiKey 解密失败优雅降级，deep_research 保持 fail-closed

非严格场景（compile/query）apiKey 解密失败时返回 Optional.empty()，
调用方回退到 bootstrap 路由；严格场景（deep_research）保持 re-throw，
确保不允许降级。log.warn 仅输出 connectionId，不泄漏密钥。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

## 10. 当前状态

- 未 stage
- 未 commit
- 未 push
- 仅新增本验证报告
