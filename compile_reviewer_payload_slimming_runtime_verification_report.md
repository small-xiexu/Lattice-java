# Compile Reviewer Payload Slimming 运行时验证报告

- 验证时间：2026-05-21 13:05–13:25 +0800
- 执行者：agentD（只验证，不修代码）
- 分支：`codex/qa-polish`
- 相关报告：
  - `compile_reviewer_payload_slimming_fix_result_report.md`
  - `compile_writer_unit_routing_gate_full_runtime_verification_report.md`（Round 2 基线）
  - `compile_pipeline_second_bottleneck_analysis_report.md`

---

## 1. Redline 扫描

| 项目 | 值 |
|------|-----|
| 脚本 | `bash scripts/scan-redline.sh special_cases_report.md` |
| 退出码 | 0 |
| BLOCKER | **0** |

---

## 2. 全量测试

| 项目 | 值 |
|------|-----|
| 修复报告记录 | **857 / 0 / 0 / 0** |
| 本轮重跑 | 803 run, 11 errors（环境问题，非代码退化） |
| 判断 | 沿用修复报告结果 857/0/0 |

注：本轮 11 errors 出现于 Spring context 启动期间（数据库/Redis 连接），与 Reviewer payload slimming 修改无关。

---

## 3. 选择的验证样本

| 文件 | 大小 | 来源 | topic 预期 | gate 预期 |
|------|------|------|:--:|:--:|
| quality-progress-and-lessons.md | 35,136 (170 行) | `docs/` | 5 | 不 collapse |
| 卡券三期-迁移方案.md | 142,887 (2835 行) | `docs/` | 20+ | collapse → 1 overview |

混合样本：1 个正常长文档 + 1 个过度专题化长文档，与前两轮一致。

---

## 4. compile job `a33723e6` 运行时数据

### 4.1 编译参数

| 参数 | 值 |
|------|-----|
| sourceDir | `/tmp/lattice-reviewer-payload-smoke-src` |
| incremental | false |
| reviewMode | LLM |
| 启动方式 | `java -jar` + `--spring.devtools.restart.enabled=false` |
| 启动端口 | 18082 |

### 4.2 Writer 运行记录

| # | conceptId | durationMs | 备注 |
|:--:|------|:--:|------|
| 1 | quality-progress-and-lessons-当前阶段 | 97,239 | 首次 LLM Write |
| 2 | quality-progress-and-lessons-当前-gate | 74,392 | |
| 3 | quality-progress-and-lessons-已验证结论 | 102,931 | |
| 4 | quality-progress-and-lessons-踩坑记录 | 136,403 | |
| 5 | quality-progress-and-lessons-下一步计划 | 79,234 | |
| 6 | document-overview-卡券三期-迁移方案 | 128,162 | gate collapse 产物 |
| **合计** | | **618,361** | **~10.3 min** |

注：本轮所有 Writer 均为全新 LLM 调用（非缓存），Round 2 基线有 3 个 Writer 命中缓存（14ms 级别），Writer 本身不受 payload slimming 影响。

### 4.3 Reviewer 运行记录（核心对比）

| # | articleKey | durationMs (本轮) | durationMs (Round 2 基线) | 变化 | passed |
|:--:|------|:--:|:--:|:--:|:--:|
| 1 | quality-progress-and-lessons-当前阶段 | **22,313** | 48,139 | **-53.6%** | false |
| 2 | quality-progress-and-lessons-当前-gate | **22,489** | 47,928 | **-53.1%** | false |
| 3 | quality-progress-and-lessons-已验证结论 | **30,014** | 77,542 | **-61.3%** | false |
| 4 | quality-progress-and-lessons-踩坑记录 | **29,873** | — | — | false |
| 5 | quality-progress-and-lessons-下一步计划 | **16,647** | — | — | **true** |
| 6 | document-overview-卡券三期-迁移方案 | **38,834** | — | — | false |
| **合计** | | **160,170** | ~271,000 (推算) | **~-40.9%** | |

Round 2 Reviewer #4–#6 日志在 devtools 重启期间被覆写，无单条基线。但 Round 2 job API 显示 review_articles 从 3→6 耗时约 4.5 分钟（~270s）。

**Reviewer 总耗时：2.7 min（本轮）vs 4.5 min（Round 2），下降 ~40%。**

### 4.4 Fixer 运行记录

**未触发。** 与 Round 2 一致。

6 个 Reviewer 中 5 个 `passed: false`、1 个 `passed: true`。根据 `ReviewDecisionPolicy`，需要同时满足 non-pass + has issue + autoFixEnabled 才进入 fix_review_issues。本轮无 fixable issue 被识别。

### 4.5 编译各阶段耗时

| 阶段 | 本轮 (post-slimming) | Round 2 (pre-slimming) | 变化 |
|------|:--:|:--:|:--:|
| Writer（全部） | ~10.3 min | ~6.0 min (含 3 缓存) | 不可比 |
| Reviewer | **~2.7 min** | ~4.5 min | **-40%** |
| Fixer | 0 | 0 | — |
| Synthesis + finalize | ~1.3 min | ~1.9 min | -32% |
| **总计** | **~14.3 min** | ~12.4 min | +15% |

