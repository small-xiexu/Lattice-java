package com.xbk.lattice.compiler.node;

import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 结构化表格 Writer gate 策略
 *
 * 职责：识别大行数结构化表格源，并生成少量表级 overview concept 供后续 Writer 主链处理
 *
 * @author xiexu
 */
public class StructuredTableWriterGatePolicy {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private static final String STRUCTURED_TABLES_CONTENT_TYPE = "structured_tables";

    private static final int ROW_COUNT_THRESHOLD = 200;

    /**
     * 为满足 gate 条件的结构化表格源构建 overview concepts。
     *
     * @param sortedSources 已排序源文件
     * @return overview concepts；未触发 gate 时返回空集合
     */
    public List<AnalyzedConcept> buildOverviewConcepts(List<RawSource> sortedSources) {
        List<AnalyzedConcept> overviewConcepts = new ArrayList<AnalyzedConcept>();
        for (RawSource rawSource : sortedSources) {
            StructuredTableSource structuredTableSource = readStructuredTableSource(rawSource);
            if (structuredTableSource == null || !shouldGate(structuredTableSource)) {
                continue;
            }
            overviewConcepts.addAll(buildSourceOverviewConcepts(rawSource, structuredTableSource));
        }
        return overviewConcepts;
    }

    /**
     * 返回大表格 gate 行数阈值。
     *
     * @return 行数阈值
     */
    public int getRowCountThreshold() {
        return ROW_COUNT_THRESHOLD;
    }

    /**
     * 读取结构化表格元数据。
     *
     * @param rawSource 原始源文件
     * @return 结构化表格源；无法读取时返回 null
     */
    private StructuredTableSource readStructuredTableSource(RawSource rawSource) {
        JsonNode metadataNode = readTree(rawSource.getMetadataJson());
        if (metadataNode == null) {
            return null;
        }
        JsonNode structuredContentNode = readStructuredContentNode(metadataNode.path("structuredContentJson"));
        if (structuredContentNode == null) {
            return null;
        }
        if (!STRUCTURED_TABLES_CONTENT_TYPE.equals(structuredContentNode.path("contentType").asText())) {
            return null;
        }
        JsonNode tablesNode = structuredContentNode.path("tables");
        if (!tablesNode.isArray()) {
            return null;
        }
        List<StructuredTableOverview> tables = new ArrayList<StructuredTableOverview>();
        for (int index = 0; index < tablesNode.size(); index++) {
            StructuredTableOverview tableOverview = toTableOverview(tablesNode.get(index), index);
            if (tableOverview != null) {
                tables.add(tableOverview);
            }
        }
        if (tables.isEmpty()) {
            return null;
        }
        return new StructuredTableSource(tables);
    }

    /**
     * 读取结构化内容节点。
     *
     * @param structuredContentJsonNode structuredContentJson 节点
     * @return 结构化内容节点
     */
    private JsonNode readStructuredContentNode(JsonNode structuredContentJsonNode) {
        if (structuredContentJsonNode == null || structuredContentJsonNode.isMissingNode()) {
            return null;
        }
        if (structuredContentJsonNode.isObject()) {
            return structuredContentJsonNode;
        }
        String structuredContentJson = structuredContentJsonNode.asText("");
        if (structuredContentJson.isBlank()) {
            return null;
        }
        return readTree(structuredContentJson);
    }

    /**
     * 转换表格 overview 元数据。
     *
     * @param tableNode 表格节点
     * @param tableIndex 表格序号
     * @return 表格 overview；无有效列时返回 null
     */
    private StructuredTableOverview toTableOverview(JsonNode tableNode, int tableIndex) {
        List<String> columns = readColumns(tableNode.path("columns"));
        if (columns.isEmpty()) {
            return null;
        }
        int rowCount = resolveRowCount(tableNode);
        String tableName = normalizeLabel(tableNode.path("tableName").asText(""), "table-" + (tableIndex + 1));
        String sheetName = normalizeLabel(tableNode.path("sheetName").asText(""), tableName);
        int columnCount = resolveColumnCount(tableNode, columns);
        return new StructuredTableOverview(tableName, sheetName, rowCount, columnCount, columns, tableIndex);
    }

