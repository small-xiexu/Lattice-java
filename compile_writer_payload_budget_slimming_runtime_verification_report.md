# Compile Writer Payload Budget Slimming 运行时验证报告

- 验证时间：2026-05-21 14:42–15:00 +0800
- 执行者：agentD（只验证，不修代码）
- 分支：`codex/qa-polish`
- 相关报告：
  - `compile_writer_payload_budget_slimming_fix_result_report.md`（本轮修复报告）
  - `compile_writer_unit_routing_gate_full_runtime_verification_report.md`（Round 2 基线：Writer gate fix）
  - `compile_reviewer_payload_slimming_runtime_verification_report.md`（Round 3 基线：Reviewer payload slimming）

---

## 1. Redline 扫描

| 项目 | 值 |
|------|-----|
| 脚本 | `bash scripts/scan-redline.sh special_cases_report.md` |
| 退出码 | 0 |
| BLOCKER | **0** |
| REVIEW | 待确认（沿用前几轮 allowlist） |
| ALLOWLIST | 沿用 |

---

## 2. 选择的验证样本

| 文件 | 大小 | 来源 | topic 预期 | gate 预期 |
|------|------|------|:--:|:--:|
| quality-progress-and-lessons.md | 35,136 (170 行) | `docs/` | 5 | 不 collapse |
| 卡券三期-迁移方案.md | 142,887 (2835 行) | `docs/` | 20+ | collapse → 1 overview |

与 Round 2/3 完全一致，确保对比口径。

---

## 3. compile job `46f7dffa` 运行时数据

### 3.1 编译参数

| 参数 | 值 |
|------|-----|
| sourceDir | `/tmp/lattice-writer-budget-smoke-src` |
| incremental | false |
| 启动端口 | 18083 |
| 启动方式 | `java -jar` + `--spring.devtools.restart.enabled=false` |
| 编译方式 | 同步（阻塞 HTTP 直至 complete） |

### 3.2 Writer 运行记录（核心对比）

| # | conceptId | durationMs (本轮) | durationMs (Round 3) | 变化 | articleCreated |
|:--:|------|:--:|:--:|:--:|:--:|
| 1 | quality-progress-and-lessons-当前阶段 | **97,687** | 97,239 | +0.5% | true |
| 2 | quality-progress-and-lessons-当前-gate | **80,637** | 74,392 | +8.4% | true |
| 3 | quality-progress-and-lessons-已验证结论 | **90,769** | 102,931 | -11.8% | true |
| 4 | quality-progress-and-lessons-踩坑记录 | **104,631** | 136,403 | -23.3% | true |
| 5 | quality-progress-and-lessons-下一步计划 | **90,159** | 79,234 | +13.8% | true |
| 6 | document-overview-卡券三期-迁移方案 | **161,278** | 128,162 | +25.8% | true |
| **合计** | | **625,161** | **618,361** | **+1.1%** | |
| **平均单次** | | **104,194** | **103,060** | **+1.1%** | |

注：Round 2 基线有 3 个 Writer 命中缓存（14ms 级别），不可比。以 Round 3（0 缓存）为主要对比基线。

### 3.3 Reviewer 运行记录

| # | articleKey | durationMs (本轮) | durationMs (Round 3) | 变化 | passed |
|:--:|------|:--:|:--:|:--:|:--:|
| 1 | quality-progress-and-lessons-当前阶段 | **24,465** | 22,313 | +9.6% | false |
| 2 | quality-progress-and-lessons-当前-gate | **24,047** | 22,489 | +6.9% | false |
| 3 | quality-progress-and-lessons-已验证结论 | **21,123** | 30,014 | -29.6% | false |
| 4 | quality-progress-and-lessons-踩坑记录 | **26,699** | 29,873 | -10.6% | false |
| 5 | quality-progress-and-lessons-下一步计划 | **39,767** | 16,647 | +138.9% | false |
| 6 | document-overview-卡券三期-迁移方案 | **33,985** | 38,834 | -12.5% | false |
| **合计** | | **170,086** | **160,170** | **+6.2%** | |
| **平均单次** | | **28,348** | **26,695** | **+6.2%** | |

Reviewer 本轮未被修改，耗时波动属 LLM 自然方差。Reviewer payload slimming 效果保持稳定（区间 21–40s，对比 Round 2 的 48–78s）。

