# B11b Document Parse Controller 内部 DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B11b — document parse 4 个 controller 内部 DTO

---

## 一、拆分建议：B11b → B11b1 + B11b2

11 个候选类超过 10 个上限，按职责域拆分：

| 子批次 | 候选数 | 控制器 | 拆分理由 |
|---|---|---|---|
| **B11b1** | **6** | `AdminDocumentParseConnectionController`(4) + `AdminDocumentParseConnectionTestController`(2) | 连接 CRUD + 即时测试，含 `credentialJson` 敏感字段 |
| **B11b2** | **5** | `AdminDocumentParsePolicyController`(2) + `AdminDocumentParseProviderDescriptorController`(3) | 路由策略 + Provider 元数据，只读展示为主，无凭证字段 |

---

## 二、全局发现：@Data 全量污染 + credentialJson 泄露风险

**全部 11 个内部 DTO 均使用 `@Data @NoArgsConstructor @AllArgsConstructor`**，与 B11a 相同的 100% 污染率。其中 **3 个类含 `credentialJson` 字段**，@Data toString() 会在日志中泄露文档解析 Provider 的连接凭证（可能含 API key、token、password 等）。

注意：`AdminDocumentParseConnectionController.AdminMutationResponse` 与 `AdminLlmConfigController.AdminMutationResponse` 是**同名但不同的内部 static class**（各自在独立 controller 作用域内），B11a 处理的版本不影响这里的。

---

## 三、B11b1 纳入文件清单（6 个类）

### AdminDocumentParseConnectionController（4 个）

| # | 类名 | 类型 | 字段数 | 敏感字段 | 处置 |
|---|---|---|---|---|---|
| 1 | `AdminDocumentParseConnectionRequest` | Request | 7 | **`credentialJson`**（凭证 JSON）、`configJson` | @Data→@Getter/@Setter，credentialJson 加 @ToString.Exclude |
| 2 | `AdminDocumentParseConnectionResponse` | Response | 12 | `credentialMask`、`configJson` | @Data→@Getter |
| 3 | `AdminDocumentParseConnectionListResponse` | Response | 2 | — | @Data→@Getter |
| 4 | `AdminMutationResponse` | Response | 2 | — | @Data→@Getter |

### AdminDocumentParseConnectionTestController（2 个）

| # | 类名 | 类型 | 字段数 | 敏感字段 | 处置 |
|---|---|---|---|---|---|
| 5 | `AdminDocumentParseConnectionTestRequest` | Request | 5 | **`credentialJson`**（临时测试凭证） | @Data→@Getter/@Setter，credentialJson 加 @ToString.Exclude |
| 6 | `AdminDocumentParseConnectionTestResponse` | Response | 5 | — | @Data→@Getter |

---

## 四、B11b2 纳入文件清单（5 个类）

### AdminDocumentParsePolicyController（2 个）

| # | 类名 | 类型 | 字段数 | 敏感字段 | 处置 |
|---|---|---|---|---|---|
| 1 | `AdminDocumentParsePolicyRequest` | Request | 6 | `fallbackPolicyJson`（路由规则 JSON） | @Data→@Getter/@Setter |
| 2 | `AdminDocumentParsePolicyResponse` | Response | 11 | `fallbackPolicyJson` | @Data→@Getter |

### AdminDocumentParseProviderDescriptorController（3 个）

| # | 类名 | 类型 | 字段数 | 敏感字段 | 处置 |
|---|---|---|---|---|---|
| 3 | `AdminDocumentParseProviderDescriptorListResponse` | Response | 2 | — | @Data→@Getter |
| 4 | `AdminDocumentParseProviderDescriptorResponse` | Response | 7 | — | @Data→@Getter |
| 5 | `AdminDocumentParseProviderFieldResponse` | Response | 7 | — | @Data→@Getter |

---

## 五、每个类的详细分析与 Javadoc 建议

### 5.1 B11b1 — Connection DTO

