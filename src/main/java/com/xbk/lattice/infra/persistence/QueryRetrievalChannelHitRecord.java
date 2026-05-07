package com.xbk.lattice.infra.persistence;

/**
 * Query 检索通道命中记录
 *
 * 职责：承载 query_retrieval_channel_hits 表的最小写入字段
 *
 * @author xiexu
 */
public class QueryRetrievalChannelHitRecord {

    private final Long runId;

    private final String channelName;

    private final int hitRank;

    private final Integer fusedRank;

    private final boolean includedInFused;

    private final double channelWeight;

    private final String evidenceType;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final double score;

    private final Long factCardId;

    private final String cardType;

    private final String reviewStatus;

    private final Double confidence;

    private final String sourceChunkIdsJson;

    private final String sourcePathsJson;

    private final String metadataJson;

    /**
     * 创建 Query 检索通道命中记录。
     *
     * @param runId 审计主键
     * @param channelName 通道名
     * @param hitRank 通道内排序
     * @param fusedRank 最终融合排序
     * @param includedInFused 是否进入最终融合
     * @param channelWeight 通道权重
     * @param evidenceType 证据类型
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param score 原始得分
     * @param factCardId Fact Card 数据库主键
     * @param cardType Fact Card 类型
     * @param reviewStatus 审查状态
     * @param confidence 置信度
     * @param sourceChunkIdsJson Source Chunk ID JSON
     * @param sourcePathsJson 来源路径 JSON
     * @param metadataJson 元数据 JSON
     */
    public QueryRetrievalChannelHitRecord(
            Long runId,
            String channelName,
            int hitRank,
            Integer fusedRank,
            boolean includedInFused,
            double channelWeight,
            String evidenceType,
            String articleKey,
            String conceptId,
            String title,
            double score,
            Long factCardId,
            String cardType,
            String reviewStatus,
            Double confidence,
            String sourceChunkIdsJson,
            String sourcePathsJson,
            String metadataJson
    ) {
        this.runId = runId;
        this.channelName = channelName;
        this.hitRank = hitRank;
        this.fusedRank = fusedRank;
        this.includedInFused = includedInFused;
        this.channelWeight = channelWeight;
        this.evidenceType = evidenceType;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.score = score;
        this.factCardId = factCardId;
        this.cardType = cardType;
        this.reviewStatus = reviewStatus;
        this.confidence = confidence;
        this.sourceChunkIdsJson = sourceChunkIdsJson;
        this.sourcePathsJson = sourcePathsJson;
        this.metadataJson = metadataJson;
    }

    public Long getRunId() {
        return runId;
    }

    public String getChannelName() {
        return channelName;
    }

    public int getHitRank() {
        return hitRank;
    }

    public Integer getFusedRank() {
        return fusedRank;
    }

    public boolean isIncludedInFused() {
        return includedInFused;
    }

    public double getChannelWeight() {
        return channelWeight;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public String getArticleKey() {
        return articleKey;
    }

    public String getConceptId() {
        return conceptId;
    }

    public String getTitle() {
        return title;
    }

    public double getScore() {
        return score;
    }

    public Long getFactCardId() {
        return factCardId;
    }

    public String getCardType() {
        return cardType;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getSourceChunkIdsJson() {
        return sourceChunkIdsJson;
    }

    public String getSourcePathsJson() {
        return sourcePathsJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
