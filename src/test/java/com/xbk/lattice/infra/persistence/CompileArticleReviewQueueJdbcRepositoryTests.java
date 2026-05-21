package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileArticleReviewQueueJdbcRepository 测试
 *
 * 职责：验证编译人工确认队列表的建表、入队与状态更新能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class CompileArticleReviewQueueJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository;

    /**
     * 验证队列表可由仓储确保创建。
     */
    @Test
    void shouldEnsureCompileArticleReviewQueueTable() {
        compileArticleReviewQueueJdbcRepository.list(null, 1);

        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'compile_article_review_queue'
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证待人工确认草稿可入队并标记发布。
     */
    @Test
    void shouldUpsertPendingDraftAndMarkPublished() {
        compileArticleReviewQueueJdbcRepository.list(null, 1);
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_article_review_queue RESTART IDENTITY");
        CompileArticleReviewQueueRecord queueRecord = queueRecord("job-queue-published", "concept-published");

        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord);
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isEqualTo(1);
        CompileArticleReviewQueueRecord pendingRecord = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10)
                .get(0);
        boolean updated = compileArticleReviewQueueJdbcRepository.markPublished(
                pendingRecord.getId(),
                "reviewer",
                OffsetDateTime.parse("2026-05-20T09:00:00+08:00"),
                "确认发布",
                pendingRecord.getArticleKey()
        );

        CompileArticleReviewQueueRecord publishedRecord = compileArticleReviewQueueJdbcRepository
                .findById(pendingRecord.getId())
                .orElseThrow();
        assertThat(updated).isTrue();
        assertThat(publishedRecord.getReviewStatus()).isEqualTo("published");
        assertThat(publishedRecord.getReviewedBy()).isEqualTo("reviewer");
        assertThat(publishedRecord.getReviewComment()).isEqualTo("确认发布");
        assertThat(publishedRecord.getPublishedArticleKey()).isEqualTo(pendingRecord.getArticleKey());
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isZero();
    }

    /**
     * 验证待人工确认草稿可标记驳回。
     */
    @Test
    void shouldMarkPendingDraftRejected() {
        compileArticleReviewQueueJdbcRepository.list(null, 1);
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_article_review_queue RESTART IDENTITY");
        CompileArticleReviewQueueRecord queueRecord = queueRecord("job-queue-rejected", "concept-rejected");

        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord);
        CompileArticleReviewQueueRecord pendingRecord = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10)
                .get(0);
        boolean updated = compileArticleReviewQueueJdbcRepository.markRejected(
                pendingRecord.getId(),
                "reviewer",
                OffsetDateTime.parse("2026-05-20T09:00:00+08:00"),
                "拒绝发布"
        );

        CompileArticleReviewQueueRecord rejectedRecord = compileArticleReviewQueueJdbcRepository
                .findById(pendingRecord.getId())
                .orElseThrow();
        assertThat(updated).isTrue();
        assertThat(rejectedRecord.getReviewStatus()).isEqualTo("rejected");
        assertThat(rejectedRecord.getPublishedArticleKey()).isNull();
    }

    /**
     * 验证可按编译作业标识聚合待确认、已发布与已驳回数量。
     */
    @Test
    void shouldSummarizePublishOutcomeByJobId() {
        compileArticleReviewQueueJdbcRepository.list(null, 1);
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_article_review_queue RESTART IDENTITY");
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-outcome", "concept-pending"));
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-outcome", "concept-published"));
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-outcome", "concept-rejected"));

        List<CompileArticleReviewQueueRecord> pendingRecords = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10);
        for (CompileArticleReviewQueueRecord pendingRecord : pendingRecords) {
            if ("concept-published".equals(pendingRecord.getConceptId())) {
                compileArticleReviewQueueJdbcRepository.markPublished(
                        pendingRecord.getId(),
                        "reviewer-a",
                        OffsetDateTime.parse("2026-05-20T09:00:00+08:00"),
                        "确认发布",
                        pendingRecord.getArticleKey()
                );
            }
            if ("concept-rejected".equals(pendingRecord.getConceptId())) {
                compileArticleReviewQueueJdbcRepository.markRejected(
                        pendingRecord.getId(),
                        "reviewer-b",
                        OffsetDateTime.parse("2026-05-20T09:00:00+08:00"),
                        "拒绝发布"
                );
            }
        }

        CompileArticleReviewQueueJdbcRepository.PublishOutcomeSummary summary =
                compileArticleReviewQueueJdbcRepository.summarizeByJobId("job-outcome");
        assertThat(summary.getPendingHumanReviewCount()).isEqualTo(1);
        assertThat(summary.getPublishedCount()).isEqualTo(1);
        assertThat(summary.getRejectedCount()).isEqualTo(1);
        assertThat(summary.hasAnyOutcome()).isTrue();
    }

    /**
     * 验证跨 job 相同 article_key 的 needs_human_review 草稿只保留一条（覆盖旧草稿）。
     */
    @Test
    void shouldDeduplicatePendingDraftAcrossJobs() {
        compileArticleReviewQueueJdbcRepository.list(null, 1);
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_article_review_queue RESTART IDENTITY");

        // 第一次入队：job-1 + concept-dedup
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-1", "concept-dedup"));
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isEqualTo(1);

        // 第二次入队：job-2 + 同一 concept（article_key 相同），应覆盖，不新增
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-2", "concept-dedup"));
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isEqualTo(1);

        // 验证保留的是最新 job_id
        CompileArticleReviewQueueRecord record = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10).get(0);
        assertThat(record.getJobId()).isEqualTo("job-2");
        assertThat(record.getArticleKey()).isEqualTo("source-queue--concept-dedup");
    }

    /**
     * 验证已 published 的记录不影响新 needs_human_review 入队（二者可共存）。
     */
    @Test
    void shouldAllowNewPendingDraftAfterPublished() {
        compileArticleReviewQueueJdbcRepository.list(null, 1);
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_article_review_queue RESTART IDENTITY");

        // 入队并发布
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-pub-1", "concept-coexist"));
        CompileArticleReviewQueueRecord pendingRecord = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10).get(0);
        compileArticleReviewQueueJdbcRepository.markPublished(
                pendingRecord.getId(), "reviewer",
                OffsetDateTime.parse("2026-05-20T09:00:00+08:00"),
                "确认", pendingRecord.getArticleKey()
        );

        // 再次入队同一 article_key，应新增（因为之前的已 published）
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-pub-2", "concept-coexist"));
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isEqualTo(1);
        CompileArticleReviewQueueRecord secondPendingRecord = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10).get(0);
        compileArticleReviewQueueJdbcRepository.markPublished(
                secondPendingRecord.getId(),
                "reviewer-2",
                OffsetDateTime.parse("2026-05-20T10:00:00+08:00"),
                "再次确认",
                secondPendingRecord.getArticleKey()
        );
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isZero();
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("published")).isEqualTo(2);
    }

    /**
     * 验证已 rejected 的记录不影响新 needs_human_review 入队（二者可共存）。
     */
    @Test
    void shouldAllowNewPendingDraftAfterRejected() {
        compileArticleReviewQueueJdbcRepository.list(null, 1);
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_article_review_queue RESTART IDENTITY");

        // 入队并驳回
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-rej-1", "concept-rej-coexist"));
        CompileArticleReviewQueueRecord pendingRecord = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10).get(0);
        compileArticleReviewQueueJdbcRepository.markRejected(
                pendingRecord.getId(), "reviewer",
                OffsetDateTime.parse("2026-05-20T09:00:00+08:00"),
                "拒绝"
        );

        // 再次入队同一 article_key，应新增（因为之前的已 rejected）
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord("job-rej-2", "concept-rej-coexist"));
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isEqualTo(1);
        CompileArticleReviewQueueRecord secondPendingRecord = compileArticleReviewQueueJdbcRepository
                .list("needs_human_review", 10).get(0);
        compileArticleReviewQueueJdbcRepository.markRejected(
                secondPendingRecord.getId(),
                "reviewer-2",
                OffsetDateTime.parse("2026-05-20T10:00:00+08:00"),
                "再次拒绝"
        );
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("needs_human_review")).isZero();
        assertThat(compileArticleReviewQueueJdbcRepository.countByStatus("rejected")).isEqualTo(2);
    }

    private CompileArticleReviewQueueRecord queueRecord(String jobId, String conceptId) {
        return new CompileArticleReviewQueueRecord(
                0L,
                jobId,
                9L,
                "source-queue",
                conceptId,
                "source-queue--" + conceptId,
                "Queue Draft",
                """
                        ---
                        title: "Queue Draft"
                        summary: "Generic summary"
                        sources: ["docs/source.md"]
                        review_status: needs_human_review
                        ---

                        # Queue Draft
                        """,
                "ACTIVE",
                OffsetDateTime.parse("2026-05-20T08:00:00+08:00"),
                List.of("docs/source.md"),
                "{}",
                "needs_human_review",
                "llm",
                "llm",
                "[{\"severity\":\"HIGH\",\"category\":\"GROUNDING\",\"description\":\"缺少来源\"}]",
                1,
                1,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
