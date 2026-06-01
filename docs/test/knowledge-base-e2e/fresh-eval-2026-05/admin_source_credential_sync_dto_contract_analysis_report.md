# B5a: api/admin Source / Credential / Sync DTO 边界审查报告

审查时间：2026-06-01
审查人：agentB（治理/链路分析 Agent）
审查对象：`docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` B5a 批次
审查类型：只读边界审查，未修改任何生产代码

---

## 1. B5a 纳入文件清单（精确）

| # | 文件名 | 类型 | 字段数 | 当前 Lombok | 手写 Getter | 手写 Setter | 构造器 | 敏感字段 |
|---|---|---|---|---|---|---|---|---|
| 1 | `AdminSourceCreateRequest.java` | **Request** (可变) | 8 | 无 | 8 | 8 | 无（默认无参） | 无 |
| 2 | `AdminSourceCredentialRequest.java` | **Request** (可变) | 4 | 无 | 4 | 4 | 无（默认无参） | **`secret`** (凭据明文) |
| 3 | `AdminSourceCredentialResponse.java` | **Response** (不可变) | 6 | 无 | 6 | 0 | 1 个手写全参 | `secretMask` (脱敏值，低风险) |
| 4 | `AdminSourceFileResponse.java` | **Response** (不可变) | 8 | 无 | 8 | 0 | 1 个手写全参 | 无 |
| 5 | `AdminSourceRunConfirmRequest.java` | **Request** (可变) | 2 | 无 | 2 | 2 | 无（默认无参） | 无 |
| 6 | `AdminSourceValidationResponse.java` | **Response** (不可变) | 6 | 无 | 6 | 0 | 1 个手写全参 | 无 |

**合计：6 个类，34 个字段，34 个手写 getter，14 个手写 setter。均在 5-10 个类的批次上限内。**

---

## 2. 明确排除文件清单及理由

| 文件名 | 排除理由 | 归属批次 |
|---|---|---|
| `AdminSourceController.java` | Controller 本体，不改控制器行为。其内部 4 个 `@Data` static DTO 归 B11c | B11c |
| `AdminSourceCredentialController.java` | Controller 本体 | 排除（非模型类） |
| `AdminUploadController.java` | Controller 本体。其引用的 `SourceSyncRunDetail` 等 DTO 在 `source/domain` 包，归 B15 | 排除 / B15 |
| `AdminSnapshotController.java` | Controller 本体。文章级快照而非 source 快照 | 排除（非模型类） |
| `AdminVaultController.java` | Controller 本体，vault 归 B5b | B5b |
| `AdminVaultExportRequest.java` | Vault 导出请求，归 B5b | **B5b** |
| `AdminVaultSyncRequest.java` | Vault 同步请求，归 B5b | **B5b** |
| `AdminRepoBaselineRequest.java` | Repo 基线请求，归 B5b | **B5b** |
| `AdminRepoDiffResponse.java` | Repo diff 响应，归 B5b | **B5b** |
| `AdminRepoRollbackRequest.java` | Repo 回滚请求，归 B5b | **B5b** |
| `AdminRepoSnapshotController.java` | Controller 本体，归 B5b | B5b |
| `AdminLifecycleRequest.java` | 生命周期请求，归 B5b | **B5b** |
| `AdminKnowledgeHelpActionResponse.java` | Dashboard 帮助卡，归 B10 | B10 |
| `AdminKnowledgeHelpStateResponse.java` | Dashboard 帮助卡，归 B10 | B10 |
| `AdminInspectImportRequest.java` | 质量 inspection 导入，非 source/credential/sync 范畴 | B8（article/fact card/quality 附近）或单独评估 |

---

## 3. 每个纳入类的逐类分析

### 3.1 AdminSourceCreateRequest（可变 Request）

**当前结构**：8 个 `private String` 字段（non-final），8 个手写 getter + 8 个手写 setter。无构造器（依赖默认无参 + Spring Jackson 反序列化注入）。无字段 Javadoc。

**Spring/Jackson 绑定方式**：`@RequestBody AdminSourceCreateRequest request` → Jackson 通过默认无参构造器 + setter 注入反序列化。

