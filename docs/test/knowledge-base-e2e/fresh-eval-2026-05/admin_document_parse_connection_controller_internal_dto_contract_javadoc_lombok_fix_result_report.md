# B11b1: Document Parse 连接/测试 Controller 内部 DTO @Data 降级报告

改造时间：2026-06-01
改造人：agentA
批次：B11b1（B11b 第 1 子批次，6/11 类）

---

## 1. 修改文件清单

| 文件 | DTO 数 | 变更 |
|---|---|---|
| `AdminDocumentParseConnectionController.java` | 4 | @Data 全量降级，credentialJson @ToString.Exclude，补 23 字段 Javadoc |
| `AdminDocumentParseConnectionTestController.java` | 2 | @Data 全量降级，credentialJson @ToString.Exclude，补 10 字段 Javadoc |

---

## 2. DTO 降级详情

### AdminDocumentParseConnectionController（4 个）

| 类 | 字段数 | 注解变更 | 安全处置 |
|---|---|---|---|
| `AdminDocumentParseConnectionRequest` | 7 | @Data → @Getter @Setter | **credentialJson @ToString.Exclude**（凭证 JSON，加密存储） |
| `AdminDocumentParseConnectionResponse` | 12 | @Data → @Getter | credentialMask 标注为脱敏展示 |
| `AdminDocumentParseConnectionListResponse` | 2 | @Data → @Getter | — |
| `AdminMutationResponse` | 2 | @Data → @Getter | — |

### AdminDocumentParseConnectionTestController（2 个）

| 类 | 字段数 | 注解变更 | 安全处置 |
|---|---|---|---|
| `AdminDocumentParseConnectionTestRequest` | 5 | @Data → @Getter @Setter | **credentialJson @ToString.Exclude**（临时测试，不持久化） |
| `AdminDocumentParseConnectionTestResponse` | 5 | @Data → @Getter | — |

---

## 3. 安全处置

### credentialJson 泄露修复（2 处）

- `AdminDocumentParseConnectionRequest.credentialJson`："提交后由 LlmSecretCryptoService.encrypt() 加密存储，更新为空表示沿用旧凭证，新增时必填"
- `AdminDocumentParseConnectionTestRequest.credentialJson`："仅用于本次连接测试，不持久化，禁止记录到日志"

两处均加 `@ToString.Exclude`。

---

## 4. Lombok 统计

| 指标 | ConnectionController | TestController | 合计 |
|---|---|---|---|
| `@Data` → 0 | 4 | 2 | **6** |
| `@Getter` | 4 | 2 | 6 |
| `@Setter`（仅 Request） | 1 | 1 | 2 |
| `@ToString.Exclude` | 1 | 1 | 2 |
| 字段 Javadoc | 23 | 10 | **33** |

---

## 5. 验证

```
mvn compile: BUILD SUCCESS
rg -n '@Data': (无结果) ✓
@ToString.Exclude: 2 处 credentialJson ✓
@Setter: 仅 Request 类（ConnectionController:1, TestController:1） ✓
```

Controller 方法未修改。

---

## 6. B11b 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B11b1** | **已完成** | 6 |
| B11b2 | 待开始 | 5 (Policy + ProviderDescriptor) |

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 2 个目标文件 | 通过 |
| 6 个 @Data 全部降级 | 通过 |
| credentialJson @ToString.Exclude | 通过（2 处） |
| Request 有 @Setter，Response 无 | 通过 |
| 未修改 B11a 文件 | 通过 |
| 未 stage/commit/push | 通过 |
