# B5b: api/admin Vault / Repo / Lifecycle DTO 边界审查报告

审查时间：2026-06-01
审查人：agentB（治理/链路分析 Agent）
审查对象：`docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` B5b 批次
审查类型：只读边界审查，未修改任何生产代码

---

## 1. B5b 纳入文件清单（精确）

| # | 文件名 | 类型 | 字段数 | 当前 Lombok | 手写 Getter | 手写 Setter | 构造器 | 特殊风险 |
|---|---|---|---|---|---|---|---|---|
| 1 | `AdminVaultExportRequest.java` | **Request** (可变) | 1 | 无 | 1 | 1 | 无（默认无参） | **路径遍历** — `vaultDir` 为文件系统路径 |
| 2 | `AdminVaultSyncRequest.java` | **Request** (可变) | 2 | 无 | 2 | 2 | 无（默认无参） | **路径遍历** + `force` 覆盖开关 |
| 3 | `AdminRepoBaselineRequest.java` | **Request** (可变) | 2 | 无 | 2 | 2 | 无（默认无参） | **路径遍历** + baseline 描述 |
| 4 | `AdminRepoDiffResponse.java` | **Response** (不可变) | 4 | 无 | 5（含 1 个计算 getter） | 0 | 1 个手写全参 | commit ref 对比语义 |
| 5 | `AdminRepoRollbackRequest.java` | **Request** (可变) | 2 | 无 | 2 | 2 | 无（默认无参） | **路径遍历** + **回滚目标验证** |
| 6 | `AdminLifecycleRequest.java` | **Request** (可变) | 2 | 无 | 2 | 2 | 无（默认无参） | 审计跟踪（`reason`/`updatedBy`） |

**合计：6 个类，13 个字段，14 个手写 getter，11 个手写 setter。均在 5-10 个类的批次上限内。**

---

## 2. 明确排除文件清单及理由

| 文件名 | 排除理由 | 归属批次 |
|---|---|---|
| `AdminVaultController.java` | Controller 本体 | 排除（非模型类） |
| `AdminRepoSnapshotController.java` | Controller 本体 | 排除（非模型类） |
| `AdminSnapshotController.java` | Controller 本体，文章级快照 | 排除（非模型类） |
| `AdminArticleRollbackRequest.java` | 文章级回滚，非 repo 回滚 | **B8**（article 批次） |
| `AdminArticleController.java` | Controller 本体（仅使用 AdminLifecycleRequest） | 排除（非模型类） |
| `RepoBaselineResult` / `RepoRollbackResult` / `VaultExportResult` / `VaultSyncResult` | 在 `vault/snapshot` 或 `governance` 包下，非 `api/admin` DTO | B15（source/domain）或 B19（governance/domain） |
| 所有 B5a 已处理的 Source/Credential DTO（6 个） | 已在 B5a 完成 | **B5a** ✓ |
| 所有 B6-B10 的 admin DTO | 属于 vector/config/compile/article/feedback/overview 范畴 | 各自批次 |

---

## 3. 每个纳入类的逐类分析

### 3.1 AdminVaultExportRequest（可变 Request — 路径遍历风险）

**当前结构**：1 个 `private String vaultDir`（non-final），1 个手写 getter + 1 个手写 setter（均有基本 Javadoc）。无构造器。

**Spring/Jackson 绑定方式**：`@RequestBody AdminVaultExportRequest request` → Jackson 无参构造器 + setter。

**路径遍历风险**：`vaultDir` 是文件系统路径，服务端在 `VaultController.export()` 中将其传给 `VaultService.export(Path.of(request.getVaultDir()))`。如果服务端未做路径规范化（如 `Path.of(vaultDir).normalize()`），调用方可传入 `../../etc/passwd` 这类路径。**这是服务端责任**（controller/service 应做校验），DTO 层只需在注释中说明风险。

**Lombok 改造建议**：
- **不引入 Lombok**。单字段类引入 `@Data` 无价值（且会生成不必要的 `toString/equals/hashCode`）。
- **仅补字段级 Javadoc**：说明 `vaultDir` 的含义（Vault 本地仓库根目录）、服务端应校验的路径安全性、为空时的行为。