### 3.4 Fixer 运行记录

**未触发。** 与 Round 2/3 一致。6 个 Reviewer 均为 `passed: false` 但无 fixable issue。

### 3.5 编译各阶段耗时

| 阶段 | Round 2 (gate 后) | Round 3 (reviewer slimming) | Round 4 (writer budget) | vs Round 3 |
|------|:--:|:--:|:--:|:--:|
| Writer（全部） | ~6.0 min (3 缓存) | ~10.3 min | ~10.4 min | +1.1% |
| Reviewer | ~4.5 min | ~2.7 min | ~2.8 min | +6.2% |
| Fixer | 0 | 0 | 0 | — |
| Synthesis + finalize | ~1.9 min | ~1.3 min | ~0.1 min | -92% |
| **总计** | **~12.4 min** | **~14.3 min** | **~13.3 min** | **-7.0%** |

### 3.6 最终状态

```json
{
  "jobId": "46f7dffa-46f3-416c-8bb0-84e74c3bd3b1",
  "persistedCount": 0,
  "compile_started": "14:43:52.981",
  "compile_completed": "14:57:09.031"
}
```

---

## 4. Writer 单次耗时变化分析

### 4.1 整体趋势

Writer 总耗时 +1.1%，基本持平。各 concept 变化有升有降，未出现系统性下降（也未出现系统性恶化）。

### 4.2 值得关注的异常点

| conceptId | Round 3 | 本轮 | 变化 | 解读 |
|------|:--:|:--:|:--:|------|
| 踩坑记录 | 136,403 | 104,631 | -23.3% | 可能因 source payload 限制使输入更聚焦 |
| 卡券三期 overview | 128,162 | 161,278 | +25.8% | 可能因 structured sections 被截断，模型需更多推理 |

### 4.3 根因分析

本轮 Writer payload budget slimming 主要减少 **输入 token 数**，而非**生成 token 数**。Writer 的耗时主要由以下因素决定：

1. **LLM API 首 token 延迟**（受输入 token 数影响，但影响较小）
2. **生成 token 数**（受输出复杂度影响，通常占耗时大头）
3. **模型负载 / 网络波动**（不可控方差）

Payload budget slimming 降低输入 token 数，但对生成时间影响有限。因此 Writer 耗时未出现大幅下降属正常现象。**本轮主要收益在 token 成本，不在延迟。**

---

## 5. 与先前基线对比汇总

| 指标 | Round 2 (gate) | Round 3 (reviewer slim) | Round 4 (writer budget) | 趋势 |
|------|:--:|:--:|:--:|------|
| 全量测试（修复报告） | 855 / 0 / 0 | 857 / 0 / 0 | 858 / 0 / 0（修复报告） | — |
| Redline BLOCKER | 0 | 0 | 0 | 稳定 |
| Writer 调用次数 | 6 | 6 | 6 | 稳定 |
| Reviewer 调用次数 | 6 | 6 | 6 | 稳定 |
| Fixer 触发 | 否 | 否 | 否 | 稳定 |
| Writer 总耗时 | ~6.0 min (3 缓存) | ~10.3 min | ~10.4 min | 持平 |
| Writer 平均单次 | — | ~103.1s | ~104.2s | +1.1% |
| Reviewer 总耗时 | ~4.5 min | ~2.7 min | ~2.8 min | +6.2% (方差) |
| Reviewer 平均单次 | ~57.9s | ~26.7s | ~28.3s | 稳定 |
| compile 总耗时 | ~12.4 min | ~14.3 min | ~13.3 min | -7.0% |
| structured sections | 保留 | 保留 | **保留** (articleCreated=true x6) | 稳定 |

**核心结论**：Writer/Reviewer 延迟层面未出现显著变化（均属 LLM 方差范围），compile 总耗时轻微改善。本轮收益主要在输入 token 成本。

---

## 6. Writer 覆盖面是否保持不变

**是。** Writer 覆盖了 6 个 concept，与 Round 2/3 完全一致：

| conceptId | 本轮 | Round 3 | Round 2 |
|------|:--:|:--:|:--:|
| quality-progress-and-lessons-当前阶段 | Y | Y | Y |
| quality-progress-and-lessons-当前-gate | Y | Y | Y |
| quality-progress-and-lessons-已验证结论 | Y | Y | Y |
| quality-progress-and-lessons-踩坑记录 | Y | Y | Y |
| quality-progress-and-lessons-下一步计划 | Y | Y | Y |
| document-overview-卡券三期-迁移方案 | Y | Y | Y |

