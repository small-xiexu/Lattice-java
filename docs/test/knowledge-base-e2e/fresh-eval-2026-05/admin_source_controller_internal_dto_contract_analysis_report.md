# B11c AdminSourceController 内部 DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B11c — `AdminSourceController` 内部 DTO

---

## 一、无需拆分

仅 5 个内部 static DTO，远在 10 个上限以内，一轮完成。

---

## 二、纳入文件清单（5 个类，全部在 AdminSourceController 内）

| # | 类名 | 类型 | 字段数 | 敏感/大字段 | 当前 Lombok | 处置 |
|---|---|---|---|---|---|---|
| 1 | `AdminKnowledgeSourcePageResponse` | Response | 4 | — | `@Data @NoArgsConstructor @AllArgsConstructor` | @Data→@Getter |
| 2 | `AdminKnowledgeSourceSummaryResponse` | Response | 14 | — | `@Data @NoArgsConstructor @AllArgsConstructor` | @Data→@Getter |
| 3 | `AdminKnowledgeSourceDetailResponse` | Response | 18 | `configJson`、`metadataJson`（可能为大型 JSON） | `@Data @NoArgsConstructor @AllArgsConstructor` | @Data→@Getter |
| 4 | `AdminKnowledgeSourcePatchRequest` | Request | 5 | `configJson`（**JsonNode 类型**，可能很大） | `@Data @NoArgsConstructor @AllArgsConstructor` | @Data→@Getter/@Setter，configJson 标注大 JSON 风险 |

**注意**：Controller 还引用了 3 个已完成的 B5a DTO（`AdminSourceCreateRequest`、`AdminSourceValidationResponse`、`AdminSourceFileResponse`），这些不在 B11c 范围。

---

## 三、每个类的详细分析

### 3.1 AdminKnowledgeSourcePageResponse（Response）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 构造方式：`new AdminKnowledgeSourcePageResponse(page, size, total, items)` — 通过全参构造器
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `page` | 当前页码（1-based） |
| `size` | 每页大小 |
| `total` | 符合条件的总记录数 |
| `items` | 资料源摘要列表 |

### 3.2 AdminKnowledgeSourceSummaryResponse（Response）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 构造方式：`toSummaryResponse()` 通过全参构造器
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `id` | 资料源主键 |
| `sourceCode` | 资料源编码（系统内唯一标识） |
| `name` | 资料源原始名称 |
| `displayName` | 管理台展示名称；由 controller 根据 metadataJson 中的 bundleSummary 计算（优先 displayName→文件/目录名→name） |
| `primaryDocumentTitle` | 主要文档标题；从 metadataJson bundleSummary.titleHints 提取，null 表示未提取到 |
| `sourceType` | 资料源类型（`UPLOAD` / `GIT`） |
| `contentProfile` | 内容画像（如 `code` / `document` / `mixed`） |
| `status` | 资料源状态（`ACTIVE` / `DISABLED` / `ARCHIVED`）；状态流转受 validateStatusTransition 约束 |
| `visibility` | 可见性（`NORMAL` / `ADMIN_ONLY`）；`ADMIN_ONLY` 时前端用户不可见 |
| `defaultSyncMode` | 默认同步模式（`AUTO` / `FULL` / `INCREMENTAL`） |
| `lastSyncRunId` | 最后一次同步 run 主键；null 表示从未同步 |
| `lastSyncStatus` | 最后一次同步状态 |
| `lastSyncAt` | 最后一次同步时间（ISO 字符串） |
| `updatedAt` | 资料源最后更新时间（ISO 字符串） |

### 3.3 AdminKnowledgeSourceDetailResponse（Response）

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Controller 构造方式：`toDetailResponse()` 通过全参构造器
- 与 SummaryResponse 相比新增了 `configJson`、`metadataJson`、`latestManifestHash`、`createdAt`
- **处置**：替换 `@Data` 为 `@Getter @NoArgsConstructor @AllArgsConstructor`
- 新增字段 Javadoc（Summary 已有字段同上）：

