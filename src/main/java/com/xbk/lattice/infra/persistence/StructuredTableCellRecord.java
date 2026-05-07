package com.xbk.lattice.infra.persistence;

/**
 * 结构化表格单元格记录
 *
 * 职责：承载结构化表格单元格的持久化视图
 *
 * @author xiexu
 */
public class StructuredTableCellRecord {

    private final Long id;

    private final Long tableId;

    private final Long rowId;

    private final int rowNumber;

    private final int columnIndex;

    private final String columnName;

    private final String cellValue;

    private final String normalizedValue;

    private final String valueType;

    private final String metadataJson;

    /**
     * 创建结构化表格单元格记录。
     *
     * @param id 主键
     * @param tableId 表格主键
     * @param rowId 行主键
     * @param rowNumber 原始行号
     * @param columnIndex 原始列序号
     * @param columnName 列名
     * @param cellValue 单元格原始值
     * @param normalizedValue 归一化值
     * @param valueType 值类型
     * @param metadataJson 元数据 JSON
     */
    public StructuredTableCellRecord(
            Long id,
            Long tableId,
            Long rowId,
            int rowNumber,
            int columnIndex,
            String columnName,
            String cellValue,
            String normalizedValue,
            String valueType,
            String metadataJson
    ) {
        this.id = id;
        this.tableId = tableId;
        this.rowId = rowId;
        this.rowNumber = rowNumber;
        this.columnIndex = columnIndex;
        this.columnName = columnName;
        this.cellValue = cellValue;
        this.normalizedValue = normalizedValue;
        this.valueType = valueType;
        this.metadataJson = metadataJson;
    }

    /**
     * 返回主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 返回表格主键。
     *
     * @return 表格主键
     */
    public Long getTableId() {
        return tableId;
    }

    /**
     * 返回行主键。
     *
     * @return 行主键
     */
    public Long getRowId() {
        return rowId;
    }

    /**
     * 返回原始行号。
     *
     * @return 原始行号
     */
    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * 返回原始列序号。
     *
     * @return 原始列序号
     */
    public int getColumnIndex() {
        return columnIndex;
    }

    /**
     * 返回列名。
     *
     * @return 列名
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * 返回单元格原始值。
     *
     * @return 单元格原始值
     */
    public String getCellValue() {
        return cellValue;
    }

    /**
     * 返回归一化值。
     *
     * @return 归一化值
     */
    public String getNormalizedValue() {
        return normalizedValue;
    }

    /**
     * 返回值类型。
     *
     * @return 值类型
     */
    public String getValueType() {
        return valueType;
    }

    /**
     * 返回元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }
}
