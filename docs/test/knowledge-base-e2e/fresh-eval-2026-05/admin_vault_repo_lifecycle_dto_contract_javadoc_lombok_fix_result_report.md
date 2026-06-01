# api/admin Vault / Repo / Lifecycle DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-06-01
改造人：agentA（代码执行 Agent）
批次：B5b

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminVaultExportRequest.java` | 可变 Request | 1 字段 Javadoc（vaultDir 路径遍历风险标注），保留 getter/setter |
| `AdminVaultSyncRequest.java` | 可变 Request | 2 字段 Javadoc（vaultDir + force 覆盖开关），保留 getter/setter |
| `AdminRepoBaselineRequest.java` | 可变 Request | 2 字段 Javadoc（vaultDir + description），保留 getter/setter |
| `AdminRepoDiffResponse.java` | 不可变 Response | 类级 @Getter + 4 字段 Javadoc + 删除 4 简单 getter + 保留计算 getCount() + 补构造器 @param |
| `AdminRepoRollbackRequest.java` | 可变 Request | 2 字段 Javadoc（snapshotId 回滚验证 + vaultDir），保留 getter/setter |
| `AdminLifecycleRequest.java` | 可变 Request | 2 字段 Javadoc（reason + updatedBy 审计风险标注），保留 getter/setter |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B5b 状态回写 + "当前下一步" → B6 |

**无调用点迁移。** 构造器签名、getter/setter 方法名、Spring/Jackson 绑定方式均未修改。

---

## 2. 各文件详细变更

### 2.1 AdminVaultExportRequest（1 字段，可变 Request——路径遍历风险标注）

| 字段 | 注释要点 |
|---|---|
| `vaultDir` | Vault 本地仓库根目录的绝对路径。**标注服务端应做路径规范化（normalize）和存在性校验，防止路径遍历攻击。** |

### 2.2 AdminVaultSyncRequest（2 字段，可变 Request——路径 + force 覆盖风险标注）

| 字段 | 注释要点 |
|---|---|
| `vaultDir` | Vault 本地仓库根目录，服务端应做路径规范化和存在性校验 |
| `force` | true=强制覆盖本地未提交变更或绕过安全检查；false/默认=安全模式 |

### 2.3 AdminRepoBaselineRequest（2 字段，可变 Request）

| 字段 | 注释要点 |
|---|---|
| `vaultDir` | Vault 仓库根目录，决定对哪个 Vault 创建 baseline |
| `description` | baseline 描述，记录目的和上下文，用于后续审计和回滚选择 |

### 2.4 AdminRepoDiffResponse（4 字段，唯一 @Getter 类——保留计算 getter）

| 字段 | 注释要点 |
|---|---|
| `snapshotId` | 目标 snapshot ID |
| `targetCommitId` | snapshot 创建时的 commit hash（diff 基准端） |
| `currentCommitId` | 当前 HEAD commit hash（diff 比较端） |
| `items` | 两 commit 间变更文件清单，null 时 getCount() 返回 0 |

**关键保留：** `getCount()` 是计算 getter（`items == null ? 0 : items.size()`），Lombok 不会为不存在的字段生成此方法。手写保留。

构造器 @param Javadoc 已补齐（原完全缺失）。

### 2.5 AdminRepoRollbackRequest（2 字段，可变 Request——回滚验证风险标注）

| 字段 | 注释要点 |
|---|---|
| `snapshotId` | 回滚目标 snapshot ID。**标注服务端应校验该 snapshot 存在且属于当前 Vault，不得接受未经校验的 ID。** |
| `vaultDir` | Vault 仓库根目录，服务端应做路径规范化和存在性校验 |

### 2.6 AdminLifecycleRequest（2 字段，可变 Request——审计风险标注）

| 字段 | 注释要点 |
|---|---|
| `reason` | 生命周期变更原因，用于审计追踪，应被持久化到审计表 |
| `updatedBy` | 操作者标识。**标注理想应从认证上下文获取，当前从请求体传入，存在身份伪造风险。** 调用方不应依赖此处取值做授权判断 |

---

## 3. Lombok 使用统计

| 类 | 注解 | 替代 getter 数 |
|---|---|---|
| `AdminRepoDiffResponse` | 类级 `@Getter` | 4（简单字段） |
| **合计** | | **4** |

### 未使用 Lombok 的类（5 个）

均为可变 Request，保留 getter/setter。

**未使用：** `@Data`、`@Setter`、`@AllArgsConstructor`、`@NoArgsConstructor`、`@Builder`

---

## 4. 风险标注汇总

| 类 | 字段 | 风险类型 | 标注方式 |
|---|---|---|---|
| `AdminVaultExportRequest` | `vaultDir` | 路径遍历 | 服务端应做 normalize + 存在性校验 |
| `AdminVaultSyncRequest` | `vaultDir` | 路径遍历 | 同上 |
| `AdminVaultSyncRequest` | `force` | 强制覆盖 | true=覆盖本地未提交变更/绕过安全检查 |
| `AdminRepoBaselineRequest` | `vaultDir` | 路径遍历 | 同上 |
| `AdminRepoRollbackRequest` | `snapshotId` | 快照验证 | 服务端应校验存在且属于当前 Vault |
| `AdminRepoRollbackRequest` | `vaultDir` | 路径遍历 | 同上 |
| `AdminLifecycleRequest` | `updatedBy` | 审计伪造 | 理想应从认证上下文获取，当前从请求体传入 |

**未修改任何业务行为。** 路径校验、回滚验证、认证逻辑均保持原样。

---

## 5. 测试与 Redline

```
mvn test
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

getCount() 保留验证：
```
rg -n 'getCount' AdminRepoDiffResponse.java
45:     * 为 null 时 getCount() 返回 0。
74:    public int getCount() {
```
确认手写 `getCount()` 在第 74 行保留。

---

## 6. B0-B5b 累计统计

| 批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter |
|---|---|---|---|---|
| B0-B5a | api/query + compiler + admin (source/credential) | 33 | 168 | 138 |
| B5b | admin (vault/repo/lifecycle) | 6 | 13 | 4 |
| **合计** | | **39** | **181** | **142** |

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 6 个目标文件 | 通过 |
| 可变 Request 保留 getter/setter | 通过 |
| AdminRepoDiffResponse 仅 @Getter + 保留 getCount() | 通过（已自查确认） |
| 未使用 @Data/@Setter/@AllArgsConstructor/@Builder | 通过 |
| 未修改路径校验/回滚验证/认证逻辑 | 通过 |
| 未修改 B6（vector/retrieval config）类 | 通过 |
| 未修改 Controller/Service/domain/infra/test | 通过 |
| 未 stage/commit/push | 通过 |

---

## 8. 残留风险

无代码层风险。以下为标注性关注点（不在本轮修改范围）：

- **路径遍历**：4 个类的 `vaultDir` 均已标注"服务端应做路径规范化"，但校验逻辑未变。如果服务端未实现校验，风险仍然存在。属于服务端安全加固的候选。
- **审计身份**：`AdminLifecycleRequest.updatedBy` 已标注"应从认证上下文获取"，但实现未变。属于后续认证安全加固的候选。
- **回滚验证**：`AdminRepoRollbackRequest.snapshotId` 已标注"应校验归属"，但实现未变。
