package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 待确认查询记录
 *
 * 职责：表示 pending query 持久化对象
 *
 * @author xiexu
 */
public class PendingQueryRecord {

    private final String queryId;

    private final String question;

    private final String answer;

    private final List<String> selectedConceptIds;

    private final List<String> selectedArticleKeys;

    private final List<String> sourceFilePaths;

    private final String correctionsJson;

    private final String reviewStatus;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime expiresAt;

    /**
     * 创建待确认查询记录。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param answer 答案
     * @param selectedConceptIds 命中概念标识
     * @param sourceFilePaths 来源文件路径
     * @param correctionsJson 纠错历史 JSON
     * @param reviewStatus 审查状态
     * @param createdAt 创建时间
     * @param expiresAt 过期时间
     */
    public PendingQueryRecord(
            String queryId,
            String question,
            String answer,
            List<String> selectedConceptIds,
            List<String> sourceFilePaths,
            String correctionsJson,
            String reviewStatus,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt
    ) {
        this(
                queryId,
                question,
                answer,
                selectedConceptIds,
                List.of(),
                sourceFilePaths,
                correctionsJson,
                reviewStatus,
                createdAt,
                expiresAt
        );
    }

    /**
     * 创建待确认查询记录。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param answer 答案
     * @param selectedConceptIds 命中概念标识
     * @param selectedArticleKeys 命中的文章唯一键
     * @param sourceFilePaths 来源文件路径
     * @param correctionsJson 纠错历史 JSON
     * @param reviewStatus 审查状态
     * @param createdAt 创建时间
     * @param expiresAt 过期时间
     */
    public PendingQueryRecord(
            String queryId,
            String question,
            String answer,
            List<String> selectedConceptIds,
            List<String> selectedArticleKeys,
            List<String> sourceFilePaths,
            String correctionsJson,
            String reviewStatus,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt
    ) {
        this.queryId = queryId;
        this.question = question;
        this.answer = answer;
        this.selectedConceptIds = selectedConceptIds;
        this.selectedArticleKeys = selectedArticleKeys;
        this.sourceFilePaths = sourceFilePaths;
        this.correctionsJson = correctionsJson;
        this.reviewStatus = reviewStatus;
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
     * 获取命中概念标识。
     *
     * @return 命中概念标识
     */
    public List<String> getSelectedConceptIds() {
        return selectedConceptIds;
    }

    /**
     * 获取命中的文章唯一键。
     *
     * @return 命中的文章唯一键
     */
    public List<String> getSelectedArticleKeys() {
        return selectedArticleKeys;
    }

    /**
     * 获取来源文件路径。
     *
     * @return 来源文件路径
     */
    public List<String> getSourceFilePaths() {
        return sourceFilePaths;
    }

    /**
     * 获取纠错历史 JSON。
     *
     * @return 纠错历史 JSON
     */
    public String getCorrectionsJson() {
        return correctionsJson;
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
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取过期时间。
     *
     * @return 过期时间
     */
    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