**Lombok 改造建议**：
- **不建议**使用 `@Data`。`@Data` 会生成 `toString()`，虽然当前无敏感字段，但 `remoteUrl` 可能含 token 参数（如 `https://token@host/repo.git`）。保守处理。
- **推荐**：仅补字段级 Javadoc（含义、取值约束、与 KnowledgeSource 表字段的对应关系）。保留手写 getter/setter 不变。与 B0-B4 中可变 Request 的处理方式一致（B3 的 `QueryRequest` 仅补注释，未改构造）。

**字段注释要点**：
| 字段 | 关键语义 |
|---|---|
| `sourceCode` | 资料源编码，对应 `knowledge_sources.source_code`，创建后不可变 |
| `name` | 展示名称 |
| `contentProfile` | 内容画像：DOCUMENT / CODE |
| `visibility` | 可见性：NORMAL / ADMIN_ONLY |
| `defaultSyncMode` | 默认同步模式：AUTO / FULL / INCREMENTAL |
| `remoteUrl` | Git 远程仓库地址（**注意：可能含 access token**） |
| `branch` | Git 分支名 |
| `credentialRef` | 关联的凭据编码引用，指向 `source_credentials.credential_code` |

### 3.2 AdminSourceCredentialRequest（可变 Request — 含敏感字段）

**当前结构**：4 个 `private String` 字段（non-final），4 个手写 getter + 4 个手写 setter（均有基本 Javadoc）。无构造器（依赖默认无参）。

**敏感字段风险**：
- **`secret`**：凭据明文（如 Git token、SSH key）。这是 Request 入参，从客户端经 HTTPS 传入服务端。服务端在 `SourceCredentialController.saveCredential()` 中加密后存储。**该字段不应出现在任何日志、toString() 或序列化输出中**。
- 当前仅手写 getter/setter → 无 toString 风险。但如果未来有人加 `@Data`，toString 会泄露 secret。

**Lombok 改造建议**：
- **严格禁止**使用 `@Data`（toString 泄露风险）。
- **推荐**：仅补字段级 Javadoc。保留手写 getter/setter 不变。
- **Javadoc 补充重点**：
  - `secret`：标注"凭据明文，仅用于创建/更新时的入参传输；服务端加密存储，不返回明文"
  - `credentialCode`：标注"唯一编码，对应 source_credentials.credential_code"
  - `credentialType`：标注"凭据类型，如 SSH_KEY / TOKEN / PASSWORD"

### 3.3 AdminSourceCredentialResponse（不可变 Response — 含脱敏字段）

**当前结构**：6 个 `private final` 字段，1 个手写全参构造器（有 `@param` Javadoc），6 个手写 getter（无字段 Javadoc）。构造方式：`AdminSourceCredentialController.toResponse()` 手动 `new`。

**敏感字段分析**：
- **`secretMask`**：凭据脱敏值（如 `ghp_abc***xyz`），非明文。**安全**——可以出现在 toString/日志中。
- 不存在 `secret` 明文字段 —— Response 已正确脱敏。

**Lombok 改造建议**：
- **推荐**：类级 `@Getter`，删除 6 个手写 getter。保留手写全参构造器（参数有 `@param` Javadoc，保留不删）。
- **不推荐**：`@AllArgsConstructor`——会生成与手写构造器参数顺序相同的全参构造器，但会丢失 `@param` Javadoc，且破坏现有调用点（`new AdminSourceCredentialResponse(id, code, type, mask, enabled, updatedAt)` 的参数命名可读性依赖手写构造器的 `@param`）。
- **Javadoc 补充**：
  - 每个 final 字段补字段级 Javadoc
  - 重点说明 `secretMask` 的脱敏规则（由 `SourceCredential` 的 `credentialMask()` 方法生成）

**风险评估**：低。`@Getter` 对纯 final 字段安全。无 @JsonCreator，无反序列化需求（Response 只由 controller 手动构造）。

### 3.4 AdminSourceFileResponse（不可变 Response）

**当前结构**：8 个字段（`Long`×2 + `String`×5 + `long`×1），均为 `private final`，1 个手写全参构造器（无 `@param` Javadoc），8 个手写 getter（无字段/方法 Javadoc）。

**构造方式**：`AdminSourceController.listSourceFiles()` 中手动 `new`：
```java
responses.add(new AdminSourceFileResponse(
    sourceFile.getId(),
    sourceFile.getSourceId(),
    sourceFile.getRelativePath(),
    sourceFile.getFormat(),
    sourceFile.getFileSize(),
    parseMode,
    parseProvider,
    contentPreview
));
```

