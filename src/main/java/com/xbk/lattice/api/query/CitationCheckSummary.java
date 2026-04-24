package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Citation 检查摘要
 *
 * 职责：向 Query API 暴露最终答案的引用核验概览
 *
 * @author xiexu
 */
public class CitationCheckSummary {

    private final int verifiedCount;

    private final int demotedCount;

    private final int skippedCount;

    private final double coverageRate;

    private final boolean noCitation;

    /**
     * 创建 Citation 检查摘要。
     *
     * @param verifiedCount 已验证引用数
     * @param demotedCount 已降级引用数
     * @param skippedCount 已跳过引用数
     * @param coverageRate 引用覆盖率
     * @param noCitation 是否无引用
     */
    @JsonCreator
    public CitationCheckSummary(
            @JsonProperty("verifiedCount") int verifiedCount,
            @JsonProperty("demotedCount") int demotedCount,
            @JsonProperty("skippedCount") int skippedCount,
            @JsonProperty("coverageRate") double coverageRate,
            @JsonProperty("noCitation") boolean noCitation
    ) {
        this.verifiedCount = verifiedCount;
        this.demotedCount = demotedCount;
        this.skippedCount = skippedCount;
        this.coverageRate = coverageRate;
        this.noCitation = noCitation;
    }

    /**
     * 返回已验证引用数。
     *
     * @return 已验证引用数
     */
    public int getVerifiedCount() {
        return verifiedCount;
    }

    /**
     * 返回已降级引用数。
     *
     * @return 已降级引用数
     */
    public int getDemotedCount() {
        return demotedCount;
    }

    /**
     * 返回已跳过引用数。
     *
     * @return 已跳过引用数
     */
    public int getSkippedCount() {
        return skippedCount;
    }

    /**
     * 返回引用覆盖率。
     *
     * @return 引用覆盖率
     */
    public double getCoverageRate() {
        return coverageRate;
    }

    /**
     * 返回是否无引用。
     *
     * @return 是否无引用
     */
    public boolean isNoCitation() {
        return noCitation;
    }
}
