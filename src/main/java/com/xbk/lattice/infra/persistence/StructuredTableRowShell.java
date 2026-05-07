package com.xbk.lattice.infra.persistence;

/**
 * 结构化表格行查询壳
 *
 * 职责：承载表格记录与行记录的组合查询结果
 *
 * @author xiexu
 */
public class StructuredTableRowShell {

    private final StructuredTableRecord tableRecord;

    private final StructuredTableRowRecord rowRecord;

    /**
     * 创建结构化表格行查询壳。
     *
     * @param tableRecord 表格记录
     * @param rowRecord 行记录
     */
    public StructuredTableRowShell(StructuredTableRecord tableRecord, StructuredTableRowRecord rowRecord) {
        this.tableRecord = tableRecord;
        this.rowRecord = rowRecord;
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
}
