# B14 DocumentParse Domain 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B14 — `documentparse/domain`（10 个类，2 枚举 + 8 不可变领域对象）

---

## 一、拆分建议：B14a + B14b

10 个类恰好在上限，按职责域拆分：

| 子批次 | 候选数 | 范围 | 职责 |
|---|---|---|---|
| **B14a** | **5** | Parse 管道对象 | 解析输入→输出→结果 + 模式/能力枚举 |
| **B14b** | **5** | Provider/Route 对象 | 连接配置、路由策略、Provider 元数据、探测结果 |

---

## 二、全局发现：0 个 Lombok，全部干净

- **全部 10 个类 0 个 Lombok**，无 @Data，无待降级项
- **全部 8 个领域类为不可变 final-field 模型**，手写构造器 + 手写 getter
- 所有 69 个 getter 均为简单字段访问，可安全用 `@Getter` 替代
- 所有 boolean getter 使用标准 `isXxx()` 命名，与 Lombok 一致
- **无计算 getter 需要保留**（ParseOutput 的业务方法 `hasResolvedContent()`/`resolveContent()`/`resolveContentFormat()` 不是 getter，不在替换范围）

---

## 三、B14a — Parse 管道对象（5 个）

| # | 类 | 类型 | 字段/Getter | 特殊方法 | 处置 |
|---|---|---|---|---|---|
| 1 | `DocumentParseMode` | Enum | 5 值，各含 `code` | `getCode()` | 枚举值 Javadoc |
| 2 | `ParseCapability` | Enum | 2 值 | — | 枚举值 Javadoc |
| 3 | `DocumentParseResult` | 不可变 | 10 getter | — | @Getter + Javadoc |
| 4 | `ParseOutput` | 不可变 | 12 getter | `hasResolvedContent()`/`resolveContent()`/`resolveContentFormat()`/`hasText()`（4 个业务方法） | @Getter + Javadoc，保留业务方法 |
| 5 | `ParseRequest` | 不可变 | 5 getter（含 `java.nio.file.Path` 类型） | — | @Getter + Javadoc |

### 3.1 每类详细分析

#### DocumentParseMode（Enum）
- 5 个枚举值：TEXT_READ（纯文本读取）、OFFICE_EXTRACT（Office 文档提取）、PDF_TEXT（PDF 文本提取）、OCR_IMAGE（图片 OCR）、OCR_SCANNED_PDF（扫描 PDF OCR）
- 每个值有 `code` 字段（如 `"text_read"`）
- 需补：每个枚举值的文档格式覆盖范围和下游编译消费路径

#### ParseCapability（Enum）
- 2 个值：IMAGE_OCR、SCANNED_PDF_OCR
- 约束 Provider 能承接的解析能力类型

#### DocumentParseResult（不可变）
- 解析层输出给标准化器的统一结果。10 字段
- `extractedText` 可能为大型文本
- `parseMode` 枚举类型字段，标注解析模式对下游编译的影响
- `verbatim`（boolean，`isVerbatim()` → Lombok 一致）

#### ParseOutput（不可变）⚠️ 含业务方法
- 解析编排层输出，12 字段
- `plainText`、`markdown`、`structuredContentJson` 可能为大型内容
- **4 个业务方法必须保留**：
  - `hasResolvedContent()` — 判断是否有可用正文（非 getter，命名不同）
  - `resolveContent()` — 优先返回 plainText，其次 markdown
  - `resolveContentFormat()` — 返回 `"plain_text"` / `"markdown"` / `"empty"`
  - `hasText()` — private 工具方法

#### ParseRequest（不可变）
- 封装文件进入解析编排层的统一上下文，5 字段
- 含 `java.nio.file.Path` 类型（`workspaceRoot`、`filePath`）
- 无业务方法，纯数据载体

---

## 四、B14b — Provider/Route 对象（5 个）

