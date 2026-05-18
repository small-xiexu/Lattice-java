# StateGraphCompileOrchestratorTests LLM Secret Fixture 修复报告

## 任务

修复 `StateGraphCompileOrchestratorTests` 中 3 个测试因 `Failed to decrypt llm secret` / `AEADBadTagException` 导致的失败。

## 根因分析

测试数据库中 `llm_provider_connections` 表存在历史加密数据（`api_key_ciphertext`），`agent_model_bindings` 表存在编译场景的绑定记录。测试执行流程：

1. `InitializeJobNode.freezeSnapshotsFailOpen()` → 从 `agent_model_bindings` 查到启用的绑定 → 关联 `llm_model_profiles` → 关联 `llm_provider_connections` → 将快照写入 `execution_llm_snapshots`
2. `ArticleCompileSupport.currentCompileRoute()` → `AgentModelRouter.routeFor()` → `ExecutionLlmSnapshotService.resolveRoute()` → 找到刚冻结的快照 → 读取对应 `llm_provider_connections` → 调用 `LlmSecretCryptoService.decrypt()` 解密 API Key
3. 解密时使用的 `lattice.llm.secret-encryption-key`（默认值 `lattice-phase8-bootstrap-key-change-me`）与历史数据实际加密时使用的密钥不一致 → `AEADBadTagException`

测试未设置 `lattice.llm.secret-encryption-key`，也未在 `resetTables()` 中清理 LLM 相关表，导致历史脏数据干扰。

## 修复方案

仅修改测试文件 `StateGraphCompileOrchestratorTests.java`，不改动任何生产代码：

### 1. 添加测试加密密钥属性

```java
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.llm.secret-encryption-key=test-orchestrator-compile-key!"  // 新增
})
```

### 2. 在 `resetTables()` 中清理 LLM 相关表

```java
private void resetTables() {
    // 新增：先清理 LLM 快照和绑定，避免历史加密数据干扰
    jdbcTemplate.execute("TRUNCATE TABLE lattice.execution_llm_snapshots RESTART IDENTITY CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE lattice.agent_model_bindings RESTART IDENTITY CASCADE");
    // 原有清理
    jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE lattice.synthesis_artifacts");
    jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_job_steps");
}
```

### 修复原理

- 清理 `agent_model_bindings` → `freezeSnapshots` 查不到编译场景绑定 → 返回空列表（编译场景非 strict bindings）
- 清理 `execution_llm_snapshots` → `resolveRoute` 查不到快照 → 返回 `Optional.empty()`
- 路由器 fallback 到 `LlmGateway` → `resolveBootstrapRoute` → 使用 `llmProperties` 中的 bootstrap 配置（不走 JDBC 解密路径）
- 添加 `secret-encryption-key` 属性作为安全网，确保如果未来有路径触发解密也使用自洽的密钥

## 修改文件清单

| 文件 | 修改类型 |
|---|---|
| `src/test/java/.../StateGraphCompileOrchestratorTests.java` | 修改（添加属性 + 扩展 resetTables） |

**未修改任何生产代码。**

## 验证结果

| 门禁 | 结果 |
|---|---|
| Redline Scan | BLOCKER=0 |
| `StateGraphCompileOrchestratorTests` | 3/3 通过 |
| `IncrementalCompileServiceTests` | 1/1 通过 |
| 全量 `mvn test` | **811 通过，0 失败，0 错误** |

修复前：808 pass / 3 fail；修复后：811 pass / 0 fail。
