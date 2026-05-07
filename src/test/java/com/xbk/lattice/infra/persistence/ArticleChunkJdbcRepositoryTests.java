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
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
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
     * 验证 手动 DDL 已创建 article_chunks 表。
     */
    @Test
    void shouldCreateArticleChunksTableByManualDdl() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice' and table_name = 'article_chunks'",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证 chunk 可按 conceptId 替换写入并读取。
     */
    @Test
    void shouldReplaceAndLoadChunksByConceptId() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment",
                "Payment",
                "# Payment",
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("payment/a.md"),
                "{\"description\":\"payment summary\"}"
        ));

        articleChunkJdbcRepository.replaceChunks("payment", List.of("chunk-a", "chunk-b"));
        List<String> chunks = articleChunkJdbcRepository.findChunkTexts("payment");

        assertThat(chunks).containsExactly("chunk-a", "chunk-b");
    }

    /**
     * 验证可基于文章正文执行完整重建。
     */
    @Test
    void shouldRebuildAllChunksFromArticleContent() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment",
                "Payment",
                """
                        # Payment

                        ## Timeout Rules
                        - retry=3
                        - interval=30s
                        """,
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("payment/a.md"),
                "{\"description\":\"payment summary\"}"
        ));
        articleChunkJdbcRepository.replaceChunks("payment", List.of("legacy-chunk"));

        int rebuiltCount = articleChunkJdbcRepository.rebuildAll(articleJdbcRepository.findAll());
        List<String> chunks = articleChunkJdbcRepository.findChunkTexts("payment");

        assertThat(rebuiltCount).isEqualTo(1);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("# Payment");
        assertThat(chunks.get(0)).contains("## Timeout Rules");
        assertThat(chunks.get(0)).doesNotContain("legacy-chunk");
    }

    /**
     * 验证 article chunk 可通过数据库侧 lexical 查询召回。
     */
    @Test
    void shouldSearchArticleChunksByLexicalIndex() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment",
                "Payment",
                "# Payment",
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("payment/a.md"),
                "{\"description\":\"payment summary\"}"
        ));
        articleChunkJdbcRepository.replaceChunks(
                "payment",
                List.of("retry interval is 30s", "unrelated shipping content")
        );

        List<LexicalSearchRecord> hits = articleChunkJdbcRepository.searchLexical(
                "retry interval",
                List.of("retry", "interval"),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getContent()).contains("retry interval");
        assertThat(hits.get(0).getConceptId()).isEqualTo("payment");
    }
}
