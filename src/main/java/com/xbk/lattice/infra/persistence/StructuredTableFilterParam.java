package com.xbk.lattice.infra.persistence;

/**
 * 结构化表格过滤参数
 *
 * 职责：承载列名归一化值与单元格归一化值
 *
 * @author xiexu
 */
public class StructuredTableFilterParam {

    private final String columnNameNorm;

    private final String normalizedValue;

    /**
     * 创建结构化表格过滤参数。
     *
     * @param columnNameNorm 归一化列名
     * @param normalizedValue 归一化单元格值
     */
    public StructuredTableFilterParam(String columnNameNorm, String normalizedValue) {
        this.columnNameNorm = columnNameNorm;
        this.normalizedValue = normalizedValue;
    }

    /**
     * 返回归一化列名。
     *
     * @return 归一化列名
     */
    public String getColumnNameNorm() {
        return columnNameNorm;
    }

    /**
     * 返回归一化单元格值。
     *
     * @return 归一化单元格值
     */
    public String getNormalizedValue() {
        return normalizedValue;
    }
}
