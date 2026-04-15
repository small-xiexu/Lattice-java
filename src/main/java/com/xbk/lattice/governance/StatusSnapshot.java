package com.xbk.lattice.governance;

/**
 * 知识库状态快照
 *
 * 职责：承载 B7 阶段最小状态面板需要的汇总指标
 *
 * @author xiexu
 */
public class StatusSnapshot {

    private final int articleCount;

    private final int sourceFileCount;

    private final int contributionCount;

    private final int pendingQueryCount;

    private final int reviewPendingArticleCount;

    /**
     * 创建状态快照。
     *
     * @param articleCount 文章数量
     * @param sourceFileCount 源文件数量
     * @param contributionCount contribution 数量
     * @param pendingQueryCount pending 数量
     * @param reviewPendingArticleCount 待人工处理文章数量
     */
    public StatusSnapshot(
            int articleCount,
            int sourceFileCount,
            int contributionCount,
            int pendingQueryCount,
            int reviewPendingArticleCount
    ) {
        this.articleCount = articleCount;
        this.sourceFileCount = sourceFileCount;
        this.contributionCount = contributionCount;
        this.pendingQueryCount = pendingQueryCount;
        this.reviewPendingArticleCount = reviewPendingArticleCount;
    }

    /**
     * 获取文章数量。
     *
     * @return 文章数量
     */
    public int getArticleCount() {
        return articleCount;
    }

    /**
     * 获取源文件数量。
     *
     * @return 源文件数量
     */
    public int getSourceFileCount() {
        return sourceFileCount;
    }

    /**
     * 获取 contribution 数量。
     *
     * @return contribution 数量
     */
    public int getContributionCount() {
        return contributionCount;
    }

    /**
     * 获取 pending 数量。
     *
     * @return pending 数量
     */
    public int getPendingQueryCount() {
        return pendingQueryCount;
    }

    /**
     * 获取待人工处理文章数量。
     *
     * @return 待人工处理文章数量
     */
    public int getReviewPendingArticleCount() {
        return reviewPendingArticleCount;
    }
}
