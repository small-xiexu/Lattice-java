package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleUsageStatsRecord;

import java.util.List;

/**
 * 文章热点刷新结果
 *
 * 职责：描述一次热点统计刷新和待抽检队列生成结果
 *
 * @author xiexu
 */
public class ArticleHotspotRefreshResult {

    private final int rebuiltStatsCount;

    private final int hotspotCandidateCount;

    private final int updatedArticleCount;

    private final int heatScoreThreshold;

    private final List<ArticleUsageStatsRecord> candidates;

    /**
     * 创建文章热点刷新结果。
     *
     * @param rebuiltStatsCount 重建统计数量
     * @param hotspotCandidateCount 热点候选数量
     * @param updatedArticleCount 更新文章数量
     * @param heatScoreThreshold 热度阈值
     * @param candidates 热点候选
     */
    public ArticleHotspotRefreshResult(
            int rebuiltStatsCount,
            int hotspotCandidateCount,
            int updatedArticleCount,
            int heatScoreThreshold,
            List<ArticleUsageStatsRecord> candidates
    ) {
        this.rebuiltStatsCount = rebuiltStatsCount;
        this.hotspotCandidateCount = hotspotCandidateCount;
        this.updatedArticleCount = updatedArticleCount;
        this.heatScoreThreshold = heatScoreThreshold;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * 获取重建统计数量。
     *
     * @return 重建统计数量
     */
    public int getRebuiltStatsCount() {
        return rebuiltStatsCount;
    }

    /**
     * 获取热点候选数量。
     *
     * @return 热点候选数量
     */
    public int getHotspotCandidateCount() {
        return hotspotCandidateCount;
    }

    /**
     * 获取更新文章数量。
     *
     * @return 更新文章数量
     */
    public int getUpdatedArticleCount() {
        return updatedArticleCount;
    }

    /**
     * 获取热度阈值。
     *
     * @return 热度阈值
     */
    public int getHeatScoreThreshold() {
        return heatScoreThreshold;
    }

    /**
     * 获取热点候选。
     *
     * @return 热点候选
     */
    public List<ArticleUsageStatsRecord> getCandidates() {
        return candidates;
    }
}
