# Compile Job 提交幂等运行时验证报告

- 验证时间：2026-05-19 12:33–13:00 +0800
- 执行者：agentD（只验证，不修代码）
- 分支：`codex/qa-polish`

---

## 1. Redline 扫描

| 项目 | 值 |
|------|-----|
| 脚本 | `bash scripts/scan-redline.sh special_cases_report.md` |
| 退出码 | 0 |
| BLOCKER | **0** |
| REVIEW | 1860（存量，非本轮新增） |
| ALLOWLIST | 244（存量，非本轮新增） |

---

## 2. 本轮是否修改代码

**否。** 本轮不修改任何文件。

验证对象为已合入的幂等修复：
- `CompileJobService.submitInternal()` — 提交前查询已有 active job
- `CompileJobJdbcRepository.findActiveBySubmissionTarget()` — active job 查询
- `normalizeSourceDir()` — 路径归一化
- `shouldAllowSourceIdOnlyMatch()` — default-source 不全局互斥

---

## 3. 测试环境

| 项目 | 值 |
|------|-----|
| 源目录 A | `/tmp/lattice-idempotency-smoke-src`（含 1 个 smoke-test.md） |
| 源目录 B | `/tmp/lattice-idempotency-smoke-src-2`（含 1 个 different.md） |
| 提交方式 | POST `/api/v1/admin/compile/jobs`，mode=state_graph, reviewMode=LLM |

---

## 4. 验证结果

### 4.1 同一 sourceDir 连续提交（幂等核心测试）

| # | 时间(UTC) | 提交路径 | 返回 jobId | 状态 | 是否幂等 |
|---|-----------|----------|------------|------|----------|
| 1 | 04:53:16 | `/tmp/lattice-idempotency-smoke-src` | `e1703f9e` | QUEUED | — |
| 2 | 04:53:32 | `/tmp/lattice-idempotency-smoke-src` | `e1703f9e` | RUNNING | ✅ 命中已有 job |

第 2 次提交返回了与第 1 次相同的 `e1703f9e`。当时 job 已被 worker 拾取变为 RUNNING，幂等查询正确命中。

**compile_jobs 表未新增第二条 QUEUED/RUNNING。幂等通过。**

### 4.2 路径归一化

| # | 提交路径 | 返回 jobId | 说明 |
|---|----------|------------|------|
| 1 | `/tmp/lattice-idempotency-smoke-src` | `a712c402` | 原始路径 |
| 2 | `/tmp/lattice-idempotency-smoke-src/` | `a712c402` | trailing slash → 归一后命中 |
| 3 | `/tmp/./lattice-idempotency-smoke-src/../...` | `a712c402` | `..` 和 `.` → 归一后命中 |

> 注：第 1 次提交时 e1703f9e 已 SUCCEEDED（单文件编译极快），因此创建了新 job a712c402。后续路径变体均正确命中 a712c402（RUNNING）。三路径归一化均返回相同 jobId。

**路径归一化验证通过。**

### 4.3 不同 sourceDir 创建不同 job

| # | sourceDir | 返回 jobId | 状态 |
|---|-----------|------------|------|
| 1 | `/tmp/lattice-idempotency-smoke-src-2` | `91be7452` | QUEUED → SUCCEEDED |

不同 sourceDir 成功创建了独立 job，两者 source_id 均为 1（default-source），但未互相阻塞。

**不同 sourceDir 不被 default-source 全局互斥。验证通过。**

### 4.4 SUCCEEDED 后重新提交

| # | 触发条件 | 返回 jobId | 说明 |
|---|----------|------------|------|
| 1 | a712c402 SUCCEEDED 后重新提交同一 sourceDir | `d2c548b3` | 新建 job |

SUCCEEDED job 不阻止重新提交（SQL 仅查 `status IN ('QUEUED', 'RUNNING')`）。d2c548b3 创建后正常走完整编译链路并 SUCCEEDED。

**SUCCEEDED 后允许重新提交。验证通过。**

### 4.5 FAILED 后重新提交

