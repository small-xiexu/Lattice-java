package com.xbk.lattice.api.admin;

import java.util.Map;

/**
 * 管理侧 Fact Card 统计响应
 *
 * 职责：承载结构化证据卡数量、类型分布和质量状态摘要
 *
 * @author xiexu
 */
public class AdminFactCardSummaryResponse {

    private final int totalCount;

    private final Map<String, Integer> countByCardType;

    private final Map<String, Integer> countByReviewStatus;

    private final int sourceReferenceMissingCount;

    private final int lowConfidenceCount;

    /**
     * 创建管理侧 Fact Card 统计响应。
     *
     * @param totalCount 总数
     * @param countByCardType 按类型统计
     * @param countByReviewStatus 按审查状态统计
     * @param sourceReferenceMissingCount source 回指缺失数
     * @param lowConfidenceCount 低置信数
     */
    public AdminFactCardSummaryResponse(
            int totalCount,
            Map<String, Integer> countByCardType,
            Map<String, Integer> countByReviewStatus,
            int sourceReferenceMissingCount,
            int lowConfidenceCount
    ) {
        this.totalCount = totalCount;
        this.countByCardType = countByCardType;
        this.countByReviewStatus = countByReviewStatus;
        this.sourceReferenceMissingCount = sourceReferenceMissingCount;
        this.lowConfidenceCount = lowConfidenceCount;
    }

    /**
     * 获取总数。
     *
     * @return 总数
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * 获取按类型统计。
     *
     * @return 按类型统计
     */
    public Map<String, Integer> getCountByCardType() {
        return countByCardType;
    }

    /**
     * 获取按审查状态统计。
     *
     * @return 按审查状态统计
     */
    public Map<String, Integer> getCountByReviewStatus() {
        return countByReviewStatus;
    }

    /**
     * 获取 source 回指缺失数。
     *
     * @return source 回指缺失数
     */
    public int getSourceReferenceMissingCount() {
        return sourceReferenceMissingCount;
    }

    /**
     * 获取低置信数。
     *
     * @return 低置信数
     */
    public int getLowConfidenceCount() {
        return lowConfidenceCount;
    }
}
