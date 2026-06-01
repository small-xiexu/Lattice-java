package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 查询结构化证据响应。
 *
 * <p>承载结构化表格问答的完整证据，包括逐行证据和按维度聚合的分组统计。
 * 当查询涉及数据库表、XLSX/CSV 表格、YAML 结构化配置等数据源时，
 * 这个结构向调用方提供可复核的行/列/单元格详情和聚合概览。
 *
 * @author xiexu
 */
@Getter
public class QueryStructuredEvidenceResponse {

    /**
     * 查询类型。
     *
     * <p>标识本次结构化查询的类型，例如 structured_query（结构化查值）、
     * table_lookup（表格查找）等。调用方据此决定如何渲染证据面板——
     * 不同类型对应不同的展示布局和交互模式。</p>
     */
    private final String queryType;

    /**
     * 行级证据列表。
     *
     * <p>每条记录对应结构化数据源中的一行原始数据，包含来源路径、表名、行号和所有单元格值。
     * 调用方通过这个列表展示查询命中的具体数据行，用户可逐行复核。构造器保证不为 null。</p>
     */
    private final List<QueryStructuredRowEvidenceResponse> rows;

    /**
     * 分组聚合证据列表。
     *
     * <p>按指定字段和过滤条件对行数据进行分组统计的结果。每条记录包含分组字段值、
     * 归一化值和聚合计数，帮助调用方展示数据分布概览。构造器保证不为 null。</p>
     */
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
}
