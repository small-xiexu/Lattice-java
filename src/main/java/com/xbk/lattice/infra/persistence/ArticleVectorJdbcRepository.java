package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.ArticleVectorMapper;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
public class ArticleVectorJdbcRepository {

    private static final int HNSW_MAX_DIMENSIONS = 2000;

    private static final String ANN_INDEX_NAME_HNSW = "idx_article_vector_index_embedding_hnsw";

    private static final String ANN_INDEX_NAME_IVFFLAT = "idx_article_vector_index_embedding_ivfflat";

    private final ArticleVectorMapper articleVectorMapper;

    /**
     * 创建文章向量索引仓储。
     *
     * @param articleVectorMapper 文章向量 Mapper
     */
    @Autowired
    public ArticleVectorJdbcRepository(ArticleVectorMapper articleVectorMapper) {
        this.articleVectorMapper = articleVectorMapper;
    }

    /**
     * 保存或更新文章向量索引。
     *
     * @param articleVectorRecord 向量索引记录
     */
    public void upsert(ArticleVectorRecord articleVectorRecord) {
        if (articleVectorMapper == null) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        articleVectorMapper.upsert(articleVectorRecord, formatVector(articleVectorRecord.getEmbedding()), vectorTypeName);
    }

    /**
     * 按文章唯一键查询向量索引。
     *
     * @param articleKey 文章唯一键
     * @return 向量索引
     */
    public Optional<ArticleVectorRecord> findByArticleKey(String articleKey) {
        if (articleVectorMapper == null || articleKey == null || articleKey.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(articleVectorMapper.findByArticleKey(articleKey));
    }

    /**
     * 返回当前向量索引记录总数。
     *
     * @return 向量索引总数
     */
    public int countAll() {
        if (articleVectorMapper == null) {
            return 0;
        }
        return articleVectorMapper.countAll();
    }

    /**
     * 清空全部向量索引记录。
     *
     * @return 删除记录数
     */
    public int deleteAll() {
        if (articleVectorMapper == null) {
            return 0;
        }
        return articleVectorMapper.deleteAll();
    }

    /**
     * 把 embedding 列对齐到目标维度。
     *
     * <p>当切换到不同维度的 embedding profile 且历史索引已清空后，
     * 需要先调整 pgvector 列维度，后续重建才能成功写入。</p>
     *
     * @param targetDimensions 目标维度
     */
    public void alignEmbeddingColumnDimensions(int targetDimensions) {
        if (articleVectorMapper == null || targetDimensions <= 0) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        dropEmbeddingAnnIndexes();
        articleVectorMapper.alignEmbeddingColumnDimensions(vectorTypeName, targetDimensions);
    }

    /**
     * 确保文章向量表已具备可用的 ANN 索引。
     */
    public void ensureEmbeddingAnnIndex() {
        if (articleVectorMapper == null) {
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
            articleVectorMapper.createHnswIndex(ANN_INDEX_NAME_HNSW, opClass);
            return;
        }
        articleVectorMapper.createIvfflatIndex(ANN_INDEX_NAME_IVFFLAT, opClass);
    }

    /**
     * 返回当前向量索引最近更新时间。
     *
     * @return 最近更新时间
     */
    public Optional<OffsetDateTime> findLatestUpdatedAt() {
        if (articleVectorMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articleVectorMapper.findLatestUpdatedAt());
    }

    /**
     * 返回当前向量索引中出现过的模型名称。
     *
     * @return 模型名称列表
     */
    public List<String> findDistinctModelNames() {
        if (articleVectorMapper == null) {
            return List.of();
        }
        return articleVectorMapper.findDistinctModelNames();
    }

    /**
     * 返回向量列的数据库类型描述。
     *
     * @return 向量列类型描述
     */
    public Optional<String> findEmbeddingColumnType() {
        if (articleVectorMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articleVectorMapper.findEmbeddingColumnType());
    }

    /**
     * 返回 embedding 列使用的 ANN 索引类型。
     *
     * @return ANN 索引类型
     */
    public Optional<String> findEmbeddingAnnIndexType() {
        if (articleVectorMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articleVectorMapper.findEmbeddingAnnIndexType());
    }

    /**
     * 执行向量近邻检索。
     *
     * @param embedding 查询向量
     * @param limit 返回数量
     * @return 文章命中
     */
    public List<QueryArticleHit> searchNearestNeighbors(float[] embedding, int limit) {
        if (articleVectorMapper == null || embedding == null || embedding.length == 0) {
            return List.of();
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return List.of();
        }
        String distanceOperator = resolveDistanceOperator(vectorTypeName);

        String vectorLiteral = formatVector(embedding);
        return articleVectorMapper.searchNearestNeighbors(vectorLiteral, vectorTypeName, distanceOperator, limit);
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
     * 解析当前可用的 vector 类型名称。
     *
     * @return vector 类型名称
     */
    private String resolveVectorTypeName() {
        String vectorTypeName = articleVectorMapper.resolveVectorTypeName();
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
        String preferredMethod = articleVectorMapper.resolvePreferredAnnIndexMethod();
        if (preferredMethod == null) {
            return "";
        }
        return preferredMethod;
    }

    /**
     * 解析当前维度下可兼容的 ANN 索引实现。
     *
     * <p>pgvector 当前 ANN 索引在高维场景下存在维度上限，
     * 当 embedding 列超过上限时直接跳过 ANN 索引创建，避免重建阶段失败。</p>
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
        String opClass = articleVectorMapper.resolveVectorOperatorClass();
        if (opClass == null) {
            return "";
        }
        return opClass;
    }

    /**
     * 删除历史 ANN 索引，便于在切换向量维度时重新创建。
     */
    private void dropEmbeddingAnnIndexes() {
        articleVectorMapper.dropIndex(ANN_INDEX_NAME_HNSW);
        articleVectorMapper.dropIndex(ANN_INDEX_NAME_IVFFLAT);
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
