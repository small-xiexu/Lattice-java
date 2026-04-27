package com.xbk.lattice.query.service;

/**
 * Query 检索配置状态
 *
 * 职责：承载并行召回与加权 RRF 运行时配置
 *
 * @author xiexu
 */
public class QueryRetrievalSettingsState {

    public static final boolean DEFAULT_REWRITE_ENABLED = true;

    public static final boolean DEFAULT_INTENT_AWARE_VECTOR_ENABLED = true;

    public static final double DEFAULT_FTS_WEIGHT = 1.0D;

    public static final double DEFAULT_REFKEY_WEIGHT = 1.45D;

    public static final double DEFAULT_ARTICLE_CHUNK_WEIGHT = 1.25D;

    public static final double DEFAULT_SOURCE_WEIGHT = 1.0D;

    public static final double DEFAULT_SOURCE_CHUNK_WEIGHT = 1.30D;

    public static final double DEFAULT_CONTRIBUTION_WEIGHT = 1.0D;

    public static final double DEFAULT_GRAPH_WEIGHT = 1.20D;

    public static final double DEFAULT_ARTICLE_VECTOR_WEIGHT = 1.0D;

    public static final double DEFAULT_CHUNK_VECTOR_WEIGHT = 1.35D;

    public static final int DEFAULT_RRF_K = 60;

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
        this(
                parallelEnabled,
                DEFAULT_REWRITE_ENABLED,
                DEFAULT_INTENT_AWARE_VECTOR_ENABLED,
                ftsWeight,
                DEFAULT_REFKEY_WEIGHT,
                DEFAULT_ARTICLE_CHUNK_WEIGHT,
                sourceWeight,
                DEFAULT_SOURCE_CHUNK_WEIGHT,
                contributionWeight,
                graphWeight,
                articleVectorWeight,
                chunkVectorWeight,
                rrfK
        );
    }

    /**
     * 创建 Query 检索配置状态。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param ftsWeight FTS 权重
     * @param refkeyWeight RefKey 权重
     * @param articleChunkWeight Article Chunk lexical 权重
     * @param sourceWeight Source 文件级权重
     * @param sourceChunkWeight Source Chunk lexical 权重
     * @param contributionWeight Contribution 权重
     * @param graphWeight Graph 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight Chunk 向量权重
     * @param rrfK RRF K 值
     */
    public QueryRetrievalSettingsState(
            boolean parallelEnabled,
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
        this(
                parallelEnabled,
                DEFAULT_REWRITE_ENABLED,
                DEFAULT_INTENT_AWARE_VECTOR_ENABLED,
                ftsWeight,
                refkeyWeight,
                articleChunkWeight,
                sourceWeight,
                sourceChunkWeight,
                contributionWeight,
                graphWeight,
                articleVectorWeight,
                chunkVectorWeight,
                rrfK
        );
    }

    /**
     * 创建 Query 检索配置状态。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param rewriteEnabled 是否启用查询改写
     * @param intentAwareVectorEnabled 是否启用意图感知向量通道
     * @param ftsWeight FTS 权重
     * @param refkeyWeight RefKey 权重
     * @param articleChunkWeight Article Chunk lexical 权重
     * @param sourceWeight Source 文件级权重
     * @param sourceChunkWeight Source Chunk lexical 权重
     * @param contributionWeight Contribution 权重
     * @param graphWeight Graph 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight Chunk 向量权重
     * @param rrfK RRF K 值
     */
    public QueryRetrievalSettingsState(
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

    public boolean isParallelEnabled() {
        return parallelEnabled;
    }

    public boolean isRewriteEnabled() {
        return rewriteEnabled;
    }

    public boolean isIntentAwareVectorEnabled() {
        return intentAwareVectorEnabled;
    }

    public double getFtsWeight() {
        return ftsWeight;
    }

    public double getRefkeyWeight() {
        return refkeyWeight;
    }

    public double getArticleChunkWeight() {
        return articleChunkWeight;
    }

    public double getSourceWeight() {
        return sourceWeight;
    }

    public double getSourceChunkWeight() {
        return sourceChunkWeight;
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
