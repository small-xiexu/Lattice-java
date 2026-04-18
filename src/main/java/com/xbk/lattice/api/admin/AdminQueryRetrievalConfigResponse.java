package com.xbk.lattice.api.admin;

/**
 * 管理侧 Query 检索配置响应
 *
 * 职责：返回当前并行召回与加权 RRF 的有效配置
 *
 * @author xiexu
 */
public class AdminQueryRetrievalConfigResponse {

    private final boolean parallelEnabled;

    private final double ftsWeight;

    private final double sourceWeight;

    private final double contributionWeight;

    private final double articleVectorWeight;

    private final double chunkVectorWeight;

    private final int rrfK;

    /**
     * 创建管理侧 Query 检索配置响应。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param ftsWeight FTS 权重
     * @param sourceWeight Source 权重
     * @param contributionWeight Contribution 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight Chunk 向量权重
     * @param rrfK RRF K 值
     */
    public AdminQueryRetrievalConfigResponse(
            boolean parallelEnabled,
            double ftsWeight,
            double sourceWeight,
            double contributionWeight,
            double articleVectorWeight,
            double chunkVectorWeight,
            int rrfK
    ) {
        this.parallelEnabled = parallelEnabled;
        this.ftsWeight = ftsWeight;
        this.sourceWeight = sourceWeight;
        this.contributionWeight = contributionWeight;
        this.articleVectorWeight = articleVectorWeight;
        this.chunkVectorWeight = chunkVectorWeight;
        this.rrfK = rrfK;
    }

    /**
     * 返回是否启用并行召回。
     *
     * @return 是否启用并行召回
     */
    public boolean isParallelEnabled() {
        return parallelEnabled;
    }

    /**
     * 返回 FTS 权重。
     *
     * @return FTS 权重
     */
    public double getFtsWeight() {
        return ftsWeight;
    }

    /**
     * 返回 Source 权重。
     *
     * @return Source 权重
     */
    public double getSourceWeight() {
        return sourceWeight;
    }

    /**
     * 返回 Contribution 权重。
     *
     * @return Contribution 权重
     */
    public double getContributionWeight() {
        return contributionWeight;
    }

    /**
     * 返回文章向量权重。
     *
     * @return 文章向量权重
     */
    public double getArticleVectorWeight() {
        return articleVectorWeight;
    }

    /**
     * 返回 Chunk 向量权重。
     *
     * @return Chunk 向量权重
     */
    public double getChunkVectorWeight() {
        return chunkVectorWeight;
    }

    /**
     * 返回 RRF K 值。
     *
     * @return RRF K 值
     */
    public int getRrfK() {
        return rrfK;
    }
}
