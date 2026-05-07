package com.xbk.lattice.infra.persistence;

import java.util.List;

/**
 * 结构化表格行证据
 *
 * 职责：承载查询侧需要的表格、行与单元格组合视图
 *
 * @author xiexu
 */
public class StructuredTableRowEvidence {

    private final StructuredTableRecord tableRecord;

    private final StructuredTableRowRecord rowRecord;

    private final List<StructuredTableCellRecord> cellRecords;

    /**
     * 创建结构化表格行证据。
     *
     * @param tableRecord 表格记录
     * @param rowRecord 行记录
     * @param cellRecords 单元格记录
     */
    public StructuredTableRowEvidence(
            StructuredTableRecord tableRecord,
            StructuredTableRowRecord rowRecord,
            List<StructuredTableCellRecord> cellRecords
    ) {
        this.tableRecord = tableRecord;
        this.rowRecord = rowRecord;
        this.cellRecords = cellRecords == null ? List.of() : cellRecords;
    }

    /**
     * 返回表格记录。
     *
     * @return 表格记录
     */
    public StructuredTableRecord getTableRecord() {
        return tableRecord;
    }

    /**
     * 返回行记录。
     *
     * @return 行记录
     */
    public StructuredTableRowRecord getRowRecord() {
        return rowRecord;
    }

    /**
     * 返回单元格记录。
     *
     * @return 单元格记录
     */
    public List<StructuredTableCellRecord> getCellRecords() {
        return cellRecords;
    }
}
