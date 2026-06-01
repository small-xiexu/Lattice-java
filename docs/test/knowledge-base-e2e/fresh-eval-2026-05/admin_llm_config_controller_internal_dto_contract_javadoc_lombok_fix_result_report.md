# B11a1: AdminLlmConfigController 内部 DTO @Data 降级 + 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B11a1（B11a 第 1 子批次，10/14 类）

---

## 1. 修改文件

| 文件 | 变更 |
|---|---|
| `AdminLlmConfigController.java` | 10 个内部 static DTO：@Data 全量降级，补 81 字段 Javadoc，apiKey 加 @ToString.Exclude |

---

## 2. DTO 降级详情

### Request 类（@Data → @Getter @Setter @NoArgsConstructor @AllArgsConstructor，共 3 个）

| 类 | 字段数 | 安全处置 |
|---|---|---|
| `AdminLlmConnectionRequest` | 7 | **apiKey 加 @ToString.Exclude**（明文密钥，提交后加密存储，更新为空表示沿用旧密钥） |
| `AdminLlmModelRequest` | 15 | extraOptionsJson 标注为 Provider 扩展配置 |
| `AdminLlmBindingRequest` | 8 | scene/agentRole 标注由 SCENE_ROLE_OPTIONS 校验 |

### Response 类（@Data → @Getter @NoArgsConstructor @AllArgsConstructor，共 7 个）

| 类 | 字段数 | 备注 |
|---|---|---|
| `AdminLlmConnectionResponse` | 11 | apiKeyMask 标注为脱敏展示，非完整密钥 |
| `AdminLlmModelResponse` | 20 | — |
| `AdminLlmBindingResponse` | 12 | — |
| `AdminLlmConnectionListResponse` | 2 | — |
| `AdminLlmModelListResponse` | 2 | — |
| `AdminLlmBindingListResponse` | 2 | — |
| `AdminMutationResponse` | 2 | id/status |

---

## 3. 安全处置

### apiKey 泄露修复

**修复前**：`AdminLlmConnectionRequest` 使用 `@Data`，`toString()` 输出 `apiKey=sk-xxxxxx` 明文密钥。任何日志/调试触发 toString() 均会泄露。

**修复后**：
- `@Data` 降级为 `@Getter @Setter`
- `apiKey` 字段加 `@ToString.Exclude`（防御性标注）
- Javadoc 明确："提交后立即由 LlmSecretCryptoService.encrypt() 加密存储，禁止记录到日志，更新时为空字符串表示沿用旧密钥"

---

## 4. 关键字段 Javadoc 标注

| 字段 | 所属类 | 标注内容 |
|---|---|---|
| `apiKey` | ConnectionRequest | 明文密钥，加密存储，禁止日志，@ToString.Exclude |
| `apiKeyMask` | ConnectionResponse | 脱敏展示（sk-\*\*\*\*xxxx），非完整密钥 |
| `modelKind` | ModelRequest | CHAT/EMBEDDING 互斥校验（EMBEDDING 必填 expectedDimensions，CHAT 禁止） |
| `scene` | BindingRequest | compile/query/deep_research，由 SCENE_ROLE_OPTIONS 校验 |
| `agentRole` | BindingRequest | 必须属于 scene 的有效角色集合 |
| `extraOptionsJson` | ModelRequest/Response | Provider 扩展配置，可能含额外参数 |

---

## 5. Lombok 统计

| 注解 | 数量 | 用途 |
|---|---|---|
| `@Getter` | 10 | 所有 DTO |
| `@Setter` | 3 | 3 个 Request |
| `@NoArgsConstructor` | 10 | 所有 DTO（保留 Spring 绑定） |
| `@AllArgsConstructor` | 10 | 所有 DTO（保留） |
| `@ToString.Exclude` | 1 | apiKey 字段 |
| `@Data` | **0** | 已全部清除 |

---

## 6. 验证

```
mvn compile: BUILD SUCCESS
rg -n '@Data': (无结果) ✓
rg -n '@ToString.Exclude': 第 542 行 ✓
@Getter 计数: 10 ✓
@Setter 计数: 3（仅 Request） ✓
```

未修改 controller 路由方法、校验方法、映射方法、工具方法。未修改 SCENE_ROLE_OPTIONS。

---

## 7. B11a 剩余

| 子批次 | 状态 | 类数 | 文件 |
|---|---|---|---|
| **B11a1** | **已完成** | 10 | `AdminLlmConfigController.java` |
| B11a2 | 待开始 | 4 | `AdminLlmConnectionTestController.java` + `AdminLlmModelTestController.java` |

## 8. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 AdminLlmConfigController.java | 通过 |
| 10 个 @Data 全量降级 | 通过（0 残留） |
| apiKey @ToString.Exclude | 通过（第 542 行） |
| Request 有 @Setter，Response 无 @Setter | 通过 |
| @NoArgsConstructor/@AllArgsConstructor 全部保留 | 通过 |
| 未修改 controller 方法 | 通过 |
| 未修改 SCENE_ROLE_OPTIONS | 通过 |
| 未修改字段类型/名称 | 通过 |
| 未修改 docs/模型绑定配置参考.md / special_cases_report.md | 通过 |
| 未 stage/commit/push | 通过 |
