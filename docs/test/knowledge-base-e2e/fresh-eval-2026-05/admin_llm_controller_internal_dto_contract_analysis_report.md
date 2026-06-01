# B11a LLM Controller 内部 DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B11a — controller 内部 DTO（`AdminLlmConfigController`、`AdminLlmConnectionTestController`、`AdminLlmModelTestController`）

---

## 一、拆分建议：B11a → B11a1 + B11a2

14 个候选类超过 10 个上限，按控制器文件自然拆分：

| 子批次 | 候选数 | 范围 | 拆分理由 |
|---|---|---|---|
| **B11a1** | **10** | `AdminLlmConfigController` 全部内部 DTO | 连接/模型/绑定的 CRUD 配置 DTO，单一控制器文件内聚 |
| **B11a2** | **4** | `AdminLlmConnectionTestController`(2) + `AdminLlmModelTestController`(2) | 连接/模型的即时测试 DTO，独立于配置管理 |

---

## 二、全局发现：@Data 全量污染

**全部 14 个内部 DTO 均使用 `@Data` `@NoArgsConstructor` `@AllArgsConstructor`**，是所有已完成批次中 @Data 密度最高的（100%）。其中 **2 个类包含明文 `apiKey` 字段**，@Data 生成的 `toString()` 会在任何日志输出中泄露 API 密钥。

---

## 三、B11a1 纳入文件清单（AdminLlmConfigController，10 个类）

| # | 类名 | 类型 | 字段数 | 敏感字段 | 处置 |
|---|---|---|---|---|---|
| 1 | `AdminLlmConnectionRequest` | Request | 7 | **`apiKey`（明文）** | @Data→@Getter/@Setter，apiKey 加 @ToString.Exclude 或全降级 |
| 2 | `AdminLlmConnectionResponse` | Response | 11 | `apiKeyMask`（脱敏后） | @Data→@Getter |
| 3 | `AdminLlmConnectionListResponse` | Response | 2 | — | @Data→@Getter |
| 4 | `AdminLlmModelRequest` | Request | 15 | `extraOptionsJson`（可能含配置敏感信息） | @Data→@Getter/@Setter |
| 5 | `AdminLlmModelResponse` | Response | 20 | `extraOptionsJson` | @Data→@Getter |
| 6 | `AdminLlmModelListResponse` | Response | 2 | — | @Data→@Getter |
| 7 | `AdminLlmBindingRequest` | Request | 8 | — | @Data→@Getter/@Setter |
| 8 | `AdminLlmBindingResponse` | Response | 12 | — | @Data→@Getter |
| 9 | `AdminLlmBindingListResponse` | Response | 2 | — | @Data→@Getter |
| 10 | `AdminMutationResponse` | Response | 2 | — | @Data→@Getter |

### 3.1 每个类的详细分析

#### AdminLlmConnectionRequest（Request）⛔ 含明文 apiKey

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Spring 绑定：`@RequestBody`，需无参构造 + setter
- **安全风险**：`apiKey` 是明文 LLM Provider API 密钥。@Data 生成的 `toString()` 会输出 `AdminLlmConnectionRequest(connectionCode=..., providerType=..., baseUrl=..., apiKey=sk-xxxxxx, ...)`。如果任何日志框架（Logback、SLF4J）或调试断点触发 `toString()`，密钥将以明文写入日志文件。
- Controller 使用方式：`request.getApiKey()` 传给 `LlmSecretCryptoService.encrypt()` 加密后存储。密钥仅在内存中短暂存在，但 toString() 可将其持久化到日志。
- **强制处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`。对 `apiKey` 字段额外加 `@ToString.Exclude`（即使移除 @Data 后 Lombok 不再生成 toString()，加此注解作为防御性文档标记和安全网）。
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `connectionCode` | 连接编码；管理侧唯一标识，用于模型配置引用 |
| `providerType` | LLM Provider 类型（如 `openai` / `anthropic` / `local`） |
| `baseUrl` | Provider API 端点 URL；为空时使用该 provider 的默认端点 |
| `apiKey` | **Provider API 密钥（明文）**；提交后立即加密存储，禁止记录到日志；更新时为空表示沿用旧密钥 |
| `enabled` | 是否启用；false 时使用该连接的所有模型不可用 |
| `remarks` | 备注说明；可选 |
| `operator` | 操作人标识；为空时默认 `"admin"` |

#### AdminLlmConnectionResponse（Response）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 构造方式：`new AdminLlmConnectionResponse(id, code, ..., apiKeyMask, ...)` 使用全参构造器
- `apiKeyMask` 是脱敏后的密钥展示（如 `sk-****xxxx`），非原始密钥，但仍不应出现在 toString() 中
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`。Response 不需要 @Setter（仅通过构造器创建）。
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `id` | 连接配置主键 |
| `connectionCode` | 连接编码 |
| `providerType` | Provider 类型 |
| `baseUrl` | API 端点 URL |
| `apiKeyMask` | API 密钥脱敏展示（如 `sk-****xxxx`）；非完整密钥 |
| `enabled` | 是否启用 |
| `remarks` | 备注 |
| `createdBy` | 创建人 |
| `updatedBy` | 最后更新人 |
| `createdAt` | 创建时间（ISO 字符串） |
| `updatedAt` | 最后更新时间（ISO 字符串） |

#### AdminLlmConnectionListResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段：`count`（连接数）、`items`（连接列表）

#### AdminLlmModelRequest（Request）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 校验逻辑：`validateModelRequest()` 检查 connectionId、modelName、timeoutSeconds、expectedDimensions 与 modelKind 的互斥约束
- `extraOptionsJson` 可能包含 provider 特有配置，有一定敏感性
- **处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `modelCode` | 模型编码；为空时由 controller 根据 modelName/modelKind/connectionId 自动生成 |
| `connectionId` | 关联连接配置主键 |
| `modelName` | Provider 模型名（如 `gpt-4` / `claude-sonnet-4-6`） |
| `modelKind` | 模型类别（`CHAT` / `EMBEDDING`）；为空默认 `CHAT`；决定 expectedDimensions 校验规则 |
| `expectedDimensions` | 期望向量维度；仅 `EMBEDDING` 模型必填，`CHAT` 模型禁止填写 |
| `supportsDimensionOverride` | 是否支持维度覆写；仅部分 embedding 模型支持 |
| `temperature` | 温度参数；控制生成随机性 |
| `maxTokens` | 最大输出 token 数 |
| `timeoutSeconds` | 请求超时秒数 |
| `inputPricePer1kTokens` | 输入价格（每千 token） |
| `outputPricePer1kTokens` | 输出价格（每千 token） |
| `extraOptionsJson` | 扩展选项 JSON；Provider 特有配置，可能含额外参数 |
| `enabled` | 是否启用 |
| `remarks` | 备注 |
| `operator` | 操作人 |

#### AdminLlmModelResponse（Response）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：与 Request 对应字段相同，补充 ID/审计字段。JSON 序列化时 `extraOptionsJson` 会原样输出，注意不包含密钥信息。

#### AdminLlmModelListResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`

#### AdminLlmBindingRequest（Request）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 校验：`validateBindingRequest()` 检查 scene、agentRole（与 SCENE_ROLE_OPTIONS 匹配）、primaryModelProfileId
- **处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `scene` | 场景标识（`compile` / `query` / `deep_research`）；决定可选 agentRole 范围 |
| `agentRole` | Agent 角色（如 `writer` / `answer` / `planner`）；必须属于 scene 的有效角色集合 |
| `primaryModelProfileId` | 主模型配置主键 |
| `fallbackModelProfileId` | 降级模型配置主键；主模型不可用时自动切换 |
| `routeLabel` | 路由标签；为空时由 controller 根据 scene.agentRole.modelCode 自动生成 |
| `enabled` | 是否启用 |
| `remarks` | 备注 |
| `operator` | 操作人 |

#### AdminLlmBindingResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：与 Request 对应 + ID/审计字段

#### AdminLlmBindingListResponse（Response）
- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`

#### AdminMutationResponse（Response）
- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- 用于 deleteConnection/deleteModel/deleteBinding 的返回
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：`id`（受影响记录主键）、`status`（操作结果，如 `"deleted"`）

---

## 四、B11a2 纳入文件清单（2 个 Controller，4 个类）

| # | 类名 | 所属 Controller | 类型 | 字段数 | 敏感字段 | 处置 |
|---|---|---|---|---|---|---|
| 1 | `AdminLlmConnectionTestRequest` | ConnectionTest | Request | 4 | **`apiKey`（明文）** | @Data→@Getter/@Setter，apiKey 加 @ToString.Exclude |
| 2 | `AdminLlmConnectionTestResponse` | ConnectionTest | Response | 5 | — | @Data→@Getter |
| 3 | `AdminLlmModelTestRequest` | ModelTest | Request | 6 | — | @Data→@Getter/@Setter |
| 4 | `AdminLlmModelTestResponse` | ModelTest | Response | 5 | — | @Data→@Getter |

### 4.1 每个类的详细分析

#### AdminLlmConnectionTestRequest（Request）⛔ 含明文 apiKey

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- **安全风险**：与 `AdminLlmConnectionRequest` 相同，`apiKey` 是明文密钥。@Data toString() 会将密钥泄露到日志。
- **强制处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`，`apiKey` 加 `@ToString.Exclude`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `connectionId` | 已有连接配置主键；非空时优先使用已存储密钥 |
| `providerType` | Provider 类型；用于选择探测适配器 |
| `baseUrl` | 探测目标端点 URL |
| `apiKey` | **临时探测用 API 密钥（明文）**；仅用于本次连接测试，不持久化；禁止记录到日志 |