**Lombok 改造建议**：
- **推荐**：类级 `@Getter`，删除 8 个手写 getter。保留手写全参构造器。**补充构造器 `@param` Javadoc**——当前完全没有。
- 无敏感字段。无 @JsonCreator。

### 3.5 AdminSourceRunConfirmRequest（可变 Request）

**当前结构**：2 个字段（`String decision` + `Long sourceId`），均为 non-final，2 个手写 getter + 2 个手写 setter（均有基本 Javadoc）。无构造器。

**Spring/Jackson 绑定方式**：`@RequestBody AdminSourceRunConfirmRequest request`。

**Lombok 改造建议**：
- 与 B3 可变 Request 一致：**不引入 Lombok**，仅补字段级 Javadoc。
- 保留手写 getter/setter 不变。

**字段注释要点**：
- `decision`：ACCEPT / REJECT，对应 `SourceSyncWorkflowService` 的分支逻辑
- `sourceId`：目标 `knowledge_sources.id`，WAIT_CONFIRM 状态下的人工确认目标资料源

### 3.6 AdminSourceValidationResponse（不可变 Response）

**当前结构**：6 个字段（`boolean` + 5×`String`），均为 `private final`，1 个手写全参构造器（无 `@param` Javadoc），6 个手写 getter（含 `isValid()` 而非 `getValid()`——Lombok 对 boolean 生成 `isValid()`，兼容）。

**构造方式**：`AdminSourceController.validateSource()` 中手动 `new AdminSourceValidationResponse(...)`。

**Lombok 改造建议**：
- **推荐**：类级 `@Getter`，删除 6 个手写 getter。保留手写全参构造器。
- Lombok `@Getter` 对 `boolean valid` 生成 `isValid()`——与当前手写一致。
- **补充构造器 `@param` Javadoc**——当前完全没有。
- 无敏感字段。无 @JsonCreator。

---

## 4. 敏感字段与绑定风险汇总

### 4.1 敏感字段矩阵

| 类 | 敏感字段 | 敏感级别 | 当前保护 | 风险 |
|---|---|---|---|---|
| `AdminSourceCredentialRequest` | `secret` | **高** — 凭据明文 | 仅手写 getter/setter，无 toString | 如果有人加 `@Data`，toString 会泄露。**建议在类级 Javadoc 中写"禁止加 @Data"** |
| `AdminSourceCredentialResponse` | `secretMask` | **低** — 脱敏值 | 仅手写 getter | 安全 |
| `AdminSourceCreateRequest` | `remoteUrl` | **中** — 可能含 token | 仅手写 getter/setter | 如果有人加 `@Data`，toString 会输出 URL（可能含 `token@host`）。**建议同样标注禁止 @Data** |
| 其余 3 个类 | 无 | — | — | 安全 |

### 4.2 Spring/Jackson 绑定风险

| 类 | 绑定方式 | 改造风险 |
|---|---|---|
| `AdminSourceCreateRequest` | `@RequestBody` → Jackson 无参构造器 + setter | 低。保留 setter 即可。不改绑定方式 |
| `AdminSourceCredentialRequest` | `@RequestBody` → Jackson 无参构造器 + setter | 低。同上 |
| `AdminSourceRunConfirmRequest` | `@RequestBody` → Jackson 无参构造器 + setter | 低。同上 |
| 3 个 Response 类 | 手动 `new` 构造 | 无绑定风险。Response 不反序列化 |

### 4.3 调用点搜索（手动 new 残留）

如果 B5a 后续某轮要收敛构造器，需搜索以下调用点：

| Response 类 | 调用位置 | `new` 次数 |
|---|---|---|
| `AdminSourceCredentialResponse` | `AdminSourceCredentialController.toResponse()` | 1 处（list 中循环 new） |
| `AdminSourceFileResponse` | `AdminSourceController.listSourceFiles()` | 1 处（list 中循环 new） |
| `AdminSourceValidationResponse` | `AdminSourceController.validateSource()` | 1 处 |

**本轮不收敛构造器**，仅补注释和 `@Getter`。

---

## 5. 改造建议汇总

