# B11a2: LLM 测试 Controller 内部 DTO @Data 降级 + 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B11a2（B11a 第 2/最后子批次，4/14 类）

---

## 1. 修改文件清单

| 文件 | 变更 |
|---|---|
| `AdminLlmConnectionTestController.java` | 2 个内部 DTO @Data 降级，apiKey @ToString.Exclude，补 9 字段 Javadoc |
| `AdminLlmModelTestController.java` | 2 个内部 DTO @Data 降级，补 11 字段 Javadoc |

---

## 2. DTO 降级详情

### AdminLlmConnectionTestController

| 类 | 字段数 | 注解变更 | 安全处置 |
|---|---|---|---|
| `AdminLlmConnectionTestRequest` | 4 | @Data → @Getter @Setter | **apiKey @ToString.Exclude**（临时探测用明文密钥，不持久化） |
| `AdminLlmConnectionTestResponse` | 5 | @Data → @Getter | — |

**apiKey Javadoc**："仅用于本次连接测试，不持久化，禁止记录到日志。已加 @ToString.Exclude 防御性排除。"

### AdminLlmModelTestController

| 类 | 字段数 | 注解变更 |
|---|---|---|
| `AdminLlmModelTestRequest` | 6 | @Data → @Getter @Setter |
| `AdminLlmModelTestResponse` | 5 | @Data → @Getter |

---

## 3. 安全处置

**修复前**：`AdminLlmConnectionTestRequest` 使用 `@Data`，`toString()` 输出临时探测用明文 apiKey，日志/调试中泄露。

**修复后**：`@Data` → `@Getter @Setter`，`apiKey` 加 `@ToString.Exclude`。

这是 B11a 中修复的**第 2 个** apiKey 泄露点（B11a1 已修复 `AdminLlmConnectionRequest`）。

---

## 4. Lombok 统计

### B11a2

| 注解 | ConnectionTest | ModelTest | 合计 |
|---|---|---|---|
| `@Getter` | 2 | 2 | 4 |
| `@Setter` | 1 (Request) | 1 (Request) | 2 |
| `@NoArgsConstructor` | 2 | 2 | 4 |
| `@AllArgsConstructor` | 2 | 2 | 4 |
| `@ToString.Exclude` | 1 (apiKey) | 0 | 1 |
| `@Data` | 0 | 0 | 0 |

### B11a 合计（B11a1 + B11a2 = 14 类）

| 指标 | 数量 |
|---|---|
| `@Data` → 降级 | 14 → **0** |
| `@Getter` | 14 |
| `@Setter`（仅 Request） | 5 |
| `@ToString.Exclude`（apiKey） | 2 |
| 字段 Javadoc | 95 |
| `@NoArgsConstructor` / `@AllArgsConstructor` | 14 对全部保留 |

---

## 5. 验证

```
mvn compile: BUILD SUCCESS
rg -n '@Data' (2 文件): (无结果) ✓
@ToString.Exclude: ConnectionTestController 第 90 行 ✓
@Setter: 仅 Request 类有（ConnectionTestController:1, ModelTestController:1） ✓
Response 无 @Setter ✓
```

Controller testConnection() / testModel() 方法未修改。

---

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 2 个目标文件 | 通过 |
| 4 个 @Data 全部降级 | 通过 |
| apiKey @ToString.Exclude | 通过 |
| Request 有 @Setter，Response 无 | 通过 |
| @NoArgsConstructor/@AllArgsConstructor 保留 | 通过 |
| testConnection()/testModel() 未修改 | 通过 |
| 未修改 B11a1 文件 | 通过 |
| 未修改 docs/模型绑定配置参考.md / special_cases_report.md | 通过 |
| 未 stage/commit/push | 通过 |
