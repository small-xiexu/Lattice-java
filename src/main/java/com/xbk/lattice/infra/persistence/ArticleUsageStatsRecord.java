package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章使用热度统计记录
 *
 * 职责：承载 article_usage_stats 表的文章级统计结果
 *
 * @author xiexu
 */
public class ArticleUsageStatsRecord {

    private final String articleKey;

    private final String conceptId;

    private final int retrievalHitCount;

    private final int citationCount;

    private final int answerFeedbackCount;

    private final int manualMarkCount;

    private final int heatScore;

    private final List<String> sourcePaths;

    private final OffsetDateTime updatedAt;

    /**
     * 创建文章使用热度统计记录。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param retrievalHitCount 检索命中次数
     * @param citationCount 答案引用次数
     * @param answerFeedbackCount 答案反馈次数
     * @param manualMarkCount 人工标记次数
     * @param heatScore 热度分
     * @param sourcePaths 来源路径
     * @param updatedAt 更新时间
     */
    public ArticleUsageStatsRecord(
            String articleKey,
            String conceptId,
            int retrievalHitCount,
            int citationCount,
            int answerFeedbackCount,
            int manualMarkCount,
            int heatScore,
            List<String> sourcePaths,
            OffsetDateTime updatedAt
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
     * 获取检索命中次数。
     *
     * @return 检索命中次数
     */
    public int getRetrievalHitCount() {
        return retrievalHitCount;
    }

    /**
     * 获取答案引用次数。
     *
     * @return 答案引用次数
     */
    public int getCitationCount() {
        return citationCount;
    }

    /**
     * 获取答案反馈次数。
     *
     * @return 答案反馈次数
     */
    public int getAnswerFeedbackCount() {
        return answerFeedbackCount;
    }

    /**
     * 获取人工标记次数。
     *
     * @return 人工标记次数
     */
    public int getManualMarkCount() {
        return manualMarkCount;
    }

    /**
     * 获取热度分。
     *
     * @return 热度分
     */
    public int getHeatScore() {
        return heatScore;
    }

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
