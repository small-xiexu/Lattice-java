package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleChunkJdbcRepository 测试
 *
 * 职责：验证 article_chunks 表和最小 chunk 落盘能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_chunk_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_chunk_test",
        "spring.flyway.default-schema=lattice_b1_chunk_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class ArticleChunkJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ArticleChunkJdbcRepository articleChunkJdbcRepository;

    /**
     * 验证 Flyway 已创建 article_chunks 表。
     */
    @Test
    void shouldCreateArticleChunksTableByFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice_b1_chunk_test' and table_name = 'article_chunks'",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证 chunk 可按 conceptId 替换写入并读取。
     */
    @Test
    void shouldReplaceAndLoadChunksByConceptId() {
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment",
                "Payment",
                "# Payment",
                "ACTIVE",
                OffsetDateTime.now()
        ));

        articleChunkJdbcRepository.replaceChunks("payment", List.of("chunk-a", "chunk-b"));
        List<String> chunks = articleChunkJdbcRepository.findChunkTexts("payment");

        assertThat(chunks).containsExactly("chunk-a", "chunk-b");
    }
}
