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
}
