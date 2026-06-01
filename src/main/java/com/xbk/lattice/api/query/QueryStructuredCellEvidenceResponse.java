package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 查询结构化单元格证据响应。
 *
 * <p>承载结构化数据源中单个单元格的证据，包括列名、列序号、原始值和归一化值。
 * 这是证据层级的最细粒度——调用方通过单元格级别的证据实现对数据表中每个字段值的精确追溯。
 *
 * @author xiexu
 */
@Getter
public class QueryStructuredCellEvidenceResponse {

    /**
     * 列名。
     *
     * <p>单元格所属列的标题或字段名，例如"存储条件""max_borrow_days""端口号"等。
     * 调用方用它标识单元格的语义含义。</p>
     */
    private final String columnName;

    /**
     * 原始列序号。
     *
     * <p>该列在原始数据源中的列序号（从 0 开始）。调用方可以用它在原始表格中定位列位置。</p>
     */
    private final int columnIndex;

    /**
     * 原始单元格值。
     *
     * <p>数据源中该单元格的原始文本值（未经归一化处理）。调用方展示实际存储的原始数据时使用。</p>
     */
    private final String cellValue;

    /**
     * 归一化值。
     *
     * <p>对原始单元格值做标准化处理后的值，例如去除单位、统一格式、数值标准化等。
     * 归一化后的值更利于程序化比较和计算。</p>
     */
    private final String normalizedValue;

    /**
     * 证据角色。
     *
     * <p>标识该单元格在本次查询答案中扮演的角色——例如 primary（主证据）、
     * context（上下文辅助）、reference（引用来源）等。
     * 调用方据此决定该单元格在证据面板中的展示优先级和样式。</p>
     */
    private final String role;

    /**
     * 创建查询结构化单元格证据响应。
     *
     * @param columnName 列名
     * @param columnIndex 原始列序号
     * @param cellValue 原始单元格值
     * @param normalizedValue 归一化值
     * @param role 证据角色
     */
    @JsonCreator
    public QueryStructuredCellEvidenceResponse(
            @JsonProperty("columnName") String columnName,
            @JsonProperty("columnIndex") int columnIndex,
            @JsonProperty("cellValue") String cellValue,
            @JsonProperty("normalizedValue") String normalizedValue,
            @JsonProperty("role") String role
    ) {
        this.columnName = columnName;
        this.columnIndex = columnIndex;
        this.cellValue = cellValue;
        this.normalizedValue = normalizedValue;
        this.role = role;
    }
}
