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

    private final boolean rewriteEnabled;

    private final boolean intentAwareVectorEnabled;

    private final double ftsWeight;

    private final double refkeyWeight;

    private final double articleChunkWeight;

    private final double sourceWeight;

    private final double sourceChunkWeight;

    private final double contributionWeight;

    private final double graphWeight;

    private final double articleVectorWeight;

    private final double chunkVectorWeight;

    private final int rrfK;

    /**
     * 创建管理侧 Query 检索配置响应。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param rewriteEnabled 是否启用查询改写
     * @param intentAwareVectorEnabled 是否启用意图感知向量通道
     * @param ftsWeight FTS 权重
     * @param refkeyWeight RefKey 权重
     * @param articleChunkWeight Article Chunk lexical 权重
     * @param sourceWeight Source 权重
     * @param sourceChunkWeight Source Chunk lexical 权重
     * @param contributionWeight Contribution 权重
     * @param graphWeight Graph 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight Chunk 向量权重
     * @param rrfK RRF K 值
     */
    public AdminQueryRetrievalConfigResponse(
            boolean parallelEnabled,
            boolean rewriteEnabled,
            boolean intentAwareVectorEnabled,
            double ftsWeight,
            double refkeyWeight,
            double articleChunkWeight,
            double sourceWeight,
            double sourceChunkWeight,
            double contributionWeight,
            double graphWeight,
            double articleVectorWeight,
            double chunkVectorWeight,
            int rrfK
    ) {
        this.parallelEnabled = parallelEnabled;
        this.rewriteEnabled = rewriteEnabled;
        this.intentAwareVectorEnabled = intentAwareVectorEnabled;
        this.ftsWeight = ftsWeight;
        this.refkeyWeight = refkeyWeight;
        this.articleChunkWeight = articleChunkWeight;
        this.sourceWeight = sourceWeight;
        this.sourceChunkWeight = sourceChunkWeight;
        this.contributionWeight = contributionWeight;
        this.graphWeight = graphWeight;
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
     * 返回是否启用查询改写。
     *
     * @return 是否启用查询改写
     */
    public boolean isRewriteEnabled() {
        return rewriteEnabled;
    }

    /**
     * 返回是否启用意图感知向量通道。
     *
     * @return 是否启用意图感知向量通道
     */
    public boolean isIntentAwareVectorEnabled() {
        return intentAwareVectorEnabled;
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
     * 返回 RefKey 权重。
     *
     * @return RefKey 权重
     */
    public double getRefkeyWeight() {
        return refkeyWeight;
    }

    /**
     * 返回 Article Chunk lexical 权重。
     *
     * @return Article Chunk lexical 权重
     */
    public double getArticleChunkWeight() {
        return articleChunkWeight;
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
     * 返回 Source Chunk lexical 权重。
     *
     * @return Source Chunk lexical 权重
     */
    public double getSourceChunkWeight() {
        return sourceChunkWeight;
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
     * 返回 Graph 权重。
     *
     * @return Graph 权重
     */
    public double getGraphWeight() {
        return graphWeight;
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
