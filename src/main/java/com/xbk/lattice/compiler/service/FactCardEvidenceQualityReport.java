package com.xbk.lattice.compiler.service;

/**
 * 事实证据卡质量报告
 *
 * 职责：承载证据层 source 回指、状态解释与问题原因覆盖指标
 *
 * @author xiexu
 */
public class FactCardEvidenceQualityReport {

    private final int totalCount;

    private final int sourceReferencePassedCount;

    private final int statusExplainedCount;

    private final int issueCount;

    private final int issueWithReasonCount;

    /**
     * 创建事实证据卡质量报告。
     *
     * @param totalCount 总样本数
     * @param sourceReferencePassedCount source 回指通过数
     * @param statusExplainedCount 状态可解释数
     * @param issueCount 问题状态数
     * @param issueWithReasonCount 带原因的问题状态数
     */
    public FactCardEvidenceQualityReport(
            int totalCount,
            int sourceReferencePassedCount,
            int statusExplainedCount,
            int issueCount,
            int issueWithReasonCount
    ) {
        this.totalCount = totalCount;
        this.sourceReferencePassedCount = sourceReferencePassedCount;
        this.statusExplainedCount = statusExplainedCount;
        this.issueCount = issueCount;
        this.issueWithReasonCount = issueWithReasonCount;
    }

    /**
     * 获取总样本数。
     *
     * @return 总样本数
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * 获取 source 回指通过数。
     *
     * @return source 回指通过数
     */
    public int getSourceReferencePassedCount() {
        return sourceReferencePassedCount;
    }

    /**
     * 获取状态可解释数。
     *
     * @return 状态可解释数
     */
    public int getStatusExplainedCount() {
        return statusExplainedCount;
    }

    /**
     * 获取问题状态数。
     *
     * @return 问题状态数
     */
    public int getIssueCount() {
        return issueCount;
    }

    /**
     * 获取带原因的问题状态数。
     *
     * @return 带原因的问题状态数
     */
    public int getIssueWithReasonCount() {
        return issueWithReasonCount;
    }

    /**
     * 获取 source 回指通过率。
     *
     * @return source 回指通过率
     */
    public double getSourceReferencePassRate() {
        if (totalCount == 0) {
            return 0.0D;
        }
        return (double) sourceReferencePassedCount / (double) totalCount;
    }

    /**
     * 获取状态可解释率。
     *
     * @return 状态可解释率
     */
    public double getStatusExplainabilityRate() {
        if (totalCount == 0) {
            return 0.0D;
        }
        return (double) statusExplainedCount / (double) totalCount;
    }

    /**
     * 获取问题状态原因覆盖率。
     *
     * @return 问题状态原因覆盖率
     */
    public double getIssueReasonCoverageRate() {
        if (issueCount == 0) {
            return 1.0D;
        }
        return (double) issueWithReasonCount / (double) issueCount;
    }

    /**
     * 判断是否通过给定质量门槛。
     *
     * @param minimumSourceReferencePassRate 最低 source 回指通过率
     * @param minimumStatusExplainabilityRate 最低状态可解释率
     * @param minimumIssueReasonCoverageRate 最低问题原因覆盖率
     * @return 通过返回 true
     */
    public boolean passesGate(
            double minimumSourceReferencePassRate,
            double minimumStatusExplainabilityRate,
            double minimumIssueReasonCoverageRate
    ) {
        return getSourceReferencePassRate() >= minimumSourceReferencePassRate
                && getStatusExplainabilityRate() >= minimumStatusExplainabilityRate
                && getIssueReasonCoverageRate() >= minimumIssueReasonCoverageRate;
    }
}
