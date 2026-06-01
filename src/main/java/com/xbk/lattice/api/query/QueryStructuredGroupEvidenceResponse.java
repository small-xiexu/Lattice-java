package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;

/**
 * 查询结构化聚合证据响应。
 *
 * <p>承载按指定字段分组统计后的单条聚合结果，包括分组字段、分组值、
 * 归一化值和过滤条件。调用方通过这个结构展示数据的分布概览和维度聚合信息。
 *
 * @author xiexu
 */
@Getter
public class QueryStructuredGroupEvidenceResponse {

    /**
     * 分组字段名。
     *
     * <p>按哪个字段进行分组——例如按产品类型、状态、部门等字段维度聚合。</p>
     */
    private final String groupByField;

    /**
     * 分组原始值。
     *
     * <p>分组字段在原始数据中的实际取值（未经归一化处理）。</p>
     */
    private final String groupValue;

    /**
     * 分组归一化值。
     *
     * <p>对分组原始值做标准化处理后的值，用于跨数据源的一致性对比。
     * 归一化可能包括去空格、统一大小写、单位换算等。</p>
     */
    private final String normalizedGroupValue;

    /**
     * 聚合行数。
     *
     * <p>该分组下匹配的行总数，帮助调用方判断该分组的规模和数据代表性。</p>
     */
    private final long count;

    /**
     * 过滤条件。
     *
     * <p>本次分组统计所应用的过滤条件集合（key 为字段名，value 为过滤值）。
     * 构造器保证不为 null——传入 null 时归一化为空 Map。</p>
     */
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
}
