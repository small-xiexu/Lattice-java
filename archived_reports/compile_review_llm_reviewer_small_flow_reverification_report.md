# compile review LLM reviewer 小流量复验报告

验证时间：2026-05-17
验证角色：agentD（验证/测试）
验证类型：小流量 compile 复验（仅通过环境变量启用，不改代码）

## 1. 前置检查

### 1.1 Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1853
- ALLOWLIST：239

### 1.2 Git Status

```
## codex/qa-polish...origin/codex/qa-polish
 M special_cases_report.md
?? compile_review_llm_reviewer_enablement_gate_report.md
?? compile_review_llm_reviewer_enablement_readiness_report.md
?? compile_review_llm_reviewer_loop_design_report.md
?? compile_review_llm_reviewer_small_flow_verification_report.md
?? compile_review_llm_reviewer_test_db_binding_setup_report.md
?? current_gate_snapshot_after_query_visibility.md
```

工作区仅有未跟踪报告文件，无源码/配置修改。

### 1.3 数据库确认

| 项目 | 值 |
|---|---|
| 服务 datasource | `jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge-test?currentSchema=lattice` |
| 主库触碰 | 否（未读取、未写入、未 compile） |

### 1.4 配置确认

| 项目 | 值 |
|---|---|
| review-enabled | 仅运行时环境变量 `LATTICE_LLM_REVIEW_ENABLED=true` |
| 是否修改 lattice-llm.yml | 否 |
| 是否修改 application*.yml | 否 |
| 是否修改源码 | 否 |
| 测试库 LLM 配置 | 已在前序轮次补齐（connections=3, model_profiles=2, bindings=3） |

## 2. 编译执行

### 2.1 测试源

| 项目 | 值 |
|---|---|
| sourceCode | `smoke-test-v2` |
| sourceType | SERVER_DIR |
| contentProfile | MARKDOWN |
| 文件 | `smoke-test-facts.md`（3 条中性事实：Alpha/Beta/Gamma 服务） |

### 2.2 执行流程

```
compile_new_articles (31.6s)
  → review_articles (12.1s)
    → refresh_vector_index
      → generate_synthesis_artifacts
        → finalize_job
```

无 fix loop（LLM reviewer 直接 approved）。

### 2.3 详细日志

| 步骤 | route | 结果 | 耗时 |
|---|---|---|---|
| compile_new_articles | `compile.writer.baseline-gpt-5-5-chat` | articleCreated=true | 31572ms |
| review_articles | `compile.reviewer.baseline-gpt-5-5-chat` | **passed=true** | 12110ms |

### 2.4 错误数

LLM 调用失败次数：**0**。本轮的 compile 无 `llm_raw_call_failed` 或 `llm_retry_attempt_failed` 日志。

## 3. 复验项逐一核验

### 3a. writer route 为测试库 compile writer route

**通过。**

日志：`route: compile.writer.baseline-gpt-5-5-chat`，与测试库 binding 一致。

### 3b. reviewer route 非 rule-based

**通过。**

| 来源 | route 值 |
|---|---|
| 后台 API `reviewSummary.reviewRoute` | `compile.reviewer.baseline-gpt-5-5-chat` |
| 后台 API `reviewSummary.reviewModeLabel` | `LLM 审查` |
| 服务日志 | `route: compile.reviewer.baseline-gpt-5-5-chat` |

### 3c. reviewer 输出 JSON 可被解析

**通过。**

- Reviewer 调用成功返回（无 parse failed 日志）
- `passed: true` 表明 `ReviewResultParser` 成功解析 LLM 输出的 JSON
- 未触发 `PARSE_FAILED` 状态

### 3d. LLM approved → passed → persist

**通过。**

| 检查项 | 结果 |
|---|---|
| acceptedCount | **1** |
| article.review_status | **passed** |
| article.lifecycle | **ACTIVE** |
| compile_jobs.persisted_count | **1** |
| article_chunks 数量 | **2** |

数据库验证：

```
articles: id=2, review_status=passed, lifecycle=ACTIVE
article_chunks: 2 rows
compile_jobs: persisted_count=1, status=SUCCEEDED
```

### 3e. fail-closed（本轮未触发）

本轮 LLM reviewer 调用成功，未触发 fail-closed。但前序轮次已验证：

