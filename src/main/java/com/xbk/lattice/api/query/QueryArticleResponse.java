package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 查询文章响应
 *
 * 职责：承载命中文章的最小摘要信息
 *
 * @author xiexu
 */
public class QueryArticleResponse {

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    /**
     * 创建查询文章响应。
     *
     * @param conceptId 概念标识
     * @param title 标题
     */
    public QueryArticleResponse(String conceptId, String title) {
        this(null, null, conceptId, title);
    }

    /**
     * 创建查询文章响应。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     */
    @JsonCreator
    public QueryArticleResponse(
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
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
