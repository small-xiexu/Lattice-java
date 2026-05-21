# compile_article_review_queue 人工确认队列后端运行时验证报告

- **验证时间**：2026-05-20 08:38–08:44 +0800
- **执行者**：agentD（只验证，不修代码）
- **分支**：`codex/qa-polish`
- **验证目标**：compile_article_review_queue 表 + 列表/详情 API + approve/reject 流程端到端运行时行为

---

## 1. Redline 扫描

| 时间 | 阶段 | 退出码 | BLOCKER |
|------|------|--------|---------|
| 08:38 (验证前) | 初始 | 0 | **0** |
| 08:44 (验证后) | 最终 | 0 | **0** |

REVIEW=1860（存量），ALLOWLIST=244（存量）。未产生新 BLOCKER。

---

## 2. 本轮是否修改代码

**否。** 未修改任何 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、前端、prompt、或模型配置。

唯一操作：
- 执行 `scripts/reset-lattice-schema.sh` 清库重建（使 schema 包含 compile_article_review_queue 表）
- 通过 admin API 配置模型连接、profile、binding、compile_review_settings、vector_config

---

## 3. 使用的测试资料源

### Job 1（产生 approve 用 queue 记录）

| 项目 | 值 |
|------|-----|
| 目录 | `/tmp/lattice-hr-queue-test-src/` |
| 文件 | `test-knowledge.md`（门店 POS 终端操作 + 故障排查 + 退款操作，4 个一级标题） |
| Job ID | `0dfd33ae-c8f8-4f8b-af73-25ad28af0282` |
| 结果 | needsHumanReview=1, accepted=0, persisted=0 |

### Job 2（产生 reject 用 queue 记录）

| 项目 | 值 |
|------|-----|
| 目录 | `/tmp/lattice-hr-queue-test-src-2/` |
| 文件 | `test-knowledge-2.md`（门店设备维护指南，4 个一级标题） |
| Job ID | `add8c01d-ee35-42c7-b1bf-333105f54ad8` |
| 结果 | needsHumanReview=1, accepted=0, persisted=0 |

### 模型配置

| 组件 | Connection | Model | Profile ID |
|------|-----------|-------|------------|
| Embedding | zhipu_embedding | embedding-3 | 1 |
| Compile Writer/Reviewer/Fixer | local_openai_compatible | gpt-5.5 | 2 |
| Query | local_openai_compatible | gpt-5.5 | 2 |
| Deep Research | local_openai_compatible | gpt-5.5 | 2 |

compile_review_settings: autoFixEnabled=true, maxFixRounds=1, allowPersistNeedsHumanReview=true

---

## 4. 是否成功产生 needs_human_review queue 记录

**是。** 两次 compile 各产生 1 条 needs_human_review 记录。

```
 lattice=# SELECT id, job_id, title, review_status FROM lattice.compile_article_review_queue;
  id | job_id                               | title           | review_status
 ----+--------------------------------------+-----------------+----------------
   1 | 0dfd33ae-...                         | Test Knowledge  | published
   2 | add8c01d-...                         | Test Knowledge 2| rejected
```

两条记录均由 compile job 在 needs_human_review review 结果后自动写入 compile_article_review_queue。

---

## 5. Queue 列表 API 结果

**端点**：`GET /api/v1/admin/compile/review-queue?status=needs_human_review`

| 字段 | 值 |
|------|-----|
| HTTP status | 200 |
| total | 1（仅 needs_human_review 的记录） |
| items[0].id | 2 |
| items[0].title | "Test Knowledge 2" |
| items[0].reviewStatus | needs_human_review |
| items[0].articleKey | "default-source--test-knowledge-2" |
| items[0].content | 包含完整 YAML front matter + Markdown 正文 |
| items[0].metadataJson | `{"structured":false, ..., "sourceCount":1}` |
| items[0].reviewIssuesJson | `[]` |
| items[0].fixAttemptCount | 0 |
| items[0].maxFixRounds | 1 |
| items[0].sourcePaths | `["test-knowledge-2.md"]` |
| items[0].reviewedBy | null（未确认前） |
| items[0].reviewedAt | null |
| items[0].publishedArticleKey | null |

**已确认：列表 API 正确过滤 status=needs_human_review，已 published/rejected 的记录不出现在列表中。**

---

## 6. Queue 详情 API 结果

**端点**：`GET /api/v1/admin/compile/review-queue/1`

