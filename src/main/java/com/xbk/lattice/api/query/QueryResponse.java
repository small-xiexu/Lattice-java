package com.xbk.lattice.api.query;

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

    /**
     * 创建查询响应。
     *
     * @param answer 答案
     * @param sources 来源列表
     * @param articles 命中文章列表
     */
    public QueryResponse(String answer, List<QuerySourceResponse> sources, List<QueryArticleResponse> articles) {
        this.answer = answer;
        this.sources = sources;
        this.articles = articles;
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
}
