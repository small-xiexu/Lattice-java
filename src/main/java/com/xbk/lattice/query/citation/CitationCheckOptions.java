package com.xbk.lattice.query.citation;

/**
 * Citation 检查选项
 *
 * 职责：统一管理引用核验与修复过程中的阈值配置
 *
 * @author xiexu
 */
public class CitationCheckOptions {

    private final double minCitationCoverage;

    private final int maxRepairRounds;

    /**
     * 创建 Citation 检查选项。
     *
     * @param minCitationCoverage 最低引用覆盖率
     * @param maxRepairRounds 最大修复轮次
     */
    public CitationCheckOptions(double minCitationCoverage, int maxRepairRounds) {
        this.minCitationCoverage = minCitationCoverage;
        this.maxRepairRounds = maxRepairRounds;
    }

    /**
     * 返回默认选项。
     *
     * @return 默认选项
     */
    public static CitationCheckOptions defaults() {
        return new CitationCheckOptions(0.6D, 1);
    }

    /**
     * 返回最低引用覆盖率。
     *
     * @return 最低引用覆盖率
     */
    public double getMinCitationCoverage() {
        return minCitationCoverage;
    }

    /**
     * 返回最大修复轮次。
     *
     * @return 最大修复轮次
     */
    public int getMaxRepairRounds() {
        return maxRepairRounds;
    }
}
