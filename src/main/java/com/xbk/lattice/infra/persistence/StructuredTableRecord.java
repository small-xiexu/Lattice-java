package com.xbk.lattice.infra.persistence;

/**
 * 结构化表格记录
 *
 * 职责：承载结构化表格主表的持久化视图
 *
 * @author xiexu
 */
public class StructuredTableRecord {

    private final Long id;

    private final Long sourceFileId;

    private final Long sourceId;

    private final String sourcePath;

    private final String tableName;

    private final String sheetName;

    private final String format;

    private final int headerRowNumber;

    private final int rowCount;

    private final int columnCount;

    private final String metadataJson;

    /**
     * 创建结构化表格记录。
     *
     * @param id 主键
     * @param sourceFileId 源文件主键
     * @param sourceId 资料源主键
     * @param sourcePath 源文件路径
     * @param tableName 表格名称
     * @param sheetName sheet 名称
     * @param format 表格格式
     * @param headerRowNumber 表头行号
     * @param rowCount 数据行数
     * @param columnCount 列数
     * @param metadataJson 元数据 JSON
     */
    public StructuredTableRecord(
            Long id,
            Long sourceFileId,
            Long sourceId,
            String sourcePath,
            String tableName,
            String sheetName,
            String format,
            int headerRowNumber,
            int rowCount,
            int columnCount,
            String metadataJson
    ) {
        this.id = id;
        this.sourceFileId = sourceFileId;
        this.sourceId = sourceId;
        this.sourcePath = sourcePath;
        this.tableName = tableName;
        this.sheetName = sheetName;
        this.format = format;
        this.headerRowNumber = headerRowNumber;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
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
     * 返回源文件主键。
     *
     * @return 源文件主键
     */
    public Long getSourceFileId() {
        return sourceFileId;
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
     * 返回源文件路径。
     *
     * @return 源文件路径
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
     * 返回 sheet 名称。
     *
     * @return sheet 名称
     */
    public String getSheetName() {
        return sheetName;
    }

    /**
     * 返回格式。
     *
     * @return 格式
     */
    public String getFormat() {
        return format;
    }

    /**
     * 返回表头行号。
     *
     * @return 表头行号
     */
    public int getHeaderRowNumber() {
        return headerRowNumber;
    }

    /**
     * 返回数据行数。
     *
     * @return 数据行数
     */
    public int getRowCount() {
        return rowCount;
    }

    /**
     * 返回列数。
     *
     * @return 列数
     */
    public int getColumnCount() {
        return columnCount;
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
