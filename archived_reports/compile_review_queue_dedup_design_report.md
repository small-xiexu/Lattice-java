# compile_review_queue_dedup_design_report

## 1. 当前重复入队的实测根因

### 1.1 Schema 现状

```sql
CREATE TABLE compile_article_review_queue (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    source_id BIGINT,
    source_code VARCHAR(128),
    concept_id VARCHAR(128) NOT NULL,
    article_key VARCHAR(256) NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'needs_human_review',
    ...
);

CREATE UNIQUE INDEX uk_compile_article_review_queue_job_concept
    ON compile_article_review_queue (job_id, concept_id);
```

**现有唯一性约束**：`(job_id, concept_id)`

### 1.2 当前 upsertPending 逻辑

```sql
INSERT INTO compile_article_review_queue (...)
VALUES (...)
ON CONFLICT (job_id, concept_id) DO UPDATE
SET ...
WHERE compile_article_review_queue.review_status = 'needs_human_review'
```

**关键限制**：
- 去重范围仅限**同一 job_id 内的 concept_id**
- `ON CONFLICT ... DO UPDATE` 只在 `review_status = 'needs_human_review'` 时生效
- 若已 `published` 或 `rejected`，DO UPDATE WHERE 子句不满足，**conflict 仍会报错**

### 1.3 重复入队的 3 条路径

#### 场景 A：不同 compile job 产生相同 concept

**实测现象（来自 `phase_compile_query_stage_acceptance_report.md`）**：
- `quality-progress-and-lessons-当前阶段` 出现 **4 次**
- 24 条 `needs_human_review` 草稿堆积

**根因**：
1. 每次 `POST /api/v1/compile` 或 `POST /api/v1/admin/compile/jobs` 产生新 `job_id`
2. 同一资料源多次编译时：
   - `job_id_1 + concept_id_X`
   - `job_id_2 + concept_id_X`
   - `job_id_3 + concept_id_X`
3. 唯一约束是 `(job_id, concept_id)`，所以**每个新 job_id 都会产生一条新记录**

#### 场景 B：旧草稿已 published，新 compile 又来

**流转示例**：
```
job_id_1 + concept_X -> needs_human_review -> approved -> published
job_id_2 + concept_X -> INSERT 成功（不同 job_id）-> needs_human_review
```

**结果**：
- 队列中同时存在：
  - `id=123, job_id=job_id_1, concept_id=X, review_status=published`
  - `id=456, job_id=job_id_2, concept_id=X, review_status=needs_human_review`

#### 场景 C：旧草稿已 rejected，新 compile 又来

**流转示例**：
```
job_id_1 + concept_X -> needs_human_review -> rejected
job_id_2 + concept_X -> INSERT 成功（不同 job_id）-> needs_human_review
```

**结果**：
- 队列中同时存在：
  - `id=123, job_id=job_id_1, concept_id=X, review_status=rejected`
  - `id=456, job_id=job_id_2, concept_id=X, review_status=needs_human_review`

### 1.4 article_key 重复的独立问题

**article_key 构造规则**（`CompileArticleReviewQueueService.resolveArticleKey()`）：
```java
if (articleRecord.getArticleKey() != null && !articleRecord.getArticleKey().isBlank()) {
    return articleRecord.getArticleKey();
}
if (sourceCode == null || sourceCode.isBlank()) {
    return articleRecord.getConceptId();
}
return sourceCode + "--" + articleRecord.getConceptId();
```

**观察**：
- 同一 concept 的 article_key 跨 job_id 保持不变
- 例如：`default-source--quality-progress-and-lessons-当前阶段`

**与队列重复的关系**：
- article_key 相同 ≠ 队列记录相同
- 队列可以有多条 `article_key` 相同但 `job_id` 不同的记录

---

## 2. "同一草稿语义身份"的推荐判定口径

### 2.1 候选身份键分析

| 候选键组合 | 优点 | 缺点 | 推荐 |
|---|---|---|---|
| `(job_id, concept_id)` | 当前实现，job 内幂等 | 跨 job 重复入队 | ❌ 不足够 |
| `(concept_id)` | 最强去重 | 不区分 source，多源冲突 | ❌ 过强 |
| `(source_id, concept_id)` | 业务语义清晰 | source_id 可能为 null | ⚠️ 次优 |
| `(article_key)` | 与正式文章对齐 | 不表达队列中的"版本身份" | ⚠️ 次优 |
| `(source_id, concept_id, review_status)` | 区分待处理/已处理 | 状态流转时需要 DELETE+INSERT | ❌ 复杂 |
| **(article_key, review_status)** | **跨 job 去重 + 状态隔离** | **需要改唯一索引** | ✅ **推荐** |

