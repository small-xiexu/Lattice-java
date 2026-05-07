package com.xbk.lattice.infra.persistence;

/**
 * 结构化表格行记录
 *
 * 职责：承载结构化表格数据行的持久化视图
 *
 * @author xiexu
 */
public class StructuredTableRowRecord {

    private final Long id;

    private final Long tableId;

    private final int rowNumber;

    private final String rowText;

    private final String metadataJson;

    /**
     * 创建结构化表格行记录。
     *
     * @param id 主键
     * @param tableId 表格主键
     * @param rowNumber 原始行号
     * @param rowText 行文本
     * @param metadataJson 元数据 JSON
     */
    public StructuredTableRowRecord(
            Long id,
            Long tableId,
            int rowNumber,
            String rowText,
            String metadataJson
    ) {
        this.id = id;
        this.tableId = tableId;
        this.rowNumber = rowNumber;
        this.rowText = rowText;
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
     * 返回原始行号。
     *
     * @return 原始行号
     */
    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * 返回行文本。
     *
     * @return 行文本
     */
    public String getRowText() {
        return rowText;
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