#### AdminLlmConnectionTestResponse（Response）

- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：
  - `success` — 连接测试是否成功
  - `providerType` — 探测到的 Provider 类型
  - `latencyMs` — 连接延迟（毫秒）；null 表示测试失败未获得延迟
  - `endpoint` — 实际探测的端点 URL
  - `message` — 测试结果描述；失败时含错误原因

#### AdminLlmModelTestRequest（Request）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- 无敏感字段（modelId/connectionId 引用已有配置，不含密钥）
- **处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：
  - `modelId` — 已有模型配置主键
  - `connectionId` — 关联连接配置主键；用于获取 apiKey
  - `modelName` — 模型名
  - `modelKind` — 模型类别
  - `expectedDimensions` — 期望维度；仅 embedding 模型使用
  - `timeoutSeconds` — 探测超时秒数

#### AdminLlmModelTestResponse（Response）

- 替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：
  - `success` — 模型测试是否成功
  - `providerType` — Provider 类型
  - `modelKind` — 模型类别
  - `latencyMs` — 调用延迟（毫秒）
  - `message` — 测试结果描述

---

## 五、明确排除清单

| 排除项 | 理由 |
|---|---|
| 三个 Controller 的路由方法、校验方法、映射方法（`toConnection`/`toModelProfile`/`toBinding`/`toXxxResponse` 等） | 禁止修改 controller 行为逻辑 |
| `LlmConfigAdminService`、`LlmSecretCryptoService`、`LlmConnectionProbeService`、`LlmModelProbeService` | 服务层，不属于 DTO 治理 |
| `LlmProviderConnection`、`LlmModelProfile`、`AgentModelBinding` | domain 层，归属 B16 |
| `SCENE_ROLE_OPTIONS` 静态配置 | controller 内部常量，不改 |
| B11b/B11c 的 controller 内部 DTO | 各自批次 |

---

## 六、字段风险与运行影响说明

### 6.1 最高风险：apiKey 泄露

| 类 | 风险字段 | 当前状态 | 后果 |
|---|---|---|---|
| `AdminLlmConnectionRequest` | `apiKey` | `@Data` 生成 toString() 包含明文密钥 | 任何日志或调试输出会泄露 LLM Provider API Key |
| `AdminLlmConnectionTestRequest` | `apiKey` | 同上 | 同上，且测试接口调用频率可能更高 |

**影响范围**：apiKey 泄露意味着攻击者可利用日志文件中的密钥调用 LLM API，产生费用和数据泄露。这是本批次**必须修复**的安全问题。

### 6.2 中等风险字段

| 字段 | 所属类 | 风险 |
|---|---|---|
| `apiKeyMask` | ConnectionResponse | 脱敏后密钥，仍不应出现在 toString() 中 |
| `extraOptionsJson` | ModelRequest/Response | 可能含 provider 特有配置参数 |
| `baseUrl` | ConnectionRequest, ConnectionTestRequest | Provider 端点 URL，可能含内部网络路径 |

### 6.3 低风险/纯配置字段

所有 `connectionCode`、`providerType`、`modelCode`、`modelName`、`modelKind`、`scene`、`agentRole`、`routeLabel`、温度/token/超时/价格参数、`enabled`、`remarks`、`operator`、审计字段 — 均为普通配置参数，不涉及密钥。

### 6.4 boolean getter 命名

所有 boolean 字段均使用标准命名（`enabled`→`isEnabled()`、`success`→`isSuccess()`、`supportsDimensionOverride`→`isSupportsDimensionOverride()`），Lombok `@Getter` 生成一致，无 B8a 式问题。

---

## 七、禁止事项汇总

本轮及后续 agentA 改造时的绝对禁止项：

