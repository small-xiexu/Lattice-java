package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧文章热点刷新响应
 *
 * 职责：返回热点统计刷新结果和候选文章摘要
 *
 * @author xiexu
 */
public class AdminArticleHotspotRefreshResponse {

    private final int rebuiltStatsCount;

    private final int hotspotCandidateCount;

    private final int updatedArticleCount;

    private final int heatScoreThreshold;

    private final List<AdminArticleUsageStatsResponse> candidates;

    /**
     * 创建管理侧文章热点刷新响应。
     *
     * @param rebuiltStatsCount 重建统计数量
     * @param hotspotCandidateCount 热点候选数量
     * @param updatedArticleCount 更新文章数量
     * @param heatScoreThreshold 热度阈值
     * @param candidates 热点候选
     */
    public AdminArticleHotspotRefreshResponse(
            int rebuiltStatsCount,
            int hotspotCandidateCount,
            int updatedArticleCount,
            int heatScoreThreshold,
            List<AdminArticleUsageStatsResponse> candidates
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
    public List<AdminArticleUsageStatsResponse> getCandidates() {
        return candidates;
    }
}
