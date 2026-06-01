package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧文章热点刷新响应。
 *
 * <p>返回热点统计刷新的执行结果和候选文章热度详情，
 * 由 {@code AdminArticleController} 在热点刷新完成后组装返回。
 * 构造器含 {@code List.copyOf} 防御性拷贝以保证不可变性。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleHotspotRefreshResponse {

    /**
     * 本次重建的 usage stats 数量。
     *
     * <p>反映热点刷新时实际重新计算的统计记录数。</p>
     */
    private final int rebuiltStatsCount;

    /**
     * 满足热度阈值的候选数量。
     *
     * <p>heatScore {@code >=} heatScoreThreshold 的文章数。
     * 可能大于 {@code updatedArticleCount}（部分候选可能因其他条件被过滤）。</p>
     */
    private final int hotspotCandidateCount;

    /**
     * 实际更新 hotspot 标记的文章数。
     *
     * <p>等于最终被标记为热点的文章数，小于等于 {@code hotspotCandidateCount}。</p>
     */
    private final int updatedArticleCount;

    /**
     * 本次刷新使用的热度阈值。
     *
     * <p>回显请求中的阈值或 controller 使用的默认值。</p>
     */
    private final int heatScoreThreshold;

    /**
     * 热点候选列表。
     *
     * <p>包含满足阈值条件的 usage stats 详情，按热度分降序排列。
     * 不可变（构造器中通过 {@code List.copyOf} 防御性拷贝）。</p>
     */
    private final List<AdminArticleUsageStatsResponse> candidates;

    /**
     * 创建管理侧文章热点刷新响应。
     *
     * @param rebuiltStatsCount 重建统计数量
     * @param hotspotCandidateCount 热点候选数量
     * @param updatedArticleCount 更新文章数量
     * @param heatScoreThreshold 热度阈值
     * @param candidates 热点候选（构造器中做防御性拷贝）
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
}
