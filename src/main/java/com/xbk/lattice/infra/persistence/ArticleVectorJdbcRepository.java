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
                    concept_id, model_profile_id, embedding_dimensions, index_version, content_hash, embedding, updated_at
                )
                values (?, ?, ?, ?, ?, cast(? as %s), ?)
                on conflict (concept_id) do update
                set model_profile_id = excluded.model_profile_id,
                    embedding_dimensions = excluded.embedding_dimensions,
                    index_version = excluded.index_version,
                    content_hash = excluded.content_hash,
                    embedding = excluded.embedding,
                    updated_at = excluded.updated_at
                """.formatted(vectorTypeName);
        jdbcTemplate.update(
                sql,
                articleVectorRecord.getConceptId(),
                articleVectorRecord.getModelProfileId(),
                articleVectorRecord.getEmbeddingDimensions(),
                articleVectorRecord.getIndexVersion(),
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
                        select concept_id, model_profile_id, embedding_dimensions, index_version,
                               content_hash, embedding::text as embedding, updated_at
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
     * 返回当前向量索引记录总数。
     *
     * @return 向量索引总数
     */
    public int countAll() {
        if (jdbcTemplate == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from article_vector_index", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 清空全部向量索引记录。
     *
     * @return 删除记录数
     */
    public int deleteAll() {
        if (jdbcTemplate == null) {
            return 0;
        }
        return jdbcTemplate.update("delete from article_vector_index");
    }

    /**
     * 返回当前向量索引最近更新时间。
     *
     * @return 最近更新时间
     */
    public Optional<OffsetDateTime> findLatestUpdatedAt() {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        List<OffsetDateTime> values = jdbcTemplate.query(
                "select updated_at from article_vector_index order by updated_at desc limit 1",
                (resultSet, rowNum) -> resultSet.getObject("updated_at", OffsetDateTime.class)
        );
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
    }

    /**
     * 返回当前向量索引中出现过的模型名称。
     *
     * @return 模型名称列表
     */
    public List<String> findDistinctModelNames() {
        if (jdbcTemplate == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                """
                        select distinct profile.model_name
                        from article_vector_index vector_index
                        join llm_model_profiles profile on profile.id = vector_index.model_profile_id
                        order by profile.model_name asc
                        """,
                String.class
        );
    }

    /**
     * 返回向量列的数据库类型描述。
     *
     * @return 向量列类型描述
     */
    public Optional<String> findEmbeddingColumnType() {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        List<String> values = jdbcTemplate.queryForList(
                """
                        select format_type(a.atttypid, a.atttypmod)
                        from pg_attribute a
                        join pg_class c on c.oid = a.attrelid
                        join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = current_schema()
                          and c.relname = 'article_vector_index'
                          and a.attname = 'embedding'
                          and a.attnum > 0
                          and not a.attisdropped
                        """,
                String.class
        );
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
    }

    /**
     * 返回 embedding 列使用的 ANN 索引类型。
     *
     * @return ANN 索引类型
     */
    public Optional<String> findEmbeddingAnnIndexType() {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        List<String> values = jdbcTemplate.queryForList(
                """
                        select am.amname
                        from pg_index idx
                        join pg_class index_rel on index_rel.oid = idx.indexrelid
                        join pg_class table_rel on table_rel.oid = idx.indrelid
                        join pg_namespace namespace_rel on namespace_rel.oid = table_rel.relnamespace
                        join pg_am am on am.oid = index_rel.relam
                        join pg_attribute attr on attr.attrelid = table_rel.oid
                        where namespace_rel.nspname = current_schema()
                          and table_rel.relname = 'article_vector_index'
                          and attr.attname = 'embedding'
                          and attr.attnum = any(idx.indkey)
                        order by index_rel.relname asc
                        """,
                String.class
        );
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
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
        String distanceOperator = resolveDistanceOperator(vectorTypeName);

        String vectorLiteral = formatVector(embedding);
        return jdbcTemplate.query(
                """
                        select a.concept_id,
                               a.title,
                               a.content,
                               a.metadata_json::text as metadata_json,
                               a.source_paths,
                               1 - (v.embedding %s cast(? as %s)) as score
                        from article_vector_index v
                        join articles a on a.concept_id = v.concept_id
                        order by v.embedding %s cast(? as %s), a.compiled_at desc
                        limit ?
                        """.formatted(distanceOperator, vectorTypeName, distanceOperator, vectorTypeName),
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
                resultSet.getObject("model_profile_id", Long.class),
                resultSet.getInt("embedding_dimensions"),
                resultSet.getString("index_version"),
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

    /**
     * 解析向量距离运算符表达式。
     *
     * <p>当连接通过 {@code currentSchema} 切到业务 schema 时，pgvector 扩展仍常驻在
     * {@code public}，此时必须显式写成 {@code OPERATOR(public.<=>)} 才能命中对应运算符。</p>
     *
     * @param vectorTypeName vector 类型名
     * @return 距离运算符表达式
     */
    private String resolveDistanceOperator(String vectorTypeName) {
        int schemaSeparatorIndex = vectorTypeName.indexOf('.');
        if (schemaSeparatorIndex < 0) {
            return "<=>";
        }
        String schemaName = vectorTypeName.substring(0, schemaSeparatorIndex);
        return "OPERATOR(" + schemaName + ".<=>)";
    }
}
