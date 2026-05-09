package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.ArticleChunkVectorMapper;
import com.xbk.lattice.query.service.ArticleChunkVectorHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
public class ArticleChunkVectorJdbcRepository {

    private static final int HNSW_MAX_DIMENSIONS = 2000;

    private static final String ANN_INDEX_NAME_HNSW = "idx_article_chunk_vector_index_embedding_hnsw";

    private static final String ANN_INDEX_NAME_IVFFLAT = "idx_article_chunk_vector_index_embedding_ivfflat";

    private final ArticleChunkVectorMapper articleChunkVectorMapper;

    /**
     * 创建文章分块向量索引仓储。
     *
     * @param articleChunkVectorMapper 文章分块向量 Mapper
     */
    @Autowired
    public ArticleChunkVectorJdbcRepository(ArticleChunkVectorMapper articleChunkVectorMapper) {
        this.articleChunkVectorMapper = articleChunkVectorMapper;
    }

    /**
     * 保存或更新 chunk 向量索引。
     *
     * @param articleChunkVectorRecord 向量索引记录
     */
    public void upsert(ArticleChunkVectorRecord articleChunkVectorRecord) {
        if (articleChunkVectorMapper == null) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        articleChunkVectorMapper.upsert(
                articleChunkVectorRecord,
                formatVector(articleChunkVectorRecord.getEmbedding()),
                vectorTypeName
        );
    }

    /**
     * 按分块主键查询向量索引。
     *
     * @param articleChunkId 分块主键
     * @return 向量索引
     */
    public Optional<ArticleChunkVectorRecord> findByArticleChunkId(Long articleChunkId) {
        if (articleChunkVectorMapper == null || articleChunkId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articleChunkVectorMapper.findByArticleChunkId(articleChunkId));
    }

    /**
     * 返回当前分块向量索引记录总数。
     *
     * @return 向量索引总数
     */
    public int countAll() {
        if (articleChunkVectorMapper == null) {
            return 0;
        }
        return articleChunkVectorMapper.countAll();
    }

    /**
     * 清空全部分块向量索引记录。
     *
     * @return 删除记录数
     */
    public int deleteAll() {
        if (articleChunkVectorMapper == null) {
            return 0;
        }
        return articleChunkVectorMapper.deleteAll();
    }

    /**
     * 把 chunk 向量列对齐到目标维度。
     *
     * @param targetDimensions 目标维度
     */
    public void alignEmbeddingColumnDimensions(int targetDimensions) {
        if (articleChunkVectorMapper == null || targetDimensions <= 0) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        dropEmbeddingAnnIndexes();
        articleChunkVectorMapper.alignEmbeddingColumnDimensions(vectorTypeName, targetDimensions);
    }

    /**
     * 确保 chunk 向量表已具备可用的 ANN 索引。
     */
    public void ensureEmbeddingAnnIndex() {
        if (articleChunkVectorMapper == null) {
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
            articleChunkVectorMapper.createHnswIndex(ANN_INDEX_NAME_HNSW, opClass);
            return;
        }
        articleChunkVectorMapper.createIvfflatIndex(ANN_INDEX_NAME_IVFFLAT, opClass);
    }

    /**
     * 返回当前分块向量索引最近更新时间。
     *
     * @return 最近更新时间
     */
    public Optional<OffsetDateTime> findLatestUpdatedAt() {
        if (articleChunkVectorMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articleChunkVectorMapper.findLatestUpdatedAt());
    }

    /**
     * 返回 chunk 向量列的数据库类型描述。
     *
     * @return 向量列类型描述
     */
    public Optional<String> findEmbeddingColumnType() {
        if (articleChunkVectorMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articleChunkVectorMapper.findEmbeddingColumnType());
    }

    /**
     * 执行 chunk 级向量近邻检索。
     *
     * @param embedding 查询向量
     * @param limit 返回数量
     * @return chunk 命中
     */
    public List<ArticleChunkVectorHit> searchNearestNeighbors(float[] embedding, int limit) {
        if (articleChunkVectorMapper == null || embedding == null || embedding.length == 0) {
            return List.of();
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return List.of();
        }
        String distanceOperator = resolveDistanceOperator(vectorTypeName);

        String vectorLiteral = formatVector(embedding);
        return articleChunkVectorMapper.searchNearestNeighbors(
                vectorLiteral,
                vectorTypeName,
                distanceOperator,
                limit
        );
    }

    /**
     * 解析当前可用的 vector 类型名称。
     *
     * @return vector 类型名称
     */
    private String resolveVectorTypeName() {
        String vectorTypeName = articleChunkVectorMapper.resolveVectorTypeName();
        if (vectorTypeName == null) {
            return "";
        }
        return vectorTypeName;
    }

    /**
     * 解析当前优先使用的 ANN 索引实现。
     *
     * @return 索引实现名
     */
    private String resolvePreferredAnnIndexMethod() {
        String preferredMethod = articleChunkVectorMapper.resolvePreferredAnnIndexMethod();
        if (preferredMethod == null) {
            return "";
        }
        return preferredMethod;
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
        String opClass = articleChunkVectorMapper.resolveVectorOperatorClass();
        if (opClass == null) {
            return "";
        }
        return opClass;
    }

    /**
     * 删除历史 ANN 索引，便于在切换向量维度时重新创建。
     */
    private void dropEmbeddingAnnIndexes() {
        articleChunkVectorMapper.dropIndex(ANN_INDEX_NAME_HNSW);
        articleChunkVectorMapper.dropIndex(ANN_INDEX_NAME_IVFFLAT);
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

}
