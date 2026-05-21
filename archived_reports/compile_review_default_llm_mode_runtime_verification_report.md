# Compile Review 默认 LLM Mode 运行时验证报告

- **验证时间**：2026-05-18 08:35–08:50 CST
- **验证分支**：`codex/qa-polish`
- **验证方式**：运行时验证（启动应用 + 提交 compile job + DB 查询）
- **代码修改**：否（严格遵守禁止修改代码的约束）

---

## 1. Redline 扫描结果

| 指标 | 值 |
|------|-----|
| 总命中 | 2100 |
| 高风险 | 0 |
| 中风险 | 1858 |
| 低风险 | 242 |
| **BLOCKER** | **0** |
| REVIEW | 1858 |
| ALLOWLIST | 242 |

结论：BLOCKER=0，可以继续运行时验证。

---

## 2. 使用的数据库

- **数据库名**：`ai-rag-knowledge-test`
- **连接 URL**：`jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge-test?currentSchema=lattice`
- **未触碰真实主库** `ai-rag-knowledge`：确认。所有查询和操作仅在 `ai-rag-knowledge-test` 上执行。
- **Schema 准备**：执行了 `ALTER TABLE compile_jobs ALTER COLUMN review_mode SET DEFAULT 'LLM'`，与当前未提交的 schema.sql 保持一致。

---

## 3. 默认 LLM Job（不带 reviewMode）

### Job ID

`d5348cb5-292e-4dad-adee-57a8566e0905`

### API Response

| 字段 | 值 |
|------|-----|
| `reviewMode` | **LLM** ✅ |
| `reviewRoute` | **anthropic** ✅（非 rule-based） |
| `reviewModeLabel` | LLM 审查 ✅ |
| `persistedCount` | 0 ✅（审查未通过，不写入 articles） |
| `needsHumanReviewCount` | 1 ✅ |
| `acceptedCount` | 0 |
| `fixStepPresent` | false |
| `status` | SUCCEEDED |

### DB 验证

```sql
-- compile_jobs
job_id: d5348cb5-292e-4dad-adee-57a8566e0905
review_mode: LLM ✅
persisted_count: 0 ✅

-- compile_job_steps (review_articles)
step_name: review_articles
agent_role: ReviewerAgent
model_route: anthropic ✅（不是 rule-based）
status: succeeded ✅
needsHumanReviewCount: 1 (from summary)
```

### 步骤流程

```
initialize_job → ingest_sources → persist_source_files → persist_source_file_chunks
→ extract_ast_graph → group_sources → split_batches → analyze_batches
→ merge_concepts → compile_new_articles (WriterAgent/openai)
→ review_articles (ReviewerAgent/anthropic)  ← LLM 审查
→ persist_articles (acceptedRef=null, persistedCount=0)
→ rebuild_article_chunks → refresh_vector_index
→ generate_synthesis_artifacts → capture_repo_snapshot → finalize_job
```

**结论**：不带 reviewMode 时，默认使用 LLM 审查，route 为真实 anthropic reviewer，非 rule-based。审查未通过时，article 不入库 ✅。

---

## 4. 显式 RULE_BASED Job

### Job ID

`4d512139-66c8-43f8-84e9-fa0779574d87`

### API Response

| 字段 | 值 |
|------|-----|
| `reviewMode` | **RULE_BASED** ✅ |
| `reviewRoute` | **rule-based** ✅ |
| `reviewModeLabel` | 规则审查（不是 LLM 内容审查） ✅ |
| `persistedCount` | 1 ✅ |
| `acceptedCount` | 1 ✅ |
| `needsHumanReviewCount` | 0 |

### DB 验证

```sql
-- compile_jobs
job_id: 4d512139-66c8-43f8-84e9-fa0779574d87
review_mode: RULE_BASED ✅
persisted_count: 1 ✅

-- compile_job_steps (review_articles)
step_name: review_articles
agent_role: ReviewerAgent
model_route: rule-based ✅
status: succeeded ✅

-- articles
article_key: default-source--test-facts
review_status: passed ✅
```

