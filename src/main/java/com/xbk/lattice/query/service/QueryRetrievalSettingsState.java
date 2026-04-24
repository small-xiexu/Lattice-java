package com.xbk.lattice.query.service;

/**
 * Query 检索配置状态
 *
 * 职责：承载并行召回与加权 RRF 运行时配置
 *
 * @author xiexu
 */
public class QueryRetrievalSettingsState {

    private final boolean parallelEnabled;

    private final double ftsWeight;

    private final double sourceWeight;

    private final double contributionWeight;

    private final double graphWeight;

    private final double articleVectorWeight;

    private final double chunkVectorWeight;

    private final int rrfK;

    /**
     * 创建 Query 检索配置状态。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param ftsWeight FTS 权重
     * @param sourceWeight Source 权重
     * @param contributionWeight Contribution 权重
     * @param graphWeight Graph 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight Chunk 向量权重
     * @param rrfK RRF K 值
     */
    public QueryRetrievalSettingsState(
            boolean parallelEnabled,
            double ftsWeight,
            double sourceWeight,
            double contributionWeight,
            double graphWeight,
            double articleVectorWeight,
            double chunkVectorWeight,
            int rrfK
    ) {
        this.parallelEnabled = parallelEnabled;
        this.ftsWeight = ftsWeight;
        this.sourceWeight = sourceWeight;
        this.contributionWeight = contributionWeight;
        this.graphWeight = graphWeight;
        this.articleVectorWeight = articleVectorWeight;
        this.chunkVectorWeight = chunkVectorWeight;
        this.rrfK = rrfK;
    }

    public boolean isParallelEnabled() {
        return parallelEnabled;
    }

    public double getFtsWeight() {
        return ftsWeight;
    }

    public double getSourceWeight() {
        return sourceWeight;
    }

    public double getContributionWeight() {
        return contributionWeight;
    }

    public double getGraphWeight() {
        return graphWeight;
    }

    public double getArticleVectorWeight() {
        return articleVectorWeight;
    }

    public double getChunkVectorWeight() {
        return chunkVectorWeight;
    }

    public int getRrfK() {
        return rrfK;
    }
}
