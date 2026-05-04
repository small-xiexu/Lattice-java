package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.service.ArticleChunkVectorHit;
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
 * 文章分块向量索引 JDBC 仓储
 *
 * 职责：提供 chunk 级 embedding 索引的持久化与近邻检索能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ArticleChunkVectorJdbcRepository {

    private static final int HNSW_MAX_DIMENSIONS = 2000;

    private static final String ANN_INDEX_NAME_HNSW = "idx_article_chunk_vector_index_embedding_hnsw";

    private static final String ANN_INDEX_NAME_IVFFLAT = "idx_article_chunk_vector_index_embedding_ivfflat";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文章分块向量索引 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ArticleChunkVectorJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存或更新 chunk 向量索引。
     *
     * @param articleChunkVectorRecord 向量索引记录
     */
    public void upsert(ArticleChunkVectorRecord articleChunkVectorRecord) {
        if (jdbcTemplate == null) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        String sql = """
                insert into article_chunk_vector_index (
                    article_chunk_id, article_id, concept_id, chunk_index, model_profile_id, content_hash, embedding, updated_at
                )
                values (?, ?, ?, ?, ?, ?, cast(? as %s), ?)
                on conflict (article_chunk_id) do update
                set article_id = excluded.article_id,
                    concept_id = excluded.concept_id,
                    chunk_index = excluded.chunk_index,
                    model_profile_id = excluded.model_profile_id,
                    content_hash = excluded.content_hash,
                    embedding = excluded.embedding,
                    updated_at = excluded.updated_at
                """.formatted(vectorTypeName);
        jdbcTemplate.update(
                sql,
                articleChunkVectorRecord.getArticleChunkId(),
                articleChunkVectorRecord.getArticleId(),
                articleChunkVectorRecord.getConceptId(),
                articleChunkVectorRecord.getChunkIndex(),
                articleChunkVectorRecord.getModelProfileId(),
                articleChunkVectorRecord.getContentHash(),
                formatVector(articleChunkVectorRecord.getEmbedding()),
                articleChunkVectorRecord.getUpdatedAt()
        );
    }

    /**
     * 按分块主键查询向量索引。
     *
     * @param articleChunkId 分块主键
     * @return 向量索引
     */
    public Optional<ArticleChunkVectorRecord> findByArticleChunkId(Long articleChunkId) {
        if (jdbcTemplate == null || articleChunkId == null) {
            return Optional.empty();
        }
        List<ArticleChunkVectorRecord> records = jdbcTemplate.query(
                """
                        select article_chunk_id, article_id, concept_id, chunk_index, model_profile_id,
                               content_hash, embedding::text as embedding, updated_at
                        from article_chunk_vector_index
                        where article_chunk_id = ?
                        """,
                this::mapRecord,
                articleChunkId
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 返回当前分块向量索引记录总数。
     *
     * @return 向量索引总数
     */
    public int countAll() {
        if (jdbcTemplate == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from article_chunk_vector_index", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 清空全部分块向量索引记录。
     *
     * @return 删除记录数
     */
    public int deleteAll() {
        if (jdbcTemplate == null) {
            return 0;
        }
        return jdbcTemplate.update("delete from article_chunk_vector_index");
    }

    /**
     * 把 chunk 向量列对齐到目标维度。
     *
     * @param targetDimensions 目标维度
     */
    public void alignEmbeddingColumnDimensions(int targetDimensions) {
        if (jdbcTemplate == null || targetDimensions <= 0) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        dropEmbeddingAnnIndexes();
        jdbcTemplate.execute(
                "alter table article_chunk_vector_index alter column embedding type "
                        + vectorTypeName + "(" + targetDimensions + ")"
        );
    }

    /**
     * 确保 chunk 向量表已具备可用的 ANN 索引。
     */
    public void ensureEmbeddingAnnIndex() {
        if (jdbcTemplate == null) {
            return;
        }
        String annIndexMethod = resolveCompatibleAnnIndexMethod();
        if (annIndexMethod.isBlank()) {
            return;
        }
        String opClass = resolveVectorOperatorClass();
        if (opClass.isBlank()) {
            return;
        }
        if ("hnsw".equals(annIndexMethod)) {
            jdbcTemplate.execute(
                    "create index if not exists " + ANN_INDEX_NAME_HNSW
                            + " on article_chunk_vector_index using hnsw (embedding " + opClass + ")"
            );
            return;
        }
        jdbcTemplate.execute(
                "create index if not exists " + ANN_INDEX_NAME_IVFFLAT
                        + " on article_chunk_vector_index using ivfflat (embedding " + opClass + ") with (lists = 100)"
        );
    }

    /**
     * 返回当前分块向量索引最近更新时间。
     *
     * @return 最近更新时间
     */
    public Optional<OffsetDateTime> findLatestUpdatedAt() {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        List<OffsetDateTime> values = jdbcTemplate.query(
                "select updated_at from article_chunk_vector_index order by updated_at desc limit 1",
                (resultSet, rowNum) -> resultSet.getObject("updated_at", OffsetDateTime.class)
        );
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
    }

    /**
     * 返回 chunk 向量列的数据库类型描述。
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
                          and c.relname = 'article_chunk_vector_index'
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
     * 执行 chunk 级向量近邻检索。
     *
     * @param embedding 查询向量
     * @param limit 返回数量
     * @return chunk 命中
     */
    public List<ArticleChunkVectorHit> searchNearestNeighbors(float[] embedding, int limit) {
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
                        select vector_index.article_id,
                               article.source_id,
                               article.article_key,
                               vector_index.concept_id,
                               article.title,
                               article.content,
                               article.metadata_json::text as metadata_json,
                               article.review_status,
                               article.source_paths,
                               vector_index.chunk_index,
                               article_chunk.chunk_text,
                               1 - (vector_index.embedding %s cast(? as %s)) as score
                        from article_chunk_vector_index vector_index
                        join articles article on article.id = vector_index.article_id
                        join article_chunks article_chunk on article_chunk.id = vector_index.article_chunk_id
                        order by vector_index.embedding %s cast(? as %s), article.compiled_at desc
                        limit ?
                        """.formatted(distanceOperator, vectorTypeName, distanceOperator, vectorTypeName),
                this::mapHit,
                vectorLiteral,
                vectorLiteral,
                limit
        );
    }

    private ArticleChunkVectorRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ArticleChunkVectorRecord(
                resultSet.getLong("article_chunk_id"),
                resultSet.getLong("article_id"),
                resultSet.getString("concept_id"),
                resultSet.getInt("chunk_index"),
                resultSet.getObject("model_profile_id", Long.class),
                resultSet.getString("content_hash"),
                parseVector(resultSet.getString("embedding")),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private ArticleChunkVectorHit mapHit(ResultSet resultSet, int rowNum) throws SQLException {
        return new ArticleChunkVectorHit(
                resultSet.getLong("article_id"),
                readLong(resultSet, "source_id"),
                resultSet.getString("article_key"),
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("metadata_json"),
                resultSet.getString("review_status"),
                readSourcePaths(resultSet),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text"),
                resultSet.getDouble("score")
        );
    }

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

    private Long readLong(ResultSet resultSet, String columnName) throws SQLException {
        Object value = resultSet.getObject(columnName);
        return value == null ? null : resultSet.getLong(columnName);
    }

    private String resolveVectorTypeName() {
        if (jdbcTemplate == null) {
            return "";
        }

        List<String> values = jdbcTemplate.queryForList(
                "select coalesce(to_regtype('vector')::text, to_regtype('public.vector')::text)",
                String.class
        );
        if (values.isEmpty() || values.get(0) == null) {
            return "";
        }
        return values.get(0);
    }

    /**
     * 解析当前优先使用的 ANN 索引实现。
     *
     * @return 索引实现名
     */
    private String resolvePreferredAnnIndexMethod() {
        List<String> methods = jdbcTemplate.queryForList(
                "select amname from pg_am where amname in ('hnsw','ivfflat') order by case amname when 'hnsw' then 0 else 1 end",
                String.class
        );
        if (methods.isEmpty() || methods.get(0) == null) {
            return "";
        }
        return methods.get(0);
    }

    /**
     * 解析当前维度下可兼容的 ANN 索引实现。
     *
     * @return 可兼容的索引实现名
     */
    private String resolveCompatibleAnnIndexMethod() {
        String preferredMethod = resolvePreferredAnnIndexMethod();
        if (!"hnsw".equals(preferredMethod)) {
            return preferredMethod;
        }

        Integer embeddingDimensions = findEmbeddingColumnDimensions();
        if (embeddingDimensions == null || embeddingDimensions.intValue() <= HNSW_MAX_DIMENSIONS) {
            return preferredMethod;
        }
        return "";
    }

    /**
     * 返回当前 embedding 列维度。
     *
     * @return embedding 列维度
     */
    private Integer findEmbeddingColumnDimensions() {
        String embeddingColumnType = findEmbeddingColumnType().orElse("");
        if (embeddingColumnType.isBlank()) {
            return null;
        }
        int startIndex = embeddingColumnType.lastIndexOf("vector(");
        if (startIndex < 0) {
            return null;
        }
        int dimensionsStartIndex = startIndex + "vector(".length();
        int dimensionsEndIndex = embeddingColumnType.indexOf(')', dimensionsStartIndex);
        if (dimensionsEndIndex < 0) {
            return null;
        }
        return Integer.valueOf(embeddingColumnType.substring(dimensionsStartIndex, dimensionsEndIndex));
    }


    /**
     * 解析向量索引使用的 opclass。
     *
     * @return schema-qualified opclass
     */
    private String resolveVectorOperatorClass() {
        List<String> values = jdbcTemplate.queryForList(
                "select opcnamespace::regnamespace || '.vector_cosine_ops' from pg_opclass where opcname = 'vector_cosine_ops' order by oid asc limit 1",
                String.class
        );
        if (values.isEmpty() || values.get(0) == null) {
            return "";
        }
        return values.get(0);
    }

    /**
     * 删除历史 ANN 索引，便于在切换向量维度时重新创建。
     */
    private void dropEmbeddingAnnIndexes() {
        jdbcTemplate.execute("drop index if exists " + ANN_INDEX_NAME_HNSW);
        jdbcTemplate.execute("drop index if exists " + ANN_INDEX_NAME_IVFFLAT);
    }

    /**
     * 解析向量距离运算符表达式。
     *
     * <p>独立 schema 连接下，pgvector 运算符通常仍定义在扩展 schema（例如 {@code public}）
     * 中，因此需要使用显式 schema-qualified operator 调用。</p>
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

    /**
     * 格式化向量字面量。
     *
     * @param embedding embedding 向量
     * @return 向量字面量
     */
    private String formatVector(float[] embedding) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding[index]);
        }
        builder.append(']');
        return builder.toString();
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
        String normalized = vectorLiteral.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return new float[0];
        }
        String[] tokens = normalized.split(",");
        float[] embedding = new float[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            embedding[index] = Float.parseFloat(tokens[index].trim());
        }
        return embedding;
    }
}
