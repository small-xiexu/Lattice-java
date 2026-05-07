package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 查询结构化聚合证据响应
 *
 * 职责：承载可复算的分组字段、过滤条件和聚合计数
 *
 * @author xiexu
 */
public class QueryStructuredGroupEvidenceResponse {

    private final String groupByField;

    private final String groupValue;

    private final String normalizedGroupValue;

    private final long count;

    private final Map<String, String> filters;

    /**
     * 创建查询结构化聚合证据响应。
     *
     * @param groupByField 分组字段
     * @param groupValue 分组原始值
     * @param normalizedGroupValue 分组归一化值
     * @param count 聚合行数
     * @param filters 过滤条件
     */
    @JsonCreator
    public QueryStructuredGroupEvidenceResponse(
            @JsonProperty("groupByField") String groupByField,
            @JsonProperty("groupValue") String groupValue,
            @JsonProperty("normalizedGroupValue") String normalizedGroupValue,
            @JsonProperty("count") long count,
            @JsonProperty("filters") Map<String, String> filters
    ) {
        this.groupByField = groupByField;
        this.groupValue = groupValue;
        this.normalizedGroupValue = normalizedGroupValue;
        this.count = count;
        this.filters = filters == null ? Map.of() : filters;
    }

    /**
     * 获取分组字段。
     *
     * @return 分组字段
     */
    public String getGroupByField() {
        return groupByField;
    }

    /**
     * 获取分组原始值。
     *
     * @return 分组原始值
     */
    public String getGroupValue() {
        return groupValue;
    }

    /**
     * 获取分组归一化值。
     *
     * @return 分组归一化值
     */
    public String getNormalizedGroupValue() {
        return normalizedGroupValue;
    }

    /**
     * 获取聚合行数。
     *
     * @return 聚合行数
     */
    public long getCount() {
        return count;
    }

    /**
     * 获取过滤条件。
     *
     * @return 过滤条件
     */
    public Map<String, String> getFilters() {
        return filters;
    }
}
