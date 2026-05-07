package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 结构化表格内容构建器
 *
 * 职责：把通用二维表转换为解析层可传递的结构化 JSON
 *
 * @author xiexu
 */
public class StructuredTableContentBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_CELL_TEXT_LENGTH = 5000;

    /**
     * 构建结构化表格 JSON。
     *
     * @param tables 表格列表
     * @return 结构化表格 JSON；无有效表格时返回空字符串
     */
    public String buildJson(List<TableContent> tables) {
        List<Map<String, Object>> tableNodes = new ArrayList<Map<String, Object>>();
        if (tables == null || tables.isEmpty()) {
            return "";
        }
        for (TableContent table : tables) {
            Map<String, Object> tableNode = buildTableNode(table);
            if (!tableNode.isEmpty()) {
                tableNodes.add(tableNode);
            }
        }
        if (tableNodes.isEmpty()) {
            return "";
        }
        Map<String, Object> rootNode = new LinkedHashMap<String, Object>();
        rootNode.put("contentType", "structured_tables");
        rootNode.put("version", 1);
        rootNode.put("tables", tableNodes);
        try {
            return OBJECT_MAPPER.writeValueAsString(rootNode);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建结构化表格 JSON 失败", ex);
        }
    }

    private Map<String, Object> buildTableNode(TableContent table) {
        Map<String, Object> tableNode = new LinkedHashMap<String, Object>();
        if (table == null || table.getRows().isEmpty()) {
            return tableNode;
        }
        List<String> headers = normalizeHeaders(table.getRows().get(0));
        List<Map<String, Object>> rowNodes = new ArrayList<Map<String, Object>>();
        int dataRowCount = 0;
        for (int rowIndex = 1; rowIndex < table.getRows().size(); rowIndex++) {
            List<String> row = table.getRows().get(rowIndex);
            if (isBlankRow(row)) {
                continue;
            }
            Map<String, Object> rowNode = buildRowNode(rowIndex + 1, headers, row);
            if (!rowNode.isEmpty()) {
                rowNodes.add(rowNode);
                dataRowCount++;
            }
        }
        if (rowNodes.isEmpty()) {
            return tableNode;
        }
        tableNode.put("tableName", table.getTableName());
        tableNode.put("sheetName", table.getSheetName());
        tableNode.put("format", table.getFormat());
        tableNode.put("headerRowNumber", Integer.valueOf(1));
        tableNode.put("rowCount", Integer.valueOf(dataRowCount));
        tableNode.put("columnCount", Integer.valueOf(headers.size()));
        tableNode.put("columns", buildColumnNodes(headers));
        tableNode.put("rows", rowNodes);
        return tableNode;
    }

    private List<String> normalizeHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<String>();
        if (rawHeaders == null) {
            return headers;
        }
        for (int index = 0; index < rawHeaders.size(); index++) {
            String header = rawHeaders.get(index);
            headers.add(normalizeHeader(header, index));
        }
        return headers;
    }

    private List<Map<String, Object>> buildColumnNodes(List<String> headers) {
        List<Map<String, Object>> columnNodes = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < headers.size(); index++) {
            Map<String, Object> columnNode = new LinkedHashMap<String, Object>();
            columnNode.put("columnIndex", Integer.valueOf(index + 1));
            columnNode.put("columnName", headers.get(index));
            columnNodes.add(columnNode);
        }
        return columnNodes;
    }

    private Map<String, Object> buildRowNode(int rowNumber, List<String> headers, List<String> row) {
        Map<String, Object> rowNode = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> cellNodes = new ArrayList<Map<String, Object>>();
        List<String> rowParts = new ArrayList<String>();
        int columnCount = Math.max(headers.size(), row.size());
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            String value = columnIndex < row.size() ? row.get(columnIndex) : "";
            if (value == null || value.isBlank()) {
                continue;
            }
            String columnName = columnIndex < headers.size()
                    ? headers.get(columnIndex)
                    : normalizeHeader("", columnIndex);
            String normalizedValue = normalizeValue(value);
            Map<String, Object> cellNode = new LinkedHashMap<String, Object>();
            cellNode.put("columnIndex", Integer.valueOf(columnIndex + 1));
            cellNode.put("columnName", columnName);
            cellNode.put("cellValue", truncate(value.trim()));
            cellNode.put("normalizedValue", normalizedValue);
            cellNode.put("valueType", inferValueType(normalizedValue));
            cellNodes.add(cellNode);
            rowParts.add(columnName + "=" + truncate(value.trim()));
        }
        if (cellNodes.isEmpty()) {
            return rowNode;
        }
        rowNode.put("rowNumber", Integer.valueOf(rowNumber));
        rowNode.put("rowText", String.join("; ", rowParts));
        rowNode.put("cells", cellNodes);
        return rowNode;
    }

    private boolean isBlankRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String value : row) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String normalizeHeader(String header, int columnIndex) {
        if (header == null || header.isBlank()) {
            return "column_" + (columnIndex + 1);
        }
        return header.trim();
    }

    private String normalizeValue(String value) {
        return value == null
                ? ""
                : value.trim().replace('\n', ' ').replace('\r', ' ').toLowerCase(Locale.ROOT);
    }

    private String inferValueType(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return "blank";
        }
        if (normalizedValue.matches("[-+]?\\d+(\\.\\d+)?")) {
            return "number";
        }
        if (normalizedValue.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) {
            return "date";
        }
        if ("true".equals(normalizedValue) || "false".equals(normalizedValue)) {
            return "boolean";
        }
        return "text";
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_CELL_TEXT_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_CELL_TEXT_LENGTH);
    }

    /**
     * 通用表格内容。
     *
     * 职责：承载解析阶段读取到的二维表数据
     *
     * @author xiexu
     */
    public static class TableContent {

        private final String tableName;

        private final String sheetName;

        private final String format;

        private final List<List<String>> rows;

        /**
         * 创建通用表格内容。
         *
         * @param tableName 表格名称
         * @param sheetName sheet 名称
         * @param format 文件格式
         * @param rows 行数据
         */
        public TableContent(String tableName, String sheetName, String format, List<List<String>> rows) {
            this.tableName = tableName;
            this.sheetName = sheetName;
            this.format = format;
            this.rows = rows == null ? List.of() : rows;
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
         * 返回文件格式。
         *
         * @return 文件格式
         */
        public String getFormat() {
            return format;
        }

        /**
         * 返回行数据。
         *
         * @return 行数据
         */
        public List<List<String>> getRows() {
            return rows;
        }
    }
}