| 字段 | 需补充 |
|---|---|
| `configJson` | 资料源配置 JSON；含 repo 路径、vault 引用等配置详情；**可能为大型 JSON 字符串** |
| `metadataJson` | 资料源扩展元数据 JSON；含 bundleSummary、文件路径、标题提示等；**可能为大型 JSON 字符串** |
| `latestManifestHash` | 最近一次资料清单哈希；用于变更检测 |
| `createdAt` | 资料源创建时间（ISO 字符串） |

### 3.4 AdminKnowledgeSourcePatchRequest（Request）⚠️ 特殊类型

- 当前：`@Data @NoArgsConstructor @AllArgsConstructor`
- Spring 绑定：`@RequestBody` via `PATCH` 方法，需无参构造 + setter
- **特殊点**：`configJson` 字段类型为 `com.fasterxml.jackson.databind.JsonNode`（非 String），Jackson 直接将请求体中的 configJson 子树反序列化为 JsonNode 树。@Data toString() 会输出完整 JSON 树。
- Controller 的 `resolveConfigJson()` 将 JsonNode 序列化回 JSON 字符串后存入 KnowledgeSource
- `default-source` 资料源有只读保护（`updateSource()` 方法检查）
- **处置**：替换 `@Data` 为 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`。`configJson` 的 JsonNode 类型意味着 toString() 输出可能是大型 JSON 树，降级 @Data 可消除该风险。
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `name` | 资料源名称；为空时沿用现有名称 |
| `status` | 目标状态；为空时沿用现有状态；状态流转受 validateStatusTransition 约束（ACTIVE↔DISABLED↔ARCHIVED） |
| `visibility` | 目标可见性；为空时沿用现有值 |
| `defaultSyncMode` | 目标默认同步模式；为空时沿用现有值 |
| `configJson` | 目标配置 JSON 对象（**JsonNode 类型**）；null 时沿用现有配置；控制器层负责序列化为 JSON 字符串存储 |

---

## 四、全局发现：@Data 全量覆盖

全部 5 个内部 DTO 均使用 `@Data @NoArgsConstructor @AllArgsConstructor`，延续了 B11a/B11b 的相同模式。与 B11a 的 apiKey 和 B11b 的 credentialJson 不同，B11c 没有直接的密钥字段，但有大型 JSON 字段。

**关键差异**：`AdminKnowledgeSourcePatchRequest.configJson` 类型为 `JsonNode`，这是 B11 系列中唯一使用 Jackson JsonNode 作为字段类型的 DTO。@Data toString() 会输出完整的反序列化 JSON 树。

---

## 五、排除清单

| 排除项 | 理由 |
|---|---|
| Controller 的路由方法（`listSources`/`getSource`/`createGitSource`/`updateSource`/`validateSource`/`syncSource`/`listSourceFiles`） | Controller 行为逻辑 |
| 校验/映射方法（`validateStatusTransition`/`resolveName`/`resolveStatus`/`resolveVisibility`/`resolveDefaultSyncMode`/`resolveConfigJson`/`toSummaryResponse`/`toDetailResponse`/`resolveDisplayName`/`resolvePrimaryDocumentTitle` 等） | Controller 私有逻辑 |
| `ALLOWED_STATUSES` / `ALLOWED_VISIBILITIES` / `ALLOWED_SYNC_MODES` / `ALLOWED_SOURCE_TYPES` | 静态常量 |
| `AdminSourceCreateRequest` / `AdminSourceValidationResponse` / `AdminSourceFileResponse` | 已在 B5a 完成 |
| `KnowledgeSource` / `KnowledgeSourcePage` / `SourceSyncRunDetail` / `SourceValidationResult` / `SourceFileRecord` | domain/persistence 层 |
| `SourceService` / `SourceSyncWorkflowService` | 服务层 |

---

## 六、字段风险说明

### 6.1 中等风险

| 字段 | 所属类 | 类型 | 风险 |
|---|---|---|---|
| `configJson` | PatchRequest | **JsonNode** | @Data toString() 输出完整 JSON 树；可能含 repo 路径、vault 引用等内部配置信息 |
| `configJson` | DetailResponse | String | 大型 JSON 字符串；可能含内部路径 |
| `metadataJson` | DetailResponse | String | 大型 JSON 字符串；含文件路径、bundleSummary 等 |

### 6.2 低风险

`sourceCode`、`name`、`displayName`、`primaryDocumentTitle`、`sourceType`、`contentProfile`、`status`、`visibility`、`defaultSyncMode`、`latestManifestHash`、`lastSyncRunId`、`lastSyncStatus`、所有时间戳、分页字段 — 均为配置/状态/元数据，无密钥风险。

### 6.3 boolean getter

无 boolean 字段，无命名不一致问题。

---

## 七、给 agentA 的下一轮提示词草案

```
交给 agentA。

