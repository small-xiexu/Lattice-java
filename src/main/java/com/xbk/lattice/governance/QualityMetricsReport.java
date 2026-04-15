package com.xbk.lattice.governance;

/**
 * 质量指标报告
 *
 * 职责：汇总知识库质量相关的最小核心指标
 *
 * @author xiexu
 */
public class QualityMetricsReport {

    private final int totalArticles;

    private final int passedArticles;

    private final int pendingReviewArticles;

    private final int needsHumanReviewArticles;

    private final int contributionCount;

    private final int sourceFileCount;

    /**
     * 创建质量指标报告。
     *
     * @param totalArticles 文章总数
     * @param passedArticles 已通过审查文章数
     * @param pendingReviewArticles 待审查文章数
     * @param needsHumanReviewArticles 需人工处理文章数
     * @param contributionCount contribution 数
     * @param sourceFileCount 源文件数
     */
    public QualityMetricsReport(
            int totalArticles,
            int passedArticles,
            int pendingReviewArticles,
            int needsHumanReviewArticles,
            int contributionCount,
            int sourceFileCount
    ) {
        this.totalArticles = totalArticles;
        this.passedArticles = passedArticles;
        this.pendingReviewArticles = pendingReviewArticles;
        this.needsHumanReviewArticles = needsHumanReviewArticles;
        this.contributionCount = contributionCount;
        this.sourceFileCount = sourceFileCount;
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
     * 获取已通过审查文章数。
     *
     * @return 已通过审查文章数
     */
    public int getPassedArticles() {
        return passedArticles;
    }

    /**
     * 获取待审查文章数。
     *
     * @return 待审查文章数
     */
    public int getPendingReviewArticles() {
        return pendingReviewArticles;
    }

    /**
     * 获取需人工处理文章数。
     *
     * @return 需人工处理文章数
     */
    public int getNeedsHumanReviewArticles() {
        return needsHumanReviewArticles;
    }

    /**
     * 获取 contribution 数。
     *
     * @return contribution 数
     */
    public int getContributionCount() {
        return contributionCount;
    }

    /**
     * 获取源文件数。
     *
     * @return 源文件数
     */
    public int getSourceFileCount() {
        return sourceFileCount;
    }
}
