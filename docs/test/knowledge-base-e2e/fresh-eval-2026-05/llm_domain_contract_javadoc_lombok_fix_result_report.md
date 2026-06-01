# B16: LLM Domain 领域对象契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B16（4 类，一轮完成）

---

## 1. 自审结果

全部 4 类均通过自审——不可变 final-field，无 Lombok，getter 全部简单字段访问。

---

## 2. 修改文件清单

| 文件 | 字段 | 删除 getter | 安全/风险标注 |
|---|---|---|---|
| `LlmProviderConnection.java` | 12 | 12 | **apiKeyCiphertext/apiKeyMask** 敏感标注，baseUrl 内部路径 |
| `LlmModelProfile.java` | 19 | 19 | MODEL_KIND_CHAT/EMBEDDING 常量保留，extraOptionsJson/expectedDimensions |
| `AgentModelBinding.java` | 12 | 12 | scene/agentRole 路由绑定语义 |
| `ExecutionLlmSnapshot.java` | 20 | 20 | 快照语义（审计/成本估算/回放），价格快照 |

**合计**：4 @Getter，63 getter 删除。

---

## 3. 关键标注

| 字段 | 类 | 标注 |
|---|---|---|
| `apiKeyCiphertext` | LlmProviderConnection | 加密存储，非明文但仍敏感，禁止 toString()/日志 |
| `apiKeyMask` | LlmProviderConnection | 脱敏展示（sk-\*\*\*\*xxxx），非完整密钥 |
| `baseUrl` | LlmProviderConnection/ExecutionLlmSnapshot | 可能含内部网络路径 |
| `extraOptionsJson` | LlmModelProfile/ExecutionLlmSnapshot | Provider 扩展配置 |
| `expectedDimensions` | LlmModelProfile | 与实际模型不匹配时检索异常 |
| `snapshotVersion` | ExecutionLlmSnapshot | 递增版本号，用于审计链路追溯 |
| `inputPrice/outputPrice` | ExecutionLlmSnapshot | 快照时刻定价，用于成本估算 |

**保留**：`MODEL_KIND_CHAT` / `MODEL_KIND_EMBEDDING` 常量。

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
@Getter: 4/4 ✓
apiKeyCiphertext/apiKeyMask 敏感标注: ✓
MODEL_KIND_* 常量保留: ✓
```

---

## 5. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 4 个目标文件 | 通过 |
| 仅 @Getter | 通过 |
| apiKey 敏感标注 | 通过 |
| 常量/构造器保留 | 通过 |
| 未 stage/commit/push | 通过 |
