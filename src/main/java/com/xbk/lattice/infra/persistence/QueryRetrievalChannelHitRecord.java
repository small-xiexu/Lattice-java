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

    public String getSourcePathsJson() {
        return sourcePathsJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
