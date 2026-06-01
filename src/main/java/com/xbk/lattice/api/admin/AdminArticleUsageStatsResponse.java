package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧文章使用热度统计响应。
 *
 * <p>向后台返回文章级检索命中、答案引用、反馈与人工标记的综合热度指标，
 * 由 {@code AdminArticleController} 从 usage stats 记录组装返回。
 * 构造器含 {@code List.copyOf} 防御性拷贝以保证不可变性。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleUsageStatsResponse {

    /** 文章唯一键。 */
    private final String articleKey;

    /** 概念标识。 */
    private final String conceptId;

    /**
     * 检索命中次数。
     *
     * <p>反映文章在 query 检索中的曝光度——每次检索返回该文章计为一次命中。</p>
     */
    private final int retrievalHitCount;

    /**
     * 答案引用次数。
     *
     * <p>反映文章在最终回答中的被引用频率——每次 LLM 回答引用该文章计为一次。</p>
     */
    private final int citationCount;

    /**
     * 答案反馈次数。
     *
     * <p>含正负反馈。用户对含该文章引用的回答进行反馈时累计。</p>
     */
    private final int answerFeedbackCount;

    /**
     * 人工标记次数。
     *
     * <p>管理侧操作（如纠错、复核、标记）的累计计数。</p>
     */
    private final int manualMarkCount;

    /**
     * 综合热度分。
     *
     * <p>由上述四个指标（检索命中、引用、反馈、人工标记）加权计算得出，
     * 用于热点判定——{@code heatScore >= heatScoreThreshold} 时文章被标记为热点。</p>
     */
    private final int heatScore;

    /**
     * 来源文件路径列表。
     *
     * <p>不可变（构造器中通过 {@code List.copyOf} 防御性拷贝）。</p>
     */
    private final List<String> sourcePaths;

    /** 统计更新时间（ISO-8601 字符串）。 */
    private final String updatedAt;

    /**
     * 创建管理侧文章使用热度统计响应。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param retrievalHitCount 检索命中次数
     * @param citationCount 答案引用次数
     * @param answerFeedbackCount 答案反馈次数
     * @param manualMarkCount 人工标记次数
     * @param heatScore 热度分
     * @param sourcePaths 来源路径（构造器中做防御性拷贝）
     * @param updatedAt 更新时间
     */
    public AdminArticleUsageStatsResponse(
            String articleKey,
            String conceptId,
            int retrievalHitCount,
            int citationCount,
            int answerFeedbackCount,
            int manualMarkCount,
            int heatScore,
            List<String> sourcePaths,
            String updatedAt
    ) {
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.retrievalHitCount = retrievalHitCount;
        this.citationCount = citationCount;
        this.answerFeedbackCount = answerFeedbackCount;
        this.manualMarkCount = manualMarkCount;
        this.heatScore = heatScore;
        this.sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        this.updatedAt = updatedAt;
    }
}
