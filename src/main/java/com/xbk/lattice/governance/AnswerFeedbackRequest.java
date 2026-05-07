package com.xbk.lattice.governance;

import java.util.List;

/**
 * 答案反馈请求
 *
 * 职责：承载问答结果反馈创建入参
 *
 * @author xiexu
 */
public class AnswerFeedbackRequest {

    private final String queryId;

    private final String question;

    private final String answerSummary;

    private final String feedbackType;

    private final String comment;

    private final List<String> articleKeys;

    private final List<String> sourcePaths;

    private final String reportedBy;

    /**
     * 创建答案反馈请求。
     *
     * @param queryId 查询 ID
     * @param question 用户问题
     * @param answerSummary 答案摘要
     * @param feedbackType 反馈类型
     * @param comment 反馈说明
     * @param articleKeys 关联文章唯一键
     * @param sourcePaths 关联来源路径
     * @param reportedBy 反馈提交人
     */
    public AnswerFeedbackRequest(
            String queryId,
            String question,
            String answerSummary,
            String feedbackType,
            String comment,
            List<String> articleKeys,
            List<String> sourcePaths,
            String reportedBy
    ) {
        this.queryId = queryId;
        this.question = question;
        this.answerSummary = answerSummary;
        this.feedbackType = feedbackType;
        this.comment = comment;
        this.articleKeys = articleKeys == null ? List.of() : List.copyOf(articleKeys);
        this.sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        this.reportedBy = reportedBy;
    }

    /**
     * 获取查询 ID。
     *
     * @return 查询 ID
     */
    public String getQueryId() {
        return queryId;
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
     * 获取答案摘要。
     *
     * @return 答案摘要
     */
    public String getAnswerSummary() {
        return answerSummary;
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
     * 获取反馈说明。
     *
     * @return 反馈说明
     */
    public String getComment() {
        return comment;
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
     * 获取关联来源路径。
     *
     * @return 关联来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取反馈提交人。
     *
     * @return 反馈提交人
     */
    public String getReportedBy() {
        return reportedBy;
    }
}
