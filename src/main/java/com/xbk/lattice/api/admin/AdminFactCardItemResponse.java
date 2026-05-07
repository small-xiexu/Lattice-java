package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 Fact Card 条目响应
 *
 * 职责：承载结构化证据卡列表与详情展示字段
 *
 * @author xiexu
 */
public class AdminFactCardItemResponse {

    private final Long id;

    private final String cardId;

    private final Long sourceId;

    private final Long sourceFileId;

    private final String sourceFilePath;

    private final String cardType;

    private final String answerShape;

    private final String title;

    private final String claim;

    private final String itemsJson;

    private final String evidenceText;

    private final List<Long> sourceChunkIds;

    private final List<Long> articleIds;

    private final double confidence;

    private final String reviewStatus;

    private final String contentHash;

    private final String createdAt;

    private final String updatedAt;

    /**
     * 创建管理侧 Fact Card 条目响应。
     *
     * @param id 主键
     * @param cardId 稳定标识
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param sourceFilePath 源文件路径
     * @param cardType 卡类型
     * @param answerShape 答案形态
     * @param title 标题
     * @param claim 结论
     * @param itemsJson 结构化条目 JSON
     * @param evidenceText 证据文本
     * @param sourceChunkIds source chunk 主键
     * @param articleIds article 主键
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @param contentHash 内容哈希
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AdminFactCardItemResponse(
            Long id,
            String cardId,
            Long sourceId,
            Long sourceFileId,
            String sourceFilePath,
            String cardType,
            String answerShape,
            String title,
            String claim,
            String itemsJson,
            String evidenceText,
            List<Long> sourceChunkIds,
            List<Long> articleIds,
            double confidence,
            String reviewStatus,
            String contentHash,
            String createdAt,
            String updatedAt
    ) {
        this.id = id;
        this.cardId = cardId;
        this.sourceId = sourceId;
        this.sourceFileId = sourceFileId;
        this.sourceFilePath = sourceFilePath;
        this.cardType = cardType;
        this.answerShape = answerShape;
        this.title = title;
        this.claim = claim;
        this.itemsJson = itemsJson;
        this.evidenceText = evidenceText;
        this.sourceChunkIds = sourceChunkIds;
        this.articleIds = articleIds;
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
     * 获取稳定标识。
     *
     * @return 稳定标识
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
     * 获取源文件路径。
     *
     * @return 源文件路径
     */
    public String getSourceFilePath() {
        return sourceFilePath;
    }

    /**
     * 获取卡类型。
     *
     * @return 卡类型
     */
    public String getCardType() {
        return cardType;
    }

    /**
     * 获取答案形态。
     *
     * @return 答案形态
     */
    public String getAnswerShape() {
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
     * 获取证据文本。
     *
     * @return 证据文本
     */
    public String getEvidenceText() {
        return evidenceText;
    }

    /**
     * 获取 source chunk 主键。
     *
     * @return source chunk 主键
     */
    public List<Long> getSourceChunkIds() {
        return sourceChunkIds;
    }

    /**
     * 获取 article 主键。
     *
     * @return article 主键
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
    public String getReviewStatus() {
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
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public String getUpdatedAt() {
        return updatedAt;
    }
}