#### AdminDocumentParseConnectionRequest（Request）⛔ 含 credentialJson

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Spring 绑定：`@RequestBody`，需无参构造 + setter
- **安全风险**：`credentialJson` 是 JSON 格式的 Provider 连接凭证（如 `{"apiKey":"...", "token":"..."}`）。@Data toString() 会将整个凭证 JSON 写入日志。Controller 使用 `LlmSecretCryptoService.encrypt()` 加密后存储，但加密前的明文会通过 toString() 暴露。
- **强制处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`，`credentialJson` 加 `@ToString.Exclude`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `connectionCode` | 连接编码；管理侧唯一标识 |
| `providerType` | 文档解析 Provider 类型（由 ProviderDescriptor 定义，如 `unstructured` / `llm_parser`） |
| `baseUrl` | Provider API 端点 URL；尾部斜杠会被自动去除 |
| `credentialJson` | **Provider 连接凭证（JSON 格式）**；提交后立即加密存储，禁止记录到日志；更新时为空表示沿用旧凭证；新增时必填 |
| `configJson` | 扩展配置 JSON；Provider 特有设置，可为空（默认 `{}`） |
| `enabled` | 是否启用；null 默认为 true |
| `operator` | 操作人 |

#### AdminDocumentParseConnectionResponse（Response）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 构造方式：`new AdminDocumentParseConnectionResponse(id, code, ..., credentialMask, credentialConfigured, configJson, enabled, createdBy, updatedBy, createdAt, updatedAt)`
- `credentialMask` 值为脱敏后的展示文案（如"已配置 JSON 凭证"）
- `credentialConfigured`（boolean）由 `StringUtils.hasText(credentialCiphertext)` 计算，标识是否已配置凭证
- `configJson` 是 API 返回的规范化配置 JSON
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `id` | 连接配置主键 |
| `connectionCode` | 连接编码 |
| `providerType` | Provider 类型 |
| `baseUrl` | API 端点 URL |
| `credentialMask` | 凭证脱敏展示（如"已配置 JSON 凭证"）；非实际凭证 |
| `credentialConfigured` | 是否已配置凭证；true 表示该连接有可用凭证 |
| `configJson` | 当前生效的扩展配置 JSON |
| `enabled` | 是否启用 |
| `createdBy` | 创建人 |
| `updatedBy` | 最后更新人 |
| `createdAt` | 创建时间（ISO 字符串） |
| `updatedAt` | 最后更新时间（ISO 字符串） |

#### AdminDocumentParseConnectionListResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段：`count`、`items`

#### AdminMutationResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段：`id`（受影响记录主键）、`status`（操作结果，如 `"deleted"`）
- 注意：这是 `AdminDocumentParseConnectionController` 内部的独立类，与 B11a 的 `AdminLlmConfigController.AdminMutationResponse` 是同名不同类

#### AdminDocumentParseConnectionTestRequest（Request）⛔ 含 credentialJson

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- **安全风险**：`credentialJson` 是临时探测用的 Provider 凭证。用于 `DocumentParseConnectionProbeService.probe()` 的一次性测试调用，不持久化。@Data toString() 会泄露临时凭证到日志。
- **强制处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`，`credentialJson` 加 `@ToString.Exclude`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `connectionId` | 已有连接配置主键；非空时优先使用已存储凭证和配置 |
| `providerType` | Provider 类型；用于选择探测适配器 |
| `baseUrl` | 探测目标端点 URL |
| `credentialJson` | **临时探测用凭证（JSON 格式）**；仅用于本次连接测试，不持久化；禁止记录到日志 |
| `configJson` | 临时探测用扩展配置 JSON |

#### AdminDocumentParseConnectionTestResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：`success`（测试是否成功）、`providerType`、`latencyMs`（延迟毫秒）、`endpoint`（探测端点）、`message`（结果描述，失败时含错误原因）

### 5.2 B11b2 — Policy + ProviderDescriptor DTO

#### AdminDocumentParsePolicyRequest（Request）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 校验：`validateRequest()` 仅检查非 null
- **处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `imageConnectionId` | 图片文档解析使用的连接配置主键 |
| `scannedPdfConnectionId` | 扫描 PDF 文档解析使用的连接配置主键 |
| `cleanupEnabled` | 是否启用文档预处理清理；true 时通过 LLM 清理文档噪声 |
| `cleanupModelProfileId` | 清理步骤使用的 LLM 模型配置主键；cleanupEnabled=false 时忽略 |
| `fallbackPolicyJson` | 降级路由策略 JSON；定义无法匹配 connectionId 时的兜底行为；可为空（默认 `{}`） |
| `operator` | 操作人 |

