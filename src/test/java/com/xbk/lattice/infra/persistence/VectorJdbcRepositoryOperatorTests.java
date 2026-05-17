package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.service.ArticleChunkVectorHit;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.llm.deep-research-startup-validation-enabled=false"
})
class VectorJdbcRepositoryOperatorTests {

    private static final int BASELINE_VECTOR_DIMENSIONS = 2000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleVectorJdbcRepository articleVectorJdbcRepository;

    @Autowired
    private ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    /**
     * 验证文章级向量检索在 lattice schema 下仍可正常返回命中。
     */
    @Test
    void shouldSearchArticleVectorsWhenOperatorLivesInPublicSchema() {
        resetTables();
        Long modelProfileId = ensureEmbeddingModelProfile();
        seedArticle(1L, "refund-status", "Refund Status", "退款状态流转说明");
        seedArticle(2L, "refund-status-pending", "Refund Status Pending", "退款待复核状态流转说明", "pending");
        seedArticle(
                3L,
                "refund-status-needs-human-review",
                "Refund Status Needs Human Review",
                "退款人工复核状态流转说明",
                "needs_human_review"
        );
        seedArticle(4L, "refund-status-rejected", "Refund Status Rejected", "退款拒绝状态流转说明", "rejected");
        seedArticle(
                5L,
                "refund-status-archived",
                "Refund Status Archived",
                "退款归档状态流转说明",
                "passed",
                "ARCHIVED"
        );
        float[] embedding = testEmbedding();

        articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                "refund-status",
                modelProfileId,
                embedding.length,
                "repo-operator-test",
                "hash-article",
                embedding,
                OffsetDateTime.now()
        ));
        articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                "refund-status-pending",
                modelProfileId,
                embedding.length,
                "repo-operator-test",
                "hash-article-pending",
                embedding,
                OffsetDateTime.now()
        ));
        articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                "refund-status-needs-human-review",
                modelProfileId,
                embedding.length,
                "repo-operator-test",
                "hash-article-needs-human-review",
                embedding,
                OffsetDateTime.now()
        ));
        articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                "refund-status-rejected",
                modelProfileId,
                embedding.length,
                "repo-operator-test",
                "hash-article-rejected",
                embedding,
                OffsetDateTime.now()
        ));
        articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                "refund-status-archived",
                modelProfileId,
                embedding.length,
                "repo-operator-test",
                "hash-article-archived",
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
        assertThat(hits)
                .extracting(QueryArticleHit::getConceptId)
                .doesNotContain(
                        "refund-status-pending",
                        "refund-status-needs-human-review",
                        "refund-status-rejected",
                        "refund-status-archived"
                );
    }

    /**
     * 验证 chunk 级向量检索在 lattice schema 下仍可正常返回命中。
     */
    @Test
    void shouldSearchChunkVectorsWhenOperatorLivesInPublicSchema() {
        resetTables();
        Long modelProfileId = ensureEmbeddingModelProfile();
        seedArticle(1L, "refund-status", "Refund Status", "退款状态流转说明");
        seedArticle(2L, "refund-status-pending", "Refund Status Pending", "退款待复核状态流转说明", "pending");
        seedArticle(
                3L,
                "refund-status-needs-human-review",
                "Refund Status Needs Human Review",
                "退款人工复核状态流转说明",
                "needs_human_review"
        );
        seedArticle(4L, "refund-status-rejected", "Refund Status Rejected", "退款拒绝状态流转说明", "rejected");
        seedArticle(
                5L,
                "refund-status-archived",
                "Refund Status Archived",
                "退款归档状态流转说明",
                "passed",
                "ARCHIVED"
        );
        seedChunk(jdbcTemplate, 11L, 1L, 0, "退款完成后 T+1 日到账");
        seedChunk(jdbcTemplate, 12L, 2L, 0, "退款待复核后 T+1 日到账");
        seedChunk(jdbcTemplate, 13L, 3L, 0, "退款人工复核后 T+1 日到账");
        seedChunk(jdbcTemplate, 14L, 4L, 0, "退款拒绝后 T+1 日到账");
        seedChunk(jdbcTemplate, 15L, 5L, 0, "退款归档后 T+1 日到账");
        float[] embedding = testEmbedding();

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
        articleChunkVectorJdbcRepository.upsert(new ArticleChunkVectorRecord(
                Long.valueOf(12L),
                Long.valueOf(2L),
                "refund-status-pending",
                0,
                modelProfileId,
                "hash-chunk-pending",
                embedding,
                OffsetDateTime.now()
        ));
        articleChunkVectorJdbcRepository.upsert(new ArticleChunkVectorRecord(
                Long.valueOf(13L),
                Long.valueOf(3L),
                "refund-status-needs-human-review",
                0,
                modelProfileId,
                "hash-chunk-needs-human-review",
                embedding,
                OffsetDateTime.now()
        ));
        articleChunkVectorJdbcRepository.upsert(new ArticleChunkVectorRecord(
                Long.valueOf(14L),
                Long.valueOf(4L),
                "refund-status-rejected",
                0,
                modelProfileId,
                "hash-chunk-rejected",
                embedding,
                OffsetDateTime.now()
        ));
        articleChunkVectorJdbcRepository.upsert(new ArticleChunkVectorRecord(
                Long.valueOf(15L),
                Long.valueOf(5L),
                "refund-status-archived",
                0,
                modelProfileId,
                "hash-chunk-archived",
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
        assertThat(hits)
                .extracting(ArticleChunkVectorHit::getConceptId)
                .doesNotContain(
                        "refund-status-pending",
                        "refund-status-needs-human-review",
                        "refund-status-rejected",
                        "refund-status-archived"
                );
    }

    /**
     * 清理测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute(
                """
                        TRUNCATE TABLE article_chunk_vector_index, article_vector_index, article_chunks, articles
                        RESTART IDENTITY CASCADE
                        """
        );
        restoreVectorSchemaBaseline();
        synchronizeLlmSequences();
    }

    /**
     * 恢复共享测试库中的向量 schema 基线。
     */
    private void restoreVectorSchemaBaseline() {
        articleChunkVectorJdbcRepository.deleteAll();
        articleVectorJdbcRepository.deleteAll();
        articleVectorJdbcRepository.alignEmbeddingColumnDimensions(BASELINE_VECTOR_DIMENSIONS);
        articleChunkVectorJdbcRepository.alignEmbeddingColumnDimensions(BASELINE_VECTOR_DIMENSIONS);
    }

    /**
     * 写入文章测试数据。
     *
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     */
    private void seedArticle(
            Long articleId,
            String conceptId,
            String title,
            String content
    ) {
        seedArticle(articleId, conceptId, title, content, "passed");
    }

    /**
     * 写入指定审查状态的文章测试数据。
     *
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param reviewStatus 审查状态
     */
    private void seedArticle(
            Long articleId,
            String conceptId,
            String title,
            String content,
            String reviewStatus
    ) {
        seedArticle(articleId, conceptId, title, content, reviewStatus, "ACTIVE");
    }

    /**
     * 写入指定审查状态与生命周期的文章测试数据。
     *
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param reviewStatus 审查状态
     * @param lifecycle 生命周期
     */
    private void seedArticle(
            Long articleId,
            String conceptId,
            String title,
            String content,
            String reviewStatus,
            String lifecycle
    ) {
        articleJdbcRepository.upsert(
                new ArticleRecord(
                        null,
                        conceptId,
                        conceptId,
                        title,
                        content,
                        lifecycle,
                        OffsetDateTime.now(),
                        Arrays.asList("refund/status.md"),
                        "{\"source\":\"vector\"}",
                        "",
                        List.<String>of(),
                        List.<String>of(),
                        List.<String>of(),
                        "medium",
                        reviewStatus
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
     * @return 模型配置主键
     */
    private Long ensureEmbeddingModelProfile() {
        synchronizeLlmSequences();
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
                        VALUES ('vector-operator-test-embedding', ?, 'text-embedding-test', 'EMBEDDING', ?)
                        ON CONFLICT (model_code) DO NOTHING
                        """,
                connectionId,
                Integer.valueOf(BASELINE_VECTOR_DIMENSIONS)
        );
        synchronizeLlmSequences();
        return jdbcTemplate.queryForObject(
                "SELECT id FROM llm_model_profiles WHERE model_code = 'vector-operator-test-embedding'",
                Long.class
        );
    }

    /**
     * 将 LLM 配置表序列同步到当前最大主键，避免默认自增撞主键。
     */
    private void synchronizeLlmSequences() {
        jdbcTemplate.execute(
                """
                        select setval(
                            'llm_provider_connections_id_seq'::regclass,
                            coalesce((select max(id) from llm_provider_connections), 1),
                            true
                        )
                        """
        );
        jdbcTemplate.execute(
                """
                        select setval(
                            'llm_model_profiles_id_seq'::regclass,
                            coalesce((select max(id) from llm_model_profiles), 1),
                            true
                        )
                        """
        );
    }

    /**
     * 构造匹配正式 schema 的 2000 维测试向量。
     *
     * @return 测试向量
     */
    private float[] testEmbedding() {
        float[] embedding = new float[BASELINE_VECTOR_DIMENSIONS];
        embedding[0] = 1.0F;
        embedding[1] = 2.0F;
        embedding[2] = 3.0F;
        return embedding;
    }
}