    /**
     * 判断结构化表格源是否需要 gate。
     *
     * @param structuredTableSource 结构化表格源
     * @return 是否触发 gate
     */
    private boolean shouldGate(StructuredTableSource structuredTableSource) {
        if (structuredTableSource.getTotalRowCount() >= ROW_COUNT_THRESHOLD) {
            return true;
        }
        for (StructuredTableOverview table : structuredTableSource.getTables()) {
            if (table.getRowCount() >= ROW_COUNT_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建单个源文件的表级 overview concepts。
     *
     * @param rawSource 原始源文件
     * @param structuredTableSource 结构化表格源
     * @return overview concepts
     */
    private List<AnalyzedConcept> buildSourceOverviewConcepts(
            RawSource rawSource,
            StructuredTableSource structuredTableSource
    ) {
        List<AnalyzedConcept> overviewConcepts = new ArrayList<AnalyzedConcept>();
        for (StructuredTableOverview table : structuredTableSource.getTables()) {
            overviewConcepts.add(buildTableOverviewConcept(rawSource, table));
        }
        return overviewConcepts;
    }

    /**
     * 构建单个表格 overview concept。
     *
     * @param rawSource 原始源文件
     * @param table 表格 overview
     * @return overview concept
     */
    private AnalyzedConcept buildTableOverviewConcept(RawSource rawSource, StructuredTableOverview table) {
        String conceptId = buildConceptId(rawSource, table);
        String title = "Structured Table Overview - " + table.getTableName();
        String description = "Overview of structured table "
                + table.getTableName()
                + " from source "
                + rawSource.getRelativePath()
                + ".";
        List<String> sourcePaths = List.of(rawSource.getRelativePath());
        List<String> snippets = buildOverviewLines(rawSource, table);
        List<ConceptSection> sections = List.of(new ConceptSection(
                "Table Overview",
                snippets,
                List.of(rawSource.getRelativePath() + "#structured-table-overview")
        ));
        return new AnalyzedConcept(conceptId, title, description, sourcePaths, snippets, sections);
    }

    /**
     * 构建 overview 行。
     *
     * @param rawSource 原始源文件
     * @param table 表格 overview
     * @return overview 行
     */
    private List<String> buildOverviewLines(RawSource rawSource, StructuredTableOverview table) {
        List<String> lines = new ArrayList<String>();
        lines.add("Source path: " + rawSource.getRelativePath());
        lines.add("Table name: " + table.getTableName());
        lines.add("Sheet name: " + table.getSheetName());
        lines.add("Row count: " + table.getRowCount());
        lines.add("Column count: " + table.getColumnCount());
        lines.add("Columns: " + String.join(", ", table.getColumns()));
        return lines;
    }

    /**
     * 构建稳定 conceptId。
     *
     * @param rawSource 原始源文件
     * @param table 表格 overview
     * @return conceptId
     */
    private String buildConceptId(RawSource rawSource, StructuredTableOverview table) {
        String sourcePart = slugify(stripExtension(rawSource.getRelativePath()));
        String tablePart = slugify(table.getTableName() + "-" + table.getSheetName());
        String indexPart = "table-" + (table.getTableIndex() + 1);
        return joinConceptIdParts("structured-table", sourcePart, tablePart, indexPart);
    }

    /**
     * 拼接 conceptId 片段。
     *
     * @param parts 片段
     * @return conceptId
     */
    private String joinConceptIdParts(String... parts) {
        List<String> normalizedParts = new ArrayList<String>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                normalizedParts.add(part);
            }
        }
        return String.join("-", normalizedParts);
    }

    /**
     * 去掉文件扩展名。
     *
     * @param path 路径
     * @return 去扩展名后的路径
     */
    private String stripExtension(String path) {
        int slashIndex = path.lastIndexOf('/');
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > slashIndex) {
            return path.substring(0, dotIndex);
        }
        return path;
    }

    /**
     * 读取列名。
     *
     * @param columnsNode columns 节点
     * @return 列名列表
     */
    private List<String> readColumns(JsonNode columnsNode) {
        Set<String> columns = new LinkedHashSet<String>();
        if (!columnsNode.isArray()) {
            return new ArrayList<String>();
        }
        for (JsonNode columnNode : columnsNode) {
            String columnName = columnNode.isObject()
                    ? columnNode.path("columnName").asText("")
                    : columnNode.asText("");
            String normalizedColumnName = normalizeWhitespace(columnName);
            if (!normalizedColumnName.isEmpty()) {
                columns.add(normalizedColumnName);
            }
        }
        return new ArrayList<String>(columns);
    }

