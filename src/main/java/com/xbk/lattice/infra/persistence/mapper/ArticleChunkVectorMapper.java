package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ArticleChunkVectorRecord;
import com.xbk.lattice.query.service.ArticleChunkVectorHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章分块向量索引 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 article_chunk_vector_index 表和 pgvector 元数据
 *
 * @author xiexu
 */
@Mapper
public interface ArticleChunkVectorMapper {

    /**
     * 保存或更新 chunk 向量索引。
     *
     * @param record 向量索引记录
     * @param vectorLiteral 向量字面量
     * @param vectorTypeName vector 类型名称
     * @return 影响行数
     */
    int upsert(
            @Param("record") ArticleChunkVectorRecord record,
            @Param("vectorLiteral") String vectorLiteral,
            @Param("vectorTypeName") String vectorTypeName
    );

    /**
     * 按分块主键查询向量索引。
     *
     * @param articleChunkId 分块主键
     * @return 向量索引
     */
    ArticleChunkVectorRecord findByArticleChunkId(@Param("articleChunkId") Long articleChunkId);

    /**
     * 统计全部记录。
     *
     * @return 记录数
     */
    int countAll();

    /**
     * 删除全部记录。
     *
     * @return 影响行数
     */
    int deleteAll();

    /**
     * 调整 embedding 列维度。
     *
     * @param vectorTypeName vector 类型名称
     * @param targetDimensions 目标维度
     */
    void alignEmbeddingColumnDimensions(
            @Param("vectorTypeName") String vectorTypeName,
            @Param("targetDimensions") int targetDimensions
    );

    /**
     * 创建 HNSW ANN 索引。
     *
     * @param indexName 索引名
     * @param opClass operator class
     */
    void createHnswIndex(@Param("indexName") String indexName, @Param("opClass") String opClass);

    /**
     * 创建 IVFFLAT ANN 索引。
     *
     * @param indexName 索引名
     * @param opClass operator class
     */
    void createIvfflatIndex(@Param("indexName") String indexName, @Param("opClass") String opClass);

    /**
     * 查询最近更新时间。
     *
     * @return 最近更新时间
     */
    OffsetDateTime findLatestUpdatedAt();

    /**
     * 查询 embedding 列类型。
     *
     * @return 列类型
     */
    String findEmbeddingColumnType();

    /**
     * 执行 chunk 级向量近邻检索。
     *
     * @param vectorLiteral 向量字面量
     * @param vectorTypeName vector 类型名称
     * @param distanceOperator 距离操作符
     * @param limit 返回上限
     * @return chunk 命中列表
     */
    List<ArticleChunkVectorHit> searchNearestNeighbors(
            @Param("vectorLiteral") String vectorLiteral,
            @Param("vectorTypeName") String vectorTypeName,
            @Param("distanceOperator") String distanceOperator,
            @Param("limit") int limit
    );

    /**
     * 查询当前可用 vector 类型名称。
     *
     * @return vector 类型名称
     */
    String resolveVectorTypeName();

    /**
     * 查询优先 ANN 索引实现。
     *
     * @return ANN 索引实现
     */
    String resolvePreferredAnnIndexMethod();

    /**
     * 查询 vector cosine operator class。
     *
     * @return operator class
     */
    String resolveVectorOperatorClass();

    /**
     * 删除指定索引。
     *
     * @param indexName 索引名
     */
    void dropIndex(@Param("indexName") String indexName);
}