| 类 | 改造动作 | 删除手写代码 | 新增 | 风险 |
|---|---|---|---|---|
| `AdminSourceCreateRequest` | 仅补 8 字段 Javadoc | 0 行 | ~24 行注释 | 无 |
| `AdminSourceCredentialRequest` | 仅补 4 字段 Javadoc + 类级安全标注 | 0 行 | ~16 行注释 | 无 |
| `AdminSourceCredentialResponse` | 类级 `@Getter` + 补 6 字段 Javadoc | 删除 6 个手写 getter (~36 行) | 1 个 import + ~18 行注释 | 低 |
| `AdminSourceFileResponse` | 类级 `@Getter` + 补 8 字段 Javadoc + 补构造器 @param | 删除 8 个手写 getter (~48 行) | 1 个 import + ~32 行注释 | 低 |
| `AdminSourceRunConfirmRequest` | 仅补 2 字段 Javadoc | 0 行 | ~8 行注释 | 无 |
| `AdminSourceValidationResponse` | 类级 `@Getter` + 补 6 字段 Javadoc + 补构造器 @param | 删除 6 个手写 getter (~36 行) | 1 个 import + ~24 行注释 | 低 |

**净效果**：6 个类，补 34 个字段 Javadoc。3 个 Response 类引入 `@Getter`，删除 20 个手写 getter（~120 行代码）。3 个 Request 类仅补注释，不动结构。

---

## 6. 给 agentA 的下一轮提示词草案

```
你是 agentA，本轮任务：B5a — api/admin source / credential / sync DTO 契约治理。

## 唯一变量
字段 Javadoc 补充 + 3 个不可变 Response 类引入类级 @Getter。不收敛构造器，不改 Request 绑定。

## 允许修改范围（仅 6 个文件）
- src/main/java/com/xbk/lattice/api/admin/AdminSourceCreateRequest.java
- src/main/java/com/xbk/lattice/api/admin/AdminSourceCredentialRequest.java
- src/main/java/com/xbk/lattice/api/admin/AdminSourceCredentialResponse.java
- src/main/java/com/xbk/lattice/api/admin/AdminSourceFileResponse.java
- src/main/java/com/xbk/lattice/api/admin/AdminSourceRunConfirmRequest.java
- src/main/java/com/xbk/lattice/api/admin/AdminSourceValidationResponse.java

## 改造内容

### 所有类（6 个）
1. 每个字段补字段级 Javadoc，按以下模板：
   - 含义：字段表达的工程概念
   - 写入方/消费方：谁构造、谁读取
   - 为空条件：何时为 null / 空字符串 / 0
   - 关联：与 DB 字段（如 knowledge_sources.xxx）、其他字段或 API 路径的关系

### Response 类（3 个：AdminSourceCredentialResponse / AdminSourceFileResponse / AdminSourceValidationResponse）
2. 类级加 `import lombok.Getter;` + `@Getter`
3. 删除所有手写 getter 方法（保留构造器不变）
4. 补充构造器的 @param Javadoc（AdminSourceFileResponse 和 AdminSourceValidationResponse 当前完全没有）

### Request 类（3 个：AdminSourceCreateRequest / AdminSourceCredentialRequest / AdminSourceRunConfirmRequest）
5. 仅补字段 Javadoc。保留所有手写 getter/setter 不变
6. 对 AdminSourceCredentialRequest：在类级 Javadoc 中标注"此类的 secret 字段为凭据明文，仅用于入参传输；禁止加 @Data 或任何会 toString 输出该字段的注解"

## 禁止事项
- 禁止引入 @Data / @AllArgsConstructor / @Builder
- 禁止修改构造器签名、参数顺序或调用方式
- 禁止修改 Controller、Service 或任何非本批次的 Java 文件
- 禁止修改 src/test/java/**
- 禁止在注释中写业务映射、答案值、eval 题面
- 禁止修改 secret/secretMask 的脱敏逻辑或加密行为
- 禁止把 B5b（vault/repo/lifecycle）的类纳入本轮

## 验证
1. 编译通过（mvn compile）
2. rg 自查无空泛模板注释（如仅字段名翻译）
3. 相关定向测试通过（AdminSourceController / AdminSourceCredentialController 相关测试）
4. 输出 B5a fix_result_report.md，回写计划台账
```

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未修改 `docs/模型绑定配置参考.md`、`special_cases_report.md`、redline allowlist
- 本轮未清库、未重建、未重导、未跑测试
- 本轮未 stage、未 commit、未 push
- 本轮仅在 `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` 中将 B5a 状态改为 "进行中：只读边界审查"
- 本轮新增报告：`admin_source_credential_sync_dto_contract_analysis_report.md`
