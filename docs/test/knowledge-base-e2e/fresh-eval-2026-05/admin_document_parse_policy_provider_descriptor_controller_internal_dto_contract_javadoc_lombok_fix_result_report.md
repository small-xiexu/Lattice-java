# B11b2: Document Parse Policy/ProviderDescriptor Controller 内部 DTO @Data 降级报告

改造时间：2026-06-01
改造人：agentA
批次：B11b2（B11b 第 2/最后子批次，5/11 类）

---

## 1. 修改文件清单

| 文件 | DTO 数 | 变更 |
|---|---|---|
| `AdminDocumentParsePolicyController.java` | 2 | @Data 降级，补 17 字段 Javadoc（fallbackPolicyJson 路由语义标注） |
| `AdminDocumentParseProviderDescriptorController.java` | 3 | @Data 降级，补 16 字段 Javadoc（动态表单字段定义语义标注） |

---

## 2. DTO 降级详情

### AdminDocumentParsePolicyController（2 个）

| 类 | 字段数 | 注解变更 |
|---|---|---|
| `AdminDocumentParsePolicyRequest` | 6 | @Data → @Getter @Setter |
| `AdminDocumentParsePolicyResponse` | 11 | @Data → @Getter |

关键字段 Javadoc：
- `imageConnectionId` / `scannedPdfConnectionId`：按文档类型路由到对应解析连接
- `cleanupEnabled` / `cleanupModelProfileId`：清理步骤开关和模型配置
- `fallbackPolicyJson`：降级路由策略 JSON，为空默认 `{}`，定义无法匹配连接时的兜底行为

### AdminDocumentParseProviderDescriptorController（3 个）

| 类 | 字段数 | 注解变更 |
|---|---|---|
| `AdminDocumentParseProviderDescriptorListResponse` | 2 | @Data → @Getter |
| `AdminDocumentParseProviderDescriptorResponse` | 7 | @Data → @Getter |
| `AdminDocumentParseProviderFieldResponse` | 7 | @Data → @Getter |

关键字段 Javadoc：
- `credentialFields` / `configFields`：前端据此动态生成凭证/配置表单
- `inputType`：password 类型时前端应做脱敏展示
- `required` / `defaultValue` / `placeholder`：标准动态表单字段属性

---

## 3. Lombok 统计

### B11b2

| 指标 | PolicyController | ProviderDescriptorController | 合计 |
|---|---|---|---|
| `@Data` → 0 | 2 | 3 | **5** |
| `@Getter` | 2 | 3 | 5 |
| `@Setter`（仅 Request） | 1 | 0 | 1 |
| 字段 Javadoc | 17 | 16 | **33** |

### B11b 合计（B11b1 + B11b2 = 11 类）

| 指标 | 数量 |
|---|---|
| `@Data` → 降级 | 11 → **0** |
| `@Getter` | 11 |
| `@Setter`（仅 Request） | 3 |
| `@ToString.Exclude`（credentialJson） | 2 |
| 字段 Javadoc | 60 |

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
rg -n '@Data' (2 文件): (无结果) ✓
@Setter: 仅 PolicyRequest（PolicyController:1） ✓
```

Controller getDefaultPolicy() / updateDefaultPolicy() / listProviders() 未修改。

---

## 5. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 2 个目标文件 | 通过 |
| 5 个 @Data 全部降级 | 通过 |
| Request 有 @Setter，Response 无 | 通过 |
| 未修改 B11b1/B11a 文件 | 通过 |
| 未 stage/commit/push | 通过 |