### 2.2 推荐方案：`(article_key, review_status)` 唯一约束

#### 为什么选择 `article_key`？
1. **业务语义对齐**：与正式文章 `articles.article_key` 保持一致
2. **跨 job 稳定**：同一 concept 的 article_key 不会因 job_id 变化
3. **已有索引支持**：`idx_compile_article_review_queue_article_key` 已存在

#### 为什么加 `review_status`？
1. **隔离历史记录**：已 `published` / `rejected` 的记录不再参与去重
2. **避免 UPDATE 失败**：只对 `needs_human_review` 做覆盖或跳过
3. **运维可查**：保留历史决策记录（published/rejected）

#### 具体约束设计
```sql
CREATE UNIQUE INDEX uk_compile_article_review_queue_article_status
    ON compile_article_review_queue (article_key, review_status);
```

#### 去重语义
- **同一 article_key + needs_human_review**：只保留一条最新草稿
- **同一 article_key + published**：保留历史批准记录
- **同一 article_key + rejected**：保留历史驳回记录

---

## 3. 新 compile 命中已有草稿时的处理策略

### 3.1 场景矩阵

| 队列中已有记录 | 新 compile 草稿 | 推荐策略 | 原因 |
|---|---|---|---|
| `needs_human_review` | 同 article_key | **覆盖旧草稿** | 最新编译结果应替代旧草稿 |
| `published` | 同 article_key | **INSERT 新 needs_human_review** | 历史批准记录不动，新草稿独立入队 |
| `rejected` | 同 article_key | **INSERT 新 needs_human_review** | 历史驳回记录不动，新草稿独立入队 |
| `needs_human_review` | 同 article_key + 内容完全相同 | **覆盖旧草稿（更新 job_id）** | 避免相同内容重复展示 |

### 3.2 推荐 upsertPending 改造逻辑

#### 方案 A：唯一约束 + ON CONFLICT（推荐）

```sql
-- schema.sql 添加新唯一索引
CREATE UNIQUE INDEX uk_compile_article_review_queue_article_status
    ON compile_article_review_queue (article_key, review_status);

-- 保持旧索引以支持 job_id 查询
CREATE INDEX idx_compile_article_review_queue_job_id
    ON compile_article_review_queue (job_id);
```

```sql
-- CompileArticleReviewQueueMapper.xml 改造 upsertPending
INSERT INTO compile_article_review_queue (...)
VALUES (...)
ON CONFLICT (article_key, review_status) DO UPDATE
SET job_id = excluded.job_id,
    source_id = excluded.source_id,
    source_code = excluded.source_code,
    concept_id = excluded.concept_id,
    title = excluded.title,
    content = excluded.content,
    lifecycle = excluded.lifecycle,
    compiled_at = excluded.compiled_at,
    source_paths = excluded.source_paths,
    metadata_json = excluded.metadata_json,
    review_route = excluded.review_route,
    reviewer_model = excluded.reviewer_model,
    review_issues_json = excluded.review_issues_json,
    fix_attempt_count = excluded.fix_attempt_count,
    max_fix_rounds = excluded.max_fix_rounds,
    updated_at = CURRENT_TIMESTAMP
WHERE compile_article_review_queue.review_status = 'needs_human_review'
```

**关键变化**：
- 不再要求 `WHERE review_status = 'needs_human_review'`
- 因为唯一约束已经确保 `(article_key, review_status)` 唯一
- UPDATE WHERE 子句是额外防护，避免误覆盖 published/rejected

#### 方案 B：应用层先查再决策（次优）

```java
// CompileArticleReviewQueueService.enqueue()
for (ArticleReviewEnvelope reviewEnvelope : needsHumanReviewArticles) {
    CompileArticleReviewQueueRecord queueRecord = toQueueRecord(...);
    // 先查是否存在 article_key + needs_human_review
    CompileArticleReviewQueueRecord existing = 
        compileArticleReviewQueueJdbcRepository.findByArticleKeyAndStatus(
            queueRecord.getArticleKey(), "needs_human_review"
        ).orElse(null);
    if (existing != null) {
        // 覆盖旧草稿：UPDATE
        compileArticleReviewQueueJdbcRepository.updatePending(queueRecord);
    } else {
        // 新草稿：INSERT
        compileArticleReviewQueueJdbcRepository.insertPending(queueRecord);
    }
}
```

