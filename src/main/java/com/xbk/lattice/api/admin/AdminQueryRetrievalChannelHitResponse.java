package com.xbk.lattice.api.admin;

/**
 * 管理侧 Query 检索通道命中响应
 *
 * 职责：承载一次 run 的通道命中与漏召回明细
 *
 * @author xiexu
 */
public class AdminQueryRetrievalChannelHitResponse {

    private final Long hitId;

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

    private final String createdAt;

    /**
     * 创建管理侧 Query 检索通道命中响应。
     *
     * @param hitId 主键
     * @param runId run 主键
     * @param channelName 通道名
     * @param hitRank 通道内排序
     * @param fusedRank 融合排序
     * @param includedInFused 是否进入融合
     * @param channelWeight 通道权重
     * @param evidenceType 证据类型
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param score 分数
     * @param sourcePathsJson 来源路径 JSON
     * @param metadataJson 元数据 JSON
     * @param createdAt 创建时间
     */
    public AdminQueryRetrievalChannelHitResponse(
            Long hitId,
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
            String metadataJson,
            String createdAt
    ) {
        this.hitId = hitId;
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
        this.createdAt = createdAt;
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getHitId() {
        return hitId;
    }

    /**
     * 获取 run 主键。
     *
     * @return run 主键
     */
    public Long getRunId() {
        return runId;
    }

    /**
     * 获取通道名。
     *
     * @return 通道名
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * 获取通道内排序。
     *
     * @return 通道内排序
     */
    public int getHitRank() {
        return hitRank;
    }

    /**
     * 获取融合排序。
     *
     * @return 融合排序
     */
    public Integer getFusedRank() {
        return fusedRank;
    }

    /**
     * 获取是否进入融合。
     *
     * @return 是否进入融合
     */
    public boolean isIncludedInFused() {
        return includedInFused;
    }

    /**
     * 获取通道权重。
     *
     * @return 通道权重
     */
    public double getChannelWeight() {
        return channelWeight;
    }

    /**
     * 获取证据类型。
     *
     * @return 证据类型
     */
    public String getEvidenceType() {
        return evidenceType;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
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
     * 获取分数。
     *
     * @return 分数
     */
    public double getScore() {
        return score;
    }

    /**
     * 获取来源路径 JSON。
     *
     * @return 来源路径 JSON
     */
    public String getSourcePathsJson() {
        return sourcePathsJson;
    }

    /**
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public String getCreatedAt() {
        return createdAt;
    }
}
