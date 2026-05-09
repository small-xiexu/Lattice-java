package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.FactCardVectorMapper;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

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

    private final FactCardVectorMapper factCardVectorMapper;

    /**
     * 创建事实证据卡向量索引仓储。
     *
     * @param factCardVectorMapper 事实卡向量 Mapper
     */
    @Autowired
    public FactCardVectorJdbcRepository(FactCardVectorMapper factCardVectorMapper) {
        this.factCardVectorMapper = factCardVectorMapper;
    }

    /**
     * 保存或更新事实证据卡向量索引。
     *
     * @param factCardVectorRecord 事实证据卡向量索引记录
     */
    public void upsert(FactCardVectorRecord factCardVectorRecord) {
        if (factCardVectorMapper == null || factCardVectorRecord == null || !tableExists()) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        factCardVectorMapper.upsert(
                factCardVectorRecord,
                formatVector(factCardVectorRecord.getEmbedding()),
                vectorTypeName
        );
    }

    /**
     * 按事实证据卡主键查询向量索引。
     *
     * @param factCardId 事实证据卡主键
     * @return 向量索引记录
     */
    public Optional<FactCardVectorRecord> findByFactCardId(Long factCardId) {
        if (factCardVectorMapper == null || factCardId == null || !tableExists()) {
            return Optional.empty();
        }
        return Optional.ofNullable(factCardVectorMapper.findByFactCardId(factCardId));
    }

    /**
     * 统计全部事实证据卡向量索引数量。
     *
     * @return 向量索引数量
     */
    public int countAll() {
        if (factCardVectorMapper == null || !tableExists()) {
            return 0;
        }
        return factCardVectorMapper.countAll();
    }

    /**
     * 清空全部事实证据卡向量索引。
     *
     * @return 删除数量
     */
    public int deleteAll() {
        if (factCardVectorMapper == null || !tableExists()) {
            return 0;
        }
        return factCardVectorMapper.deleteAll();
    }

    /**
     * 把 fact card 向量列对齐到目标维度。
     *
     * @param targetDimensions 目标维度
     */
    public void alignEmbeddingColumnDimensions(int targetDimensions) {
        if (factCardVectorMapper == null || targetDimensions <= 0 || !tableExists()) {
            return;
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return;
        }

        dropEmbeddingAnnIndexes();
        factCardVectorMapper.alignEmbeddingColumnDimensions(vectorTypeName, targetDimensions);
    }

    /**
     * 确保 fact card 向量表具备可用 ANN 索引。
     */
    public void ensureEmbeddingAnnIndex() {
        if (factCardVectorMapper == null || !tableExists()) {
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
            factCardVectorMapper.createHnswIndex(ANN_INDEX_NAME_HNSW, opClass);
            return;
        }
        factCardVectorMapper.createIvfflatIndex(ANN_INDEX_NAME_IVFFLAT, opClass);
    }

    /**
     * 查询向量列数据库类型。
     *
     * @return 向量列类型
     */
    public Optional<String> findEmbeddingColumnType() {
        if (factCardVectorMapper == null || !tableExists()) {
            return Optional.empty();
        }
        return Optional.ofNullable(factCardVectorMapper.findEmbeddingColumnType());
    }

    /**
     * 查询向量索引最近更新时间。
     *
     * @return 最近更新时间
     */
    public Optional<OffsetDateTime> findLatestUpdatedAt() {
        if (factCardVectorMapper == null || !tableExists()) {
            return Optional.empty();
        }
        return Optional.ofNullable(factCardVectorMapper.findLatestUpdatedAt());
    }

    /**
     * 执行 fact card 向量近邻检索。
     *
     * @param embedding 查询向量
     * @param limit 返回数量
     * @return fact card 命中
     */
    public List<QueryArticleHit> searchNearestNeighbors(float[] embedding, int limit) {
        if (factCardVectorMapper == null || embedding == null || embedding.length == 0 || !tableExists()) {
            return List.of();
        }

        String vectorTypeName = resolveVectorTypeName();
        if (vectorTypeName.isBlank()) {
            return List.of();
        }
        String distanceOperator = resolveDistanceOperator(vectorTypeName);
        int safeLimit = limit <= 0 ? 5 : limit;
        String vectorLiteral = formatVector(embedding);
        return factCardVectorMapper.searchNearestNeighbors(
                vectorLiteral,
                vectorTypeName,
                distanceOperator,
                safeLimit
        );
    }

    /**
     * 判断向量索引表是否存在。
     *
     * @return 表是否存在
     */
    private boolean tableExists() {
        return factCardVectorMapper.tableExists();
    }

    /**
     * 解析当前可用的 vector 类型名称。
     *
     * @return vector 类型名称
     */
    private String resolveVectorTypeName() {
        String vectorTypeName = factCardVectorMapper.resolveVectorTypeName();
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
        String preferredMethod = factCardVectorMapper.resolvePreferredAnnIndexMethod();
        if (preferredMethod == null) {
            return "";
        }
        return preferredMethod;
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
        String opClass = factCardVectorMapper.resolveVectorOperatorClass();
        if (opClass == null) {
            return "";
        }
        return opClass;
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
        factCardVectorMapper.dropIndex(ANN_INDEX_NAME_HNSW);
        factCardVectorMapper.dropIndex(ANN_INDEX_NAME_IVFFLAT);
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

}