### 3.2 AdminVaultSyncRequest（可变 Request — 路径 + force 覆盖）

**当前结构**：2 个字段（`String vaultDir` + `boolean force`），2 个手写 getter（`isForce()` 而非 `getForce()`）+ 2 个手写 setter。无 Javadoc（字段和 getter/setter 都没有）。无构造器。

**Spring/Jackson 绑定方式**：`@RequestBody AdminVaultSyncRequest request`。

**风险**：
- `vaultDir`：与 3.1 相同的路径遍历风险
- `force`：强制覆盖开关。若为 true，sync 可能覆盖本地未提交的变更。注释需说明其语义

**Lombok 改造建议**：
- **不引入 Lombok**。仅补字段级 Javadoc。
- 保留手写 getter/setter 不变。
- 补充 `isForce()` 的行为说明。

### 3.3 AdminRepoBaselineRequest（可变 Request — 路径 + baseline 描述）

**当前结构**：2 个字段（`String vaultDir` + `String description`），2 个手写 getter + 2 个手写 setter。无 Javadoc。无构造器。

**Spring/Jackson 绑定方式**：`@RequestBody AdminRepoBaselineRequest request`。

**风险**：
- `vaultDir`：路径遍历
- `description`：baseline 描述，纯展示字段，无结构性风险

**Lombok 改造建议**：
- **不引入 Lombok**。仅补字段级 Javadoc。
- Javadoc 说明 `vaultDir` 的路径安全约束和 `description` 的用途（用于 snapshot 元数据展示）。

### 3.4 AdminRepoDiffResponse（不可变 Response — 含计算 getter）

**当前结构**：4 个 `private final` 字段，1 个手写全参构造器（无 `@param` Javadoc），**5 个**手写 getter——其中 4 个是简单字段访问，1 个是计算 getter：

```java
public int getCount() {
    return items == null ? 0 : items.size();
}
```

**构造方式**：`AdminRepoSnapshotController.diff()` 中手动 `new AdminRepoDiffResponse(snapshotId, targetCommitId, currentCommitId, diffItems)`。

**风险**：
- `targetCommitId` / `currentCommitId`：Git commit hash 对比。注释应说明两个 commit 的语义（target = snapshot 记录的目标 commit，current = Vault 当前 HEAD）
- `items`：`VaultDiffSummary` 列表。注释应说明 null 时的行为（getCount 返回 0）

**Lombok 改造建议**：
- **类级 `@Getter` 可行但需保留计算 getter**。Lombok `@Getter` 会生成 4 个字段的 getter 和一个 `getItems()`。手写的 `getCount()` 不受影响（Lombok 不会覆盖已存在的方法）。
- **推荐**：类级 `@Getter`，删除 4 个简单字段的手写 getter。保留手写 `getCount()`（计算 getter，Lombok 不会生成）。保留手写全参构造器不变。
- **补充构造器 `@param` Javadoc**——当前完全没有。

**注意**：`getItems()` 返回 `List<VaultDiffSummary>`。如果 `items` 为 null，Lombok 生成的 getter 返回 null（与当前手写一致）。`getCount()` 的 null 安全逻辑不受影响。

### 3.5 AdminRepoRollbackRequest（可变 Request — 路径 + 回滚验证）

**当前结构**：2 个字段（`long snapshotId` + `String vaultDir`），2 个手写 getter + 2 个手写 setter。无 Javadoc。无构造器。

**Spring/Jackson 绑定方式**：`@RequestBody AdminRepoRollbackRequest request`。

**风险**：
- `snapshotId`：回滚目标 snapshot ID。**必须由服务端校验**该 snapshot 存在且属于当前 Vault。如果接受任意 snapshotId，可能回滚到不相关的快照
- `vaultDir`：路径遍历（与 3.1 相同）

**Lombok 改造建议**：
- **不引入 Lombok**。仅补字段级 Javadoc。
- 注释重点：`snapshotId` 说明"服务端应校验该 snapshot 存在且属于当前 Vault，不得接受未经校验的 ID"

### 3.6 AdminLifecycleRequest（可变 Request — 审计跟踪）

