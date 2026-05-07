package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 查询结构化单元格证据响应
 *
 * 职责：承载可回指到具体列与单元格值的证据
 *
 * @author xiexu
 */
public class QueryStructuredCellEvidenceResponse {

    private final String columnName;

    private final int columnIndex;

    private final String cellValue;

    private final String normalizedValue;

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

    /**
     * 获取列名。
     *
     * @return 列名
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * 获取原始列序号。
     *
     * @return 原始列序号
     */
    public int getColumnIndex() {
        return columnIndex;
    }

    /**
     * 获取原始单元格值。
     *
     * @return 原始单元格值
     */
    public String getCellValue() {
        return cellValue;
    }

    /**
     * 获取归一化值。
     *
     * @return 归一化值
     */
    public String getNormalizedValue() {
        return normalizedValue;
    }

    /**
     * 获取证据角色。
     *
     * @return 证据角色
     */
    public String getRole() {
        return role;
    }
}
