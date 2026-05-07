package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构化表格 JDBC 仓储测试
 *
 * 职责：验证通用表格结构可按源文件落库为表、行、单元格事实
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class StructuredTableJdbcRepositoryTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private StructuredTableJdbcRepository structuredTableJdbcRepository;

    /**
     * 验证 手动 DDL 已创建结构化表格相关表。
     */
    @Test
    void shouldCreateStructuredTableTablesByManualDdl() {
        List<String> tableNames = jdbcTemplate.queryForList(
                """
                        select table_name
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name in (
                              'structured_tables',
                              'structured_table_rows',
                              'structured_table_cells'
                          )
                        order by table_name
                        """,
                String.class
        );

        assertThat(tableNames)
                .contains("structured_tables")
                .contains("structured_table_rows")
                .contains("structured_table_cells");
    }

    /**
     * 验证结构化表格 JSON 可替换落库为表、行、单元格。
     *
     * @throws Exception JSON 构建异常
     */
    @Test
    void shouldPersistTablesRowsAndCellsFromSourceFileMetadata() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        String structuredContentJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "contentType", "structured_tables",
                "version", Integer.valueOf(1),
                "tables", List.of(Map.of(
                        "tableName", "Cases",
                        "sheetName", "Cases",
                        "format", "xlsx",
                        "headerRowNumber", Integer.valueOf(1),
                        "rowCount", Integer.valueOf(1),
                        "columnCount", Integer.valueOf(3),
                        "rows", List.of(Map.of(
                                "rowNumber", Integer.valueOf(2),
                                "rowText", "id=100; name=alpha; status=done",
                                "cells", List.of(
                                        Map.of(
                                                "columnIndex", Integer.valueOf(1),
                                                "columnName", "id",
                                                "cellValue", "100",
                                                "normalizedValue", "100",
                                                "valueType", "number"
                                        ),
                                        Map.of(
                                                "columnIndex", Integer.valueOf(2),
                                                "columnName", "name",
                                                "cellValue", "alpha",
                                                "normalizedValue", "alpha",
                                                "valueType", "text"
                                        ),
                                        Map.of(
                                                "columnIndex", Integer.valueOf(3),
                                                "columnName", "status",
                                                "cellValue", "done",
                                                "normalizedValue", "done",
                                                "valueType", "text"
                                        )
                                )
                        ))
                ))
        ));
        String metadataJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "structuredContentJson", structuredContentJson
        ));
        SourceFileRecord sourceFileRecord = sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "docs/cases.xlsx",
                "cases",
                "xlsx",
                100L,
                "id,name,status\n100,alpha,done",
                metadataJson,
                true,
                "docs/cases.xlsx"
        ));

        structuredTableJdbcRepository.replaceTablesFromSourceFile(sourceFileRecord);

        List<StructuredTableRecord> tables = structuredTableJdbcRepository.findTablesBySourceFileId(
                sourceFileRecord.getId()
        );
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).getTableName()).isEqualTo("Cases");
        assertThat(tables.get(0).getRowCount()).isEqualTo(1);
        List<StructuredTableRowRecord> rows = structuredTableJdbcRepository.findRowsByTableId(tables.get(0).getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRowNumber()).isEqualTo(2);
        assertThat(rows.get(0).getRowText()).contains("status=done");
        List<StructuredTableCellRecord> cells = structuredTableJdbcRepository.findCellsByTableId(tables.get(0).getId());
        assertThat(cells).hasSize(3);
        assertThat(cells).extracting(StructuredTableCellRecord::getColumnName)
                .containsExactly("id", "name", "status");
        assertThat(structuredTableJdbcRepository.findCellsByColumnValue(
                sourceFileRecord.getId(),
                "status",
                "done"
        )).hasSize(1);
        assertThat(structuredTableJdbcRepository.findRowsByFilters(Map.of("status", "done"), 10)).hasSize(1);
        assertThat(structuredTableJdbcRepository.countRowsByFilters(Map.of("status", "done"))).isEqualTo(1L);
        assertThat(structuredTableJdbcRepository.groupCountByField(Map.of(), "status", 10))
                .extracting(StructuredTableGroupCountRecord::getCellValue)
                .containsExactly("done");
    }

    /**
     * 验证结构化表格导入后生成通用字段画像。
     *
     * @throws Exception JSON 构建异常
     */
    @Test
    void shouldPersistGenericSchemaProfileForImportedTable() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        String longTextA = "This entry contains a deliberately long explanation that should be profiled as "
                + "long text because downstream query planning must avoid treating it as a compact enum value.";
        String longTextB = "Another detailed paragraph keeps the profiling generic and verifies that text "
                + "length, not a field name, drives the long text classification.";
        String structuredContentJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "contentType", "structured_tables",
                "version", Integer.valueOf(1),
                "tables", List.of(Map.of(
                        "tableName", "GenericRecords",
                        "sheetName", "Records",
                        "format", "csv",
                        "headerRowNumber", Integer.valueOf(1),
                        "rowCount", Integer.valueOf(4),
                        "columnCount", Integer.valueOf(5),
                        "columns", List.of(
                                Map.of("columnIndex", Integer.valueOf(1), "columnName", "record_key"),
                                Map.of("columnIndex", Integer.valueOf(2), "columnName", "category"),
                                Map.of("columnIndex", Integer.valueOf(3), "columnName", "amount"),
                                Map.of("columnIndex", Integer.valueOf(4), "columnName", "event_date"),
                                Map.of("columnIndex", Integer.valueOf(5), "columnName", "description")
                        ),
                        "rows", List.of(
                                rowNode(2, "record_key=A-001; category=open; amount=10; event_date=2026-01-01",
                                        List.of(
                                                cellNode(1, "record_key", "A-001", "a-001", "text"),
                                                cellNode(2, "category", "open", "open", "text"),
                                                cellNode(3, "amount", "10", "10", "number"),
                                                cellNode(4, "event_date", "2026-01-01", "2026-01-01", "date"),
                                                cellNode(5, "description", longTextA, longTextA.toLowerCase(), "text")
                                        )),
                                rowNode(3, "record_key=A-002; category=closed; amount=20; event_date=2026-01-02",
                                        List.of(
                                                cellNode(1, "record_key", "A-002", "a-002", "text"),
                                                cellNode(2, "category", "closed", "closed", "text"),
                                                cellNode(3, "amount", "20", "20", "number"),
                                                cellNode(4, "event_date", "2026-01-02", "2026-01-02", "date"),
                                                cellNode(5, "description", longTextB, longTextB.toLowerCase(), "text")
                                        )),
                                rowNode(4, "record_key=A-003; category=open; amount=30; event_date=2026-01-03",
                                        List.of(
                                                cellNode(1, "record_key", "A-003", "a-003", "text"),
                                                cellNode(2, "category", "open", "open", "text"),
                                                cellNode(3, "amount", "30", "30", "number"),
                                                cellNode(4, "event_date", "2026-01-03", "2026-01-03", "date")
                                        )),
                                rowNode(5, "record_key=A-004; category=closed; amount=40; event_date=2026-01-04",
                                        List.of(
                                                cellNode(1, "record_key", "A-004", "a-004", "text"),
                                                cellNode(2, "category", "closed", "closed", "text"),
                                                cellNode(3, "amount", "40", "40", "number"),
                                                cellNode(4, "event_date", "2026-01-04", "2026-01-04", "date")
                                        ))
                        )
                ))
        ));
        String metadataJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "structuredContentJson", structuredContentJson
        ));
        SourceFileRecord sourceFileRecord = sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "docs/generic-records.csv",
                "generic records",
                "csv",
                200L,
                "record_key,category,amount,event_date,description",
                metadataJson,
                true,
                "docs/generic-records.csv"
        ));

        structuredTableJdbcRepository.replaceTablesFromSourceFile(sourceFileRecord);

        StructuredTableRecord table = structuredTableJdbcRepository.findTablesBySourceFileId(sourceFileRecord.getId())
                .get(0);
        Map<String, Map<String, Object>> profilesByColumnName = profilesByColumnName(table.getMetadataJson());
        assertThat(profilesByColumnName.get("record_key"))
                .containsEntry("highUnique", Boolean.TRUE)
                .containsEntry("suspectedPrimaryKey", Boolean.TRUE);
        assertThat(profilesByColumnName.get("category"))
                .containsEntry("enumLike", Boolean.TRUE)
                .containsEntry("dominantValueType", "text");
        assertThat(profilesByColumnName.get("amount"))
                .containsEntry("numericLike", Boolean.TRUE)
                .containsEntry("aggregatable", Boolean.TRUE);
        assertThat(profilesByColumnName.get("event_date"))
                .containsEntry("dateLike", Boolean.TRUE)
                .containsEntry("dominantValueType", "date");
        assertThat(profilesByColumnName.get("description"))
                .containsEntry("longTextLike", Boolean.TRUE);
    }

    /**
     * 构建测试行节点。
     *
     * @param rowNumber 行号
     * @param rowText 行文本
     * @param cells 单元格节点
     * @return 行节点
     */
    private Map<String, Object> rowNode(int rowNumber, String rowText, List<Map<String, Object>> cells) {
        return Map.of(
                "rowNumber", Integer.valueOf(rowNumber),
                "rowText", rowText,
                "cells", cells
        );
    }

    /**
     * 构建测试单元格节点。
     *
     * @param columnIndex 列序号
     * @param columnName 列名
     * @param cellValue 原始值
     * @param normalizedValue 归一化值
     * @param valueType 值类型
     * @return 单元格节点
     */
    private Map<String, Object> cellNode(
            int columnIndex,
            String columnName,
            String cellValue,
            String normalizedValue,
            String valueType
    ) {
        return Map.of(
                "columnIndex", Integer.valueOf(columnIndex),
                "columnName", columnName,
                "cellValue", cellValue,
                "normalizedValue", normalizedValue,
                "valueType", valueType
        );
    }

    /**
     * 按列名读取字段画像。
     *
     * @param metadataJson 表级元数据 JSON
     * @return 列名到字段画像的映射
     * @throws Exception JSON 解析异常
     */
    private Map<String, Map<String, Object>> profilesByColumnName(String metadataJson) throws Exception {
        Map<String, Map<String, Object>> profilesByColumnName = new LinkedHashMap<String, Map<String, Object>>();
        OBJECT_MAPPER.readTree(metadataJson)
                .path("schemaProfile")
                .path("columns")
                .forEach(columnNode -> {
                    Map<String, Object> columnProfile = OBJECT_MAPPER.convertValue(columnNode, Map.class);
                    profilesByColumnName.put(columnNode.path("columnName").asText(), columnProfile);
                });
        return profilesByColumnName;
    }
}
