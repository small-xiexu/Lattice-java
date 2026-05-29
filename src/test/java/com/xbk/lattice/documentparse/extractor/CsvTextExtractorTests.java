package com.xbk.lattice.documentparse.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 文本抽取器测试
 *
 * 职责：验证 CSV 结构化行追加为每 cell 一行的 key=value 格式
 *
 * @author xiexu
 */
class CsvTextExtractorTests {

    @TempDir
    private Path tempDir;

    /**
     * 验证原始 CSV 文本（Part A）被保留，并追加 structured rows（Part B）。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldPreserveOriginalCsvAndAppendStructuredRows() throws IOException {
        Path csvPath = tempDir.resolve("orders.csv");
        Files.writeString(csvPath, "OrderId,Product,Qty\nORD-1,Widget,10", StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        // Part A: 原始 CSV 文本保留
        assertThat(content).contains("OrderId,Product,Qty");
        assertThat(content).contains("ORD-1,Widget,10");
        // Part B: structured rows 追加
        assertThat(content).contains("--- Structured Rows ---");
        assertThat(content).contains("- table=orders");
        assertThat(content).contains("- row=2");
        assertThat(content).contains("- OrderId=ORD-1");
        assertThat(content).contains("- Product=Widget");
        assertThat(content).contains("- Qty=10");
    }

    /**
     * 验证每个 cell 是独立 "- key=value" 行，不使用分号复合 value。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldOutputPerCellKeyValueLines() throws IOException {
        Path csvPath = tempDir.resolve("parts.csv");
        Files.writeString(csvPath, "PartCode,Name,Weight\nP100,Bolt,0.5", StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        assertThat(content).contains("- PartCode=P100");
        assertThat(content).contains("- Name=Bolt");
        assertThat(content).contains("- Weight=0.5");
        // 不包含分号复合 value
        assertThat(content).doesNotContain("PartCode=P100; Name=Bolt");
    }

    /**
     * 验证单个 cell value 是短内容，不包含其他列的值。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldNotContainOtherColumnsInSingleCellValue() throws IOException {
        Path csvPath = tempDir.resolve("sensors.csv");
        Files.writeString(csvPath, "SensorId,Type,Reading\nS01,Temp,42", StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        String sensorLine = content.lines()
                .filter(line -> line.contains("SensorId=S01"))
                .findFirst()
                .orElseThrow();
        assertThat(sensorLine).doesNotContain("Temp");
        assertThat(sensorLine).doesNotContain("42");
        assertThat(sensorLine).isEqualTo("- SensorId=S01");
    }

    /**
     * 验证 CSV 使用 "table" 元数据 key 而非 "sheet"。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldUseTableMetadataKeyNotSheet() throws IOException {
        Path csvPath = tempDir.resolve("zones.csv");
        Files.writeString(csvPath, "ZoneId,Name\nZ1,East", StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        assertThat(content).contains("- table=zones");
        assertThat(content).doesNotContain("sheet=");
    }

    /**
     * 验证多行 CSV 每行都有独立的 table 和 row 元数据。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldIncludeTableAndRowMetadataForEachDataRow() throws IOException {
        Path csvPath = tempDir.resolve("items.csv");
        Files.writeString(csvPath, "Id,Label\n1,Alpha\n2,Beta\n3,Gamma", StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        long tableCount = content.lines()
                .filter(line -> line.equals("- table=items"))
                .count();
        assertThat(tableCount).isEqualTo(3);
        assertThat(content).contains("- row=2");
        assertThat(content).contains("- row=3");
        assertThat(content).contains("- row=4");
        assertThat(content).contains("- Label=Alpha");
        assertThat(content).contains("- Label=Beta");
        assertThat(content).contains("- Label=Gamma");
    }

    /**
     * 验证空 CSV 返回 null。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldReturnNullForEmptyCsv() throws IOException {
        Path csvPath = tempDir.resolve("empty.csv");
        Files.writeString(csvPath, "", StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNull();
    }
}
