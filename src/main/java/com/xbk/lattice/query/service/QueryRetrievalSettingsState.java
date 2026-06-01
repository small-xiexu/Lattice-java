package com.xbk.lattice.query.service;

import lombok.Getter;

/**
 * Query 检索配置状态。
 *
 * <p>承载并行召回与加权 RRF 的运行时配置快照，用于 query 检索链中的通道选择与排序。
 *
 * @author xiexu
 */
@Getter
public class QueryRetrievalSettingsState {

    public static final boolean DEFAULT_REWRITE_ENABLED = true;

    public static final boolean DEFAULT_INTENT_AWARE_VECTOR_ENABLED = true;

    public static final double DEFAULT_FTS_WEIGHT = 1.0D;

    public static final double DEFAULT_REFKEY_WEIGHT = 1.45D;

    public static final double DEFAULT_ARTICLE_CHUNK_WEIGHT = 1.25D;

    public static final double DEFAULT_SOURCE_WEIGHT = 1.0D;

    public static final double DEFAULT_SOURCE_CHUNK_WEIGHT = 1.30D;

    public static final double DEFAULT_FACT_CARD_WEIGHT = 1.40D;

    public static final double DEFAULT_CONTRIBUTION_WEIGHT = 1.0D;

    public static final double DEFAULT_GRAPH_WEIGHT = 1.20D;

    public static final double DEFAULT_ARTICLE_VECTOR_WEIGHT = 1.0D;

    public static final double DEFAULT_CHUNK_VECTOR_WEIGHT = 1.35D;

    public static final int DEFAULT_RRF_K = 60;

    /**
     * 并行召回开关。
     *
     * <p>{@code true} 时多通道并行执行，降低延迟但增加 DB 连接压力。
     * {@code false} 时串行执行，延迟叠加但资源消耗可控。</p>
     */
    private final boolean parallelEnabled;

    /**
     * 查询改写开关。
     *
     * <p>{@code true} 时对原始 query 做 LLM 改写/扩展后再检索，
     * 召回结果与原始 query 可能存在语义偏移。</p>
     */
    private final boolean rewriteEnabled;

    /**
     * 意图感知向量通道开关。
     *
     * <p>{@code true} 时根据 query 意图动态选择向量通道组合，策略准确性依赖意图识别模型。</p>
     */
    private final boolean intentAwareVectorEnabled;

    /** 全文检索通道 RRF 融合权重。0=关闭该通道。默认 1.0。 */
    private final double ftsWeight;

    /** RefKey 引用键通道 RRF 融合权重。0=关闭。默认 1.45。 */
    private final double refkeyWeight;

    /** 文章分块 lexical 通道 RRF 融合权重。0=关闭。默认 1.25。 */
    private final double articleChunkWeight;

    /** Source 文件级通道 RRF 融合权重。0=关闭。默认 1.0。 */
    private final double sourceWeight;

    /** Source Chunk lexical 通道 RRF 融合权重。0=关闭。默认 1.30。 */
    private final double sourceChunkWeight;

    /** Fact Card lexical 通道 RRF 融合权重。0=关闭。默认 1.40。 */
    private final double factCardWeight;

    /** Contribution 贡献度通道 RRF 融合权重。0=关闭。默认 1.0。 */
    private final double contributionWeight;

    /** Graph 知识图谱通道 RRF 融合权重。0=关闭。默认 1.20。 */
    private final double graphWeight;

    /** 文章向量通道 RRF 融合权重。0=关闭。默认 1.0。 */
    private final double articleVectorWeight;

    /** 分块向量通道 RRF 融合权重。0=关闭。默认 1.35。 */
    private final double chunkVectorWeight;

    /**
     * RRF K 参数。
     *
     * <p>控制排名平滑度：值越大排名越平滑但区分度越低。默认 60。
     * 过小（如 1）→排名断层；过大（如 120+）→排名趋同失去区分。</p>
     */
    private final int rrfK;

    /**
     * 创建 Query 检索配置状态（精简构造器——rewrite/vector 使用默认值）。
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
                DEFAULT_FACT_CARD_WEIGHT,
                contributionWeight,
                graphWeight,
                articleVectorWeight,
                chunkVectorWeight,
                rrfK
        );
    }

    /**
     * 创建 Query 检索配置状态（中等构造器——rewrite/vector 使用默认值）。
     */
    public QueryRetrievalSettingsState(
            boolean parallelEnabled,
            double ftsWeight,
            double refkeyWeight,
            double articleChunkWeight,
            double sourceWeight,
            double sourceChunkWeight,
            double factCardWeight,
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
                factCardWeight,
                contributionWeight,
                graphWeight,
                articleVectorWeight,
                chunkVectorWeight,
                rrfK
        );
    }

    /**
     * 创建 Query 检索配置状态（完整构造器——所有参数显式指定）。
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
            double factCardWeight,
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
        this.factCardWeight = factCardWeight;
        this.contributionWeight = contributionWeight;
        this.graphWeight = graphWeight;
        this.articleVectorWeight = articleVectorWeight;
        this.chunkVectorWeight = chunkVectorWeight;
        this.rrfK = rrfK;
    }
}
