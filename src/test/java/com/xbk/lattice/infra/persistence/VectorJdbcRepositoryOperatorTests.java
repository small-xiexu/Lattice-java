package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.service.ArticleChunkVectorHit;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量 JDBC 仓储运算符测试
 *
 * 职责：验证在独立 schema 连接下，pgvector 查询仍可正确解析 public schema 中的距离运算符
 *
 * @author xiexu
 */
class VectorJdbcRepositoryOperatorTests {

    private static final String JDBC_BASE_URL = "jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge";

    private static final String JDBC_USERNAME = "postgres";

    private static final String JDBC_PASSWORD = "postgres";

    /**
     * 验证文章级向量检索在独立 schema 下仍可正常返回命中。
     */
    @Test
    void shouldSearchArticleVectorsWhenOperatorLivesInPublicSchema() {
        String schemaName = "lattice_vector_repo_article_operator_test";
        resetSchema(schemaName);
        JdbcTemplate jdbcTemplate = createSchemaJdbcTemplate(schemaName);
        createArticleVectorTables(jdbcTemplate);
        seedArticle(jdbcTemplate, 1L, "refund-status", "Refund Status", "退款状态流转说明");

        ArticleVectorJdbcRepository articleVectorJdbcRepository = new ArticleVectorJdbcRepository(jdbcTemplate);
        articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                "refund-status",
                Long.valueOf(1L),
                3,
                "repo-operator-test",
                "hash-article",
                new float[]{1.0F, 2.0F, 3.0F},
                OffsetDateTime.now()
        ));

        List<QueryArticleHit> hits = articleVectorJdbcRepository.searchNearestNeighbors(
                new float[]{1.0F, 2.0F, 3.0F},
                5
        );

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getConceptId()).isEqualTo("refund-status");
        assertThat(hits.get(0).getScore()).isGreaterThan(0.99D);
    }

    /**
     * 验证 chunk 级向量检索在独立 schema 下仍可正常返回命中。
     */
    @Test
    void shouldSearchChunkVectorsWhenOperatorLivesInPublicSchema() {
        String schemaName = "lattice_vector_repo_chunk_operator_test";
        resetSchema(schemaName);
        JdbcTemplate jdbcTemplate = createSchemaJdbcTemplate(schemaName);
        createChunkVectorTables(jdbcTemplate);
        seedArticle(jdbcTemplate, 1L, "refund-status", "Refund Status", "退款状态流转说明");
        seedChunk(jdbcTemplate, 11L, 1L, 0, "退款完成后 T+1 日到账");

        ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository =
                new ArticleChunkVectorJdbcRepository(jdbcTemplate);
        articleChunkVectorJdbcRepository.upsert(new ArticleChunkVectorRecord(
                Long.valueOf(11L),
                Long.valueOf(1L),
                "refund-status",
                0,
                Long.valueOf(1L),
                "hash-chunk",
                new float[]{1.0F, 2.0F, 3.0F},
                OffsetDateTime.now()
        ));

        List<ArticleChunkVectorHit> hits = articleChunkVectorJdbcRepository.searchNearestNeighbors(
                new float[]{1.0F, 2.0F, 3.0F},
                5
        );

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getConceptId()).isEqualTo("refund-status");
        assertThat(hits.get(0).getChunkText()).isEqualTo("退款完成后 T+1 日到账");
        assertThat(hits.get(0).getScore()).isGreaterThan(0.99D);
    }

    /**
     * 重建测试 schema。
     *
     * @param schemaName schema 名称
     */
    private void resetSchema(String schemaName) {
        JdbcTemplate adminJdbcTemplate = createAdminJdbcTemplate();
        adminJdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        adminJdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
        adminJdbcTemplate.execute("CREATE SCHEMA \"" + schemaName + "\"");
    }

    /**
     * 创建管理连接 JDBC 模板。
     *
     * @return JDBC 模板
     */
    private JdbcTemplate createAdminJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(JDBC_BASE_URL);
        dataSource.setUsername(JDBC_USERNAME);
        dataSource.setPassword(JDBC_PASSWORD);
        return new JdbcTemplate(dataSource);
    }

    /**
     * 创建绑定到指定 schema 的 JDBC 模板。
     *
     * @param schemaName schema 名称
     * @return JDBC 模板
     */
    private JdbcTemplate createSchemaJdbcTemplate(String schemaName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(JDBC_BASE_URL + "?currentSchema=" + schemaName);
        dataSource.setUsername(JDBC_USERNAME);
        dataSource.setPassword(JDBC_PASSWORD);
        return new JdbcTemplate(dataSource);
    }

    /**
     * 创建文章级向量检索所需的最小表结构。
     *
     * @param jdbcTemplate JDBC 模板
     */
    private void createArticleVectorTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE articles (
                    id BIGINT PRIMARY KEY,
                    source_id BIGINT,
                    article_key VARCHAR(256) NOT NULL UNIQUE,
                    concept_id VARCHAR(128) NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    metadata_json JSONB NOT NULL,
                    source_paths TEXT[] NOT NULL,
                    compiled_at TIMESTAMPTZ NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_vector_index (
                    article_key VARCHAR(256) PRIMARY KEY,
                    concept_id VARCHAR(128) NOT NULL,
                    model_profile_id BIGINT NOT NULL,
                    embedding_dimensions INTEGER NOT NULL,
                    index_version VARCHAR(64) NOT NULL,
                    content_hash VARCHAR(64) NOT NULL,
                    embedding public.vector(3) NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL
                )
                """);
    }

    /**
     * 创建 chunk 级向量检索所需的最小表结构。
     *
     * @param jdbcTemplate JDBC 模板
     */
    private void createChunkVectorTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE articles (
                    id BIGINT PRIMARY KEY,
                    source_id BIGINT,
                    article_key VARCHAR(256) NOT NULL UNIQUE,
                    concept_id VARCHAR(128) NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    metadata_json JSONB NOT NULL,
                    source_paths TEXT[] NOT NULL,
                    compiled_at TIMESTAMPTZ NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_chunks (
                    id BIGINT PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    chunk_text TEXT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_chunk_vector_index (
                    article_chunk_id BIGINT PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    concept_id VARCHAR(128) NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    model_profile_id BIGINT NOT NULL,
                    content_hash VARCHAR(64) NOT NULL,
                    embedding public.vector(3) NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL
                )
                """);
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
        jdbcTemplate.update(
                """
                        INSERT INTO articles (
                            id, source_id, article_key, concept_id, title, content, metadata_json, source_paths, compiled_at
                        ) VALUES (?, ?, ?, ?, ?, ?, cast(? as jsonb), ARRAY['refund/status.md'], now())
                        """,
                articleId,
                null,
                conceptId,
                conceptId,
                title,
                content,
                "{\"source\":\"vector\"}"
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
                            id, article_id, chunk_index, chunk_text
                        ) VALUES (?, ?, ?, ?)
                        """,
                chunkId,
                articleId,
                Integer.valueOf(chunkIndex),
                chunkText
        );
    }
}
