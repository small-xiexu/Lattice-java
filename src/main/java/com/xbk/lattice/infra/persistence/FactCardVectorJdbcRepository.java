package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 事实证据卡向量索引 JDBC 仓储
 *
 * 职责：提供 fact card embedding 索引的持久化与基础维护能力
 *
 * @author xiexu
 */
@Repository
public class FactCardVectorJdbcRepository {

    private static final int HNSW_MAX_DIMENSIONS = 2000;

    private static final String ANN_INDEX_NAME_HNSW = "idx_fact_card_vector_index_embedding_hnsw";

    private static final String ANN_INDEX_NAME_IVFFLAT = "idx_fact_card_vector_index_embedding_ivfflat";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建事实证据卡向量索引 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public FactCardVectorJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存或更新事实证据卡向量索引。
     *
     * @param factCardVectorRecord 事实证据卡向量索引记录
     */
    public void upsert(FactCardVectorRecord factCardVectorRecord) {
        if (jdbcTemplate == null || factCardVectorRecord == null || !tableExists()) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        String sql = """
                insert into fact_card_vector_index (
                    fact_card_id, card_id, card_type, answer_shape, model_profile_id,
                    embedding_dimensions, index_version, content_hash, embedding, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as %s), ?)
                on conflict (fact_card_id) do update
                set card_id = excluded.card_id,
                    card_type = excluded.card_type,
                    answer_shape = excluded.answer_shape,
                    model_profile_id = excluded.model_profile_id,
                    embedding_dimensions = excluded.embedding_dimensions,
                    index_version = excluded.index_version,
                    content_hash = excluded.content_hash,
                    embedding = excluded.embedding,
                    updated_at = excluded.updated_at
                """.formatted(vectorTypeName);
        jdbcTemplate.update(
                sql,
                factCardVectorRecord.getFactCardId(),
                factCardVectorRecord.getCardId(),
                factCardVectorRecord.getCardType().name(),
                factCardVectorRecord.getAnswerShape().name(),
                factCardVectorRecord.getModelProfileId(),
                Integer.valueOf(factCardVectorRecord.getEmbeddingDimensions()),
                factCardVectorRecord.getIndexVersion(),
                factCardVectorRecord.getContentHash(),
                formatVector(factCardVectorRecord.getEmbedding()),
                factCardVectorRecord.getUpdatedAt()
        );
    }