**缺点**：
- 需要额外查询
- 并发时可能 race condition
- 代码复杂度高于 schema 约束

### 3.3 推荐：方案 A（唯一约束 + ON CONFLICT）

**理由**：
1. **原子性**：PostgreSQL ON CONFLICT 原子执行，无 race
2. **最简代码**：应用层无需额外逻辑
3. **可扩展**：future 若需支持 content hash 去重，只需改 ON CONFLICT 条件

---

## 4. 是否需要 schema 级约束

### 4.1 推荐：必须添加 schema 约束

**添加约束**：
```sql
-- 新增唯一约束
CREATE UNIQUE INDEX uk_compile_article_review_queue_article_status
    ON compile_article_review_queue (article_key, review_status);
```

**删除旧约束（可选）**：
```sql
-- 若确认不再需要 job_id + concept_id 唯一性，可删除
-- 但建议保留为普通索引，支持 job_id 快速查询
ALTER INDEX uk_compile_article_review_queue_job_concept RENAME TO idx_compile_article_review_queue_job_concept;
```

### 4.2 约束迁移策略

#### 数据迁移前置条件
- 当前队列中可能存在多条 `(article_key, needs_human_review)` 相同的记录
- 必须先清理重复记录，再添加唯一约束

#### 迁移步骤
```sql
-- 1. 备份当前队列
CREATE TABLE compile_article_review_queue_backup AS 
SELECT * FROM compile_article_review_queue;

-- 2. 保留每个 (article_key, review_status) 组合的最新记录
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY article_key, review_status 
               ORDER BY updated_at DESC, id DESC
           ) AS rn
    FROM compile_article_review_queue
)
DELETE FROM compile_article_review_queue
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- 3. 添加新唯一约束
CREATE UNIQUE INDEX uk_compile_article_review_queue_article_status
    ON compile_article_review_queue (article_key, review_status);

-- 4. 验证约束
SELECT article_key, review_status, COUNT(*) AS cnt
FROM compile_article_review_queue
GROUP BY article_key, review_status
HAVING COUNT(*) > 1;
-- 预期结果：0 rows
```

### 4.3 是否需要保留 job_id + concept_id 约束？

**推荐：降级为普通索引**
```sql
-- 改为普通索引，支持 job_id 查询性能
ALTER INDEX uk_compile_article_review_queue_job_concept RENAME TO idx_compile_article_review_queue_job_concept;
```

**理由**：
- `summarizeByJobId(jobId)` 仍需要 job_id 索引
- 但不再需要唯一性约束（article_key 已覆盖去重语义）

---

## 5. 最小安全修复范围

### 5.1 必须修改

#### schema.sql
```sql
-- 新增唯一约束
CREATE UNIQUE INDEX IF NOT EXISTS uk_compile_article_review_queue_article_status
    ON compile_article_review_queue (article_key, review_status);

-- 降级旧约束为普通索引（可选）
DROP INDEX IF EXISTS uk_compile_article_review_queue_job_concept;
CREATE INDEX IF NOT EXISTS idx_compile_article_review_queue_job_concept
    ON compile_article_review_queue (job_id, concept_id);
```

#### CompileArticleReviewQueueMapper.xml
```xml
<!-- 改造 upsertPending -->
<insert id="upsertPending">
    insert into compile_article_review_queue (...)
    values (...)
    on conflict (article_key, review_status) do update
    set job_id = excluded.job_id,
        source_id = excluded.source_id,
        source_code = excluded.source_code,
        concept_id = excluded.concept_id,
        title = excluded.title,
        content = excluded.content,
        lifecycle = excluded.lifecycle,
        compiled_at = excluded.compiled_at,
        source_paths = excluded.source_paths,
        metadata_json = excluded.metadata_json,
        review_route = excluded.review_route,
        reviewer_model = excluded.reviewer_model,
        review_issues_json = excluded.review_issues_json,
        fix_attempt_count = excluded.fix_attempt_count,
        max_fix_rounds = excluded.max_fix_rounds,
        updated_at = CURRENT_TIMESTAMP
    where compile_article_review_queue.review_status = 'needs_human_review'
</insert>
```

