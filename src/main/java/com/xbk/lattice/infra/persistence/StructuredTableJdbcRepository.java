package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.mapper.StructuredTableMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化表格 JDBC 仓储
 *
 * 职责：把解析阶段产出的通用表格结构落盘为表、行、单元格事实
 *
 * @author xiexu
 */
@Repository
public class StructuredTableJdbcRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final StructuredTableSchemaProfiler SCHEMA_PROFILER = new StructuredTableSchemaProfiler();

    private final StructuredTableMapper structuredTableMapper;

    /**
     * 创建结构化表格 JDBC 仓储。
     *
     * @param structuredTableMapper 结构化表格 Mapper
     */
    public StructuredTableJdbcRepository(StructuredTableMapper structuredTableMapper) {
        this.structuredTableMapper = structuredTableMapper;
    }

    /**
     * 替换指定源文件的全部结构化表格。
     *
     * @param sourceFileRecord 源文件记录
     */
    public void replaceTablesFromSourceFile(SourceFileRecord sourceFileRecord) {
        if (structuredTableMapper == null || sourceFileRecord == null || sourceFileRecord.getId() == null) {
            return;
        }
        structuredTableMapper.deleteBySourceFileId(sourceFileRecord.getId());
        String structuredContentJson = extractStructuredContentJson(sourceFileRecord.getMetadataJson());
        if (!StringUtils.hasText(structuredContentJson)) {
            return;
        }
        JsonNode rootNode = readTree(structuredContentJson);
        if (rootNode == null || !"structured_tables".equals(rootNode.path("contentType").asText())) {
            return;
        }
        JsonNode tablesNode = rootNode.path("tables");
        if (!tablesNode.isArray()) {
            return;
        }
        for (JsonNode tableNode : tablesNode) {
            persistTable(sourceFileRecord, tableNode);
        }
    }

    /**
     * 查询指定源文件下的结构化表。
     *
     * @param sourceFileId 源文件主键
     * @return 结构化表记录
     */
    public List<StructuredTableRecord> findTablesBySourceFileId(Long sourceFileId) {
        if (structuredTableMapper == null || sourceFileId == null) {
            return List.of();
        }
        return structuredTableMapper.findTablesBySourceFileId(sourceFileId);
    }

    /**
     * 查询指定表格下的结构化行。
     *
     * @param tableId 表格主键
     * @return 结构化行记录
     */
    public List<StructuredTableRowRecord> findRowsByTableId(Long tableId) {
        if (structuredTableMapper == null || tableId == null) {
            return List.of();
        }
        return structuredTableMapper.findRowsByTableId(tableId);
    }

    /**
     * 查询指定表格下的结构化单元格。
     *
     * @param tableId 表格主键
     * @return 结构化单元格记录
     */
    public List<StructuredTableCellRecord> findCellsByTableId(Long tableId) {
        if (structuredTableMapper == null || tableId == null) {
            return List.of();
        }
        return structuredTableMapper.findCellsByTableId(tableId);
    }

    /**
     * 按列名和值等值查询单元格。
     *
     * @param sourceFileId 源文件主键
     * @param columnName 列名
     * @param normalizedValue 归一化值
     * @return 命中单元格
     */
    public List<StructuredTableCellRecord> findCellsByColumnValue(
            Long sourceFileId,
            String columnName,
            String normalizedValue
    ) {
        if (structuredTableMapper == null || sourceFileId == null) {
            return List.of();
        }
        return structuredTableMapper.findCellsByColumnValue(sourceFileId, columnName, normalizedValue);
    }

    /**
     * 按任意列值等值过滤结构化表格行。
     *
     * @param filters 列名到归一化值的过滤条件
     * @param limit 返回上限
     * @return 行证据列表
     */
    public List<StructuredTableRowEvidence> findRowsByFilters(Map<String, String> filters, int limit) {
        if (structuredTableMapper == null || filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<StructuredTableFilterParam> normalizedFilters = normalizedFilters(filters);
        if (normalizedFilters.isEmpty()) {
            return List.of();
        }
        List<StructuredTableRowShell> rowShells = structuredTableMapper.findRowsByFilters(
                normalizedFilters,
                safeLimit(limit)
        );
        return attachCells(rowShells);
    }

    /**
     * 统计满足过滤条件的行数。
     *
     * @param filters 列名到归一化值的过滤条件
     * @return 行数
     */
    public long countRowsByFilters(Map<String, String> filters) {
        if (structuredTableMapper == null || filters == null || filters.isEmpty()) {
            return 0L;
        }
        List<StructuredTableFilterParam> normalizedFilters = normalizedFilters(filters);
        if (normalizedFilters.isEmpty()) {
            return 0L;
        }
        return structuredTableMapper.countRowsByFilters(normalizedFilters);
    }

    /**
     * 按指定列进行分组计数。
     *
     * @param filters 列名到归一化值的过滤条件
     * @param groupByField 分组字段
     * @param limit 返回上限
     * @return 分组计数结果
     */
    public List<StructuredTableGroupCountRecord> groupCountByField(
            Map<String, String> filters,
            String groupByField,
            int limit
    ) {
        if (structuredTableMapper == null || !StringUtils.hasText(groupByField)) {
            return List.of();
        }
        String groupByFieldNorm = groupByField.trim().toLowerCase();
        return structuredTableMapper.groupCountByField(
                normalizedFilters(filters),
                groupByFieldNorm,
                safeLimit(limit)
        );
    }

    private void persistTable(SourceFileRecord sourceFileRecord, JsonNode tableNode) {
        Long tableId = insertTable(sourceFileRecord, tableNode);
        JsonNode rowsNode = tableNode.path("rows");
        if (!rowsNode.isArray()) {
            return;
        }
        for (JsonNode rowNode : rowsNode) {
            Long rowId = insertRow(tableId, rowNode);
            persistCells(tableId, rowId, rowNode);
        }
    }

    private Long insertTable(SourceFileRecord sourceFileRecord, JsonNode tableNode) {
        String tableName = tableNode.path("tableName").asText("");
        String sheetName = tableNode.path("sheetName").asText("");
        String format = tableNode.path("format").asText(sourceFileRecord.getFormat());
        String sourcePathNorm = sourceFileRecord.getRelativePath().toLowerCase();
        String searchText = String.join(" ", sourceFileRecord.getRelativePath(), tableName, sheetName);
        String metadataJson = buildTableMetadataJson(tableNode);
        StructuredTableRecord record = new StructuredTableRecord(
                null,
                sourceFileRecord.getId(),
                sourceFileRecord.getSourceId(),
                sourceFileRecord.getRelativePath(),
                tableName,
                sheetName,
                format,
                tableNode.path("headerRowNumber").asInt(1),
                tableNode.path("rowCount").asInt(0),
                tableNode.path("columnCount").asInt(0),
                metadataJson
        );
        return structuredTableMapper.insertTable(record, sourcePathNorm, searchText);
    }

    private Long insertRow(Long tableId, JsonNode rowNode) {
        String rowText = rowNode.path("rowText").asText("");
        StructuredTableRowRecord record = new StructuredTableRowRecord(
                null,
                tableId,
                rowNode.path("rowNumber").asInt(0),
                rowText,
                "{}"
        );
        return structuredTableMapper.insertRow(record, rowText);
    }

    private void persistCells(Long tableId, Long rowId, JsonNode rowNode) {
        JsonNode cellsNode = rowNode.path("cells");
        if (!cellsNode.isArray()) {
            return;
        }
        int rowNumber = rowNode.path("rowNumber").asInt(0);
        for (JsonNode cellNode : cellsNode) {
            String columnName = cellNode.path("columnName").asText("");
            String cellValue = cellNode.path("cellValue").asText("");
            String normalizedValue = cellNode.path("normalizedValue").asText("");
            String valueType = cellNode.path("valueType").asText("text");
            String searchText = String.join(" ", columnName, cellValue, normalizedValue);
            StructuredTableCellRecord record = new StructuredTableCellRecord(
                    null,
                    tableId,
                    rowId,
                    rowNumber,
                    cellNode.path("columnIndex").asInt(0),
                    columnName,
                    cellValue,
                    normalizedValue,
                    valueType,
                    "{}"
            );
            structuredTableMapper.insertCell(record, columnName.toLowerCase(), searchText);
        }
    }

    /**
     * 构建表级元数据 JSON。
     *
     * @param tableNode 表格节点
     * @return 表级元数据 JSON
     */
    private String buildTableMetadataJson(JsonNode tableNode) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("schemaProfile", SCHEMA_PROFILER.profile(tableNode));
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (Exception ex) {
            return "{}";
        }
    }

    private String extractStructuredContentJson(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return "";
        }
        JsonNode metadataNode = readTree(metadataJson);
        if (metadataNode == null) {
            return "";
        }
        return metadataNode.path("structuredContentJson").asText("");
    }

    private JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        }
        catch (Exception ex) {
            return null;
        }
    }

    private List<StructuredTableRowEvidence> attachCells(List<StructuredTableRowShell> rowShells) {
        if (rowShells.isEmpty()) {
            return List.of();
        }
        List<Long> rowIds = new ArrayList<Long>();
        Map<Long, StructuredTableRowShell> rowShellsById = new LinkedHashMap<Long, StructuredTableRowShell>();
        for (StructuredTableRowShell rowShell : rowShells) {
            Long rowId = rowShell.getRowRecord().getId();
            rowIds.add(rowId);
            rowShellsById.put(rowId, rowShell);
        }
        List<StructuredTableCellRecord> cells = structuredTableMapper.findCellsByRowIds(rowIds);
        Map<Long, List<StructuredTableCellRecord>> cellsByRowId = new LinkedHashMap<Long, List<StructuredTableCellRecord>>();
        for (StructuredTableCellRecord cell : cells) {
            cellsByRowId.computeIfAbsent(cell.getRowId(), ignored -> new ArrayList<StructuredTableCellRecord>())
                    .add(cell);
        }
        List<StructuredTableRowEvidence> rowEvidences = new ArrayList<StructuredTableRowEvidence>();
        for (StructuredTableRowShell rowShell : rowShells) {
            List<StructuredTableCellRecord> rowCells = cellsByRowId.getOrDefault(
                    rowShell.getRowRecord().getId(),
                    List.of()
            );
            rowEvidences.add(new StructuredTableRowEvidence(
                    rowShell.getTableRecord(),
                    rowShell.getRowRecord(),
                    rowCells
            ));
        }
        return rowEvidences;
    }

    private List<StructuredTableFilterParam> normalizedFilters(Map<String, String> filters) {
        List<StructuredTableFilterParam> normalizedFilters = new ArrayList<StructuredTableFilterParam>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            normalizedFilters.add(new StructuredTableFilterParam(
                    entry.getKey().trim().toLowerCase(),
                    entry.getValue().trim().toLowerCase()
            ));
        }
        return normalizedFilters;
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 200);
    }
}
