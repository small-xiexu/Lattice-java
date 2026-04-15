package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 查询来源响应
 *
 * 职责：承载最小查询结果中的来源信息
 *
 * @author xiexu
 */
public class QuerySourceResponse {

    private final String conceptId;

    private final String title;

    private final List<String> sourcePaths;

    /**
     * 创建查询来源响应。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     */
    @JsonCreator
    public QuerySourceResponse(
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("sourcePaths") List<String> sourcePaths
    ) {
        this.conceptId = conceptId;
        this.title = title;
        this.sourcePaths = sourcePaths;
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

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }
}