#### ensureTable 同步改造
```xml
<!-- CompileArticleReviewQueueMapper.xml ensureTable -->
<update id="ensureTable">
    ...
    create unique index if not exists uk_compile_article_review_queue_article_status
        on compile_article_review_queue (article_key, review_status);
    ...
</update>
```

### 5.2 不需要修改

- **CompileArticleReviewQueueService**：业务逻辑无需改动，仍调用 `upsertPending`
- **AdminCompileArticleReviewQueueService**：approve/reject 逻辑无需改动
- **Controller 层**：无需改动
- **测试**：只需补充去重场景测试

### 5.3 数据迁移

#### 迁移脚本（独立执行）
```sql
-- scripts/deduplicate-review-queue.sql
BEGIN;

-- 保留每个 (article_key, review_status) 的最新记录
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY article_key, review_status 
               ORDER BY updated_at DESC, id DESC
           ) AS rn
    FROM compile_article_review_queue
)
DELETE FROM compile_article_review_queue
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- 添加新唯一约束
CREATE UNIQUE INDEX IF NOT EXISTS uk_compile_article_review_queue_article_status
    ON compile_article_review_queue (article_key, review_status);

-- 验证
SELECT article_key, review_status, COUNT(*) AS cnt
FROM compile_article_review_queue
GROUP BY article_key, review_status
HAVING COUNT(*) > 1;

COMMIT;
```

#### 执行时机
- **若当前队列无积压**：直接改 schema + mapper，应用启动时 ensureTable 自动生效
- **若当前队列有积压**：先运行迁移脚本清理重复，再改代码

---

## 6. 下一轮建议

### 6.1 修改范围建议

**最小安全修改范围**：
1. `src/main/resources/db/schema.sql` — 添加唯一约束
2. `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml` — 改造 upsertPending + ensureTable
3. `scripts/deduplicate-review-queue.sql` — 数据迁移脚本（一次性执行）

**测试补充**：
- `CompileArticleReviewQueueJdbcRepositoryTests`
  - 测试 `(article_key, needs_human_review)` 重复 insert 只覆盖不新增
  - 测试 `(article_key, published)` + `(article_key, needs_human_review)` 可共存
  - 测试 `(article_key, rejected)` + `(article_key, needs_human_review)` 可共存

### 6.2 风险提示

**低风险**：
- schema 改动最小，只加索引不改表结构
- ON CONFLICT 语义明确，不会误覆盖 published/rejected
- ensureTable 保证历史实例也生效

**中风险**：
- 若当前队列有大量重复记录，迁移脚本耗时较长（建议在低峰期执行）
- 若迁移脚本失败，需要回滚到备份表

### 6.3 下一轮交给哪个 agent

**推荐：agentA（代码执行 Agent）**

**执行清单**：
1. 读取本报告第 5 节"最小安全修复范围"
2. 按顺序修改 schema.sql + mapper.xml
3. 编写数据迁移脚本 `scripts/deduplicate-review-queue.sql`
4. 补充测试 `CompileArticleReviewQueueJdbcRepositoryTests`
5. 运行 redline scan：`BLOCKER=0`
6. 运行定向测试：无 failures
7. 输出 `compile_review_queue_dedup_fix_result_report.md`

**禁止事项**（按 AGENTS.md 执行禁令）：
- 不准修改 compile Writer / Reviewer / Fixer 主链
- 不准修改 Query / AnswerGeneration
- 不准同时做批量处理、UI 大改
- 不准在未输出报告前清库或跑 SWIP eval

---

## 7. 本轮是否修改代码

**否。**

本轮只做只读设计分析，未修改任何代码、测试、配置或数据库。

---

## 附录：当前队列状态快照

**来源**：`phase_compile_query_stage_acceptance_report.md`（2026-05-21）

| review_status | 计数 |
|---|---|
| `needs_human_review` | **24** |
| `published` | 6 |
| `rejected` | 6 |

**重复示例**：
- `quality-progress-and-lessons-当前阶段` 出现 **4 次**

**堆积原因**：
- 多次 compile 产生不同 job_id
- 唯一约束只覆盖 `(job_id, concept_id)`，不覆盖 `(article_key)`

**修复后预期**：
- 同一 `article_key + needs_human_review` 只保留最新草稿
- 24 条待处理堆积可能降至 10 条以内（假设 60% 是重复）

