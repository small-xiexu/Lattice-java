package com.xbk.lattice.governance.domain;

import lombok.Getter;

import java.util.List;

/**
 * 生命周期报告。
 *
 * <p>汇总知识文章生命周期分布与条目清单——含各状态的计数和条目详情。
 * {@code items} 可变 List 风险已知，本轮不修复。
 *
 * @author xiexu
 */
@Getter
public class LifecycleReport {

    /** 文章总数。等于 active+deprecated+archived+other 计数之和。 */
    private final int totalArticles;
    /** active 状态的文章数。 */
    private final int activeCount;
    /** deprecated 状态的文章数。 */
    private final int deprecatedCount;
    /** archived 状态的文章数。 */
    private final int archivedCount;
    /** 未归入标准生命周期桶的文章数。 */
    private final int otherCount;
    /** 生命周期条目列表。可变 List 风险已知，不在 B19 修复范围。 */
    private final List<LifecycleItem> items;

    public LifecycleReport(
            int totalArticles, int activeCount, int deprecatedCount, int archivedCount,
            int otherCount, List<LifecycleItem> items
    ) {
        this.totalArticles = totalArticles;
        this.activeCount = activeCount;
        this.deprecatedCount = deprecatedCount;
        this.archivedCount = archivedCount;
        this.otherCount = otherCount;
        this.items = items;
    }
}
