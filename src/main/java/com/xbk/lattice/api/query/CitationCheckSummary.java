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

    private final int claimCount;

    private final int unsupportedClaimCount;

    /**
     * 创建 Citation 检查摘要。
     *
     * @param verifiedCount 已验证引用数
     * @param demotedCount 已降级引用数
     * @param skippedCount 已跳过引用数
     * @param coverageRate 引用覆盖率
     * @param noCitation 是否无引用
     */
    public CitationCheckSummary(
            @JsonProperty("verifiedCount") int verifiedCount,
            @JsonProperty("demotedCount") int demotedCount,
            @JsonProperty("skippedCount") int skippedCount,
            @JsonProperty("coverageRate") double coverageRate,
            @JsonProperty("noCitation") boolean noCitation
    ) {
        this(verifiedCount, demotedCount, skippedCount, coverageRate, noCitation, 0, 0);
    }

    /**
     * 创建 Citation 检查摘要。
     *
     * @param verifiedCount 已验证引用数
     * @param demotedCount 已降级引用数
     * @param skippedCount 已跳过引用数
     * @param coverageRate 引用覆盖率
     * @param noCitation 是否无引用
     * @param claimCount claim 总数
     * @param unsupportedClaimCount 不受支持 claim 数
     */
    @JsonCreator
    public CitationCheckSummary(
            @JsonProperty("verifiedCount") int verifiedCount,
            @JsonProperty("demotedCount") int demotedCount,
            @JsonProperty("skippedCount") int skippedCount,
            @JsonProperty("coverageRate") double coverageRate,
            @JsonProperty("noCitation") boolean noCitation,
            @JsonProperty("claimCount") int claimCount,
            @JsonProperty("unsupportedClaimCount") int unsupportedClaimCount
    ) {
        this.verifiedCount = verifiedCount;
        this.demotedCount = demotedCount;
        this.skippedCount = skippedCount;
        this.coverageRate = coverageRate;
        this.noCitation = noCitation;
        this.claimCount = claimCount;
        this.unsupportedClaimCount = unsupportedClaimCount;
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

    /**
     * 返回 claim 总数。
     *
     * @return claim 总数
     */
    public int getClaimCount() {
        return claimCount;
    }

    /**
     * 返回不受支持 claim 数。
     *
     * @return 不受支持 claim 数
     */
    public int getUnsupportedClaimCount() {
        return unsupportedClaimCount;
    }
}
