package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.service.QueryArticleHit;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文章向量索引 JDBC 仓储
 *
 * 职责：提供文章 embedding 索引的持久化与近邻检索能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ArticleVectorJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文章向量索引 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ArticleVectorJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存或更新文章向量索引。
     *
     * @param articleVectorRecord 向量索引记录
     */
    public void upsert(ArticleVectorRecord articleVectorRecord) {
        if (jdbcTemplate == null) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        String sql = """
                insert into article_vector_index (
                    concept_id, model_name, content_hash, embedding, updated_at
                )
                values (?, ?, ?, cast(? as %s), ?)
                on conflict (concept_id) do update
                set model_name = excluded.model_name,
                    content_hash = excluded.content_hash,
                    embedding = excluded.embedding,
                    updated_at = excluded.updated_at
                """.formatted(vectorTypeName);
        jdbcTemplate.update(
                sql,
                articleVectorRecord.getConceptId(),
                articleVectorRecord.getModelName(),
                articleVectorRecord.getContentHash(),
                formatVector(articleVectorRecord.getEmbedding()),
                articleVectorRecord.getUpdatedAt()
        );
    }

    /**
     * 按概念标识查询向量索引。
     *
     * @param conceptId 概念标识
     * @return 向量索引
     */
    public Optional<ArticleVectorRecord> findByConceptId(String conceptId) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }

        List<ArticleVectorRecord> records = jdbcTemplate.query(
                """
                        select concept_id, model_name, content_hash, embedding::text as embedding, updated_at
                        from article_vector_index
                        where concept_id = ?
                        """,
                this::mapArticleVectorRecord,
                conceptId
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 执行向量近邻检索。
     *
     * @param embedding 查询向量
     * @param limit 返回数量
     * @return 文章命中
     */
    public List<QueryArticleHit> searchNearestNeighbors(float[] embedding, int limit) {
        if (jdbcTemplate == null || embedding == null || embedding.length == 0) {
            return List.of();
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return List.of();
        }

        String vectorLiteral = formatVector(embedding);
        return jdbcTemplate.query(
                """
                        select a.concept_id,
                               a.title,
                               a.content,
                               a.metadata_json::text as metadata_json,
                               a.source_paths,
                               1 - (v.embedding <=> cast(? as %s)) as score
                        from article_vector_index v
                        join articles a on a.concept_id = v.concept_id
                        order by v.embedding <=> cast(? as %s), a.compiled_at desc
                        limit ?
                        """.formatted(vectorTypeName, vectorTypeName),
                this::mapQueryArticleHit,
                vectorLiteral,
                vectorLiteral,
                limit
        );
    }

    /**
     * 映射文章向量索引记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 向量索引记录
     * @throws SQLException SQL 异常
     */
    private ArticleVectorRecord mapArticleVectorRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ArticleVectorRecord(
                resultSet.getString("concept_id"),
                resultSet.getString("model_name"),
                resultSet.getString("content_hash"),
                parseVector(resultSet.getString("embedding")),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    /**
     * 映射向量近邻文章命中。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 文章命中
     * @throws SQLException SQL 异常
     */
    private QueryArticleHit mapQueryArticleHit(ResultSet resultSet, int rowNum) throws SQLException {
        return new QueryArticleHit(
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("metadata_json"),
                readSourcePaths(resultSet),
                resultSet.getDouble("score")
        );
    }

    /**
     * 读取来源路径数组。
     *
     * @param resultSet 结果集
     * @return 来源路径
     * @throws SQLException SQL 异常
     */
    private List<String> readSourcePaths(ResultSet resultSet) throws SQLException {
        Array sourcePathsArray = resultSet.getArray("source_paths");
        if (sourcePathsArray == null) {
            return List.of();
        }

        Object[] values = (Object[]) sourcePathsArray.getArray();
        List<String> sourcePaths = new ArrayList<String>();
        for (Object value : values) {
            sourcePaths.add(String.valueOf(value));
        }
        return sourcePaths;
    }

    /**
     * 格式化向量字面量。
     *
     * @param embedding embedding 向量
     * @return 向量字面量
     */
    private String formatVector(float[] embedding) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                stringBuilder.append(',');
            }
            stringBuilder.append(embedding[index]);
        }
        stringBuilder.append(']');
        return stringBuilder.toString();
    }

    /**
     * 解析向量字面量。
     *
     * @param vectorLiteral 向量字面量
     * @return 向量数组
     */
    private float[] parseVector(String vectorLiteral) {
        if (vectorLiteral == null || vectorLiteral.isBlank()) {
            return new float[0];
        }

        String normalizedLiteral = vectorLiteral.trim();
        String body = normalizedLiteral.substring(1, normalizedLiteral.length() - 1);
        if (body.isBlank()) {
            return new float[0];
        }

        String[] values = body.split(",");
        float[] embedding = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            embedding[index] = Float.parseFloat(values[index].trim());
        }
        return embedding;
    }

    /**
     * 解析当前可用的 vector 类型名称。
     *
     * @return vector 类型名称
     */
    private String resolveVectorTypeName() {
        String vectorTypeName = jdbcTemplate.queryForObject(
                "select coalesce(to_regtype('vector')::text, to_regtype('public.vector')::text, '')",
                String.class
        );
        if (vectorTypeName == null) {
            return "";
        }
        return vectorTypeName;
    }
}