#### AdminDocumentParsePolicyResponse（Response）

- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：与 Request 对应字段相同，补充 `id`、`policyScope`（策略范围，固定为 `DEFAULT`）、`createdBy`/`updatedBy`/`createdAt`/`updatedAt`

#### AdminDocumentParseProviderDescriptorListResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段：`count`、`items`

#### AdminDocumentParseProviderDescriptorResponse（Response）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- 这是只读元数据响应，描述 Provider 的能力、凭证字段定义和配置字段定义
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `providerType` | Provider 类型标识（如 `unstructured` / `llm_parser`） |
| `displayName` | Provider 展示名称 |
| `defaultBaseUrl` | 默认 API 端点 URL；null 表示无默认值 |
| `probeMode` | 探测模式（如 `api_check` / `simple_http`） |
| `supportedCapabilities` | 支持的文档解析能力列表（ParseCapability 枚举名数组） |
| `credentialFields` | 凭证字段定义列表；前端据此动态生成凭证配置表单（每个字段含 fieldKey/label/inputType/required/defaultValue/placeholder/description） |
| `configFields` | 扩展配置字段定义列表；前端据此动态生成配置表单 |

#### AdminDocumentParseProviderFieldResponse（Response）

- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `fieldKey` | 字段键名 |
| `label` | 字段展示标签 |
| `inputType` | 输入控件类型（如 `text` / `password` / `number` / `select`） |
| `required` | 是否必填 |
| `defaultValue` | 默认值；null 表示无默认 |
| `placeholder` | 占位提示文案 |
| `description` | 字段说明描述 |

---

## 六、排除清单

| 排除项 | 理由 |
|---|---|
| 4 个 Controller 的路由方法、校验方法、映射方法（`toConnection`/`toConnectionResponse`/`toResponse`/`toFieldResponse` 等） | Controller 行为逻辑，禁止修改 |
| `DocumentParseConnectionAdminService`、`DocumentParseConnectionProbeService`、`DocumentParseRoutePolicyAdminService`、`DocumentParseProviderDescriptorService` | 服务层 |
| `ProviderConnection`、`ParseRoutePolicy`、`ProviderDescriptor`、`ProviderFieldDescriptor`、`ParseCapability` | domain 层 |
| `LlmSecretCryptoService` | 已归属 LLM domain |
| `ObjectMapper` 及 JSON 规范化方法 | 工具/基础设施 |
| B11a / B11c 的 controller | 各自批次 |

---

## 七、敏感字段与风险说明

### 7.1 最高风险：credentialJson 泄露

| 类 | 风险字段 | 当前状态 | 后果 |
|---|---|---|---|
| `AdminDocumentParseConnectionRequest` | `credentialJson` | `@Data` toString() 包含明文凭证 JSON | 日志泄露 Provider 连接凭证（可能含 apiKey/token/password） |
| `AdminDocumentParseConnectionTestRequest` | `credentialJson` | 同上 | 同上，且测试接口调用频率可能更高 |

### 7.2 中等风险

| 字段 | 所属类 | 风险 |
|---|---|---|
| `configJson` | ConnectionRequest/Response, ConnectionTestRequest | Provider 特有配置，可能含内部端点地址或敏感参数 |
| `fallbackPolicyJson` | PolicyRequest/Response | 降级路由规则，非凭证但定义了文档解析的兜底行为 |
| `baseUrl` | ConnectionRequest, ConnectionTestRequest | Provider 端点 URL，可能含内部地址 |

### 7.3 低风险

`credentialMask`（脱敏后文案）、`credentialConfigured`（布尔标识）、所有 ID/编码/审计字段、`providerType`/`displayName`/`defaultBaseUrl`/`probeMode`/`supportedCapabilities`/`credentialFields`/`configFields` — 均为配置元数据或脱敏信息。

### 7.4 boolean getter 命名

所有 boolean 字段（`enabled`、`credentialConfigured`、`cleanupEnabled`、`success`、`required`）均使用标准 `isXxx()` 命名，Lombok `@Getter` 生成一致，无 B8a 式问题。

---

## 八、给 agentA 的下一轮提示词草案（B11b1）

