package com.xbk.lattice.compiler.node;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnalyzeNode 结构化表格 Writer gate 测试
 *
 * 职责：验证大行数结构化表格源在长文档专题拆分前收敛为表级 overview concept
 *
 * @author xiexu
 */
class AnalyzeNodeStructuredTableWriterGateTests {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    /**
     * 验证大行数结构化表格源会生成表级 overview concept，且不会携带全量行数据。
     *
     * @throws Exception JSON 构建异常
     */
    @Test
    void shouldGateLargeStructuredTableIntoOverviewConcepts() throws Exception {
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, createDocumentTopicCompilerProperties());
        String content = buildLongTableLikeContent();
        RawSource rawSource = RawSource.extracted(
                "datasets/large-table.xlsx",
                content,
                "xlsx",
                content.length(),
                buildMetadataJson(List.of(
                        table("Records", "Records", 240, "record_id", "state", "value"),
                        table("Secondary", "Secondary", 12, "item_id", "state")
                )),
                false,
                "datasets/large-table.xlsx"
        );
        List<SourceBatch> sourceBatches = List.of(new SourceBatch("batch-1", "large-table", List.of(rawSource)));

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("large-table", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getTitle)
                .containsExactly(
                        "Structured Table Overview - Records",
                        "Structured Table Overview - Secondary"
                );
        assertThat(analyzedConcepts.get(0).getConceptId())
                .isEqualTo("structured-table-datasets-large-table-records-records-table-1");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("datasets/large-table.xlsx");
        assertThat(analyzedConcepts.get(0).getSnippets())
                .contains(
                        "Source path: datasets/large-table.xlsx",
                        "Table name: Records",
                        "Sheet name: Records",
                        "Row count: 240",
                        "Column count: 3",
                        "Columns: record_id, state, value"
                );
        assertThat(String.join("\n", analyzedConcepts.get(0).getSnippets()))
                .doesNotContain("row-239-value");
        assertThat(analyzedConcepts.get(0).getSections()).hasSize(1);
        assertThat(analyzedConcepts.get(0).getSections().get(0).getSourceRefs())
                .containsExactly("datasets/large-table.xlsx#structured-table-overview");
    }

    /**
     * 验证小型结构化表格不会触发 gate，仍走原有分析路径。
     *
     * @throws Exception JSON 构建异常
     */
    @Test
    void shouldNotGateSmallStructuredTable() throws Exception {
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, createDocumentTopicCompilerProperties());
        String content = "small structured table summary";
        RawSource rawSource = RawSource.extracted(
                "datasets/small-table.xlsx",
                content,
                "xlsx",
                content.length(),
                buildMetadataJson(List.of(table("Small", "Small", 12, "id", "status"))),
                false,
                "datasets/small-table.xlsx"
        );
        List<SourceBatch> sourceBatches = List.of(new SourceBatch("batch-1", "small-table", List.of(rawSource)));

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("small-table", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("small-table");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Small Table");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("small structured table summary");
    }

    /**
     * 验证普通 Markdown 长文档不受 structured table gate 影响。
     */
    @Test
    void shouldKeepMarkdownTopicExtractionUnchanged() {
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, createDocumentTopicCompilerProperties());
        String content = buildLongMarkdownContent();
        RawSource rawSource = RawSource.text("docs/platform-guide.md", content, "md", content.length());
        List<SourceBatch> sourceBatches = List.of(new SourceBatch("batch-1", "platform-guide", List.of(rawSource)));

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("platform-guide", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getTitle)
                .containsExactly("Overview", "Runtime Refresh");
    }

    /**
     * 构建 metadataJson。
     *
     * @param tables 表格定义
     * @return metadataJson
     * @throws JsonProcessingException JSON 构建异常
     */
    private String buildMetadataJson(List<Map<String, Object>> tables) throws JsonProcessingException {
        String structuredContentJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "contentType", "structured_tables",
                "version", Integer.valueOf(1),
                "tables", tables
        ));
        return OBJECT_MAPPER.writeValueAsString(Map.of("structuredContentJson", structuredContentJson));
    }

    /**
     * 构建表格定义。
     *
     * @param tableName 表名
     * @param sheetName sheet 名称
     * @param rowCount 行数
     * @param columns 列名
     * @return 表格定义
     */
    private Map<String, Object> table(String tableName, String sheetName, int rowCount, String... columns) {
        return Map.of(
                "tableName", tableName,
                "sheetName", sheetName,
                "format", "xlsx",
                "rowCount", Integer.valueOf(rowCount),
                "columnCount", Integer.valueOf(columns.length),
                "columns", buildColumns(columns),
                "rows", List.of(Map.of(
                        "rowNumber", Integer.valueOf(2),
                        "rowText", "row-1-value",
                        "cells", List.of()
                ))
        );
    }

    /**
     * 构建列定义。
     *
     * @param columns 列名
     * @return 列定义
     */
    private List<Map<String, Object>> buildColumns(String... columns) {
        List<Map<String, Object>> columnNodes = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < columns.length; index++) {
            columnNodes.add(Map.of(
                    "columnIndex", Integer.valueOf(index + 1),
                    "columnName", columns[index]
            ));
        }
        return columnNodes;
    }

    /**
     * 构建长表格纯文本内容。
     *
     * @return 长表格文本
     */
    private String buildLongTableLikeContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("=== Page: 1 ===\n");
        builder.append("1. Rows\n");
        for (int index = 0; index < 240; index++) {
            builder.append("row-").append(index).append("-value").append("\n");
        }
        return builder.toString();
    }

    /**
     * 构建普通长 Markdown 内容。
     *
     * @return Markdown 内容
     */
    private String buildLongMarkdownContent() {
        StringBuilder builder = new StringBuilder();
        appendTopic(builder, 1, "Overview", "Describe module boundary.");
        appendTopic(builder, 2, "Runtime Refresh", "Describe runtime refresh.");
        return builder.toString();
    }

    /**
     * 追加专题内容。
     *
     * @param builder 内容构建器
     * @param page 页码
     * @param title 标题
     * @param line 内容行
     */
    private void appendTopic(StringBuilder builder, int page, String title, String line) {
        builder.append("=== Page: ").append(page).append(" ===").append("\n");
        builder.append(page).append(". ").append(title).append("\n");
        for (int index = 0; index < 80; index++) {
            builder.append(line).append(" line ").append(index + 1).append(".").append("\n");
        }
    }

    /**
     * 构建测试用专题拆分配置。
     *
     * @return 编译配置
     */
    private CompilerProperties createDocumentTopicCompilerProperties() {
        CompilerProperties compilerProperties = new CompilerProperties();
        CompilerProperties.DocumentTopics documentTopics = compilerProperties.getDocumentTopics();
        documentTopics.setMediumDocumentMinChars(200);
        documentTopics.setMinHeadingsForMediumDocument(2);
        documentTopics.setPageMarkerPattern("^===\\s*Page:\\s*(\\d+)\\s*===$");
        documentTopics.setHeadingBoundaryPattern("^[：:\\-—\\s]+|[：:\\-—\\s]+$");
        documentTopics.setIgnoredLinePrefixes(List.of("table_row:", "==="));
        documentTopics.setHeadingTerminalPunctuations(List.of("。", "；", ";", "，", ","));
        documentTopics.setBodyTerminalPunctuations(List.of("。", "."));
        documentTopics.setHeadingPatterns(List.of(
                new CompilerProperties.HeadingPatternRule(
                        "numeric",
                        "^(\\d+(?:\\.\\d+){0,4})[、.．\\s]+(.+?)\\s*$",
                        2,
                        1,
                        1,
                        "numeric-depth"
                )
        ));
        return compilerProperties;
    }
}
