package com.xbk.lattice.governance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    private final int highRiskArticleCount;

    private final int hotspotPendingVerificationCount;

    private final int userReportedAnswerCount;

    private final int answerFeedbackPendingCount;

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
        this(articleCount, sourceFileCount, contributionCount, pendingQueryCount, reviewPendingArticleCount, 0, 0, 0, 0);
    }

    /**
     * 创建状态快照。
     *
     * @param articleCount 文章数量
     * @param sourceFileCount 源文件数量
     * @param contributionCount contribution 数量
     * @param pendingQueryCount pending 数量
     * @param reviewPendingArticleCount 待人工处理文章数量
     * @param highRiskArticleCount 高风险文章数量
     * @param hotspotPendingVerificationCount 热点待抽检数量
     * @param userReportedAnswerCount 用户反馈风险数量
     */
    public StatusSnapshot(
            int articleCount,
            int sourceFileCount,
            int contributionCount,
            int pendingQueryCount,
            int reviewPendingArticleCount,
            int highRiskArticleCount,
            int hotspotPendingVerificationCount,
            int userReportedAnswerCount
    ) {
        this(
                articleCount,
                sourceFileCount,
                contributionCount,
                pendingQueryCount,
                reviewPendingArticleCount,
                highRiskArticleCount,
                hotspotPendingVerificationCount,
                userReportedAnswerCount,
                0
        );
    }

    /**
     * 创建状态快照。
     *
     * @param articleCount 文章数量
     * @param sourceFileCount 源文件数量
     * @param contributionCount contribution 数量
     * @param pendingQueryCount pending 数量
     * @param reviewPendingArticleCount 待人工处理文章数量
     * @param highRiskArticleCount 高风险文章数量
     * @param hotspotPendingVerificationCount 热点待抽检数量
     * @param userReportedAnswerCount 用户反馈风险数量
     * @param answerFeedbackPendingCount 结果反馈待处理数量
     */
    @JsonCreator
    public StatusSnapshot(
            @JsonProperty("articleCount") int articleCount,
            @JsonProperty("sourceFileCount") int sourceFileCount,
            @JsonProperty("contributionCount") int contributionCount,
            @JsonProperty("pendingQueryCount") int pendingQueryCount,
            @JsonProperty("reviewPendingArticleCount") int reviewPendingArticleCount,
            @JsonProperty("highRiskArticleCount") int highRiskArticleCount,
            @JsonProperty("hotspotPendingVerificationCount") int hotspotPendingVerificationCount,
            @JsonProperty("userReportedAnswerCount") int userReportedAnswerCount,
            @JsonProperty("answerFeedbackPendingCount") int answerFeedbackPendingCount
    ) {
        this.articleCount = articleCount;
        this.sourceFileCount = sourceFileCount;
        this.contributionCount = contributionCount;
        this.pendingQueryCount = pendingQueryCount;
        this.reviewPendingArticleCount = reviewPendingArticleCount;
        this.highRiskArticleCount = highRiskArticleCount;
        this.hotspotPendingVerificationCount = hotspotPendingVerificationCount;
        this.userReportedAnswerCount = userReportedAnswerCount;
        this.answerFeedbackPendingCount = answerFeedbackPendingCount;
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

    /**
     * 获取高风险文章数量。
     *
     * @return 高风险文章数量
     */
    public int getHighRiskArticleCount() {
        return highRiskArticleCount;
    }

    /**
     * 获取热点待抽检数量。
     *
     * @return 热点待抽检数量
     */
    public int getHotspotPendingVerificationCount() {
        return hotspotPendingVerificationCount;
    }

    /**
     * 获取用户反馈风险数量。
     *
     * @return 用户反馈风险数量
     */
    public int getUserReportedAnswerCount() {
        return userReportedAnswerCount;
    }

    /**
     * 获取结果反馈待处理数量。
     *
     * @return 结果反馈待处理数量
     */
    public int getAnswerFeedbackPendingCount() {
        return answerFeedbackPendingCount;
    }
}
