package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.service.ArticleChunkVectorHit;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量 JDBC 仓储运算符测试
 *
 * 职责：验证在 lattice schema 连接下，pgvector 查询仍可正确解析 public schema 中的距离运算符
 *
 * @author xiexu
 */
class VectorJdbcRepositoryOperatorTests {

    private static final String JDBC_BASE_URL = "jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge";

    private static final String JDBC_USERNAME = "postgres";

    private static final String JDBC_PASSWORD = "postgres";

    /**
     * 验证文章级向量检索在 lattice schema 下仍可正常返回命中。
     */
    @Test
    void shouldSearchArticleVectorsWhenOperatorLivesInPublicSchema() {
        JdbcTemplate jdbcTemplate = createSchemaJdbcTemplate();
        resetTables(jdbcTemplate);
        Long modelProfileId = ensureEmbeddingModelProfile(jdbcTemplate);
        seedArticle(jdbcTemplate, 1L, "refund-status", "Refund Status", "退款状态流转说明");
        float[] embedding = testEmbedding();

        ArticleVectorJdbcRepository articleVectorJdbcRepository = new ArticleVectorJdbcRepository(jdbcTemplate);
        articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                "refund-status",
                modelProfileId,
                embedding.length,
                "repo-operator-test",
                "hash-article",
                embedding,
                OffsetDateTime.now()
        ));

        List<QueryArticleHit> hits = articleVectorJdbcRepository.searchNearestNeighbors(
                embedding,
                5
        );

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getConceptId()).isEqualTo("refund-status");
        assertThat(hits.get(0).getScore()).isGreaterThan(0.99D);
    }

    /**
     * 验证 chunk 级向量检索在 lattice schema 下仍可正常返回命中。
     */
    @Test
    void shouldSearchChunkVectorsWhenOperatorLivesInPublicSchema() {
        JdbcTemplate jdbcTemplate = createSchemaJdbcTemplate();
        resetTables(jdbcTemplate);
        Long modelProfileId = ensureEmbeddingModelProfile(jdbcTemplate);
        seedArticle(jdbcTemplate, 1L, "refund-status", "Refund Status", "退款状态流转说明");
        seedChunk(jdbcTemplate, 11L, 1L, 0, "退款完成后 T+1 日到账");
        float[] embedding = testEmbedding();

        ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository =
                new ArticleChunkVectorJdbcRepository(jdbcTemplate);
        articleChunkVectorJdbcRepository.upsert(new ArticleChunkVectorRecord(
                Long.valueOf(11L),
                Long.valueOf(1L),
                "refund-status",
                0,
                modelProfileId,
                "hash-chunk",
                embedding,
                OffsetDateTime.now()
        ));

        List<ArticleChunkVectorHit> hits = articleChunkVectorJdbcRepository.searchNearestNeighbors(
                embedding,
                5
        );

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getConceptId()).isEqualTo("refund-status");
        assertThat(hits.get(0).getChunkText()).isEqualTo("退款完成后 T+1 日到账");
        assertThat(hits.get(0).getScore()).isGreaterThan(0.99D);
    }

    /**
     * 清理测试表。
     *
     * @param jdbcTemplate JDBC 模板
     */
    private void resetTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute(
                """
                        TRUNCATE TABLE article_chunk_vector_index, article_vector_index, article_chunks, articles
                        RESTART IDENTITY CASCADE
                        """
        );
    }

    /**
     * 创建绑定到 lattice schema 的 JDBC 模板。
     *
     * @return JDBC 模板
     */
    private JdbcTemplate createSchemaJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(JDBC_BASE_URL + "?currentSchema=lattice");
        dataSource.setUsername(JDBC_USERNAME);
        dataSource.setPassword(JDBC_PASSWORD);
        return new JdbcTemplate(dataSource);
    }

    /**
     * 写入文章测试数据。
     *
     * @param jdbcTemplate JDBC 模板
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     */
    private void seedArticle(
            JdbcTemplate jdbcTemplate,
            Long articleId,
            String conceptId,
            String title,
            String content
    ) {
        ArticleJdbcRepository articleJdbcRepository = new ArticleJdbcRepository(jdbcTemplate);
        articleJdbcRepository.upsert(
                new ArticleRecord(
                        null,
                        conceptId,
                        conceptId,
                        title,
                        content,
                        "ACTIVE",
                        OffsetDateTime.now(),
                        Arrays.asList("refund/status.md"),
                        "{\"source\":\"vector\"}",
                        "",
                        List.<String>of(),
                        List.<String>of(),
                        List.<String>of(),
                        "medium",
                        "pending"
                )
        );
        jdbcTemplate.update(
                "UPDATE articles SET id = ? WHERE article_key = ?",
                articleId,
                conceptId
        );
    }

    /**
     * 写入文章分块测试数据。
     *
     * @param jdbcTemplate JDBC 模板
     * @param chunkId 分块主键
     * @param articleId 文章主键
     * @param chunkIndex 分块序号
     * @param chunkText 分块内容
     */
    private void seedChunk(
            JdbcTemplate jdbcTemplate,
            Long chunkId,
            Long articleId,
            int chunkIndex,
            String chunkText
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO article_chunks (
                            id, article_id, chunk_index, chunk_text, search_tsv
                        ) VALUES (?, ?, ?, ?, to_tsvector('simple'::regconfig, ?))
                        """,
                chunkId,
                articleId,
                Integer.valueOf(chunkIndex),
                chunkText,
                chunkText
        );
    }

    /**
     * 确保测试用 embedding 模型配置存在。
     *
     * @param jdbcTemplate JDBC 模板
     * @return 模型配置主键
     */
    private Long ensureEmbeddingModelProfile(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_provider_connections (
                            connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask
                        )
                        VALUES ('vector-operator-test-openai', 'OPENAI', 'https://api.openai.com', 'test', 'test')
                        ON CONFLICT (connection_code) DO NOTHING
                        """
        );
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM llm_provider_connections WHERE connection_code = 'vector-operator-test-openai'",
                Long.class
        );
        jdbcTemplate.update(
                """
                        INSERT INTO llm_model_profiles (
                            model_code, connection_id, model_name, model_kind, expected_dimensions
                        )
                        VALUES ('vector-operator-test-embedding', ?, 'text-embedding-test', 'EMBEDDING', 2000)
                        ON CONFLICT (model_code) DO NOTHING
                        """,
                connectionId
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM llm_model_profiles WHERE model_code = 'vector-operator-test-embedding'",
                Long.class
        );
    }

    /**
     * 构造匹配正式 schema 的 2000 维测试向量。
     *
     * @return 测试向量
     */
    private float[] testEmbedding() {
        float[] embedding = new float[2000];
        embedding[0] = 1.0F;
        embedding[1] = 2.0F;
        embedding[2] = 3.0F;
        return embedding;
    }
}