    /**
     * 解析行数。
     *
     * @param tableNode 表格节点
     * @return 行数
     */
    private int resolveRowCount(JsonNode tableNode) {
        int declaredRowCount = tableNode.path("rowCount").asInt(-1);
        if (declaredRowCount >= 0) {
            return declaredRowCount;
        }
        JsonNode rowsNode = tableNode.path("rows");
        return rowsNode.isArray() ? rowsNode.size() : 0;
    }

    /**
     * 解析列数。
     *
     * @param tableNode 表格节点
     * @param columns 列名列表
     * @return 列数
     */
    private int resolveColumnCount(JsonNode tableNode, List<String> columns) {
        int declaredColumnCount = tableNode.path("columnCount").asInt(-1);
        if (declaredColumnCount > 0) {
            return declaredColumnCount;
        }
        return columns.size();
    }

    /**
     * 标准化展示标签。
     *
     * @param value 原始值
     * @param defaultLabel 默认标签
     * @return 展示标签
     */
    private String normalizeLabel(String value, String defaultLabel) {
        String normalizedValue = normalizeWhitespace(value);
        if (!normalizedValue.isEmpty()) {
            return normalizedValue;
        }
        return defaultLabel;
    }

    /**
     * 标准化空白。
     *
     * @param value 原始值
     * @return 标准化值
     */
    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    /**
     * 转换为 slug。
     *
     * @param value 原始值
     * @return slug
     */
    private String slugify(String value) {
        String slug = normalizeWhitespace(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            return "table";
        }
        return slug;
    }

    /**
     * 读取 JSON 树。
     *
     * @param json JSON 字符串
     * @return JSON 树；解析失败时返回 null
     */
    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * 结构化表格源。
     *
     * @author xiexu
     */
    private static final class StructuredTableSource {

        private final List<StructuredTableOverview> tables;

        /**
         * 创建结构化表格源。
         *
         * @param tables 表格列表
         */
        private StructuredTableSource(List<StructuredTableOverview> tables) {
            this.tables = tables;
        }

        /**
         * 获取表格列表。
         *
         * @return 表格列表
         */
        private List<StructuredTableOverview> getTables() {
            return tables;
        }

        /**
         * 获取总行数。
         *
         * @return 总行数
         */
        private int getTotalRowCount() {
            int totalRowCount = 0;
            for (StructuredTableOverview table : tables) {
                totalRowCount += table.getRowCount();
            }
            return totalRowCount;
        }
    }

    /**
     * 结构化表格 overview。
     *
     * @author xiexu
     */
    private static final class StructuredTableOverview {

        private final String tableName;

        private final String sheetName;

        private final int rowCount;

        private final int columnCount;

        private final List<String> columns;

        private final int tableIndex;

        /**
         * 创建结构化表格 overview。
         *
         * @param tableName 表名
         * @param sheetName sheet 名称
         * @param rowCount 行数
         * @param columnCount 列数
         * @param columns 列名
         * @param tableIndex 表格序号
         */
        private StructuredTableOverview(
                String tableName,
                String sheetName,
                int rowCount,
                int columnCount,
                List<String> columns,
                int tableIndex
        ) {
            this.tableName = tableName;
            this.sheetName = sheetName;
            this.rowCount = rowCount;
            this.columnCount = columnCount;
            this.columns = columns;
            this.tableIndex = tableIndex;
        }

        /**
         * 获取表名。
         *
         * @return 表名
         */
        private String getTableName() {
            return tableName;
        }

        /**
         * 获取 sheet 名称。
         *
         * @return sheet 名称
         */
        private String getSheetName() {
            return sheetName;
        }

        /**
         * 获取行数。
         *
         * @return 行数
         */
        private int getRowCount() {
            return rowCount;
        }

        /**
         * 获取列数。
         *
         * @return 列数
         */
        private int getColumnCount() {
            return columnCount;
        }

        /**
         * 获取列名。
         *
         * @return 列名
         */
        private List<String> getColumns() {
            return columns;
        }

        /**
         * 获取表格序号。
         *
         * @return 表格序号
         */
        private int getTableIndex() {
            return tableIndex;
        }
    }
}
