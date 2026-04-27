package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleJdbcRepository 测试
 *
 * 职责：验证 Flyway 建表和最小文章落盘能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_test",
        "spring.flyway.default-schema=lattice_b1_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class ArticleJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    /**
     * 验证 Flyway 已创建 articles 表。
     */
    @Test
    void shouldCreateArticlesTableByFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice_b1_test' and table_name = 'articles'",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证最小文章记录可保存并按 conceptId 查询。
     */
    @Test
    void shouldSaveAndLoadArticleRecord() {
        ArticleRecord articleRecord = new ArticleRecord(
                "concept-ingest-node",
                "Ingest Node",
                """
                        ---
                        title: "Ingest Node"
                        summary: "ingest summary"
                        referential_keywords: ["retry=3"]
                        sources: ["docs/ingest.md"]
                        depends_on: ["compile-pipeline"]
                        related: ["source-index"]
                        confidence: medium
                        review_status: pending
                        ---

                        # Ingest Node
                        """,
                "ACTIVE",
                OffsetDateTime.now(),
                Arrays.asList("docs/ingest.md"),
                "{\"description\":\"ingest summary\",\"structured\":true}",
                "ingest summary",
                Arrays.asList("retry=3"),
                Arrays.asList("compile-pipeline"),
                Arrays.asList("source-index"),
                "medium",
                "pending"
        );

        articleJdbcRepository.upsert(articleRecord);
        Optional<ArticleRecord> loaded = articleJdbcRepository.findByConceptId("concept-ingest-node");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getTitle()).isEqualTo("Ingest Node");
        assertThat(loaded.orElseThrow().getContent()).contains("title: \"Ingest Node\"");
        assertThat(loaded.orElseThrow().getSourcePaths()).containsExactly("docs/ingest.md");
        assertThat(loaded.orElseThrow().getMetadataJson()).contains("ingest summary");
        assertThat(loaded.orElseThrow().getMetadataJson()).contains("structured");
        assertThat(loaded.orElseThrow().getSummary()).isEqualTo("ingest summary");
        assertThat(loaded.orElseThrow().getReferentialKeywords()).containsExactly("retry=3");
        assertThat(loaded.orElseThrow().getDependsOn()).containsExactly("compile-pipeline");
        assertThat(loaded.orElseThrow().getRelated()).containsExactly("source-index");
        assertThat(loaded.orElseThrow().getConfidence()).isEqualTo("medium");
        assertThat(loaded.orElseThrow().getReviewStatus()).isEqualTo("pending");
    }

    /**
     * 验证 articles 表已扩展为 Markdown/frontmatter 结构化列。
     */
    @Test
    void shouldExtendArticlesTableForMarkdownKnowledgeBase() {
        java.util.List<String> columnNames = jdbcTemplate.queryForList(
                """
                        select column_name
                        from information_schema.columns
                        where table_schema = 'lattice_b1_test'
                          and table_name = 'articles'
                        order by ordinal_position
                        """,
                String.class
        );

        assertThat(columnNames)
                .contains("summary")
                .contains("referential_keywords")
                .contains("depends_on")
                .contains("related")
                .contains("confidence")
                .contains("review_status");
    }

    /**
     * 验证 upsert 会优先把 Markdown frontmatter 中的结构化字段回灌到列值，避免正文与结构化列漂移。
     */
    @Test
    void shouldSynchronizeStructuredColumnsFromMarkdownFrontmatter() {
        OffsetDateTime compiledAt = OffsetDateTime.parse("2025-02-14T00:00:00Z");
        ArticleRecord articleRecord = new ArticleRecord(
                "payments",
                "Payments",
                """
                        ---
                        title: "Payments"
                        summary: "支付重试与网关保护配置"
                        referential_keywords: ["payment.timeout.retry", "3", "failure-rate-threshold", "50"]
                        sources: ["payments/gateway-config.yaml"]
                        depends_on: []
                        related: ["runbooks", "ops"]
                        confidence: high
                        compiled_at: "2025-02-14T00:00:00Z"
                        review_status: needs_human_review
                        ---

                        # Payments

                        | 配置键 | 精确值 | 说明 |
                        |---|---:|---|
                        | `payment.timeout.retry` | `3` | 超时场景下的重试次数配置。 |
                        """,
                "ACTIVE",
                OffsetDateTime.now(),
                Arrays.asList("stale.md"),
                "{\"description\":\"stale summary\",\"structured\":false}",
                "stale summary",
                Arrays.asList("stale"),
                Arrays.asList("stale-dependency"),
                Arrays.asList("stale-related"),
                "low",
                "pending"
        );

        articleJdbcRepository.upsert(articleRecord);
        ArticleRecord loaded = articleJdbcRepository.findByConceptId("payments").orElseThrow();

        assertThat(loaded.getTitle()).isEqualTo("Payments");
        assertThat(loaded.getSummary()).isEqualTo("支付重试与网关保护配置");
        assertThat(loaded.getSourcePaths()).containsExactly("payments/gateway-config.yaml");
        assertThat(loaded.getReferentialKeywords()).containsExactly(
                "payment.timeout.retry",
                "3",
                "failure-rate-threshold",
                "50"
        );
        assertThat(loaded.getDependsOn()).isEmpty();
        assertThat(loaded.getRelated()).containsExactly("runbooks", "ops");
        assertThat(loaded.getConfidence()).isEqualTo("high");
        assertThat(loaded.getReviewStatus()).isEqualTo("needs_human_review");
        assertThat(loaded.getCompiledAt()).isEqualTo(compiledAt);
    }

    /**
     * 验证多行 YAML 列表 frontmatter 会被完整回灌，避免 sources/source_paths 被误解析为空。
     */
    @Test
    void shouldParseMultilineYamlFrontmatterLists() {
        ArticleRecord articleRecord = new ArticleRecord(
                "docs-multiline",
                "Docs Multiline",
                """
                        ---
                        title: "Docs Multiline"
                        summary: "多行 YAML frontmatter 解析"
                        referential_keywords:
                          - "alpha"
                          - "beta"
                        sources:
                          - "docs/a.md"
                          - "docs/b.md"
                        depends_on:
                          - "compile-pipeline"
                        related:
                          - "knowledge-search"
                        confidence: medium
                        review_status: pending
                        ---

                        # Docs Multiline
                        """,
                "ACTIVE",
                OffsetDateTime.now(),
                Arrays.asList("stale.md"),
                "{\"description\":\"stale summary\",\"structured\":false}",
                "stale summary",
                Arrays.asList("stale"),
                Arrays.asList("stale-dependency"),
                Arrays.asList("stale-related"),
                "low",
                "pending"
        );

        articleJdbcRepository.upsert(articleRecord);
        ArticleRecord loaded = articleJdbcRepository.findByConceptId("docs-multiline").orElseThrow();

        assertThat(loaded.getSourcePaths()).containsExactly("docs/a.md", "docs/b.md");
        assertThat(loaded.getReferentialKeywords()).containsExactly("alpha", "beta");
        assertThat(loaded.getDependsOn()).containsExactly("compile-pipeline");
        assertThat(loaded.getRelated()).containsExactly("knowledge-search");
    }
}