| # | 类 | 类型 | 字段/Getter | 特殊方法/常量 | 敏感字段 | 处置 |
|---|---|---|---|---|---|---|
| 1 | `ParseRoutePolicy` | 不可变 | 11 getter | `defaultPolicy()` + `DEFAULT_SCOPE` | `fallbackPolicyJson` | @Getter + Javadoc |
| 2 | `ProviderConnection` | 不可变 | 12 getter | 4 `PROVIDER_*` 常量 | **`credentialCiphertext`**、`credentialMask` | @Getter + Javadoc |
| 3 | `ProviderDescriptor` | 不可变 | 7 getter | — | — | @Getter + Javadoc |
| 4 | `ProviderFieldDescriptor` | 不可变 | 7 getter | — | — | @Getter + Javadoc |
| 5 | `ProviderProbeResult` | 不可变 | 5 getter | — | — | @Getter + Javadoc |

### 4.1 每类详细分析

#### ParseRoutePolicy（不可变）
- 路由策略定义：图片 OCR 连接、扫描 PDF OCR 连接、后整理开关/模型、降级策略
- `DEFAULT_SCOPE = "default"` 常量 + `defaultPolicy()` static factory
- `fallbackPolicyJson` 为降级路由规则 JSON

#### ProviderConnection（不可变）⛔ 含凭证字段
- 单条 Provider 连接配置，12 字段
- **4 个 public static final 常量**：PROVIDER_TENCENT_OCR / PROVIDER_ALIYUN_OCR / PROVIDER_GOOGLE_DOCUMENT_AI / PROVIDER_TEXTIN_XPARSE
- **`credentialCiphertext`**：加密后的 Provider 凭证（密文，非明文）。Javadoc 需标注"已加密存储，非明文凭证"。虽然不是明文，但仍是敏感数据，不应参与 toString()
- **`credentialMask`**：凭证脱敏展示值。Javadoc 需标注"仅用于管理侧展示，非完整凭证"
- `configJson`：Provider 配置 JSON，可能较大

#### ProviderDescriptor（不可变）
- Provider 元数据描述，含 `supportedCapabilities`（`Set<ParseCapability>`）、`credentialFields`/`configFields`（`List<ProviderFieldDescriptor>`）
- 用于后台动态表单生成
- `probeMode`：连接探测模式

#### ProviderFieldDescriptor（不可变）
- 动态表单字段定义：fieldKey、label、inputType、required、defaultValue、placeholder、description
- 纯元数据，无敏感信息

#### ProviderProbeResult（不可变）
- 连接探测统一结果：success、latencyMs、endpoint、message
- `message` 失败时含错误原因

---

## 五、排除清单

| 排除 | 理由 |
|---|---|
| `documentparse/service/*` | 服务层 |
| `documentparse/adapter/*` | 适配器层 |
| `infra/persistence/*` | 持久层 |
| B15 source/domain | 下批次 |
| B16 llm/domain | 下批次 |

---

## 六、敏感字段与风险

| 字段 | 所属类 | 风险等级 | 说明 |
|---|---|---|---|
| `credentialCiphertext` | ProviderConnection | **中** | 加密后的凭证密文；虽非明文但属敏感数据，Javadoc 需标注"已加密" |
| `credentialMask` | ProviderConnection | 低 | 脱敏展示值，非完整凭证 |
| `configJson` | ProviderConnection | 低 | Provider 配置，可能较大 |
| `fallbackPolicyJson` | ParseRoutePolicy | 低 | 降级路由规则 JSON |
| `extractedText` | DocumentParseResult | 低 | 大型文本，不应参与 toString() |
| `plainText` / `markdown` | ParseOutput | 低 | 大型文本 |
| `structuredContentJson` | ParseOutput | 低 | 大型 JSON |

**当前无 toString 泄露风险**：全部 10 个类无 @Data，无自定义 toString()，默认 Object.toString() 仅输出类名+hashCode，不泄露字段值。

---

## 七、给 agentA 的下一轮提示词草案（B14a）

