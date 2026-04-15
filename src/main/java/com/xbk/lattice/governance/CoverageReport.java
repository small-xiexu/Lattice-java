package com.xbk.lattice.governance;

import java.util.List;

/**
 * 覆盖率报告
 *
 * 职责：汇总已覆盖源文件数量、未覆盖数量与覆盖率比例
 *
 * @author xiexu
 */
public class CoverageReport {

    private final int totalSourceFileCount;

    private final int coveredSourceFileCount;

    private final int uncoveredSourceFileCount;

    private final double coverageRatio;

    private final List<String> coveredSourcePaths;

    /**
     * 创建覆盖率报告。
     *
     * @param totalSourceFileCount 源文件总数
     * @param coveredSourceFileCount 已覆盖源文件数
     * @param uncoveredSourceFileCount 未覆盖源文件数
     * @param coverageRatio 覆盖率
     * @param coveredSourcePaths 已覆盖源文件路径
     */
    public CoverageReport(
            int totalSourceFileCount,
            int coveredSourceFileCount,
            int uncoveredSourceFileCount,
            double coverageRatio,
            List<String> coveredSourcePaths
    ) {
        this.totalSourceFileCount = totalSourceFileCount;
        this.coveredSourceFileCount = coveredSourceFileCount;
        this.uncoveredSourceFileCount = uncoveredSourceFileCount;
        this.coverageRatio = coverageRatio;
        this.coveredSourcePaths = coveredSourcePaths;
    }

    /**
     * 获取源文件总数。
     *
     * @return 源文件总数
     */
    public int getTotalSourceFileCount() {
        return totalSourceFileCount;
    }

    /**
     * 获取已覆盖源文件数。
     *
     * @return 已覆盖源文件数
     */
    public int getCoveredSourceFileCount() {
        return coveredSourceFileCount;
    }

    /**
     * 获取未覆盖源文件数。
     *
     * @return 未覆盖源文件数
     */
    public int getUncoveredSourceFileCount() {
        return uncoveredSourceFileCount;
    }

    /**
     * 获取覆盖率比例。
     *
     * @return 覆盖率比例
     */
    public double getCoverageRatio() {
        return coverageRatio;
    }

    /**
     * 获取已覆盖源文件路径。
     *
     * @return 已覆盖源文件路径
     */
    public List<String> getCoveredSourcePaths() {
        return coveredSourcePaths;
    }
}
