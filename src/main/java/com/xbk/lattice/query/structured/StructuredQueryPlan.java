package com.xbk.lattice.query.structured;

import java.util.List;
import java.util.Map;

/**
 * 结构化查询计划
 *
 * 职责：承载字段过滤、字段投影与聚合类型的确定性执行计划
 *
 * @author xiexu
 */
public class StructuredQueryPlan {

    private final StructuredQueryType queryType;

    private final Map<String, String> filters;

    private final List<String> projections;

    private final String groupByField;

    private final List<Map<String, String>> compareFilters;

    /**
     * 创建结构化查询计划。
     *
     * @param queryType 查询类型
     * @param filters 过滤条件
     * @param projections 投影字段
     */
    public StructuredQueryPlan(
            StructuredQueryType queryType,
            Map<String, String> filters,
            List<String> projections
    ) {
        this(queryType, filters, projections, null, List.of());
    }

    /**
     * 创建结构化查询计划。
     *
     * @param queryType 查询类型
     * @param filters 过滤条件
     * @param projections 投影字段
     * @param groupByField 分组字段
     * @param compareFilters 对比行过滤条件
     */
    public StructuredQueryPlan(
            StructuredQueryType queryType,
            Map<String, String> filters,
            List<String> projections,
            String groupByField,
            List<Map<String, String>> compareFilters
    ) {
        this.queryType = queryType;
        this.filters = filters == null ? Map.of() : filters;
        this.projections = projections == null ? List.of() : projections;
        this.groupByField = groupByField;
        this.compareFilters = compareFilters == null ? List.of() : compareFilters;
    }

    /**
     * 返回查询类型。
     *
     * @return 查询类型
     */
    public StructuredQueryType getQueryType() {
        return queryType;
    }

    /**
     * 返回过滤条件。
     *
     * @return 过滤条件
     */
    public Map<String, String> getFilters() {
        return filters;
    }

    /**
     * 返回投影字段。
     *
     * @return 投影字段
     */
    public List<String> getProjections() {
        return projections;
    }

    /**
     * 返回分组字段。
     *
     * @return 分组字段
     */
    public String getGroupByField() {
        return groupByField;
    }

    /**
     * 返回对比行过滤条件。
     *
     * @return 对比行过滤条件
     */
    public List<Map<String, String>> getCompareFilters() {
        return compareFilters;
    }
}