    /**
     * 按事实证据卡主键查询向量索引。
     *
     * @param factCardId 事实证据卡主键
     * @return 向量索引记录
     */
    public Optional<FactCardVectorRecord> findByFactCardId(Long factCardId) {
        if (jdbcTemplate == null || factCardId == null || !tableExists()) {
            return Optional.empty();
        }
        List<FactCardVectorRecord> records = jdbcTemplate.query(
                """
                        select fact_card_id, card_id, card_type, answer_shape, model_profile_id,
                               embedding_dimensions, index_version, content_hash, embedding::text as embedding, updated_at
                        from fact_card_vector_index
                        where fact_card_id = ?
                        """,
                this::mapRecord,
                factCardId
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 统计全部事实证据卡向量索引数量。
     *
     * @return 向量索引数量
     */
    public int countAll() {
        if (jdbcTemplate == null || !tableExists()) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from fact_card_vector_index", Integer.class);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 清空全部事实证据卡向量索引。
     *
     * @return 删除数量
     */
    public int deleteAll() {
        if (jdbcTemplate == null || !tableExists()) {
            return 0;
        }
        return jdbcTemplate.update("delete from fact_card_vector_index");
    }

    /**
     * 把 fact card 向量列对齐到目标维度。
     *
     * @param targetDimensions 目标维度
     */
    public void alignEmbeddingColumnDimensions(int targetDimensions) {
        if (jdbcTemplate == null || targetDimensions <= 0 || !tableExists()) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        dropEmbeddingAnnIndexes();
        jdbcTemplate.execute(
                "alter table fact_card_vector_index alter column embedding type "
                        + vectorTypeName + "(" + targetDimensions + ")"
        );
    }

    /**
     * 确保 fact card 向量表具备可用 ANN 索引。
     */
    public void ensureEmbeddingAnnIndex() {
        if (jdbcTemplate == null || !tableExists()) {
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
                            + " on fact_card_vector_index using hnsw (embedding " + opClass + ")"
            );
            return;
        }
        jdbcTemplate.execute(
                "create index if not exists " + ANN_INDEX_NAME_IVFFLAT
                        + " on fact_card_vector_index using ivfflat (embedding " + opClass + ") with (lists = 100)"
        );
    }

    /**
     * 查询向量列数据库类型。
     *
     * @return 向量列类型
     */
    public Optional<String> findEmbeddingColumnType() {
        if (jdbcTemplate == null || !tableExists()) {
            return Optional.empty();
        }
        List<String> values = jdbcTemplate.queryForList(
                """
                        select format_type(a.atttypid, a.atttypmod)
                        from pg_attribute a
                        join pg_class c on c.oid = a.attrelid
                        join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = current_schema()
                          and c.relname = 'fact_card_vector_index'
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
     * 查询向量索引最近更新时间。
     *
     * @return 最近更新时间
     */
    public Optional<OffsetDateTime> findLatestUpdatedAt() {
        if (jdbcTemplate == null || !tableExists()) {
            return Optional.empty();
        }
        List<OffsetDateTime> values = jdbcTemplate.query(
                "select updated_at from fact_card_vector_index order by updated_at desc limit 1",
                (resultSet, rowNum) -> resultSet.getObject("updated_at", OffsetDateTime.class)
        );
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
    }

    /**
     * 执行 fact card 向量近邻检索。
     *
     * @param embedding 查询向量
     * @param limit 返回数量
     * @return fact card 命中
     */
    public List<QueryArticleHit> searchNearestNeighbors(float[] embedding, int limit) {
        if (jdbcTemplate == null || embedding == null || embedding.length == 0 || !tableExists()) {
            return List.of();
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return List.of();
        }
        String distanceOperator = resolveDistanceOperator(vectorTypeName);
        int safeLimit = limit <= 0 ? 5 : limit;
        String vectorLiteral = formatVector(embedding);
        return jdbcTemplate.query(
                """
                        select fc.source_id,
                               fc.card_id,
                               fc.card_id as concept_id,
                               fc.title,
                               concat_ws(E'\\n\\n', fc.claim, fc.items_json::text, fc.evidence_text) as content,
                               sf.file_path,
                               jsonb_build_object(
                                   'factCardId', fc.id,
                                   'cardId', fc.card_id,
                                   'sourceFileId', fc.source_file_id,
                                   'cardType', fc.card_type,
                                   'answerShape', fc.answer_shape,
                                   'confidence', fc.confidence,
                                   'sourceChunkIds', fc.source_chunk_ids,
                                   'articleIds', fc.article_ids
                               )::text as metadata_json,
                               fc.review_status,
                               1 - (vector_index.embedding %s cast(? as %s)) as score
                        from fact_card_vector_index vector_index
                        join fact_cards fc on fc.id = vector_index.fact_card_id
                        left join source_files sf on sf.id = fc.source_file_id
                        order by vector_index.embedding %s cast(? as %s), fc.updated_at desc
                        limit ?
                        """.formatted(distanceOperator, vectorTypeName, distanceOperator, vectorTypeName),
                this::mapQueryArticleHit,
                vectorLiteral,
                vectorLiteral,
                Integer.valueOf(safeLimit)
        );
    }

    /**
     * 映射事实证据卡向量索引记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 向量索引记录
     * @throws SQLException SQL 异常
     */
    private FactCardVectorRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new FactCardVectorRecord(
                resultSet.getObject("fact_card_id", Long.class),
                resultSet.getString("card_id"),
                FactCardType.fromValue(resultSet.getString("card_type")),
                AnswerShape.fromValue(resultSet.getString("answer_shape")),
                resultSet.getObject("model_profile_id", Long.class),
                resultSet.getInt("embedding_dimensions"),
                resultSet.getString("index_version"),
                resultSet.getString("content_hash"),
                parseVector(resultSet.getString("embedding")),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    /**
     * 映射 fact card 查询命中。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 查询命中
     * @throws SQLException SQL 异常
     */
    private QueryArticleHit mapQueryArticleHit(ResultSet resultSet, int rowNum) throws SQLException {
        return new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                resultSet.getObject("source_id", Long.class),
                resultSet.getString("card_id"),
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("metadata_json"),
                resultSet.getString("review_status"),
                sourcePaths(resultSet),
                resultSet.getDouble("score")
        );
    }

    /**
     * 读取事实证据卡回指的源文件路径。
     *
     * @param resultSet 结果集
     * @return 源文件路径列表
     * @throws SQLException SQL 异常
     */
    private List<String> sourcePaths(ResultSet resultSet) throws SQLException {
        String filePath = resultSet.getString("file_path");
        if (filePath == null || filePath.isBlank()) {
            return List.of();
        }
        return List.of(filePath);
    }

    /**
     * 判断向量索引表是否存在。
     *
     * @return 表是否存在
     */
    private boolean tableExists() {
        List<String> values = jdbcTemplate.queryForList(
                "select to_regclass(current_schema() || '.fact_card_vector_index')::text",
                String.class
        );
        return !values.isEmpty() && values.get(0) != null && !values.get(0).isBlank();
    }

    /**
     * 解析当前可用的 vector 类型名称。
     *
     * @return vector 类型名称
     */
    private String resolveVectorTypeName() {
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
     * @return 可兼容索引实现名
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
     * 解析向量距离操作符。
     *
     * @param vectorTypeName vector 类型名称
     * @return 距离操作符
     */
    private String resolveDistanceOperator(String vectorTypeName) {
        if (vectorTypeName != null && vectorTypeName.contains("halfvec")) {
            return "<=>";
        }
        return "<=>";
    }

    /**
     * 删除历史 ANN 索引。
     */
    private void dropEmbeddingAnnIndexes() {
        jdbcTemplate.execute("drop index if exists " + ANN_INDEX_NAME_HNSW);
        jdbcTemplate.execute("drop index if exists " + ANN_INDEX_NAME_IVFFLAT);
    }

    /**
     * 格式化向量字面量。
     *
     * @param embedding embedding 向量
     * @return 向量字面量
     */
    private String formatVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[]";
        }
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
