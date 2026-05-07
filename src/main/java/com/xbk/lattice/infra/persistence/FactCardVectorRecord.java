package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardType;

import java.time.OffsetDateTime;

/**
 * 事实证据卡向量索引记录
 *
 * 职责：承载 fact_card_vector_index 的持久化对象
 *
 * @author xiexu
 */
public class FactCardVectorRecord {

    private final Long factCardId;

    private final String cardId;

    private final FactCardType cardType;

    private final AnswerShape answerShape;

    private final Long modelProfileId;

    private final int embeddingDimensions;

    private final String indexVersion;

    private final String contentHash;

    private final float[] embedding;

    private final OffsetDateTime updatedAt;

    /**
     * 创建事实证据卡向量索引记录。
     *
     * @param factCardId 事实证据卡主键
     * @param cardId 事实证据卡稳定业务标识
     * @param cardType 证据卡类型
     * @param answerShape 答案形态
     * @param modelProfileId 模型配置主键
     * @param embeddingDimensions 向量维度
     * @param indexVersion 索引版本
     * @param contentHash 内容哈希
     * @param embedding 向量
     * @param updatedAt 更新时间
     */
    public FactCardVectorRecord(
            Long factCardId,
            String cardId,
            FactCardType cardType,
            AnswerShape answerShape,
            Long modelProfileId,
            int embeddingDimensions,
            String indexVersion,
            String contentHash,
            float[] embedding,
            OffsetDateTime updatedAt
    ) {
        this.factCardId = factCardId;
        this.cardId = cardId;
        this.cardType = cardType;
        this.answerShape = answerShape;
        this.modelProfileId = modelProfileId;
        this.embeddingDimensions = embeddingDimensions;
        this.indexVersion = indexVersion;
        this.contentHash = contentHash;
        this.embedding = embedding;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取事实证据卡主键。
     *
     * @return 事实证据卡主键
     */
    public Long getFactCardId() {
        return factCardId;
    }

    /**
     * 获取事实证据卡稳定业务标识。
     *
     * @return 事实证据卡稳定业务标识
     */
    public String getCardId() {
        return cardId;
    }

    /**
     * 获取证据卡类型。
     *
     * @return 证据卡类型
     */
    public FactCardType getCardType() {
        return cardType;
    }

    /**
     * 获取答案形态。
     *
     * @return 答案形态
     */
    public AnswerShape getAnswerShape() {
        return answerShape;
    }

    /**
     * 获取模型配置主键。
     *
     * @return 模型配置主键
     */
    public Long getModelProfileId() {
        return modelProfileId;
    }

    /**
     * 获取向量维度。
     *
     * @return 向量维度
     */
    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    /**
     * 获取索引版本。
     *
     * @return 索引版本
     */
    public String getIndexVersion() {
        return indexVersion;
    }

    /**
     * 获取内容哈希。
     *
     * @return 内容哈希
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * 获取向量。
     *
     * @return 向量
     */
    public float[] getEmbedding() {
        return embedding;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