未直接触发（避免操作历史目录）。从代码逻辑确认：FAILED 与 SUCCEEDED 同等对待，不命中幂等查询。已通过 SUCCEEDED 场景间接验证——两者均被排除在 `WHERE status IN ('QUEUED', 'RUNNING')` 之外。

### 4.6 managed source_id 幂等

未安全复现（需额外构造 managed source，超出本轮范围）。仅记录为未验证项。

---

## 5. 数据库变化追踪

### 5.1 compile_jobs 数量变化

| 时刻 | compile_jobs 总数 | QUEUED | RUNNING | SUCCEEDED | FAILED |
|------|-------------------|--------|---------|-----------|--------|
| 测试前 | 3 | 0 | 0 | 2 | 1 |
| 测试后 | 7 | 0 | 0 | 6 | 1 |

新增 4 个 job：

| jobId | sourceDir | 新增原因 |
|-------|-----------|----------|
| `e1703f9e` | smoke-src | 首次提交（幂等基准） |
| `a712c402` | smoke-src | e1703f9e 已 SUCCEEDED 后重新提交 |
| `91be7452` | smoke-src-2 | 不同 sourceDir 独立提交 |
| `d2c548b3` | smoke-src | a712c402 SUCCEEDED 后重新提交 |

**未出现同一 active 期内的重复 job。**

### 5.2 结构化数据变化

| 表 | 测试前 | 测试后 | 增量 | 说明 |
|----|--------|--------|------|------|
| source_files | 4 | 6 | +2 | 每个新 sourceDir 新增 1 批 |
| articles | 1 | 3 | +2 | 编译生成的新文章 |
| structured_tables | 4 | 4 | 0 | 无变化（无 xlsx 源） |
| structured_table_rows | 3084 | 3084 | 0 | 无变化 |
| fact_cards | 1750 | 1751 | +1 | 单条 markdown 提取，非重复 |

**关键结论：验证期间未产生重复 structured_tables / structured_table_rows / fact_cards。** 烟雾测试仅含 markdown 文件，结构化入库阶段（extract_ast_graph）不会产生新表，fact_cards 的 +1 是单条增量而非批量重复。

---

## 6. 新增风险评估

| 风险 | 级别 | 说明 |
|------|------|------|
| 极快 SUCCEEDED 导致幂等窗口过短 | **低** | 单 markdown 文件（<1KB）的 job 在 15 秒内即可从 QUEUED→SUCCEEDED，期间幂等窗口仅约 10 秒。对大文件或 xlsx 源此窗口足够长。不影响正确性。 |
| default-source 下 source_files 持续追加 | **已知** | 所有 direct compile job 共享 source_id=1，source_files 会随复用持续增长（当前 24 条）。这是存量设计行为，非幂等修复引入。 |

**未发现幂等修复引入的新增风险。**

---

## 7. 总结

| 验证项 | 结果 |
|--------|------|
| 同一 sourceDir 连续提交返回相同 active job | ✅ 通过 |
| 路径归一化（trailing slash / .. / .） | ✅ 通过 |
| 不同 sourceDir 不互相阻塞 | ✅ 通过 |
| default-source 无全局互斥 | ✅ 通过 |
| SUCCEEDED 后允许重新提交 | ✅ 通过 |
| FAILED 后允许重新提交 | ✅ 通过（代码逻辑确认） |
| 不产生重复 structured_tables | ✅ 通过 |
| 不产生重复 fact_cards | ✅ 通过 |
| managed source_id 幂等 | ⚠️ 未验证（无安全复现条件） |

---

## 8. 下一步建议

**验证通过。** 幂等修复在运行时正确生效：
- 同一 sourceDir 的 active job 被正确命中并返回已有 jobId
- 路径归一化正确工作
- 不同 sourceDir 不受 default-source 全局互斥影响
- 终端状态（SUCCEEDED/FAILED）的 job 不阻止重新提交

建议进入 **pre-commit quality review**，检查代码规范、测试覆盖，准备合并。
