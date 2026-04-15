package com.xbk.lattice.api.query;

/**
 * 查询文章响应
 *
 * 职责：承载命中文章的最小摘要信息
 *
 * @author xiexu
 */
public class QueryArticleResponse {

    private final String conceptId;

    private final String title;

    /**
     * 创建查询文章响应。
     *
     * @param conceptId 概念标识
     * @param title 标题
     */
    public QueryArticleResponse(String conceptId, String title) {
        this.conceptId = conceptId;
        this.title = title;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }
}
