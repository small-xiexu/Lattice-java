package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 事实证据卡记录
 *
 * 职责：承载 fact_cards 表的结构化证据卡持久化数据
 *
 * @author xiexu
 */
public class FactCardRecord {

    private final Long id;

    private final String cardId;

    private final Long sourceId;

    private final Long sourceFileId;

    private final FactCardType cardType;

    private final AnswerShape answerShape;

    private final String title;

    private final String claim;

    private final String itemsJson;

    private final String evidenceText;

    private final List<Long> sourceChunkIds;

    private final List<Long> articleIds;

    private final double confidence;

    private final FactCardReviewStatus reviewStatus;

    private final String contentHash;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建事实证据卡记录。
     *
     * @param cardId 证据卡稳定业务标识
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param cardType 证据卡类型
     * @param answerShape 答案形态
     * @param title 标题
     * @param claim 结论
     * @param itemsJson 结构化条目 JSON
     * @param evidenceText 原文证据文本
     * @param sourceChunkIds 源文件分块主键
     * @param articleIds 背景文章主键
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @param contentHash 内容哈希
     */
    public FactCardRecord(
            String cardId,
            Long sourceId,
            Long sourceFileId,
            FactCardType cardType,
            AnswerShape answerShape,
            String title,
            String claim,
            String itemsJson,
            String evidenceText,
            List<Long> sourceChunkIds,
            List<Long> articleIds,
            double confidence,
            FactCardReviewStatus reviewStatus,
            String contentHash
    ) {
        this(
                null,
                cardId,
                sourceId,
                sourceFileId,
                cardType,
                answerShape,
                title,
                claim,
                itemsJson,
                evidenceText,
                sourceChunkIds,
                articleIds,
                confidence,
                reviewStatus,
                contentHash,
                null,
                null
        );
    }

    /**
     * 创建事实证据卡记录。
     *
     * @param id 主键
     * @param cardId 证据卡稳定业务标识
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param cardType 证据卡类型
     * @param answerShape 答案形态
     * @param title 标题
     * @param claim 结论
     * @param itemsJson 结构化条目 JSON
     * @param evidenceText 原文证据文本
     * @param sourceChunkIds 源文件分块主键
     * @param articleIds 背景文章主键
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @param contentHash 内容哈希
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public FactCardRecord(
            Long id,
            String cardId,
            Long sourceId,
            Long sourceFileId,
            FactCardType cardType,
            AnswerShape answerShape,
            String title,
            String claim,
            String itemsJson,
            String evidenceText,
            List<Long> sourceChunkIds,
            List<Long> articleIds,
            double confidence,
            FactCardReviewStatus reviewStatus,
            String contentHash,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.cardId = cardId;
        this.sourceId = sourceId;
        this.sourceFileId = sourceFileId;
        this.cardType = cardType;
        this.answerShape = answerShape;
        this.title = title;
        this.claim = claim;
        this.itemsJson = itemsJson;
        this.evidenceText = evidenceText;
        this.sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
        this.articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
        this.confidence = confidence;
        this.reviewStatus = reviewStatus;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取证据卡稳定业务标识。
     *
     * @return 证据卡稳定业务标识
     */
    public String getCardId() {
        return cardId;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取源文件主键。
     *
     * @return 源文件主键
     */
    public Long getSourceFileId() {
        return sourceFileId;
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
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取结论。
     *
     * @return 结论
     */
    public String getClaim() {
        return claim;
    }

    /**
     * 获取结构化条目 JSON。
     *
     * @return 结构化条目 JSON
     */
    public String getItemsJson() {
        return itemsJson;
    }

    /**
     * 获取原文证据文本。
     *
     * @return 原文证据文本
     */
    public String getEvidenceText() {
        return evidenceText;
    }

    /**
     * 获取源文件分块主键。
     *
     * @return 源文件分块主键
     */
    public List<Long> getSourceChunkIds() {
        return sourceChunkIds;
    }

    /**
     * 获取背景文章主键。
     *
     * @return 背景文章主键
     */
    public List<Long> getArticleIds() {
        return articleIds;
    }

    /**
     * 获取置信度。
     *
     * @return 置信度
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public FactCardReviewStatus getReviewStatus() {
        return reviewStatus;
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
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
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