本轮任务：对 B11c 的 AdminSourceController 中 5 个内部 DTO 做 @Data 降级 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_source_controller_internal_dto_contract_analysis_report.md

## 修改范围（1 个文件，5 个内部 static class）

文件：src/main/java/com/xbk/lattice/api/admin/AdminSourceController.java

### Request（@Data→@Getter/@Setter）

1. AdminKnowledgeSourcePatchRequest
   - 替换 @Data 为 @Getter @Setter @NoArgsConstructor @AllArgsConstructor
   - 5 字段补 Javadoc（审查报告 3.4 节），注意 configJson 标注 JsonNode 类型和大型 JSON 风险
   - Patch 方法需要无参构造 + setter，保留

### Response（@Data→@Getter）

2. AdminKnowledgeSourcePageResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 4 字段补 Javadoc

3. AdminKnowledgeSourceSummaryResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 14 字段补 Javadoc（审查报告 3.2 节），displayName/primaryDocumentTitle 注明由 controller 从 metadataJson 计算

4. AdminKnowledgeSourceDetailResponse
   - 替换 @Data 为 @Getter @NoArgsConstructor @AllArgsConstructor
   - 18 字段补 Javadoc（审查报告 3.3 节），configJson/metadataJson 标注大型 JSON

## 禁止事项

- 禁止修改 Controller 的路由方法（listSources/getSource/createGitSource/updateSource/validateSource/syncSource/listSourceFiles）
- 禁止修改所有私有方法（resolveXxx/validateStatusTransition/toSummaryResponse/toDetailResponse/readJson/formatTime 等）
- 禁止修改 ALLOWED_* 静态常量
- 禁止修改字段类型（configJson 保持 JsonNode 类型）
- 禁止修改 AdminSourceCreateRequest / AdminSourceValidationResponse / AdminSourceFileResponse（B5a 已完成）
- Response 不加 @Setter
- 禁止混入 B11a/B11b 或 B12 文件

## 验收门槛

- mvn compile -pl . -q 通过
- redline 无新增 BLOCKER
- 自查：AdminKnowledgeSourcePatchRequest.configJson 保持 JsonNode 类型，Jackson 反序列化不变

## 完成后：回写 B11c → "已完成"，输出 B11c_fix_result_report.md
```

---

## 八、审查结论

- B11c 仅 5 个内部 DTO，**无需拆分**，一轮完成。
- 全部 5 个类使用 `@Data @NoArgsConstructor @AllArgsConstructor`，需降级为 `@Getter`（Response）或 `@Getter/@Setter`（Request）。
- **无 apiKey/credential/secret 密钥字段** — B11c 是 B11 系列中最安全的子批次。
- **特殊类型**：`AdminKnowledgeSourcePatchRequest.configJson` 为 `JsonNode` 类型，是 B11 系列中唯一使用 Jackson 树类型的字段。@Data toString() 会输出完整 JSON 树（可能含内部路径），降级 @Data 可消除。
- `AdminKnowledgeSourceDetailResponse` 的 `configJson` 和 `metadataJson` 为大型 JSON 字符串，需标注。
- Controller 引用的 `AdminSourceCreateRequest`、`AdminSourceValidationResponse`、`AdminSourceFileResponse` 已在 B5a 完成，不在本轮修改范围。
- 总可消除 5 个 @Data，补 41 个字段 Javadoc。0 个计算 getter、0 个 boolean 命名问题、0 个防御性拷贝。