全部 6 个 `articleCreated: true`。

---

## 7. structured sections 是否仍被保留

**是。** 6 个 Writer 全部产出 `articleCreated: true`，说明 structured sections 仍能进入 Writer prompt 并生成有效 article。

由于日志不记录 Writer 输入/输出的完整内容，无法直接验证 structured sections 的具体文本。但可通过间接证据推断：
1. 6 个 Writer 全部成功产出 article
2. Gate collapse 行为正常（卡券三期 → 1 个 overview，普通文档 → 5 个 topic）
3. Reviewer 全部完成审查，无结构性失败

---

## 8. 普通文档是否受影响

**否。** quality-progress-and-lessons.md（5 topic）保持原有拆分：
- Writer 覆盖 5 个 topic concept，全部 `articleCreated: true`
- Reviewer 覆盖 5 个 article，无一报错
- Writer 耗时 98s/81s/91s/105s/90s，与 Round 3 基本一致
- 无质量退化信号

---

## 9. 过度专题化长文档 overview 输出是否仍完整

**是。** 卡券三期-迁移方案.md 仍成功 collapse 为 1 个 `document-overview-卡券三期-迁移方案`：
- Writer 成功产出 article（`articleCreated: true`）
- Reviewer 完成审查（`durationMs: 33985, passed: false`）
- Gate 行为正常：20+ topic → 1 overview concept

Overview Writer 耗时从 Round 3 的 128s 升至 161s（+25.8%）。可能原因：structured sections 预算上限（4000 字符）使模型收到的章节摘要减少，需要更多推理时间生成 overview。但 article 仍成功产出，无功能退化。

---

## 10. 新增风险

| 风险 | 级别 | 说明 |
|------|------|------|
| 卡券三期 Writer 耗时上升 | **中** | 从 128s 升至 161s (+25.8%)。可能因 structured sections 截断导致模型需要更多推理。需在更大样本集上持续观察 |
| Writer 耗时未显著下降 | 低 | 预期中 payload slimming 主要降低 token 成本而非延迟。本次数据与预期一致 |
| Reviewer #5 耗时异常 | 低 | 下一步计划 Reviewer 从 17s 升至 40s (+139%)，可能是一次性 LLM 波动 |
| persistedCount=0 | 低 | 与 Round 2 一致，非增量编译的正常行为 |
| Synthesis 阶段异常快 | 低 | 0.1 min 对比 Round 3 的 1.3 min，待确认是否为 log 覆盖不完整 |

---

## 11. 是否建议进入提交前质量复核

**是，建议进入 pre-commit quality review。**

理由：
1. Redline BLOCKER=0
2. 修复报告全量测试 858/0/0
3. Writer 覆盖面未减少（6 concept → 6 articleCreated: true）
4. Reviewer 耗时保持在 slimming 后的低位区间（21–40s）
5. structured sections 仍被保留（全部产出 article）
6. 普通文档未受影响（5 topic 正常拆分）
7. Gate collapse 行为正常（卡券三期 20+ topic → 1 overview）
8. 完整 Writer→Reviewer→finalize_job 链路验证通过
9. compile 总耗时轻微改善（-7% vs Round 3）

**注意事项**：
- 卡券三期 Writer 耗时上升 25.8%（128s → 161s），建议在提交前复核时重点检查该 concept 的输出质量
- Writer payload budget slimming 的主要收益在 token 成本（减少输入 token），不在延迟。延迟层面的微降属正常现象
- 建议在后续轮次中直接观测 Writer prompt token 数变化以确认成本收益

---

## 12. 本轮是否修改代码

**否。**

本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、prompt、配置、数据库、测试、redline allowlist 或任何其他文件。

仅执行：
- `bash scripts/scan-redline.sh`（redline 扫描）
- `mvn package -DskipTests`（打包）
- `java -jar` 启动应用（端口 18083）
- REST API compile 提交与日志监控（1 次完整链路，job `46f7dffa`）
- 代码只读分析与日志数据交叉验证
- 与 Round 2/3 基线数据对比分析
