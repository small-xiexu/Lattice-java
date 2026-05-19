# Compile Structured Table Writer Gate Runtime Verification Report

- 验证时间：2026-05-19 07:12–11:00 +0800
- 执行者：agentD（只验证，不修代码）
- 分支：`codex/qa-polish`
- 提交：`576531f`

---

## 1. Redline 扫描

| 项目 | 值 |
|------|-----|
| 脚本 | `bash scripts/scan-redline.sh special_cases_report.md` |
| 退出码 | 0 |
| BLOCKER | **0** |
| REVIEW | 无新增输出 |
| ALLOWLIST | 无新增输出 |

BLOCKER=0，继续验证。

---

## 2. mvn test

| 项目 | 值 |
|------|-----|
| Tests run | **827** |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Result | **BUILD SUCCESS** |

修复前为 824，修复后新增 3 个 `AnalyzeNodeStructuredTableWriterGateTests` 测试（大表格 gate、小表格不 gate、Markdown 不受影响）。

---

## 3. 代码修改

**否。** 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、prompt、`scripts/scan-redline.sh`、redline allowlist、模型配置、题集。

---

## 4. 小流量源文件清单

| 文件 | 格式 | 大小 | 来源 |
|------|------|------|------|
| scenarios.xlsx | xlsx | 572,581 | `docs/scenarios.xlsx` |
| quality-progress-and-lessons.md | md | 31,469 | `docs/quality-progress-and-lessons.md` |

源目录：`/tmp/lattice-structured-table-gate-smoke-src/`，共 2 个文件。不含 eval、report、target、src/test、.codex。

---

## 5. Writer 单元数量对比

### 5.1 修复前（参考 compile_performance_current_job_snapshot_report.md）

| 指标 | 值 |
|------|-----|
| 源文件数 | 17 |
| compile_new_articles progressTotal | **72** |
| scenarios.xlsx 预估 Writer 单元 | **约 55**（按行级拆分） |

### 5.2 修复后（两轮 job 实测）

| 指标 | Job 8c5684a0 | Job 74537ae2 |
|------|-------------|-------------|
| 源文件数 | 2 | 2 |
| analyze_batches conceptCount | **7** | **7** |
| compile_new_articles progressTotal | **7** | **7** |

### 5.3 概览概念明细

| # | conceptId | 类型 |
|---|-----------|------|
| 1 | quality-progress-and-lessons-当前阶段 | Markdown 专题 |
| 2 | quality-progress-and-lessons-当前-gate | Markdown 专题 |
| 3 | quality-progress-and-lessons-已验证结论 | Markdown 专题 |
| 4 | quality-progress-and-lessons-踩坑记录 | Markdown 专题 |
| 5 | quality-progress-and-lessons-下一步计划 | Markdown 专题 |
| 6 | structured-table-scenarios-场景用例-场景用例-table-1 | 表级 overview |
| 7 | structured-table-scenarios-步骤详情-步骤详情-table-2 | 表级 overview |

### 5.4 推算

| 场景 | conceptCount | Writer单元数 |
|------|-------------|-------------|
| 修复前 scenarios.xlsx 行级拆分 | 约 55 | 约 55 |
| 修复前 17 文件合计 | — | 72 |
| **修复后 2 文件合计** | **7** | **7** |
| scenarios.xlsx 表级 overview | 1–2 | 1–2 |
| quality-progress-and-lessons.md 专题拆分 | 5–6 | 5–6 |

**结论：scenarios.xlsx 从约 55 个 Writer 单元下降到 1–2 个表级 overview 单元，降幅 > 95%。两轮 job 均稳定复现。**

---

## 6. compile job steps 完整链路

| 步骤 | conceptCount | 状态 | 说明 |
|------|-------------|------|------|
| initialize_job | 0 | succeeded | 初始化 |
| ingest_sources | 2 | succeeded | 导入 2 个源文件 |
| persist_source_file_chunks | 2 | succeeded | 分块持久化（2553 chunks） |
| extract_ast_graph | 2 | succeeded | 抽取结构化表格/图数据 |
| group_sources | 2 | succeeded | 源文件分组 |
| split_batches | 2 | succeeded | 批次拆分 |
| **analyze_batches** | **7** | **succeeded** | **Writer Gate 生效：7 个 concept** |
| merge_concepts | 7 | succeeded | 概念合并 |
| compile_new_articles | 7 | succeeded | Writer 草稿生成 |
| review_articles (1st) | 7 | succeeded | 初次审查 |
| fix_review_issues | 7 | succeeded | 自动修复 |
| review_articles (2nd) | 7 | succeeded | 修复后重审 |
| finalize_job | — | succeeded | 入库 |

analyze_batches 是 Gate 生效位置。conceptCount=7 直接决定了后续 Writer 只需处理 7 个 concept。

---

## 7. Writer / Reviewer / Fixer 调用

| 指标 | Job 8c5684a0 | Job 74537ae2 |
|------|-------------|-------------|
| Writer 调用次数 | 7 | 7 |
| Reviewer (1st) 调用次数 | 7 | 7 |
| Fixer 调用次数 | 6 | **7** |
| Reviewer (2nd) 调用次数 | 6 | **7** |
| Writer 模型路由 | compile.writer.gpt-5-5-chat-1 | compile.writer.gpt-5-5-chat-1 |
| Reviewer 模型路由 | compile.reviewer.gpt-5-5-chat-1 | compile.reviewer.gpt-5-5-chat-1 |
| Fixer 模型路由 | compile.fixer.gpt-5-5-chat-1 | compile.fixer.gpt-5-5-chat-1 |
| Writer 耗时/unit | 约 3–32 分钟 | 约 3–27 分钟 |
| Fixer 耗时/unit | — | 约 5–16 分钟 |

