package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 查询响应
 *
 * 职责：承载最小查询闭环的返回结果
 *
 * @author xiexu
 */
public class QueryResponse {

    private final String answer;

    private final List<QuerySourceResponse> sources;

    private final List<QueryArticleResponse> articles;

    private final String queryId;

    private final String reviewStatus;

    /**
     * 创建查询响应。
     *
     * @param answer 答案
     * @param sources 来源列表
     * @param articles 命中文章列表
     */
    public QueryResponse(String answer, List<QuerySourceResponse> sources, List<QueryArticleResponse> articles) {
        this(answer, sources, articles, null, null);
    }

    /**
     * 创建查询响应。
     *
     * @param answer 答案
     * @param sources 来源列表
     * @param articles 命中文章列表
     * @param queryId 待确认查询标识
     * @param reviewStatus 审查状态
     */
    @JsonCreator
    public QueryResponse(
            @JsonProperty("answer") String answer,
            @JsonProperty("sources") List<QuerySourceResponse> sources,
            @JsonProperty("articles") List<QueryArticleResponse> articles,
            @JsonProperty("queryId") String queryId,
            @JsonProperty("reviewStatus") String reviewStatus
    ) {
        this.answer = answer;
        this.sources = sources;
        this.articles = articles;
        this.queryId = queryId;
        this.reviewStatus = reviewStatus;
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
     * 获取来源列表。
     *
     * @return 来源列表
     */
    public List<QuerySourceResponse> getSources() {
        return sources;
    }

    /**
     * 获取命中文章列表。
     *
     * @return 命中文章列表
     */
    public List<QueryArticleResponse> getArticles() {
        return articles;
    }

    /**
     * 获取待确认查询标识。
     *
     * @return 待确认查询标识
     */
    public String getQueryId() {
        return queryId;
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
