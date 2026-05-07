package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 查询结构化行证据响应
 *
 * 职责：承载结构化表格中某一行的来源位置与单元格证据
 *
 * @author xiexu
 */
public class QueryStructuredRowEvidenceResponse {

    private final String sourcePath;

    private final String tableName;

    private final String sheetName;

    private final int rowNumber;

    private final List<QueryStructuredCellEvidenceResponse> cells;

    /**
     * 创建查询结构化行证据响应。
     *
     * @param sourcePath 来源路径
     * @param tableName 表名
     * @param sheetName sheet 名称
     * @param rowNumber 原始行号
     * @param cells 单元格证据
     */
    @JsonCreator
    public QueryStructuredRowEvidenceResponse(
            @JsonProperty("sourcePath") String sourcePath,
            @JsonProperty("tableName") String tableName,
            @JsonProperty("sheetName") String sheetName,
            @JsonProperty("rowNumber") int rowNumber,
            @JsonProperty("cells") List<QueryStructuredCellEvidenceResponse> cells
    ) {
        this.sourcePath = sourcePath;
        this.tableName = tableName;
        this.sheetName = sheetName;
        this.rowNumber = rowNumber;
        this.cells = cells == null ? List.of() : cells;
    }

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * 获取表名。
     *
     * @return 表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 获取 sheet 名称。
     *
     * @return sheet 名称
     */
    public String getSheetName() {
        return sheetName;
    }

    /**
     * 获取原始行号。
     *
     * @return 原始行号
     */
    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * 获取单元格证据。
     *
     * @return 单元格证据
     */
    public List<QueryStructuredCellEvidenceResponse> getCells() {
        return cells;
    }
}