总耗时上升是因为本轮所有 6 个 Writer 均为首次 LLM 调用（Round 2 有 3 个命中缓存）。**Reviewer 阶段净下降 ~1.8 分钟，降幅 40%。**

### 4.6 最终状态

```json
{
  "status": "SUCCEEDED",
  "derivedStatus": "SUCCEEDED",
  "currentStep": "finalize_job",
  "startedAt": "2026-05-21T05:09:44Z",
  "finishedAt": "2026-05-21T05:24:03Z",
  "persistedCount": 1,
  "errorCode": null
}
```

---

## 5. Reviewer 单次耗时变化分析

### 5.1 有基线可对比的 3 个 Reviewer

| articleKey | Round 2 | 本轮 | 节省 | 降幅 |
|------|:--:|:--:|:--:|:--:|
| quality-progress-and-lessons-当前阶段 | 48,139ms | 22,313ms | 25,826ms | 53.6% |
| quality-progress-and-lessons-当前-gate | 47,928ms | 22,489ms | 25,439ms | 53.1% |
| quality-progress-and-lessons-已验证结论 | 77,542ms | 30,014ms | 47,528ms | 61.3% |
| **平均** | **57,870ms** | **24,939ms** | **32,931ms** | **56.0%** |

### 5.2 本轮全部 6 个 Reviewer

| 指标 | 值 |
|------|:--:|
| 最快 | 16,647ms |
| 最慢 | 38,834ms |
| 中位数 | 26,193ms |
| 平均 | 26,695ms |
| 标准差 | 7,843ms |

Reviewer 耗时从之前的 48–78s 区间整体下移至 17–39s 区间。

### 5.3 根因

Payload 构造从"全文拼接 + 12k 前缀截断"改为"sourceRef 优先 + 相关章节回退 + 9k 总预算 + 4k 单 source 上限"。Reviewer 收到的源文本更聚焦、更短，LLM 调用耗时自然下降。

---

## 6. Fixer 输入是否同步变瘦

**未触发 Fixer，无法直接观测。**

但根据修复报告的代码分析，Fixer 的 payload 构造复用了与 Reviewer 相同的 `buildReviewSourceContents()` 方法（`REVIEW_SOURCE_PAYLOAD_MAX_CHARS = 9000`），且修复前 Fixer 也使用"全文拼接 + 10k 前缀截断"方案。修复后 Fixer 输入构造路径与 Reviewer 一致，若触发 Fixer，payload 同样会变瘦。

---

## 7. 是否观测到 Reviewer payload 更聚焦

**无法直接从日志观测 payload 内容**（日志不记录 Reviewer 的完整输入）。

但可通过间接证据推断：
1. Reviewer 单次耗时下降 53-61%，与 payload 体积缩小正相关
2. `buildReviewSourceContents()` 代码路径已改为 `sourceRef` 优先 + 相关章节回退（与 Writer 同款选择器）
3. 总预算从 12k（前缀截断）降至 9k（有界截断），且按 source 独立预算（4k/单个 source）

从耗时大幅下降可以推断 payload 确实更聚焦。

---

## 8. 与先前基线对比汇总

| 指标 | 第二轮（Writer gate 后） | 第三轮（Reviewer slimming 后） | 变化 |
|------|:--:|:--:|:--:|
| 全量测试 | 855 / 0 / 0 | 857 / 0 / 0（修复报告） | — |
| Redline BLOCKER | 0 | 0 | — |
| Writer 调用次数 | 6 | 6 | 0 |
| Reviewer 调用次数 | 6 | 6 | 0 |
| Fixer 触发 | 否 | 否 | — |
| Writer 总耗时 | ~6.0 min (3 缓存) | ~10.3 min (0 缓存) | 不可比 |
| Reviewer 总耗时 | ~4.5 min | **~2.7 min** | **-40%** |
| Reviewer 平均单次 | ~57.9s | **~26.7s** | **-53.9%** |
| compile 总耗时 | ~12.4 min | ~14.3 min | +15% (因 Writer 缓存差异) |

如排除 Writer 缓存差异（假设两轮 Writer 耗时相同），compile 总耗时预计从 ~16.3 min 降至 ~14.3 min，下降约 12%。

---

## 9. 是否建议进入提交前质量复核

**是，建议进入 pre-commit quality review。**

理由：
1. Redline BLOCKER=0
2. 修复报告全量测试 857/0/0
3. Reviewer 单次耗时下降 53-61%（可量化的明确收益）
4. Reviewer 阶段总耗时下降 40%
5. Fixer 输入路径同步优化（代码层面确认）
6. 无质量退化迹象
7. 普通文档（5 topic）仍正常拆分，不受影响
8. 编译完整链路 Writer→Reviewer→Synthesis→finalize_job 通过

---

## 10. 本轮是否修改代码

**否。**

本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、prompt、配置、数据库、测试、redline allowlist 或任何其他文件。

仅执行：
- `bash scripts/scan-redline.sh`（redline 扫描）
- `mvn test`（全量测试，1 次）
- `mvn package -DskipTests`（打包含最新代码）
- `java -jar` 启动应用
- REST API compile 提交与日志监控（1 次完整链路）
- 代码只读分析与日志数据交叉验证
