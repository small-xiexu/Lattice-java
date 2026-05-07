package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleSnapshotJdbcRepository 测试
 *
 * 职责：验证文章快照表与自动留痕读取能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class ArticleSnapshotJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    /**
     * 验证 手动 DDL 已创建 article_snapshots 表。
     */
    @Test
    void shouldCreateArticleSnapshotsTableByManualDdl() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'article_snapshots'
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证文章写入后会自动生成快照，并可按 conceptId 查看历史。
     */
    @Test
    void shouldCaptureSnapshotHistoryWhenArticleUpserted() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE lattice.article_chunks, "
                        + "lattice.article_snapshots, "
                        + "lattice.articles CASCADE"
        );

        OffsetDateTime firstCompiledAt = OffsetDateTime.parse("2026-04-15T20:00:00+08:00");
        OffsetDateTime secondCompiledAt = firstCompiledAt.plusMinutes(5);
        articleJdbcRepository.upsert(article("payment-timeout", "pending", "first summary", firstCompiledAt));
        articleJdbcRepository.upsert(article("payment-timeout", "passed", "second summary", secondCompiledAt));

        List<ArticleSnapshotRecord> conceptHistory = articleSnapshotJdbcRepository.findByConceptId("payment-timeout", 10);

        assertThat(conceptHistory).hasSize(2);
        assertThat(conceptHistory.get(0).getReviewStatus()).isEqualTo("passed");
        assertThat(conceptHistory.get(0).getSummary()).isEqualTo("second summary");
        assertThat(conceptHistory.get(0).getCompiledAt()).isEqualTo(secondCompiledAt);
        assertThat(conceptHistory.get(1).getReviewStatus()).isEqualTo("pending");
        assertThat(conceptHistory.get(1).getSummary()).isEqualTo("first summary");

        List<ArticleSnapshotRecord> recentSnapshots = articleSnapshotJdbcRepository.findRecent(10);
        assertThat(recentSnapshots).hasSize(2);
        assertThat(recentSnapshots.get(0).getConceptId()).isEqualTo("payment-timeout");
    }

    private ArticleRecord article(
            String conceptId,
            String reviewStatus,
            String summary,
            OffsetDateTime compiledAt
    ) {
        return new ArticleRecord(
                conceptId,
                "Payment Timeout",
                "# Payment Timeout",
                "active",
                compiledAt,
                List.of("payment/a.md"),
                "{\"version\":1}",
                summary,
                List.of("retry=3"),
                List.of("payment-config"),
                List.of("retry-policy"),
                "medium",
                reviewStatus
        );
    }
}