**当前结构**：2 个字段（`String reason` + `String updatedBy`），2 个手写 getter + 2 个手写 setter（均有基本 Javadoc）。无构造器。

**Spring/Jackson 绑定方式**：`@RequestBody AdminLifecycleRequest lifecycleRequest`（在 `AdminArticleController` 中使用）。

**风险**：
- `reason`：生命周期变更原因。属于审计跟踪数据，应持久化到审计表
- `updatedBy`：操作者标识。应来自认证上下文而非请求体——但当前通过 Request DTO 传入，这是**设计上的风险**（调用方可伪造操作者身份）

> **注意**：`updatedBy` 应从认证上下文（如 `@AuthenticationPrincipal` 或 `SecurityContextHolder`）获取，而非从请求体。但这是 controller 行为变更，不在本轮治理范围内。**建议在字段 Javadoc 中标注"应从认证上下文获取，不接受请求体直接传入"**，但不修改 controller 代码。

**Lombok 改造建议**：
- **不引入 Lombok**。仅补字段级 Javadoc。
- `updatedBy` 的 Javadoc 标注当前行为（从请求体获取）和理想行为（从认证上下文获取），作为后续治理的提示。

---

## 4. 路径/Ref/Rollback/Lifecycle 操作风险汇总

### 4.1 路径遍历风险（4 个类共享）

| 类 | 路径字段 | 使用位置 | 校验层面 | 风险 |
|---|---|---|---|---|
| `AdminVaultExportRequest` | `vaultDir` | `VaultController.export()` → `Path.of(vaultDir)` | Service 层 | 若未 normalize，可构造路径遍历攻击 |
| `AdminVaultSyncRequest` | `vaultDir` | `VaultController.sync()` → `Path.of(vaultDir)` | Service 层 | 同上 |
| `AdminRepoBaselineRequest` | `vaultDir` | `RepoSnapshotController.createBaseline()` | Service 层 | 同上 |
| `AdminRepoRollbackRequest` | `vaultDir` | `RepoSnapshotController.rollback()` | Service 层 | 同上 |

**本轮处理**：仅为每个 `vaultDir` 字段补充 Javadoc，标注"服务端应做路径规范化和存在性校验"。不修改校验逻辑。

### 4.2 回滚/覆盖操作风险

| 操作 | 请求类 | 风险 | 本轮处理 |
|---|---|---|---|
| Vault sync | `AdminVaultSyncRequest` | `force=true` 时可能覆盖本地未提交变更 | 注释说明 `force` 语义 |
| Repo rollback | `AdminRepoRollbackRequest` | `snapshotId` 可能指向不相关 snapshot | 注释标注服务端校验要求 |
| Lifecycle change | `AdminLifecycleRequest` | `updatedBy` 从请求体传入，可被伪造 | 注释标注理想行为（认证上下文获取），不改代码 |

### 4.3 敏感字段检查

B5b 的 6 个类**均无** `apiKey`、`token`、`password`、`secret` 等敏感字段。`vaultDir` 是文件系统路径（非密钥），`snapshotId` 是数字 ID，`commitId` 是 Git hash。

结论：B5b 无 toString 泄露风险，即使将来引入 `@Data` 也不会有密钥泄露（但仍不应引入——Request 类没必要有 `@Data`）。

---

## 5. 改造建议汇总

| 类 | 改造动作 | 删除手写代码 | 新增 | 风险 |
|---|---|---|---|---|
| `AdminVaultExportRequest` | 仅补 1 字段 Javadoc | 0 行 | ~6 行注释 | 无 |
| `AdminVaultSyncRequest` | 仅补 2 字段 Javadoc | 0 行 | ~10 行注释 | 无 |
| `AdminRepoBaselineRequest` | 仅补 2 字段 Javadoc | 0 行 | ~10 行注释 | 无 |
| `AdminRepoDiffResponse` | 类级 `@Getter` + 补 4 字段 Javadoc + 补构造器 @param | 删除 4 个简单字段 getter (~28 行) | 1 个 import + ~20 行注释 | 低（保留计算 getter） |
| `AdminRepoRollbackRequest` | 仅补 2 字段 Javadoc | 0 行 | ~10 行注释 | 无 |
| `AdminLifecycleRequest` | 仅补 2 字段 Javadoc + `updatedBy` 风险标注 | 0 行 | ~12 行注释 | 无 |