| 字段 | 值 |
|------|-----|
| HTTP status | 200 |
| id | 1 |
| title | "Test Knowledge" |
| content | 完整 YAML front matter + Markdown 正文（~400 chars） |
| reviewStatus | needs_human_review（调用时状态，approve 后变 published） |
| reviewRoute | "anthropic" |
| reviewerModel | "anthropic" |
| reviewIssuesJson | `[]` |
| fixAttemptCount | 0 |
| maxFixRounds | 1 |
| sourcePaths | `["test-knowledge.md"]` |
| metadataJson | `{"structured":false, ..., "sourceCount":1, "sectionCount":0}` |

**已确认：详情 API 可读取完整草稿内容、metadata、review issues、source paths。**

---

## 7. 未 approve 前状态验证

| 验证项 | 结果 | 说明 |
|--------|------|------|
| articles 表 | **0 条** | compile job 完成后无文章入库 |
| article_chunks 表 | **0 条** | 无 chunks |
| article_vector_index | **0 条** | 无向量 |
| article_chunk_vector_index | **0 条** | 无向量 |
| compile_article_review_queue | **2 条**（needs_human_review） | 草稿仅写入队列 |
| query 可见 | N/A（编译时 articles=0） | 未入库前自然不可见 |

**已确认：未 approve 前，草稿仅存在于 compile_article_review_queue，未进入 articles/chunks/vector_index。**

---

## 8. Approve 后验证

**操作**：`POST /api/v1/admin/compile/review-queue/1/approve`，body `{"reviewedBy":"agentD","reviewComment":"approved for verification"}`

### 8.1 Queue 状态

| 字段 | 值 |
|------|-----|
| reviewStatus | **published**（由 needs_human_review 变更） |
| reviewedBy | **agentD** |
| reviewedAt | 2026-05-20T00:41:46.491608Z |
| publishedArticleKey | **default-source--test-knowledge** |

### 8.2 正式 articles

| 字段 | 值 |
|------|-----|
| id | 1 |
| article_key | default-source--test-knowledge |
| title | Test Knowledge |
| review_status | **passed** |
| lifecycle | **ACTIVE** |
| metadata_json | `{"humanReview":{"source":"compile_review_queue","queueId":1,"reviewedAt":"..."}}` |

### 8.3 Chunks

| 指标 | 值 |
|------|-----|
| article_chunks 数量 | **1** |
| chunk_index | 0 |

### 8.4 Vector Index

| 指标 | 值 |
|------|-----|
| article_vector_index | **0** |
| article_chunk_vector_index | **0** |

**⚠️ 注意：vector index 未随 approve 自动刷新。** article 和 chunk 已写入，但 embedding 向量未生成。

### 8.5 Audit / Metadata

| 字段 | 值 |
|------|-----|
| audit.id | 1 |
| audit.action | **compile_review_queue_approve** |
| audit.previous_review_status | needs_human_review |
| audit.next_review_status | passed |
| audit.reviewed_by | agentD |
| audit.metadata_json | `{"jobId":"0dfd33ae-...","source":"compile_review_queue","queueId":1}` |
| article.metadata_json.humanReview.source | **compile_review_queue** |
| article.metadata_json.humanReview.queueId | **1** |

**已确认：audit 和 article metadata 正确记录人工确认来源为 compile_review_queue。**

### 8.6 Query 可见性

`GET /api/v1/admin/articles?limit=10` 返回：

| 字段 | 值 |
|------|-----|
| count | 1 |
| items[0].articleKey | default-source--test-knowledge |
| items[0].lifecycle | ACTIVE |
| items[0].reviewStatus | passed |

**已确认：approve 后文章立即在 admin API 可见。**

---

## 9. Reject 后验证

**操作**：`POST /api/v1/admin/compile/review-queue/2/reject`，body `{"reviewedBy":"agentD","reviewComment":"rejected for verification"}`

### 9.1 Queue 状态

| 字段 | 值 |
|------|-----|
| reviewStatus | **rejected**（由 needs_human_review 变更） |
| reviewedBy | **agentD** |
| reviewedAt | 2026-05-20T00:43:53.330395Z |
| publishedArticleKey | **null** |

### 9.2 是否未写入 articles

| 表 | 数量（reject 后） | 说明 |
|----|-------------------|------|
| articles | **1**（仅 approve 的那 1 条） | rejected 文章未写入 |
| article_chunks | **1**（仅 approve 的那 1 条） | rejected 文章未产生 chunks |
| article_vector_index | **0** | — |
| article_chunk_vector_index | **0** | — |