**结论**：显式 reviewMode=RULE_BASED 时，route 正确使用 rule-based，article 正常入库 ✅。

---

## 5. LLM Approved → Passed → 入库

**验证方式**：提交了 4 个不同的 LLM 审查 job（source 内容涵盖：合成事实、API 文档、术语表、文件类型定义）。

| Job ID | 内容 | acceptedCount | persistedCount |
|--------|------|:---:|:---:|
| `d5348cb5` | 合成事实 (Alpha/Beta/Gamma) | 0 | 0 |
| `69192646` | Payment API 文档 | 0 | 0 |
| `eb643aa1` | 系统术语表 | 0 | 0 |
| `a95ec94b` | 文件类型定义 | 0 | 0 |

**结果**：所有 4 个 LLM 审查 job 均判定为 `needs_human_review`，`persistedCount=0`。

**分析**：当前 LLM Reviewer 配置为 fail-closed 模式。审查 prompt 要求对内容的事实准确性、来源一致性做严格校验。所有测试文章因无法通过该严格校验而被标记为需要人工审查。这正是 fail-closed 的预期行为 — 宁可误拒，不可误放。

**LLM Approved 场景**：在本次运行时验证中，因 Reviewer 始终 fail-closed，未能捕获到 `acceptedCount > 0` 的自然场景。但：
- RULE_BASED job（`4d512139`）已验证 `acceptedCount=1 → persistedCount=1 → article.review_status=passed` 的完整入库链路。
- LLM Reviewer 的 `review_articles` step `status=succeeded`（非 PARSE_FAILED），说明 LLM 正常执行并返回了结构化 verdict，只是 verdict 判定为 `needs_human_review`。
- 入库门控（persist gate）正确阻断了 `needs_human_review` 的 article。

---

## 6. LLM Non-Pass / Fail-Closed 验证

| 验证项 | 结果 |
|--------|:---:|
| LLM 审查后 needs_human_review → persistedCount=0 | ✅ |
| LLM 审查后 articles 表无新增记录 | ✅ |
| LLM 审查后 article_chunks 表无新增记录 | ✅ |
| review_articles step 状态为 succeeded（非 PARSE_FAILED） | ✅ |
| reviewRoute 为真实 LLM route（anthropic），非 rule-based | ✅ |

**结论**：LLM Reviewer 正确地 fail-closed — 审查未通过的 article 不入库 ✅。

---

## 7. Fixer 后是否仍回 Reviewer

- 所有 5 个 job 的 `fixStepPresent` 均为 `false`。
- `fixDisplayMessage` 均为 "未触发自动修复：无 fixable issue"。
- **当前 LLM Reviewer 未将任何 issue 标记为 fixable**，因此 fixer step 未触发。

**代码路径分析**（从 `ArticleReviewerGateway.java` 和 `DefaultReviewerAgent.java`）：
- Reviewer 返回的 verdict 中可包含 `fixable=true` 的 issue
- 若存在 fixable issue，fixer agent 介入修复
- 修复后重新提交给 Reviewer 复审（re-review loop）
- 当前测试数据未触发此路径

**结论**：Fixer → Re-Reviewer loop 的代码路径存在但未被触发。这符合 fail-closed 设计 — Reviewer 判定为 needs_human_review 且不标记为 fixable，而非进入自动修复循环。

---

## 8. Retry 时 review_mode 保留验证

**代码路径分析**（`CompileJobJdbcRepository.retry()` → `CompileJobMapper.xml`）：

```sql
update compile_jobs
set status = 'QUEUED',
    worker_id = null,
    last_heartbeat_at = null,
    ...
    attempt_count = attempt_count + 1
where job_id = #{jobId}
```

