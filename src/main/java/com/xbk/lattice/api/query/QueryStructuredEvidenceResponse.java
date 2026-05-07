package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 查询结构化证据响应
 *
 * 职责：承载结构化表格问答可复核的行、列、单元格与聚合证据
 *
 * @author xiexu
 */
public class QueryStructuredEvidenceResponse {

    private final String queryType;

    private final List<QueryStructuredRowEvidenceResponse> rows;

    private final List<QueryStructuredGroupEvidenceResponse> groups;

    /**
     * 创建查询结构化证据响应。
     *
     * @param queryType 查询类型
     * @param rows 行证据
     * @param groups 聚合证据
     */
    @JsonCreator
    public QueryStructuredEvidenceResponse(
            @JsonProperty("queryType") String queryType,
            @JsonProperty("rows") List<QueryStructuredRowEvidenceResponse> rows,
            @JsonProperty("groups") List<QueryStructuredGroupEvidenceResponse> groups
    ) {
        this.queryType = queryType;
        this.rows = rows == null ? List.of() : rows;
        this.groups = groups == null ? List.of() : groups;
    }

    /**
     * 获取查询类型。
     *
     * @return 查询类型
     */
    public String getQueryType() {
        return queryType;
    }

    /**
     * 获取行证据。
     *
     * @return 行证据
     */
    public List<QueryStructuredRowEvidenceResponse> getRows() {
        return rows;
    }

    /**
     * 获取聚合证据。
     *
     * @return 聚合证据
     */
    public List<QueryStructuredGroupEvidenceResponse> getGroups() {
        return groups;
    }
}
