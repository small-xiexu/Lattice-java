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
                "# Ingest Node",
                "ACTIVE",
                OffsetDateTime.now(),
                Arrays.asList("docs/ingest.md"),
                "{\"description\":\"ingest summary\",\"structured\":true}"
        );

        articleJdbcRepository.upsert(articleRecord);
        Optional<ArticleRecord> loaded = articleJdbcRepository.findByConceptId("concept-ingest-node");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getTitle()).isEqualTo("Ingest Node");
        assertThat(loaded.orElseThrow().getContent()).isEqualTo("# Ingest Node");
        assertThat(loaded.orElseThrow().getSourcePaths()).containsExactly("docs/ingest.md");
        assertThat(loaded.orElseThrow().getMetadataJson()).contains("ingest summary");
        assertThat(loaded.orElseThrow().getMetadataJson()).contains("structured");
    }
}
