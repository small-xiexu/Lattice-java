package com.xbk.lattice.infra.persistence;

/**
 * 结构化表格分组计数记录
 *
 * 职责：承载某个列值对应的行数聚合结果
 *
 * @author xiexu
 */
public class StructuredTableGroupCountRecord {

    private final Long sourceId;

    private final Long tableId;

    private final String sourcePath;

    private final String tableName;

    private final String sheetName;

    private final String columnName;

    private final String cellValue;

    private final String normalizedValue;

    private final long count;

    /**
     * 创建结构化表格分组计数记录。
     *
     * @param columnName 列名
     * @param cellValue 单元格原始值
     * @param normalizedValue 归一化值
     * @param count 行数
     */
    public StructuredTableGroupCountRecord(
            String columnName,
            String cellValue,
            String normalizedValue,
            long count
    ) {
        this(null, null, null, null, null, columnName, cellValue, normalizedValue, count);
    }

    /**
     * 创建结构化表格分组计数记录。
     *
     * @param sourceId 资料源主键
     * @param tableId 表格主键
     * @param sourcePath 来源路径
     * @param tableName 表格名称
     * @param sheetName Sheet 名称
     * @param columnName 列名
     * @param cellValue 单元格原始值
     * @param normalizedValue 归一化值
     * @param count 行数
     */
    public StructuredTableGroupCountRecord(
            Long sourceId,
            Long tableId,
            String sourcePath,
            String tableName,
            String sheetName,
            String columnName,
            String cellValue,
            String normalizedValue,
            long count
    ) {
        this.sourceId = sourceId;
        this.tableId = tableId;
        this.sourcePath = sourcePath;
        this.tableName = tableName;
        this.sheetName = sheetName;
        this.columnName = columnName;
        this.cellValue = cellValue;
        this.normalizedValue = normalizedValue;
        this.count = count;
    }

    /**
     * 返回资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
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
     * 返回来源路径。
     *
     * @return 来源路径
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * 返回表格名称。
     *
     * @return 表格名称
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 返回 Sheet 名称。
     *
     * @return Sheet 名称
     */
    public String getSheetName() {
        return sheetName;
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
     * 返回行数。
     *
     * @return 行数
     */
    public long getCount() {
        return count;
    }
}
