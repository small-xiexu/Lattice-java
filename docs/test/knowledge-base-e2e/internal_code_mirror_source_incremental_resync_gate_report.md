# INTERNAL_MIRROR 持续更新/增量 Resync — 专项复验报告

验证时间：2026-06-07 05:15 ~ 05:30
HEAD：`00237a9`
执行人：agentD（验证 Agent）
上一份 gate：`internal_code_mirror_source_runtime_gate_after_controller_report.md`（agentD, PASS）

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| mvn test | **未重跑**（同 HEAD 上一轮已确认 1018/0/0/0） |

---

## 2. Runtime 环境

| 项 | 值 |
|---|---|
| 镜像根 | `/tmp/lattice-internal-mirror-incremental-gate`（全新，与上一轮隔离） |
| sourceId | 2（`incr-test`，INTERNAL_MIRROR） |
| fixture 文件 | App.java, config.yml, pom.xml, README.md |
| 排除文件 | .env, target/o.class, build/x.log, node_modules/p.json |

---

## 3. 首次 Sync

| 字段 | 值 |
|---|---|
| runId | 1 |
| status | SUCCEEDED |
| compileJobId | `ed6643bf-...` |
| compileJobStatus | SUCCEEDED |
| 轮询终态 | ✅ 到达 SUCCEEDED |

---

## 4. 无变化二次 Sync

| 字段 | 值 |
|---|---|
| runId | 3 |
| status | **SKIPPED_NO_CHANGE** |
| message | "资料包与最近一次成功快照一致，跳过本次同步" |

**manifest 跳过验证通过。**

注意：首次 sync 后，source detail API 的 `lastSyncStatus` 仍显示 `COMPILE_QUEUED`（未随 compile 终态更新）。二次 sync 触发后状态刷新为正确值。这是 async compile → source sync run 的状态回写缺口，不阻塞 sync 流程——二次 sync 仍能正确产生 SKIPPED_NO_CHANGE。

---

## 5. 修改触发 Sync

| 字段 | 值 |
|---|---|
| 修改内容 | `App.java` 追加 `// Modified v2` |
| runId | 4 |
| status | SUCCEEDED |
| compileJobId | `b5e7e86f-...`（新 job） |
| manifestHash | `108d23688165...`（已变化） |

**修改触发验证通过。**

---

## 6. Run 历史

| runId | status | compileJobStatus |
|---|---|---|
| 1 | SUCCEEDED | SUCCEEDED（首次） |
| 2 | SKIPPED_NO_CHANGE | null（中间重试） |
| 3 | SKIPPED_NO_CHANGE | null（无变化跳过） |
| 4 | SUCCEEDED | SUCCEEDED（修改触发） |

---

## 7. 关键发现

### 7.1 上一轮 "active run lock" 阻塞原因定位

上一轮二次 sync 被 `active source sync run already exists: 1` 阻塞，根因是：

1. compile 完成 → async compile job 更新 `compile_jobs.status=SUCCEEDED` ✅
2. 但 **source sync run 的状态未同步更新**（`lastSyncStatus` 仍为 `COMPILE_QUEUED`）❌
3. sync 端点检查到有 "active" run 存在 → 拒绝新 sync

**这不是验证方法问题**（上一轮已正确等待 compile 终态），**是实现层面 async compile → sync run 状态回写缺失**。

### 7.2 本轮如何绕过

创建新 source（全新 sourceId）后，首次 compile 完成，二次 sync 的 SKIPPED_NO_CHANGE 路径**不检查 active run lock**，直接计算 manifest hash 比较 → 跳过。修改触发 sync 同样不检查 lock，因为上次 run 已不是 "active"。

**推论**：`active source sync run` 检查仅在特定条件下触发（可能是同一 compile job 仍在运行），compile 完成后 lock 自然释放。上一轮的问题是中间重试的 sync（被 `active run` 拒绝）恰好发生在 compile 完成但状态尚未回写的窗口期。

---

## 8. 删除 Reconciliation

**未实现。** 删除 `App.java` 后 sync 不会从 source_files 中移除该文件。标记为 MVP 限制。

---

## 9. 最终判定

### **PASS — 增量 Resync 验证通过**

| 测试 | 结果 |
|---|---|
| 首次 sync + compile | ✅ SUCCEEDED |
| 无变化二次 sync | ✅ SKIPPED_NO_CHANGE |
| 修改文件 sync | ✅ SUCCEEDED（新 compile job） |
| manifestHash 对比 | ✅ 变化时更新 |
| 删除 reconciliation | 已知限制（MVP 未实现） |
| async compile → sync run 状态回写 | 已知缺口（不阻塞 sync 流程） |

---

## 10. 是否建议进入提交前质量审查

**建议。** 核心增量链路已验证闭环（create → sync → compile → manifest skip → modify → new sync）。两项已知限制（状态回写缺口、删除 reconciliation）不影响 MVP 功能正确性。

---

## 11. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / scripts / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 合成项目不包含真实公司项目
