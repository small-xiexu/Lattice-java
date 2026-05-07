package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧答案反馈创建请求
 *
 * 职责：承载问答页提交的结果反馈上下文
 *
 * @author xiexu
 */
public class AdminQueryFeedbackCreateRequest {

    private String queryId;

    private String question;

    private String answerSummary;

    private String feedbackType;

    private String comment;

    private List<String> articleKeys;

    private List<String> sourcePaths;

    private String reportedBy;

    /**
     * 获取查询 ID。
     *
     * @return 查询 ID
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 设置查询 ID。
     *
     * @param queryId 查询 ID
     */
    public void setQueryId(String queryId) {
        this.queryId = queryId;
    }

    /**
     * 获取用户问题。
     *
     * @return 用户问题
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 设置用户问题。
     *
     * @param question 用户问题
     */
    public void setQuestion(String question) {
        this.question = question;
    }

    /**
     * 获取答案摘要。
     *
     * @return 答案摘要
     */
    public String getAnswerSummary() {
        return answerSummary;
    }

    /**
     * 设置答案摘要。
     *
     * @param answerSummary 答案摘要
     */
    public void setAnswerSummary(String answerSummary) {
        this.answerSummary = answerSummary;
    }

    /**
     * 获取反馈类型。
     *
     * @return 反馈类型
     */
    public String getFeedbackType() {
        return feedbackType;
    }

    /**
     * 设置反馈类型。
     *
     * @param feedbackType 反馈类型
     */
    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    /**
     * 获取反馈说明。
     *
     * @return 反馈说明
     */
    public String getComment() {
        return comment;
    }

    /**
     * 设置反馈说明。
     *
     * @param comment 反馈说明
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * 获取关联文章唯一键。
     *
     * @return 关联文章唯一键
     */
    public List<String> getArticleKeys() {
        return articleKeys;
    }

    /**
     * 设置关联文章唯一键。
     *
     * @param articleKeys 关联文章唯一键
     */
    public void setArticleKeys(List<String> articleKeys) {
        this.articleKeys = articleKeys;
    }

    /**
     * 获取关联来源路径。
     *
     * @return 关联来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 设置关联来源路径。
     *
     * @param sourcePaths 关联来源路径
     */
    public void setSourcePaths(List<String> sourcePaths) {
        this.sourcePaths = sourcePaths;
    }

    /**
     * 获取反馈提交人。
     *
     * @return 反馈提交人
     */
    public String getReportedBy() {
        return reportedBy;
    }

    /**
     * 设置反馈提交人。
     *
     * @param reportedBy 反馈提交人
     */
    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }
}