### 9.3 Audit

| 字段 | 值 |
|------|-----|
| audit.id | 2 |
| audit.action | **compile_review_queue_reject** |
| audit.previous_review_status | needs_human_review |
| audit.next_review_status | rejected |
| audit.reviewed_by | agentD |
| audit.metadata_json | `{"jobId":"add8c01d-...","source":"compile_review_queue","queueId":2}` |

**已确认：reject 后不入 articles/chunks/vector_index，audit 正确记录 reject 动作。**

---

## 10. 验证总结

| # | 验证项 | 结果 |
|---|--------|------|
| 1 | compile 产生 needs_human_review 后写入 compile_article_review_queue | ✅ 通过 |
| 2 | 列表 API 正确过滤 needs_human_review 记录 | ✅ 通过 |
| 3 | 详情 API 可读取草稿内容、metadata、review issues | ✅ 通过 |
| 4 | 未 approve 前不入 articles/chunks/vector_index | ✅ 通过 |
| 5 | approve 后 queue 状态变 published | ✅ 通过 |
| 6 | approve 后 article 写入，review_status=passed，lifecycle=ACTIVE | ✅ 通过 |
| 7 | approve 后 chunks 生成 | ✅ 通过 |
| 8 | approve 后 metadata 记录人工确认来源 | ✅ 通过 |
| 9 | approve 后 audit 记录 compile_review_queue_approve | ✅ 通过 |
| 10 | approve 后 article 在 admin API 可见 | ✅ 通过 |
| 11 | reject 后 queue 状态变 rejected | ✅ 通过 |
| 12 | reject 后 publishedArticleKey=null | ✅ 通过 |
| 13 | reject 后不入 articles | ✅ 通过 |
| 14 | reject 后不入 chunks | ✅ 通过 |
| 15 | reject 后不入 vector_index | ✅ 通过 |
| 16 | reject 后 audit 记录 compile_review_queue_reject | ✅ 通过 |
| 17 | redline BLOCKER=0（始终） | ✅ 通过 |
| 18 | 未修改代码 | ✅ 通过 |

---

## 11. 发现的运行时缺陷

### 11.1 approve 后 vector index 未自动刷新（⚠️）

- **现象**：approve 后 article（1 条）和 chunk（1 条）已写入，但 article_vector_index 和 article_chunk_vector_index 均为 0
- **影响**：approve 发布的文章无法参与语义检索，query 只能通过关键词/全文检索命中
- **推测根因**：approve 流程仅写入 articles + chunks，未触发 `refresh_vector_index` 步骤（该步骤在 compile job 的 state graph 中作为独立节点存在）
- **建议修复**：在 approve 流程中增加 vector rebuild 调用，或为 published article 触发异步 vector refresh

### 11.2 fillIssueCount 语义（⚪ 轻微）

- **现象**：两批 test-knowledge 内容均 review_issues_json=`[]`（空数组），但 review_status=needs_human_review
- **说明**：空 issues 列表 + needs_human_review 状态在语义上不一致，可能让前端/人工确认者困惑（"没有 issue 为什么还要确认？"）
- **推测**：Reviewer 判定为 needs_human_review 但没有输出结构化 issues，或 issues 格式解析损耗
- **影响**：低 — approve/reject 功能正常，仅 queue 详情展示的 "问题列表" 为空

### 11.3 review_route 显示 "anthropic" 而非实际模型路由（⚪ 轻微）

- **现象**：queue 记录中 `reviewRoute` 和 `reviewerModel` 均为 "anthropic"，但实际 Review 使用的是 gpt-5.5
- **影响**：仅展示用，不影响功能

---

## 12. 下一步建议

1. **实现前端人工确认入口**（核心）：后端 approve/reject API 已验证通过，可基于 API 构建前端人工确认页面：
   - 列表页：展示 needs_human_review 队列
   - 详情页：展示草稿正文 + review issues + metadata
   - 操作按钮：Approve / Reject
2. **修复 approve 后 vector index 刷新**：在 approve 流程中增加 embedding + vector index 写入，使发布的文章可参与语义检索
3. **review_issues 为空时展示优化**：前端展示时，若 issues 为空但 needs_human_review，可显示"Reviewer 判定需人工确认，但无结构化 issue 详情"
4. **使用更大规模资料重跑验证**：当前用 1–2 篇小 markdown 验证通过，建议用 SWIP docx 重跑确认边界情况
