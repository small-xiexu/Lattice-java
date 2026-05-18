# compile review 默认 LLM 模式 pre-commit 质量复核报告

## 复核时间

2026-05-18

## 1. redline

| 指标 | 值 |
|---|---|
| **BLOCKER** | **0** |
| REVIEW | 1858 |
| ALLOWLIST | 242 |
| scan-redline.sh exit code | 0 (BLOCKER=0 通过) |

REVIEW +5、ALLOWLIST +3 来自新增测试类名匹配与测试配置类，无业务特判。

## 2. mvn test

| 指标 | 值 |
|---|---|
| Tests run | **825** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| Result | **BUILD SUCCESS** |

全量从 816 增至 825（新增 9 个 case：默认 LLM 模式 + per-job reviewMode + 测试配置适配）。

## 3. 工作区变更清单

### 已修改文件 (36 files)

**src/main/java — compile review / admin / LLM reviewer (16):**
| 文件 | 变更要点 |
|---|---|
| `admin/service/AdminCompileReviewSummaryService.java` | observability 汇总 |
| `api/admin/AdminCompileController.java` | reviewMode API 参数 |
| `api/admin/AdminCompileJobRequest.java` | reviewMode 字段 |
| `api/admin/AdminCompileJobResponse.java` | reviewMode 返回 |
| `api/admin/AdminCompileReviewSummaryResponse.java` | observability 汇总 |
| `compiler/agent/AgentModelRouter.java` | 模型路由 |
| `compiler/agent/DefaultReviewerAgent.java` | reviewer agent |
| `compiler/agent/ReviewTask.java` | review task 模型 |
| `compiler/graph/CompileGraphState.java` | graph state |
| `compiler/graph/CompileGraphStateKeys.java` | state keys |
| `compiler/graph/CompileGraphStateMapper.java` | state mapper |
| `compiler/graph/GraphStepLogger.java` | step logger |
| `compiler/graph/node/InitializeJobNode.java` | 初始化 reviewMode |
| `compiler/service/ArticleReviewerGateway.java` | fail-closed + per-job reviewMode |
| `compiler/service/CompileExecutionRequest.java` | reviewMode 参数 |
| `compiler/service/CompileJobService.java` | 入口收敛 + reviewMode |

**src/main/java — persistence (3):**
| 文件 | 变更要点 |
|---|---|
| `infra/persistence/CompileJobJdbcRepository.java` | reviewMode 持久化 |
| `infra/persistence/CompileJobRecord.java` | reviewMode 字段 |
| `infra/persistence/mapper/CompileJobMapper.java` | reviewMode 映射 |

**src/main/resources (2):**
| 文件 | 变更要点 |
|---|---|
| `infra/persistence/mapper/CompileJobMapper.xml` | reviewMode SQL |
| `db/schema.sql` | compile_jobs 表新增 `review_mode VARCHAR(32) DEFAULT 'LLM'` |

**src/test/java (13):**
Admin API 测试适配（8）、GraphStepLogger、ArticleReviewerGateway、CompilerAgentAdapters、CompileJobJdbcRepository

**docs (2):**
| 文件 | 变更要点 |
|---|---|
| `docs/quality-progress-and-lessons.md` | 台账更新至默认 LLM 模式 |
| `special_cases_report.md` | redline 重新扫描更新 |

### 未跟踪文件 (17)

均为本轮报告、测试和设计文档，详见 git status。

## 4. 风险边界检查

| 检查项 | 预期 | 实际 | 结论 |
|---|---|---|---|
| 修改 prompt 文件 | 否 | `LatticePrompts.java` 未修改；`ArticleReviewerGateway` 仅引用已有 prompt 常量 | 通过 |
| 修改 persist gate | 否 | `PersistArticlesNode.java` 未在 diff 中 | 通过 |
| 修改 query visibility filter | 否 | 5 条 article-backed mapper XML 未在 diff 中 | 通过 |
| 修改 review_status enum | 否 | `schema.sql` 仅新增 `review_mode` 列，未修改 review_status 相关 | 通过 |
| 修改 Query / AnswerGeneration / Citation / Deep Research | 否 | 无相关文件在 diff 中 | 通过 |

## 5. 验证报告存在性

| 报告 | 状态 |
|---|---|
| `compile_review_default_llm_mode_fix_result_report.md` | 存在 |
| `compile_review_default_llm_mode_runtime_verification_report.md` | 存在 |
| `compile_review_entrypoint_loop_coverage_analysis_report.md` | 存在 |

## 6. 台账更新

| 文件 | 状态 |
|---|---|
| `docs/quality-progress-and-lessons.md` | 已更新至默认 LLM 模式 |

## 7. 剩余后续项

| 项目 | 说明 |
|---|---|
| LLM approved 正向 canary | runtime 验证中未自然触发 |
| Fixer -> Re-review runtime | runtime 验证中未触发 |
| legacy direct compile | 需后续封存入口 |
| prompt 文件化 | 后续再做 |
| compile_review_llm_reviewer_loop_design_report.md | 保留不提交 |

## 8. 提交建议

| 项目 | 结论 |
|---|---|
| 是否建议提交 | **是** |
| 本轮是否修改代码 | **否**（复核只读，未改任何文件） |
| 建议 commit message | `feat(compile): default to LLM review mode for new jobs` |