| 场景 | 前序验证结果 |
|---|---|
| LLM 调用异常（401 auth error）→ TIMEOUT_FALLBACK | needs_human_review，persisted_count=0 |
| fail-closed 不进入 rule-based pass | reviewRoute 显示为尝试调用的模型（anthropic），非 rule-based |

### 3f. 后台/API 可观测性

**通过。**

后台 API 返回完整信息：

| 字段 | 值 |
|---|---|
| reviewStepPresent | true |
| reviewRoute | `compile.reviewer.baseline-gpt-5-5-chat` |
| reviewModeLabel | `LLM 审查` |
| acceptedCount | 1 |
| pendingReviewCount | 0 |
| needsHumanReviewCount | 0 |
| fixStepPresent | false |
| fixAttemptCount | 0 |
| fixDisplayMessage | `未触发自动修复：无 fixable issue` |

progressSteps 详情：

| step | status | detail |
|---|---|---|
| TASK_RECEIVED | COMPLETED | - |
| COMPILE_NEW_ARTICLES | COMPLETED | - |
| REVIEW_ARTICLES | COMPLETED | LLM 审查 · review_articles · model_route=compile.reviewer.baseline-gpt-5-5-chat · acceptedCount=1 |
| FINALIZE_JOB | COMPLETED | 入库完成 |

## 4. 验证后恢复

| 项目 | 状态 |
|---|---|
| 服务 | 已停止 |
| LATTICE_LLM_REVIEW_ENABLED 环境变量 | 未设置（默认 false） |
| 测试库 smoke 数据 | 已清理（articles=0, knowledge_sources=0） |
| 测试库 LLM 配置 | 保留（后续复用不需要重新补齐） |
| 主库 | 未触碰（articles=4, 均为既存 passed/ACTIVE） |

## 5. 与上一轮对比

| 验证项 | 上一轮（test-db, 无 binding） | 本轮（test-db, 有 binding） |
|---|---|---|
| writer route | openai（bootstrap 默认） | compile.writer.baseline-gpt-5-5-chat |
| reviewer route | anthropic（bootstrap 默认） | compile.reviewer.baseline-gpt-5-5-chat |
| LLM 调用 | 401 失败 | 成功 |
| review 结果 | needs_human_review | accepted（passed） |
| JSON 解析 | 未验证（auth 失败，未到 parser） | 验证通过 |
| persisted_count | 0 | 1 |
| 结论 | fail-closed 验证通过 | 正向路径验证通过 |

## 6. 合规检查

| 检查项 | 结果 |
|---|---|
| 修改 src/main/java/** | **否** |
| 修改 src/main/resources/** | **否** |
| 修改 .claude/t1.md | **否** |
| 修改测试/脚本 | **否** |
| 触碰主库（写入/compile） | **否** |
| 输出 API key / secret | **否** |
| 将 LATTICE_LLM_REVIEW_ENABLED 写入配置文件 | **否** |
| 提交代码 | **否** |
| review-enabled 默认值 | 仍为 false（仅本轮运行时临时启用） |

## 7. 结论

**LLM reviewer 小流量复验全部通过。**

| 验证项 | 结果 |
|---|---|
| writer route = 测试库 compile writer binding | 通过 |
| reviewer route != rule-based | 通过 |
| reviewer JSON 输出可解析 | 通过 |
| LLM approved → passed → persist | 通过 |
| fail-closed（前序轮次） | 通过 |
| 后台 API 可观测性 | 通过 |
| 恢复默认关闭 | 通过 |
| 主库未触碰 | 通过 |

全链路验证结果：
1. LLM reviewer 使用 `compile.reviewer.baseline-gpt-5-5-chat` 成功调用并返回可解析 JSON
2. LLM approved 的文章经 review → passed → persist gate 完整入库
3. 后台 API 提供完整的 route、status、fix 可观测性
4. fail-closed 在前序轮次已独立验证

**建议：可以进入下一阶段——小范围启用策略设计。**

## 8. 本轮修改说明

本轮是否修改代码：否。
本轮是否修改配置文件：否。
本轮是否启用 LLM reviewer：是（仅运行时环境变量，已恢复）。
本轮是否运行 compile：是（测试库，已清理）。
本轮是否触碰主库：否。
本轮是否输出 secret：否。
本轮仅新增本报告：`compile_review_llm_reviewer_small_flow_reverification_report.md`。