```
交给 agentA。

本轮任务：对 B14a 的 5 个 parse 管道对象做 @Getter + 领域语义 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/documentparse_domain_contract_analysis_report.md

## 修改范围（5 个文件）

### 枚举（2 个）

1. DocumentParseMode.java
   - 5 枚举值补 Javadoc：TEXT_READ（纯文本读取）/OFFICE_EXTRACT（Office 提取）/PDF_TEXT（PDF 文本）/OCR_IMAGE（图片 OCR）/OCR_SCANNED_PDF（扫描 PDF OCR）
   - 标注每个值覆盖的文档格式和解析行为

2. ParseCapability.java
   - 2 枚举值补 Javadoc：IMAGE_OCR / SCANNED_PDF_OCR

### 不可变领域对象（3 个，加 @Getter + 字段 Javadoc）

3. DocumentParseResult.java
   - 类级 @Getter，删除 10 手写 getter
   - 保留全参构造器
   - 10 字段 Javadoc：parseMode 标注枚举驱动解析行为，extractedText 标注可能为大型文本

4. ParseOutput.java ⚠️
   - 类级 @Getter，删除 12 手写 getter
   - 保留全参构造器
   - **保留 4 个业务方法**：hasResolvedContent() / resolveContent() / resolveContentFormat() / hasText()
   - 12 字段 Javadoc：plainText/markdown/structuredContentJson 标注大文本

5. ParseRequest.java
   - 类级 @Getter，删除 5 手写 getter
   - 保留全参构造器
   - 5 字段 Javadoc：workspaceRoot/filePath 标注 java.nio.file.Path 类型

## 禁止事项
- 禁止修改 ParseOutput 的 4 个业务方法
- 禁止修改构造器
- 禁止修改字段类型（含 Path 类型）
- 禁止引入 @Data/@Setter

## 完成后：回写 B14a → "已完成"，输出 B14a_fix_result_report.md
```

---

## 八、给 agentA 的下一轮提示词草案（B14b）

```
交给 agentA。

本轮任务：对 B14b 的 5 个 Provider/Route 领域对象做 @Getter + 领域语义 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/documentparse_domain_contract_analysis_report.md

## 修改范围（5 个文件，全部不可变）

1. ParseRoutePolicy.java
   - 类级 @Getter，删除 11 手写 getter
   - 保留 DEFAULT_SCOPE 常量和 defaultPolicy() static factory
   - 11 字段 Javadoc：fallbackPolicyJson 标注降级路由规则

2. ProviderConnection.java ⚠️ 含凭证字段
   - 类级 @Getter，删除 12 手写 getter
   - 保留 4 个 PROVIDER_* 常量
   - 12 字段 Javadoc：**credentialCiphertext 标注"已加密存储，非明文凭证"**；credentialMask 标注"仅管理侧展示，非完整凭证"；configJson 标注可能较大

3. ProviderDescriptor.java
   - 类级 @Getter，删除 7 手写 getter
   - 7 字段 Javadoc：supportedCapabilities 标注 Set<ParseCapability> 类型

4. ProviderFieldDescriptor.java
   - 类级 @Getter，删除 7 手写 getter
   - 7 字段 Javadoc：fieldKey/label/inputType/required/defaultValue/placeholder/description 动态表单字段定义

5. ProviderProbeResult.java
   - 类级 @Getter，删除 5 手写 getter
   - 5 字段 Javadoc：message 标注失败时含错误原因

## 禁止事项
- 禁止修改构造器
- 禁止修改 static 常量和 factory 方法
- 禁止修改字段类型
- 禁止引入 @Data/@Setter

## 完成后：回写 B14b → "已完成"，输出 B14b_fix_result_report.md
```

---

## 九、审查结论

- B14 共 10 个类，拆分为 **B14a（5 个 parse 管道对象）+ B14b（5 个 Provider/Route 对象）**。
- **0 个 Lombok**：B14 是最干净的批次之一，无任何 @Data 需要降级。
- 全部 8 个领域类为不可变 final-field 模型，可安全加 `@Getter` 删除 69 个手写 getter。
- **ParseOutput 需特别处理**：4 个业务方法（hasResolvedContent/resolveContent/resolveContentFormat/hasText）不可被 Lombok 覆盖或删除。
- **ProviderConnection 含凭证字段**：`credentialCiphertext`（密文）和 `credentialMask`（脱敏值）需在 Javadoc 中标注安全语义。
- 全部 boolean getter 使用标准 `isXxx()` 命名，与 Lombok 完全一致。
- 无计算 getter、无防御性拷贝在 getter 中、无 equals/hashCode 自定义。
