# api/admin Source / Credential / Sync DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-06-01
改造人：agentA（代码执行 Agent）
批次：B5a

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminSourceCreateRequest.java` | 可变 Request | 8 字段 Javadoc，保留 getter/setter；类级标注 remoteUrl 含 token 风险 |
| `AdminSourceCredentialRequest.java` | 可变 Request | 4 字段 Javadoc，保留 getter/setter；类级标注 secret 禁止 @Data |
| `AdminSourceCredentialResponse.java` | 不可变 Response | 类级 @Getter + 6 字段 Javadoc + 删除 6 手写 getter |
| `AdminSourceFileResponse.java` | 不可变 Response | 类级 @Getter + 8 字段 Javadoc + 删除 8 手写 getter + 补构造器 @param |
| `AdminSourceRunConfirmRequest.java` | 可变 Request | 2 字段 Javadoc，保留 getter/setter |
| `AdminSourceValidationResponse.java` | 不可变 Response | 类级 @Getter + 6 字段 Javadoc + 删除 6 手写 getter（含 isValid()）+ 补构造器 @param |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B5a 状态回写 + "当前下一步" → B5b |

**无调用点迁移。** 构造器签名、getter/setter 方法名、Spring/Jackson 绑定方式均未修改。

---

## 2. 各文件详细变更

### 2.1 AdminSourceCreateRequest（8 字段，可变 Request——仅补 Javadoc）

| 字段 | 注释要点 |
|---|---|
| `sourceCode` | 资料源编码（knowledge_sources.source_code），创建后不可变 |
| `name` | 展示名称 |
| `contentProfile` | DOCUMENT/CODE，决定编译解析链路 |
| `visibility` | NORMAL/ADMIN_ONLY，控制普通查询可见性 |
| `defaultSyncMode` | AUTO/FULL/INCREMENTAL |
| `remoteUrl` | 可能含 access token 的 Git URL（已标注风险） |
| `branch` | Git 分支名 |
| `credentialRef` | 指向 source_credentials.credential_code，为空表示公开仓库 |

### 2.2 AdminSourceCredentialRequest（4 字段，可变 Request——含敏感字段 secret）

类级 Javadoc 包含 `<b>secret 字段为凭据明文...严格禁止加 @Data</b>` 安全警告。

| 字段 | 注释要点 |
|---|---|
| `credentialCode` | 对应 source_credentials.credential_code |
| `credentialType` | SSH_KEY/TOKEN/PASSWORD 等 |
| `secret` | **敏感字段**——凭据明文，仅入参传输，服务端加密存储 |
| `updatedBy` | 审计操作人 |

### 2.3 AdminSourceCredentialResponse（6 字段）

| 字段 | 注释要点 |
|---|---|
| `id` | 主键（source_credentials.id） |
| `credentialCode` | 凭据编码 |
| `credentialType` | 凭据类型 |
| `secretMask` | 脱敏值（由 credentialMask() 生成，如 ghp_abc***xyz），可安全出现在日志中 |
| `enabled` | 是否启用（source_credentials.enabled） |
| `updatedAt` | 最近维护时间（source_credentials.updated_at） |

Lombok `@Getter` 对 `boolean enabled` 生成 `isEnabled()`——与手写一致。

### 2.4 AdminSourceFileResponse（8 字段）

| 字段 | 注释要点 |
|---|---|
| `id` | 文件主键（source_files.id） |
| `sourceId` | 所属资料源主键（→ knowledge_sources.id） |
| `relativePath` | 仓库中相对路径 |
| `format` | 文件格式（markdown/yaml/json/java/xlsx 等），决定图标和预览策略 |
| `fileSize` | 文件大小（字节） |
| `parseMode` | 解析模式（document_parse/code_ast），为空表示未解析 |
| `parseProvider` | 解析提供者名称，为空表示无匹配解析器 |
| `contentPreview` | 文件开头内容预览 |

构造器 @param Javadoc 已补齐（原完全缺失）。

### 2.5 AdminSourceRunConfirmRequest（2 字段，可变 Request——仅补 Javadoc）

| 字段 | 注释要点 |
|---|---|
| `decision` | ACCEPT/REJECT，对应 SourceSyncWorkflowService 分支逻辑 |
| `sourceId` | 目标资料源主键（knowledge_sources.id） |

### 2.6 AdminSourceValidationResponse（6 字段）

| 字段 | 注释要点 |
|---|---|
| `valid` | 校验是否通过，false 时调用方应读取 message（Lombok 生成 isValid()，与原手写一致） |
| `sourceType` | 资料源类型（GIT/LOCAL） |
| `message` | 成功提示或失败原因 |
| `resolvedRef` | 解析到的 Git 引用 |
| `branch` | 目标分支名 |
| `gitCommit` | 目标分支最新 commit SHA |

构造器 @param Javadoc 已补齐。

---

## 3. Lombok 使用统计

| 类 | 注解 | 替代 getter 数 |
|---|---|---|
| `AdminSourceCredentialResponse` | 类级 `@Getter` | 6 |
| `AdminSourceFileResponse` | 类级 `@Getter` | 8 |
| `AdminSourceValidationResponse` | 类级 `@Getter` | 6 |
| **合计** | | **20** |

### 未使用 Lombok 的类

| 类 | 原因 |
|---|---|
| `AdminSourceCreateRequest` | 可变 Request（含 remoteUrl token 风险），保留 getter/setter |
| `AdminSourceCredentialRequest` | 可变 Request（含 secret 明文），保留 getter/setter + 类级安全警告 |
| `AdminSourceRunConfirmRequest` | 可变 Request，保留 getter/setter |

**未使用：** `@Data`、`@Setter`、`@AllArgsConstructor`、`@NoArgsConstructor`、`@Builder`

---

## 4. 敏感字段安全确认

| 类 | 敏感字段 | 保护措施 | 风险 |
|---|---|---|---|
| `AdminSourceCredentialRequest` | `secret` | 类级 Javadoc 标注"严格禁止 @Data"；仅手写 getter/setter（无 toString） | 已防护 |
| `AdminSourceCreateRequest` | `remoteUrl` | 类级 Javadoc 标注"可能含 token，禁止 @Data" | 已防护 |
| `AdminSourceCredentialResponse` | `secretMask` | 脱敏值，非明文；@Getter 安全 | 安全 |

---

## 5. 测试与 Redline

```
mvn test
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

