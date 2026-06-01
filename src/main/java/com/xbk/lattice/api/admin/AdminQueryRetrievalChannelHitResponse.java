package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧 Query 检索通道命中响应。
 *
 * <p>承载一次 run 中各通道的命中明细——含排序位置、RRF 融合结果、通道权重和证据内容，
 * 由 {@code AdminQueryRetrievalAuditController} 组装返回，用于检索排序诊断。
 * 含大 JSON 字段（{@code sourceChunkIdsJson}、{@code sourcePathsJson}、{@code metadataJson}），
 * 禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryRetrievalChannelHitResponse {

    /** 命中记录主键。 */
    private final Long hitId;

    /** 所属 run 主键。 */
    private final Long runId;

    /** 来源通道名称。 */
    private final String channelName;

    /**
     * 通道内排序位置（1-based）。
     *
     * <p>反映该命中在原始通道中的排名。值越小表示通道内越靠前。</p>
     */
    private final int hitRank;

    /**
     * RRF 融合后的排序位置。
     *
     * <p>为 {@code null} 表示该命中未进入最终融合结果——被 RRF 算法淘汰。</p>
     */
    private final Integer fusedRank;

    /**
     * 是否进入最终融合结果。
     *
     * <p>{@code false} 时 {@code fusedRank} 为 {@code null}，该命中被 RRF 淘汰。
     * 用于排查某通道的高分命中为何最终未出现在检索结果中。</p>
     */
    private final boolean includedInFused;

    /**
     * 该通道在 RRF 融合时的权重。
     *
     * <p>权重越大的通道在 RRF 排序中占比越高。</p>
     */
    private final double channelWeight;

    /**
     * 证据类型。
     *
     * <p>可选值：{@code article} / {@code source_chunk} / {@code fact_card}。</p>
     */
    private final String evidenceType;

    /** 关联文章唯一键。 */
    private final String articleKey;

    /** 概念标识。 */
    private final String conceptId;

    /** 文章标题。 */
    private final String title;

    /**
     * 通道内打分（原始分数）。
     *
     * <p>不同通道的打分尺度可能不同（如 FTS 相关性分 vs 向量余弦相似度），
     * 不可直接跨通道比较。跨通道比较应使用 {@code fusedRank}。</p>
     */
    private final double score;

    /**
     * Fact Card 主键。
     *
     * <p>为 {@code null} 表示 {@code evidenceType} 非 {@code fact_card}。</p>
     */
    private final Long factCardId;

    /**
     * Fact Card 类型。
     *
     * <p>为 {@code null} 表示非 fact_card。</p>
     */
    private final String cardType;

    /** 文章/Fact Card 的审查状态。 */
    private final String reviewStatus;

    /**
     * Fact Card 置信度。
     *
     * <p>为 {@code null} 表示非 fact_card 或无置信度数据。</p>
     */
    private final Double confidence;

    /**
     * Source Chunk ID JSON 数组。
     *
     * <p>可能较大，禁止参与 {@code toString()}。</p>
     */
    private final String sourceChunkIdsJson;

    /**
     * 来源路径 JSON 数组。
     *
     * <p>可能较大，禁止参与 {@code toString()}。</p>
     */
    private final String sourcePathsJson;

    /**
     * 扩展元数据 JSON。
     *
     * <p>可能较大，禁止参与 {@code toString()}。</p>
     */
    private final String metadataJson;

    /** 记录创建时间（ISO-8601 字符串）。 */
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
     * @param factCardId Fact Card 数据库主键
     * @param cardType Fact Card 类型
     * @param reviewStatus 审查状态
     * @param confidence 置信度
     * @param sourceChunkIdsJson Source Chunk ID JSON
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
            Long factCardId,
            String cardType,
            String reviewStatus,
            Double confidence,
            String sourceChunkIdsJson,
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
        this.factCardId = factCardId;
        this.cardType = cardType;
        this.reviewStatus = reviewStatus;
        this.confidence = confidence;
        this.sourceChunkIdsJson = sourceChunkIdsJson;
        this.sourcePathsJson = sourcePathsJson;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }
}