**净效果**：6 个类，补 13 个字段 Javadoc。仅 1 个 Response 类引入 `@Getter`，删除 4 个手写 getter（~28 行）。5 个 Request 类仅补注释，不动结构。

**与 B5a 对比**：B5b 的 Request 比例更高（5/6 vs 3/6），Response 比例更低（1/6 vs 3/6）。B5b 的净增/删代码量远小于 B5a。

---

## 6. 给 agentA 的下一轮提示词草案

```
你是 agentA，本轮任务：B5b — api/admin vault / repo / lifecycle DTO 契约治理。

## 唯一变量
字段 Javadoc 补充 + 1 个不可变 Response 类引入类级 @Getter。不收敛构造器，不改 Request 绑定。

## 允许修改范围（仅 6 个文件）
- src/main/java/com/xbk/lattice/api/admin/AdminVaultExportRequest.java
- src/main/java/com/xbk/lattice/api/admin/AdminVaultSyncRequest.java
- src/main/java/com/xbk/lattice/api/admin/AdminRepoBaselineRequest.java
- src/main/java/com/xbk/lattice/api/admin/AdminRepoDiffResponse.java
- src/main/java/com/xbk/lattice/api/admin/AdminRepoRollbackRequest.java
- src/main/java/com/xbk/lattice/api/admin/AdminLifecycleRequest.java

## 改造内容

### 所有类（6 个）
1. 每个字段补字段级 Javadoc：
   - 含义：字段表达的工程概念
   - 为空条件：何时为 null / 空字符串 / 0
   - 风险（如有）：路径遍历、覆盖操作、审计伪造等

### AdminRepoDiffResponse（不可变 Response — 唯一引入 @Getter 的类）
2. 类级加 `import lombok.Getter;` + `@Getter`
3. 删除 4 个手写简单字段 getter（getSnapshotId/getTargetCommitId/getCurrentCommitId/getItems）
4. **保留**手写 `getCount()` 方法——它是计算 getter（Lombok 对 `count` 字段不存在，不会生成）
5. 保留手写全参构造器不变；**补充构造器 @param Javadoc**——当前完全没有

### 其余 5 个 Request 类
6. 仅补字段 Javadoc。保留所有手写 getter/setter 不变
7. 不在任何 Request 类上引入 @Data 或任何 Lombok 注解

### 特殊注释要求
8. AdminVaultExportRequest.vaultDir：标注"Vault 本地仓库根目录的绝对路径；服务端应做规范化和存在性校验"
9. AdminVaultSyncRequest.force：标注"true=强制覆盖本地未提交变更；false=安全模式"
10. AdminRepoRollbackRequest.snapshotId：标注"服务端应校验该 snapshot 存在且属于当前 Vault"
11. AdminLifecycleRequest.updatedBy：标注"理想应从认证上下文获取，当前从请求体传入"

## 禁止事项
- 禁止引入 @Data / @AllArgsConstructor / @Builder
- 禁止修改构造器签名、参数顺序或调用方式
- 禁止修改 Controller、Service 或任何非本批次的 Java 文件
- 禁止修改路径校验、回滚验证、认证逻辑等业务行为
- 禁止修改 src/test/java/**
- 禁止把 B6（vector/retrieval config）、B7（compile job/review）、B8（article/fact card）的类纳入本轮

## 验证
1. 编译通过（mvn compile）
2. rg 自查无空泛模板注释
3. 相关定向测试通过（AdminVaultController / AdminRepoSnapshotController 相关测试）
4. 输出 B5b fix_result_report.md，回写计划台账
```

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未修改 `docs/模型绑定配置参考.md`、`special_cases_report.md`、redline allowlist
- 本轮未清库、未重建、未重导、未跑测试
- 本轮未 stage、未 commit、未 push
- 本轮仅在 `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` 中将 B5b 状态改为 "进行中：只读边界审查"
- 本轮新增报告：`admin_vault_repo_lifecycle_dto_contract_analysis_report.md`
