package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.Map;

/**
 * 管理侧 Fact Card 统计响应。
 *
 * <p>承载结构化证据卡的数量、类型分布和质量状态摘要，
 * 用于管理侧 Dashboard 展示 Fact Card 的全局健康度。
 *
 * @author xiexu
 */
@Getter
public class AdminFactCardSummaryResponse {

    /** Fact Card 总数。 */
    private final int totalCount;

    /**
     * 按 Fact Card 类型分组的计数。
     *
     * <p>key 为 {@code FactCardType} 枚举名（如 {@code STRUCTURED_ITEM} / {@code SUMMARY}），
     * value 为该类型的 card 数量。</p>
     */
    private final Map<String, Integer> countByCardType;

    /**
     * 按审查状态分组的计数。
     *
     * <p>key 为数据库审查状态值（如 {@code accepted} / {@code needs_human_review}），
     * value 为该状态的 card 数量。</p>
     */
    private final Map<String, Integer> countByReviewStatus;

    /**
     * source chunk 回指缺失的 card 数。
     *
     * <p>{@code > 0} 表示存在 source chunk 已被删除但 card 仍引用的情况，
     * 属于数据完整性问题，管理侧应提示用户检查。</p>
     */
    private final int sourceReferenceMissingCount;

    /**
     * 低置信度 card 数。
     *
     * <p>confidence 低于服务端阈值的 card 数量。{@code > 0} 时管理侧应关注
     * 这些 card 的事实准确性。</p>
     */
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
}
