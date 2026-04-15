package com.xbk.lattice.api.admin;

/**
 * 管理侧 pending 条目响应
 *
 * 职责：承载 admin overview 中单条待确认查询摘要
 *
 * @author xiexu
 */
public class AdminOverviewPendingItemResponse {

    private final String queryId;

    private final String question;

    private final String reviewStatus;

    /**
     * 创建管理侧 pending 条目响应。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param reviewStatus 审查状态
     */
    public AdminOverviewPendingItemResponse(String queryId, String question, String reviewStatus) {
        this.queryId = queryId;
        this.question = question;
        this.reviewStatus = reviewStatus;
    }

    /**
     * 获取查询标识。
     *
     * @return 查询标识
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 获取问题。
     *
     * @return 问题
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }
}
