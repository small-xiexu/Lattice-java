package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 pending 条目响应
 *
 * 职责：承载管理侧 pending 列表中的单条记录
 *
 * @author xiexu
 */
public class AdminPendingItemResponse {

    private final String queryId;

    private final String question;

    private final String answer;

    private final String reviewStatus;

    private final List<String> selectedConceptIds;

    private final List<String> sourceFilePaths;

    private final String createdAt;

    private final String expiresAt;

    /**
     * 创建管理侧 pending 条目响应。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param answer 答案
     * @param reviewStatus 审查状态
     * @param selectedConceptIds 概念标识
     * @param sourceFilePaths 来源路径
     * @param createdAt 创建时间
     * @param expiresAt 过期时间
     */
    public AdminPendingItemResponse(
            String queryId,
            String question,
            String answer,
            String reviewStatus,
            List<String> selectedConceptIds,
            List<String> sourceFilePaths,
            String createdAt,
            String expiresAt
    ) {
        this.queryId = queryId;
        this.question = question;
        this.answer = answer;
        this.reviewStatus = reviewStatus;
        this.selectedConceptIds = selectedConceptIds;
        this.sourceFilePaths = sourceFilePaths;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
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
     * 获取答案。
     *
     * @return 答案
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取命中概念。
     *
     * @return 命中概念
     */
    public List<String> getSelectedConceptIds() {
        return selectedConceptIds;
    }

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourceFilePaths() {
        return sourceFilePaths;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取过期时间。
     *
     * @return 过期时间
     */
    public String getExpiresAt() {
        return expiresAt;
    }
}
