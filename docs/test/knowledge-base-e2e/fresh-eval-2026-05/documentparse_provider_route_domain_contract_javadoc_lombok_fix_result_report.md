# B14b: DocumentParse Provider/Route 领域对象契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B14b（B14 第 2/最后子批次，5/10 类）

---

## 1. 修改文件清单

| 文件 | 字段数 | 变更 |
|---|---|---|
| `ParseRoutePolicy.java` | 11 | `@Getter`，删除 11 getter，11 字段 Javadoc，保留 DEFAULT_SCOPE + defaultPolicy() |
| `ProviderConnection.java` | 12 | `@Getter`，删除 12 getter，12 字段 Javadoc（credentialCiphertext 敏感标注），保留 4 PROVIDER_* 常量 |
| `ProviderDescriptor.java` | 7 | `@Getter`，删除 7 getter，7 字段 Javadoc（supportedCapabilities Set<ParseCapability> 语义） |
| `ProviderFieldDescriptor.java` | 7 | `@Getter`，删除 7 getter，7 字段 Javadoc（动态表单字段定义语义） |
| `ProviderProbeResult.java` | 5 | `@Getter`，删除 5 getter，5 字段 Javadoc（message 错误诊断标注） |

---

## 2. Lombok 统计

| 类 | @Getter | 删除 getter |
|---|---|---|
| `ParseRoutePolicy` | 1 | 11 |
| `ProviderConnection` | 1 | 12 |
| `ProviderDescriptor` | 1 | 7 |
| `ProviderFieldDescriptor` | 1 | 7 |
| `ProviderProbeResult` | 1 | 5 |
| **B14b 合计** | **5** | **42** |

**B14 合计（B14a + B14b = 10 类）**：3+5=8 @Getter，27+42=69 getter 删除，0 @Data。

---

## 3. 安全标注

| 字段 | 类 | 标注 |
|---|---|---|
| `credentialCiphertext` | ProviderConnection | 已加密存储，非明文但仍属敏感数据，禁止 toString() |
| `credentialMask` | ProviderConnection | 仅管理侧脱敏展示，非完整凭证 |

**保留项**：`DEFAULT_SCOPE`、`defaultPolicy()`、`PROVIDER_TENCENT_OCR`/`PROVIDER_ALIYUN_OCR`/`PROVIDER_GOOGLE_DOCUMENT_AI`/`PROVIDER_TEXTIN_XPARSE`。

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
无 @Data/@Setter/@Builder ✓
defaultPolicy() 保留 ✓
PROVIDER_* 常量: 4 个 ✓
```

---

## 5. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 5 个目标文件 | 通过 |
| 仅 @Getter | 通过 |
| credentialCiphertext 敏感标注 | 通过 |
| defaultPolicy/providers constants 保留 | 通过 |
| 未修改 B14a | 通过 |
| 未 stage/commit/push | 通过 |
