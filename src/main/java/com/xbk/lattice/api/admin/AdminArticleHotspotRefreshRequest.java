package com.xbk.lattice.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 管理侧文章热点刷新请求
 *
 * 职责：承载热点统计刷新阈值和候选数量上限
 *
 * @author xiexu
 */
public class AdminArticleHotspotRefreshRequest {

    @Min(value = 1, message = "热度阈值必须大于 0")
    private Integer heatScoreThreshold;

    @Min(value = 1, message = "候选数量必须大于 0")
    @Max(value = 200, message = "候选数量不能超过 200")
    private Integer limit;

    /**
     * 获取热度阈值。
     *
     * @return 热度阈值
     */
    public Integer getHeatScoreThreshold() {
        return heatScoreThreshold;
    }

    /**
     * 设置热度阈值。
     *
     * @param heatScoreThreshold 热度阈值
     */
    public void setHeatScoreThreshold(Integer heatScoreThreshold) {
        this.heatScoreThreshold = heatScoreThreshold;
    }

    /**
     * 获取候选数量上限。
     *
     * @return 候选数量上限
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * 设置候选数量上限。
     *
     * @param limit 候选数量上限
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
