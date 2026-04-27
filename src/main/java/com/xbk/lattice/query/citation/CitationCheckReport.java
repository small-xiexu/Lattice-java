package com.xbk.lattice.query.citation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xbk.lattice.api.query.CitationCheckSummary;

import java.util.List;

/**
 * Citation 检查报告
 *
 * 职责：汇总最终答案的 claim 分段、引用核验结果与覆盖率指标
 *
 * @author xiexu
 */
public class CitationCheckReport {

    private final String checkedAnswer;

    private final List<ClaimSegment> claimSegments;

    private final List<CitationValidationResult> results;

    private final int verifiedCount;

    private final int demotedCount;

    private final int skippedCount;

    private final boolean noCitation;

    private final double coverageRate;

    private final int unsupportedClaimCount;

    private final int unmatchedLiteralCount;

    private final int unusedProjectionCount;

    private final int projectionMismatchCount;

    /**
     * 创建 Citation 检查报告。
     *
     * @param checkedAnswer 检查后的答案
     * @param claimSegments claim 分段
     * @param results 引用核验结果
     * @param verifiedCount 已验证引用数
     * @param demotedCount 已降级引用数
     * @param skippedCount 已跳过引用数
     * @param noCitation 是否无引用
     * @param coverageRate 引用覆盖率
     * @param unsupportedClaimCount 不受支持 claim 数
     * @param unmatchedLiteralCount 白名单外字面量数量
     * @param unusedProjectionCount 未被最终答案使用的 projection 数量
     * @param projectionMismatchCount 投影不匹配数量
     */
    @JsonCreator
    public CitationCheckReport(
            @JsonProperty("checkedAnswer") String checkedAnswer,
            @JsonProperty("claimSegments") List<ClaimSegment> claimSegments,
            @JsonProperty("results") List<CitationValidationResult> results,
            @JsonProperty("verifiedCount") int verifiedCount,
            @JsonProperty("demotedCount") int demotedCount,
            @JsonProperty("skippedCount") int skippedCount,
            @JsonProperty("noCitation") boolean noCitation,
            @JsonProperty("coverageRate") double coverageRate,
            @JsonProperty("unsupportedClaimCount") int unsupportedClaimCount,
            @JsonProperty("unmatchedLiteralCount") int unmatchedLiteralCount,
            @JsonProperty("unusedProjectionCount") int unusedProjectionCount,
            @JsonProperty("projectionMismatchCount") int projectionMismatchCount
    ) {
        this.checkedAnswer = checkedAnswer;
        this.claimSegments = claimSegments;
        this.results = results;
        this.verifiedCount = verifiedCount;
        this.demotedCount = demotedCount;
        this.skippedCount = skippedCount;
        this.noCitation = noCitation;
        this.coverageRate = coverageRate;
        this.unsupportedClaimCount = unsupportedClaimCount;
        this.unmatchedLiteralCount = unmatchedLiteralCount;
        this.unusedProjectionCount = unusedProjectionCount;
        this.projectionMismatchCount = projectionMismatchCount;
    }

    /**
     * 返回检查后的答案。
     *
     * @return 检查后的答案
     */
    public String getCheckedAnswer() {
        return checkedAnswer;
    }

    /**
     * 返回 claim 分段。
     *
     * @return claim 分段
     */
    public List<ClaimSegment> getClaimSegments() {
        return claimSegments;
    }

    /**
     * 返回引用核验结果。
     *
     * @return 引用核验结果
     */
    public List<CitationValidationResult> getResults() {
        return results;
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
     * 返回是否无引用。
     *
     * @return 是否无引用
     */
    public boolean isNoCitation() {
        return noCitation;
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
     * 返回不受支持 claim 数。
     *
     * @return 不受支持 claim 数
     */
    public int getUnsupportedClaimCount() {
        return unsupportedClaimCount;
    }

    /**
     * 返回白名单外字面量数量。
     *
     * @return 白名单外字面量数量
     */
    public int getUnmatchedLiteralCount() {
        return unmatchedLiteralCount;
    }

    /**
     * 返回未使用 projection 数量。
     *
     * @return 未使用 projection 数量
     */
    public int getUnusedProjectionCount() {
        return unusedProjectionCount;
    }

    /**
     * 返回投影不匹配数量。
     *
     * @return 投影不匹配数量
     */
    public int getProjectionMismatchCount() {
        return projectionMismatchCount;
    }

    /**
     * 转换为对外摘要。
     *
     * @return 对外摘要
     */
    public CitationCheckSummary toSummary() {
        return new CitationCheckSummary(verifiedCount, demotedCount, skippedCount, coverageRate, noCitation);
    }
}
