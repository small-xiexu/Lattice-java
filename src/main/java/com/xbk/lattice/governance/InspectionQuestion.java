package com.xbk.lattice.governance;

import java.util.List;

/**
 * Inspection 问题项
 *
 * 职责：表示一条待人工确认的问题清单项
 *
 * @author xiexu
 */
public class InspectionQuestion {

    private final String id;

    private final String type;

    private final String question;

    private final String prompt;

    private final String suggestedAnswer;

    private final List<String> sourcePaths;

    private final String reviewStatus;

    private final String createdAt;

    private final String expiresAt;

    /**
     * 创建 inspection 问题项。
     *
     * @param id 问题标识
     * @param type 问题类型
     * @param question 原始问题
     * @param prompt 人工确认提示
     * @param suggestedAnswer 当前建议答案
     * @param sourcePaths 来源路径
     * @param reviewStatus 审查状态
     * @param createdAt 创建时间
     * @param expiresAt 过期时间
     */
    public InspectionQuestion(
            String id,
            String type,
            String question,
            String prompt,
            String suggestedAnswer,
            List<String> sourcePaths,
            String reviewStatus,
            String createdAt,
            String expiresAt
    ) {
        this.id = id;
        this.type = type;
        this.question = question;
        this.prompt = prompt;
        this.suggestedAnswer = suggestedAnswer;
        this.sourcePaths = sourcePaths;
        this.reviewStatus = reviewStatus;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getQuestion() {
        return question;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSuggestedAnswer() {
        return suggestedAnswer;
    }

    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }
}
