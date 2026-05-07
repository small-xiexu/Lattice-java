package com.xbk.lattice.documentparse.extractor;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 文本抽取器
 *
 * 职责：把 CSV 文件抽取为原始文本与通用结构化表格 JSON
 *
 * @author xiexu
 */
public class CsvTextExtractor {

    private final StructuredTableContentBuilder structuredTableContentBuilder;

    /**
     * 创建 CSV 文本抽取器。
     */
    public CsvTextExtractor() {
        this.structuredTableContentBuilder = new StructuredTableContentBuilder();
    }

    /**
     * 抽取 CSV 文本。
     *
     * @param csvPath CSV 路径
     * @return 抽取结果；无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public SourceExtractionResult extract(Path csvPath) throws IOException {
        String content = Files.readString(csvPath, StandardCharsets.UTF_8);
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            return null;
        }
        List<List<String>> rows = readRows(csvPath);
        String tableName = csvPath.getFileName() == null ? "csv" : csvPath.getFileName().toString();
        String structuredContentJson = structuredTableContentBuilder.buildJson(List.of(
                new StructuredTableContentBuilder.TableContent(tableName, tableName, "csv", rows)
        ));
        return new SourceExtractionResult(normalizedContent, buildMetadataJson(rows), structuredContentJson, false);
    }

    private List<List<String>> readRows(Path csvPath) throws IOException {
        List<List<String>> rows = new ArrayList<List<String>>();
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setIgnoreSurroundingSpaces(false)
                .setTrim(false)
                .get();
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser csvParser = csvFormat.parse(reader)) {
            for (CSVRecord csvRecord : csvParser) {
                List<String> row = new ArrayList<String>();
                for (String value : csvRecord) {
                    row.add(value == null ? "" : value.trim());
                }
                if (!row.stream().allMatch(String::isEmpty)) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private String buildMetadataJson(List<List<String>> rows) {
        int rowCount = rows == null ? 0 : Math.max(0, rows.size() - 1);
        int columnCount = rows == null || rows.isEmpty() ? 0 : rows.get(0).size();
        return "{\"tableCount\":1,\"rowCount\":" + rowCount + ",\"columnCount\":" + columnCount + "}";
    }
}
