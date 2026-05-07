package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleReviewAuditJdbcRepository 测试
 *
 * 职责：验证人工复核审计表结构、保存和按文章身份读取能力
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
class ArticleReviewAuditJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository;

    /**
     * 验证 手动 DDL 已创建人工复核审计表。
     */
    @Test
    void shouldCreateArticleReviewAuditsTableByManualDdl() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'article_review_audits'
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证审计记录可保存并按 articleKey 读取。
     */
    @Test
    void shouldSaveAndFindAuditByArticleKey() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_review_audits RESTART IDENTITY");
        OffsetDateTime reviewedAt = OffsetDateTime.parse("2026-05-05T10:00:00+08:00");

        ArticleReviewAuditRecord savedRecord = articleReviewAuditJdbcRepository.save(new ArticleReviewAuditRecord(
                0L,
                1L,
                "source-1:concept-alpha",
                "concept-alpha",
                "approve",
                "needs_human_review",
                "passed",
                "证据一致",
                "reviewer",
                reviewedAt,
                "{\"source\":\"manual\"}"
        ));

        List<ArticleReviewAuditRecord> auditRecords = articleReviewAuditJdbcRepository.findByArticleKey(
                "source-1:concept-alpha"
        );

        assertThat(savedRecord.getId()).isGreaterThan(0L);
        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.get(0).getId()).isEqualTo(savedRecord.getId());
        assertThat(auditRecords.get(0).getAction()).isEqualTo("approve");
        assertThat(auditRecords.get(0).getPreviousReviewStatus()).isEqualTo("needs_human_review");
        assertThat(auditRecords.get(0).getNextReviewStatus()).isEqualTo("passed");
        assertThat(auditRecords.get(0).getMetadataJson()).contains("\"source\": \"manual\"");
    }

    /**
     * 验证无 articleKey 时可按 conceptId + sourceId 读取。
     */
    @Test
    void shouldFindAuditByConceptIdAndSourceIdWhenArticleKeyAbsent() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_review_audits RESTART IDENTITY");
        articleReviewAuditJdbcRepository.save(new ArticleReviewAuditRecord(
                0L,
                2L,
                null,
                "concept-beta",
                "request_changes",
                "needs_human_review",
                "needs_review",
                "需要补证据",
                "reviewer",
                OffsetDateTime.parse("2026-05-05T11:00:00+08:00"),
                "{}"
        ));

        List<ArticleReviewAuditRecord> auditRecords = articleReviewAuditJdbcRepository.findByConceptIdAndSourceId(
                "concept-beta",
                2L
        );

        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.get(0).getArticleKey()).isNull();
        assertThat(auditRecords.get(0).getAction()).isEqualTo("request_changes");
    }
}
