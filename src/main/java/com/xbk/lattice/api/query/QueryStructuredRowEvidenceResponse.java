package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 查询结构化行证据响应。
 *
 * <p>承载结构化数据源中一行的完整证据，包括来源文件信息、行号和该行所有单元格的值。
 * 调用方通过这个结构展示单行数据的溯源信息，实现从答案到原始表格行列的可复核闭环。
 *
 * @author xiexu
 */
@Getter
public class QueryStructuredRowEvidenceResponse {

    /**
     * 来源文件路径。
     *
     * <p>该行数据所在的原始文件路径，例如 XLSX 文件、CSV 文件或 YAML 配置文件。
     * 调用方可据此生成可点击的文件链接，实现溯源跳转。</p>
     */
    private final String sourcePath;

    /**
     * 表名。
     *
     * <p>该行数据所在的表名或结构化数据标识。对于 XLSX 文件对应 sheet 所在的工作簿，
     * 对于 CSV 文件对应文件名，对于 YAML 对应配置段路径。</p>
     */
    private final String tableName;

    /**
     * sheet 名称。
     *
     * <p>对于 XLSX 等多 sheet 文件，记录具体 sheet 的名称；对于单表数据源可能为空。</p>
     */
    private final String sheetName;

    /**
     * 原始行号。
     *
     * <p>该行在原始数据源中的行号（从 0 或 1 开始，以数据源约定为准）。
     * 调用方用它帮助用户在原始文件中定位具体的数据行。</p>
     */
    private final int rowNumber;

    /**
     * 单元格证据列表。
     *
     * <p>该行所有列对应的单元格值，每条记录包含列名、列序号、原始值和归一化值。
     * 构造器保证不为 null——传入 null 时归一化为空列表。</p>
     */
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
}
