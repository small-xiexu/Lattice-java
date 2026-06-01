# B11c: AdminSourceController 内部 DTO @Data 降级 + 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B11c（B11 最后子批次）

---

## 1. 分析报告笔误说明

边界审查报告称"5 个内部 static DTO"，但实际代码中仅有 **4 个**：
1. `AdminKnowledgeSourcePageResponse`
2. `AdminKnowledgeSourceSummaryResponse`
3. `AdminKnowledgeSourceDetailResponse`
4. `AdminKnowledgeSourcePatchRequest`

Controller 还引用了 B5a 已完成的 3 个 DTO（`AdminSourceCreateRequest`、`AdminSourceValidationResponse`、`AdminSourceFileResponse`），这些不在 B11c 范围。

---

## 2. 修改文件

| 文件 | 变更 |
|---|---|
| `AdminSourceController.java` | 4 个内部 DTO @Data 全量降级 + 41 字段 Javadoc |

---

## 3. DTO 降级详情

| 类 | 字段数 | 注解变更 |
|---|---|---|
| `AdminKnowledgeSourcePageResponse` | 4 | @Data → @Getter |
| `AdminKnowledgeSourceSummaryResponse` | 14 | @Data → @Getter |
| `AdminKnowledgeSourceDetailResponse` | 18 | @Data → @Getter |
| `AdminKnowledgeSourcePatchRequest` | 5 | @Data → @Getter @Setter |

---

## 4. 关键字段 Javadoc 标注

| 字段 | 所属类 | 标注内容 |
|---|---|---|
| `displayName` | Summary/Detail | 由 controller 从 metadataJson.bundleSummary 计算（displayName→文件/目录名→name） |
| `primaryDocumentTitle` | Summary/Detail | 从 metadataJson.bundleSummary.titleHints 提取，null=未提取到 |
| `configJson` (Detail) | DetailResponse | 含 repo 路径、Vault 引用等，可能为大型 JSON 字符串 |
| `metadataJson` (Detail) | DetailResponse | 含 bundleSummary 信息，可能为大型 JSON |
| `configJson` (Patch) | PatchRequest | **JsonNode 类型**，null 时沿用现有配置，controller 序列化为 JSON 字符串存储 |
| `status` | Summary/Detail/Patch | ACTIVE/DISABLED/ARCHIVED 生命周期状态，Patch 为空时沿用现有值 |
| `visibility` | Summary/Detail/Patch | NORMAL/ADMIN_ONLY，Patch 为空时沿用现有值 |
| `defaultSyncMode` | Summary/Detail/Patch | AUTO/FULL/INCREMENTAL，Patch 为空时沿用现有值 |

---

## 5. Lombok 统计

| 指标 | 数量 |
|---|---|
| `@Data` → 降级 | 4 → **0** |
| `@Getter` | 4 |
| `@Setter`（仅 Request） | 1 |
| `JsonNode` 类型保留 | 1（PatchRequest.configJson） |
| 字段 Javadoc | 41 |

**B11 合计（B11a + B11b + B11c）**：**29 个内部 DTO** 全部 @Data 降级完毕，`apiKey`/`credentialJson` @ToString.Exclude 共 4 处。

---

## 6. 验证

```
mvn compile: BUILD SUCCESS
rg -n '@Data': (无结果) ✓
JsonNode: PatchRequest.configJson 第 659 行 ✓
@Setter: 仅 PatchRequest（1 处） ✓
```

Controller 方法未修改。

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 1 个目标文件 | 通过 |
| 4 个 @Data 全部降级 | 通过 |
| PatchRequest.configJson 保持 JsonNode | 通过 |
| Request 有 @Setter，Response 无 | 通过 |
| 未修改 B11a/B11b/B5a 文件 | 通过 |
| 未 stage/commit/push | 通过 |