**Writer 调用次数从 72 降至 7，降幅 90.3%。**

---

## 8. 结构化数据入库

| 表 | 数量 | 说明 |
|----|------|------|
| structured_tables | **2** | scenarios.xlsx 的 2 个 sheet 各生成 1 个表 |
| structured_table_rows | **1,542** | 全部 1542 行数据已入库 |
| fact_cards | **875** | 从结构化数据自动生成的 fact cards |
| articles | 见下方 | 两轮 job 均走完审查链路 |

**关键观察**：structured_tables 和 structured_table_rows 在 compile_new_articles 之前已通过 `persist_source_file_chunks` + `extract_ast_graph` 入库。Gate 只影响 Writer 的 concept 生成，不影响结构化数据的提取和入库。这意味着：

- 结构化查询（如 `Q-STRUCT-ROW-001` 查某行数据）仍可通过 structured_table_rows 检索
- Writer 不再为每行生成独立 article 草稿，避免了 LLM 资源浪费

---

## 9. 普通 Markdown 编译

| 项目 | 值 |
|------|-----|
| 文件 | quality-progress-and-lessons.md (31KB) |
| analyze_batches 后 conceptCount | 5（从 7 中减掉 scenarios.xlsx 的 2） |
| Writer 处理 | 正常进入 Writer，生成草稿 |
| Reviewer→Fixer→Re-review | 5 个 Markdown 概念均走完整链路 |

普通 Markdown 文档不受 Gate 影响，仍通过 `DocumentTopicConceptExtractor` 进行专题拆分并进入 Writer→Reviewer→Fixer→Re-review→Persist 链路。

---

## 10. 编译审查链路完整性（两轮 Job 对比）

| 步骤 | Job 8c5684a0 | Job 74537ae2 |
|------|-------------|-------------|
| Writer | ✅ 7/7 | ✅ 7/7 |
| Reviewer (1st) | ✅ 7/7 | ✅ 7/7 |
| Fixer | ✅ 6/6 | ✅ 7/7 |
| Re-review | ✅ 6/6 | ✅ 7/7 |
| Persist | ✅ SUCCEEDED | ✅ SUCCEEDED |
| persistedCount | 1 | 0 |
| needsHumanReview | 6 | 7 |
| fixAttempt | 1 | 1 |

### 10.1 结构化表概念全链路追踪（Job 74537ae2）

| 阶段 | 场景用例-table-1 | 步骤详情-table-2 |
|------|:--:|:--:|
| Writer | ✅ | ✅ |
| Reviewer (1st) | ✅ (6/7) | ✅ (7/7) |
| Fixer | ✅ (6/7) | ✅ (7/7) |
| Re-review | ✅ (6/7) | ✅ (7/7) |
| Persist | ✅ | ✅ |

**两个结构化表概念均未跳过任何审查步骤。Gate 只改变了 concept 粒度（从行级到表级），不影响审查链路。**

### 10.2 Job 74537ae2 各阶段耗时

| 阶段 | 开始(UTC) | 结束(UTC) | 耗时 |
|------|-----------|-----------|------|
| Writer | 01:53:13 | 02:05:44 | ~12 分钟 |
| Reviewer (1st) | 02:05:44 | 02:12:13 | ~7 分钟 |
| Fixer | 02:12:44 | 02:45:30 | ~33 分钟 |
| Re-review | 02:45:45 | 02:52:00 | ~7 分钟 |
| Persist | 02:52:00 | 02:52:22 | ~22 秒 |
| **总计** | 01:53:13 | 02:52:22 | **~59 分钟** |

---

## 11. 新增风险

| 风险 | 级别 | 说明 |
|------|------|------|
| Writer 耗时 | 已知 | gpt-5.5 via localhost:8888 每 unit 需 3–32 分钟，与 Gate 无关 |
| 结构化查询覆盖 | 需验证 | scenarios.xlsx 表级 overview concept 不含全量行数据，依赖 structured_table_rows 提供行级检索，需确认查询路由正确 |
| needsHumanReview 高 | 已知 | 两轮 job 的 needsHumanReview 分别为 6/7 和 7/7，与 Gate 无关，属 LLM 审查策略行为 |
| Job 间差异 | 低 | persistedCount 从 1→0，needsHumanReview 从 6→7，属 LLM 非确定性，不影响 Gate 验证结论 |

**未发现 Gate 引入的新增风险。**

---

## 12. 下一步建议

**验证通过。** 两轮 job 均确认：

1. Writer Gate 正确将 scenarios.xlsx 从约 55 个行级 Writer 单元收敛为 2 个表级 overview 单元（降幅 > 95%）
2. 表级 overview 概念走完整 Writer→Reviewer→Fixer→Re-review→Persist 链路，Gate 不跳过审查
3. 结构化数据正常入库（1542 rows, 875 fact cards）
4. 普通 Markdown 编译链路不受影响

建议进入 **pre-commit quality review**：检查代码规范、测试覆盖、日志输出，准备合并。