```
交给 agentA。

本轮任务：对 B11b1 的 2 个 document parse 连接/测试 Controller 中 6 个内部 DTO 做 @Data 降级 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_document_parse_controller_internal_dto_contract_analysis_report.md

## 修改范围（2 个文件，6 个内部 static class）

### AdminDocumentParseConnectionController.java（4 个内部类）

1. AdminDocumentParseConnectionRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - credentialJson 字段加 @ToString.Exclude
   - 7 字段补 Javadoc（审查报告 5.1 节），credentialJson 标注"加密存储，禁止日志"

2. AdminDocumentParseConnectionResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 12 字段补 Javadoc，credentialMask 标注"非实际凭证"

3. AdminDocumentParseConnectionListResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor；2 字段补 Javadoc

4. AdminMutationResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor；2 字段补 Javadoc

### AdminDocumentParseConnectionTestController.java（2 个内部类）

5. AdminDocumentParseConnectionTestRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - credentialJson 字段加 @ToString.Exclude
   - 5 字段补 Javadoc，credentialJson 标注"仅用于临时测试，不持久化，禁止日志"

6. AdminDocumentParseConnectionTestResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor；5 字段补 Javadoc

## 禁止事项

- 禁止修改 Controller 的 testConnection()/listConnections()/createConnection()/updateConnection()/deleteConnection() 方法
- 禁止修改 validateRequest()/toConnection()/toConnectionResponse()/resolveXxx()/normalizeBaseUrl()/normalizeJsonObject() 等私有/映射方法
- 禁止修改字段类型或名称
- 禁止删除任何 @NoArgsConstructor 或 @AllArgsConstructor
- Response 不加 @Setter

## 完成后：回写 B11b1 → "已完成"，输出 B11b1_fix_result_report.md
```

---

## 九、给 agentA 的下一轮提示词草案（B11b2）

```
交给 agentA。

本轮任务：对 B11b2 的 Policy + ProviderDescriptor 2 个 Controller 中 5 个内部 DTO 做 @Data 降级 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_document_parse_controller_internal_dto_contract_analysis_report.md

## 修改范围（2 个文件，5 个内部 static class）

### AdminDocumentParsePolicyController.java（2 个内部类）

1. AdminDocumentParsePolicyRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 6 字段补 Javadoc（审查报告 5.2 节）

2. AdminDocumentParsePolicyResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 11 字段补 Javadoc

### AdminDocumentParseProviderDescriptorController.java（3 个内部类）

3. AdminDocumentParseProviderDescriptorListResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor；2 字段补 Javadoc

4. AdminDocumentParseProviderDescriptorResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 7 字段补 Javadoc（credentialFields/configFields 语义需说明动态表单生成）

5. AdminDocumentParseProviderFieldResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 7 字段补 Javadoc（fieldKey/label/inputType/required/defaultValue/placeholder/description 动态表单字段定义）

## 禁止事项

- 禁止修改 Controller 的方法（getDefaultPolicy/updateDefaultPolicy/validateRequest/listProviders/toResponse/toFieldResponse 等）
- 禁止修改字段类型或名称
- Response 不加 @Setter
- 禁止混入 B11b1

## 完成后：回写 B11b2 → "已完成"，输出 B11b2_fix_result_report.md
```

---

## 十、审查结论

- B11b 共 11 个内部 DTO，拆分为 **B11b1（6 个连接/测试 DTO）+ B11b2（5 个策略/Provider 元数据 DTO）**。
- **安全漏洞**：全部 11 个类使用 `@Data`，其中 **2 个类含 `credentialJson`**（`AdminDocumentParseConnectionRequest`、`AdminDocumentParseConnectionTestRequest`）。@Data toString() 会在日志中泄露 Provider 连接凭证 JSON。必须在 agentA 轮次修复。
- `AdminDocumentParseConnectionController.AdminMutationResponse` 与 `AdminLlmConfigController.AdminMutationResponse` 是同名不同类，B11a 的处理不影响这里。
- 无计算 getter、无 boolean 命名不一致、无防御性拷贝 — 降级改造纯机械操作。
- 全部 11 个类的字段均无 Javadoc，需按契约标准补齐。
- `AdminDocumentParseProviderDescriptorResponse` 和 `AdminDocumentParseProviderFieldResponse` 是只读元数据，描述能力、凭证表单字段、配置表单字段，无凭证泄露风险。