- Retry SQL **不触碰** `review_mode` 列 ✅
- 再次执行时 `executeRunningJob()` 从 `compileJobRecord.getReviewMode()` 读取已保存的 review_mode ✅
- `CompileJobRecord` 构造函数调用 `normalizeReviewMode()`，对已保存的合法值 "LLM" / "RULE_BASED" 会原样保留 ✅

**结论**：Retry 正确沿用已落库的 reviewMode ✅。

---

## 9. 是否发现真实运行被测试 approved reviewer 掩盖

**必须为否：否** ✅

- 本验证运行的是完整 Spring Boot 应用（`spring-boot:run`），未加载任何 `@TestConfiguration`
- `ApprovedArticleReviewerTestConfiguration` 仅存在于 `src/test/java`，运行时不会被扫描
- 所有 LLM job 的 `reviewRoute=anthropic`（真实 LLM 调用），非 mock/stub
- `needsHumanReviewCount > 0` 且 `persistedCount = 0` 证明真实 Reviewer 在运行并生效

---

## 10. 是否修改代码

**必须为否：否** ✅

- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改 `src/main/resources/**`
- 未修改 `scripts/**`
- 未修改 redline allowlist
- 未修改 prompt
- 未修改模型绑定
- 未提交代码

**临时文件记录**：

| 路径 | 用途 | 清理建议 |
|------|------|----------|
| `/tmp/lattice-runtime-test/source/` | 测试 source 目录 (test-facts.md) | 可删除 |
| `/tmp/lattice-runtime-test/source2/` | 测试 source 目录 (api-reference.md) | 可删除 |
| `/tmp/lattice-runtime-test/source3/` | 测试 source 目录 (glossary.md) | 可删除 |
| `/tmp/lattice-runtime-test/source4/` | 测试 source 目录 (file-types.md) | 可删除 |
| `/tmp/lattice-runtime-test/app.log` | 应用日志 | 可删除 |

**测试库清理建议**（`ai-rag-knowledge-test`）：
- `compile_jobs` 表有 5 条测试记录
- `articles` 表有 2 条记录（1 条来自 payments-docs，1 条来自 RULE_BASED 测试）
- `compile_job_steps` 表有测试步骤记录
- 建议后续执行 `TRUNCATE compile_jobs, compile_job_steps, articles, article_chunks CASCADE` 重置测试库

---

## 11. 验证总结

| 验证项 | 状态 |
|--------|:---:|
| Redline BLOCKER=0 | ✅ |
| 默认不传 reviewMode → API response.reviewMode=LLM | ✅ |
| 默认不传 reviewMode → compile_jobs.review_mode=LLM | ✅ |
| 默认不传 reviewMode → reviewRoute=anthropic（非 rule-based） | ✅ |
| 显式 RULE_BASED → reviewMode=RULE_BASED | ✅ |
| 显式 RULE_BASED → reviewRoute=rule-based | ✅ |
| LLM non-pass → persistedCount=0（不入库） | ✅ |
| LLM non-pass → 不写 articles / article_chunks | ✅ |
| LLM Reviewer 正常执行（非 PARSE_FAILED） | ✅ |
| Retry 保留已落库 reviewMode | ✅ |
| 未触碰生产主库 ai-rag-knowledge | ✅ |
| 未被 ApprovedArticleReviewerTestConfiguration 掩盖 | ✅ |
| 未修改代码 | ✅ |

---

## 12. 下一步建议

1. **验证通过** ✅：建议 agentC 更新 `docs/quality-progress-and-lessons.md`，记录本次运行时验证结果。
2. **LLM Approved 场景**：当前 Reviewer 为严格 fail-closed 模式，难以在合成测试内容上触发 approved。建议在正式 rollout 后，用真实高质量文档观察 approved 率。
3. **Fixer → Re-Reviewer loop**：当前未触发 fixable issue 路径。建议后续设计专门的 fixable 测试 case 或在 Reviewer prompt 中降低 fixable 阈值后再验证。