---

## 6. B0-B5a 累计统计

| 批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter |
|---|---|---|---|---|
| B0-B4 | api/query + compiler + admin/service | 27 | 134 | 118 |
| B5a | api/admin source/credential/sync | 6 | 34 | 20 |
| **合计** | | **33** | **168** | **138** |

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 6 个目标文件 | 通过 |
| 可变 Request 保留 getter/setter | 通过 |
| 不可变 Response 仅用 @Getter | 通过 |
| 未使用 @Data/@Setter/@AllArgsConstructor/@Builder | 通过 |
| secret 字段无 toString 暴露风险 | 通过（已自查确认） |
| UndefinedCommand | （未扩大范围） |
| 未修改 B5b（vault/repo/lifecycle）类 | 通过 |
| 未修改 Controller/Service/domain/infra/test | 通过 |
| 未 stage/commit/push | 通过 |

---

## 8. 残留风险

无。三个维度的安全确认已完成：
- **@Data 防护**：`AdminSourceCredentialRequest` 和 `AdminSourceCreateRequest` 的类级 Javadoc 均已标注禁止 @Data
- **Lombok 兼容性**：boolean 字段的 `isValid()`/`isEnabled()` 由 @Getter 正确生成
- **Spring 绑定**：3 个 Request 类的 getter/setter 全部保留，反序列化行为不变
