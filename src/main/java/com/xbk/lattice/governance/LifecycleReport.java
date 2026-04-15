package com.xbk.lattice.governance;

import java.util.List;

/**
 * 生命周期报告
 *
 * 职责：汇总知识文章生命周期分布与条目清单
 *
 * @author xiexu
 */
public class LifecycleReport {

    private final int totalArticles;

    private final int activeCount;

    private final int deprecatedCount;

    private final int archivedCount;

    private final int otherCount;

    private final List<LifecycleItem> items;

    /**
     * 创建生命周期报告。
     *
     * @param totalArticles 文章总数
     * @param activeCount active 数
     * @param deprecatedCount deprecated 数
     * @param archivedCount archived 数
     * @param otherCount 其他状态数
     * @param items 生命周期条目
     */
    public LifecycleReport(
            int totalArticles,
            int activeCount,
            int deprecatedCount,
            int archivedCount,
            int otherCount,
            List<LifecycleItem> items
    ) {
        this.totalArticles = totalArticles;
        this.activeCount = activeCount;
        this.deprecatedCount = deprecatedCount;
        this.archivedCount = archivedCount;
        this.otherCount = otherCount;
        this.items = items;
    }

    /**
     * 获取文章总数。
     *
     * @return 文章总数
     */
    public int getTotalArticles() {
        return totalArticles;
    }

    /**
     * 获取 active 数。
     *
     * @return active 数
     */
    public int getActiveCount() {
        return activeCount;
    }

    /**
     * 获取 deprecated 数。
     *
     * @return deprecated 数
     */
    public int getDeprecatedCount() {
        return deprecatedCount;
    }

    /**
     * 获取 archived 数。
     *
     * @return archived 数
     */
    public int getArchivedCount() {
        return archivedCount;
    }

    /**
     * 获取其他状态数。
     *
     * @return 其他状态数
     */
    public int getOtherCount() {
        return otherCount;
    }

    /**
     * 获取生命周期条目。
     *
     * @return 生命周期条目
     */
    public List<LifecycleItem> getItems() {
        return items;
    }
}
