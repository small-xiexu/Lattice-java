package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧 Query 检索配置响应。
 *
 * <p>返回当前生效的并行召回开关与 RRF 权重配置，
 * 由 {@code AdminQueryRetrievalConfigController} 从持久化配置组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryRetrievalConfigResponse {

    /**
     * 当前是否启用并行召回。
     *
     * <p>{@code true} 时多检索通道并行执行。</p>
     */
    private final boolean parallelEnabled;

    /**
     * 当前是否启用查询改写。
     *
     * <p>{@code true} 时用户 query 会被 LLM 改写/扩展后再检索。</p>
     */
    private final boolean rewriteEnabled;

    /**
     * 当前是否启用意图感知向量通道。
     *
     * <p>{@code true} 时根据 query 意图动态选择向量通道组合。</p>
     */
    private final boolean intentAwareVectorEnabled;

    /**
     * 当前全文检索（FTS）通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double ftsWeight;

    /**
     * 当前 RefKey 引用键通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double refkeyWeight;

    /**
     * 当前文章分块 lexical 通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double articleChunkWeight;

    /**
     * 当前 Source（知识源）通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double sourceWeight;

    /**
     * 当前 Source 分块 lexical 通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double sourceChunkWeight;

    /**
     * 当前 Fact Card lexical 通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double factCardWeight;

    /**
     * 当前 Contribution（贡献度）通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double contributionWeight;

    /**
     * 当前 Graph（知识图谱）通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double graphWeight;

    /**
     * 当前文章级别向量通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double articleVectorWeight;

    /**
     * 当前分块级别向量通道 RRF 权重。
     *
     * <p>{@code 0} 表示该通道已关闭。</p>
     */
    private final double chunkVectorWeight;

    /**
     * 当前 RRF K 参数。
     *
     * <p>控制 RRF 排名平滑度，值越大排名越平滑但区分度越低。</p>
     */
    private final int rrfK;

    /**
     * 创建管理侧 Query 检索配置响应。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param rewriteEnabled 是否启用查询改写
     * @param intentAwareVectorEnabled 是否启用意图感知向量通道
     * @param ftsWeight 全文检索权重
     * @param refkeyWeight RefKey 权重
     * @param articleChunkWeight 文章分块 lexical 权重
     * @param sourceWeight Source 权重
     * @param sourceChunkWeight Source 分块 lexical 权重
     * @param factCardWeight Fact Card lexical 权重
     * @param contributionWeight Contribution 权重
     * @param graphWeight Graph 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight 分块向量权重
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