1. **禁止修改 Controller 行为**：路由方法、校验方法（`validateConnectionRequest`/`validateModelRequest`/`validateBindingRequest`）、映射方法（`toConnection`/`toModelProfile`/`toBinding`/`toConnectionResponse`/`toModelResponse`/`toBindingResponse`）、工具方法（`resolveOperator`/`normalizeExtraOptions`/`resolveModelCode`/`resolveRouteLabel`/`slugify`/`truncate`/`requireApiKey`/`normalizeModelKind`/`normalizeScene`/`normalizeAgentRole`/`resolveSceneRoles`）
2. **禁止修改 `SCENE_ROLE_OPTIONS`** 静态配置内容
3. **禁止修改 LLM 调用逻辑、加密逻辑、探测逻辑**
4. **禁止修改字段类型、名称**（即使 apiKey 也仅加注解，不改类型）
5. **禁止删除任何构造器**（保持 Spring 绑定兼容性）
6. **禁止混入 B11b/B11c 的 DTO**

---

## 八、给 agentA 的下一轮提示词草案（B11a1）

```
交给 agentA。

本轮任务：对 B11a1 的 AdminLlmConfigController 中 10 个内部 DTO 做 @Data 降级 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_llm_controller_internal_dto_contract_analysis_report.md

## 修改范围（1 个文件，10 个内部 static class）

文件：src/main/java/com/xbk/lattice/api/admin/AdminLlmConfigController.java

### 含 apiKey 敏感字段的 Request

1. AdminLlmConnectionRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - apiKey 字段加 @ToString.Exclude（防御性标注）
   - 7 字段补 Javadoc（审查报告 3.1 节）
   - apiKey 注释中明确标注"禁止记录到日志"

### 其他 Request

2. AdminLlmModelRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 15 字段补 Javadoc

3. AdminLlmBindingRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 8 字段补 Javadoc

### Response（替换 @Data 为 @Getter，移除 @Setter）

4. AdminLlmConnectionResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 11 字段补 Javadoc，apiKeyMask 标注"非完整密钥"

5. AdminLlmConnectionListResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 2 字段补 Javadoc

6. AdminLlmModelResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 20 字段补 Javadoc

7. AdminLlmModelListResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 2 字段补 Javadoc

8. AdminLlmBindingResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 12 字段补 Javadoc

9. AdminLlmBindingListResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 2 字段补 Javadoc

10. AdminMutationResponse
    - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
    - 2 字段补 Javadoc

## 禁止事项

- 禁止修改 Controller 的任何方法（路由、校验、映射、工具方法）
- 禁止修改 SCENE_ROLE_OPTIONS
- 禁止修改字段类型或名称
- 禁止删除任何 @NoArgsConstructor 或 @AllArgsConstructor
- 注意：Response 不要加 @Setter（内部 static Response 仅通过构造器创建）
- 禁止混入 B11a2 或其他 controller

## 完成后：回写 B11a1 → "已完成"，输出 B11a1_fix_result_report.md
```

---

## 九、给 agentA 的下一轮提示词草案（B11a2）

```
交给 agentA。

本轮任务：对 B11a2 的 2 个测试 Controller 中 4 个内部 DTO 做 @Data 降级 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_llm_controller_internal_dto_contract_analysis_report.md

## 修改范围（2 个文件，4 个内部 static class）

### AdminLlmConnectionTestController.java

1. AdminLlmConnectionTestRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - apiKey 字段加 @ToString.Exclude
   - 4 字段补 Javadoc（审查报告 4.1 节），apiKey 标注"仅用于临时测试，不持久化，禁止记录到日志"

2. AdminLlmConnectionTestResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 5 字段补 Javadoc

### AdminLlmModelTestController.java

3. AdminLlmModelTestRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 6 字段补 Javadoc

4. AdminLlmModelTestResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 5 字段补 Javadoc

## 禁止事项：同 B11a1。禁止修改 Controller 的 testConnection()/testModel() 方法及服务调用逻辑。

## 完成后：回写 B11a2 → "已完成"，输出 B11a2_fix_result_report.md
```

---

## 十、审查结论

- B11a 共 14 个内部 DTO，拆分为 **B11a1（AdminLlmConfigController 内 10 个）** + **B11a2（ConnectionTest + ModelTest 内 4 个）**。
- **最高严重度发现**：全部 14 个类均使用 `@Data`，其中 **2 个类含明文 `apiKey`**（`AdminLlmConnectionRequest`、`AdminLlmConnectionTestRequest`），@Data toString() 会在日志中泄露 LLM Provider 密钥。这是安全漏洞，必须在 agentA 轮次修复。
- 所有 boolean getter 命名标准，无 Lombok 不一致问题。
- 所有类需做 **@Data→@Getter 降级**（Response）或 **@Data→@Getter/@Setter 降级**（Request），保留 @NoArgsConstructor/@AllArgsConstructor。
- 无计算 getter、无防御性拷贝、无双构造器 — 降级改造纯机械操作，风险极低。
- 全部 14 个类的字段均无 Javadoc（仅类级有简短中文描述），需按契约标准补齐。
