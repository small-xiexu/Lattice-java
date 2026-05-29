package com.xbk.lattice.documentparse.extractor;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel 文本抽取器测试
 *
 * 职责：验证 XLSX 结构化行输出为每 cell 一行的 key=value 格式
 *
 * @author xiexu
 */
class ExcelTextExtractorTests {

    @TempDir
    private Path tempDir;

    /**
     * 验证每个 cell 是独立 "- key=value" 行，不再是单行分号复合 value。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldOutputPerCellKeyValueLines() throws IOException {
        Path excelPath = tempDir.resolve("inventory.xlsx");
        writeSimpleWorkbook(excelPath);

        SourceExtractionResult result = new ExcelTextExtractor().extract(excelPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        // Part A: CSV-like 文本保持不变
        assertThat(content).contains("=== Sheet: Items ===");
        assertThat(content).contains("ItemCode,ItemName,Category");
        assertThat(content).contains("A001,Widget,Hardware");
        // Part B: 每 cell 独立行
        assertThat(content).contains("- sheet=Items");
        assertThat(content).contains("- row=2");
        assertThat(content).contains("- ItemCode=A001");
        assertThat(content).contains("- ItemName=Widget");
        assertThat(content).contains("- Category=Hardware");
        // 不再包含分号复合 value
        assertThat(content).doesNotContain("ItemCode=A001; ItemName=Widget");
    }

    /**
     * 验证单个 cell value 是短 cell 内容，不包含整行其他列。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldNotContainOtherColumnsInSingleCellValue() throws IOException {
        Path excelPath = tempDir.resolve("multi-col.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Product");
            header.createCell(1).setCellValue("Price");
            header.createCell(2).setCellValue("Stock");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Gadget");
            row.createCell(1).setCellValue("99");
            row.createCell(2).setCellValue("200");
            try (OutputStream os = Files.newOutputStream(excelPath)) {
                workbook.write(os);
            }
        }

        SourceExtractionResult result = new ExcelTextExtractor().extract(excelPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        // 每个 value 行只包含自己的 cell 内容
        assertThat(content).contains("- Product=Gadget");
        assertThat(content).contains("- Price=99");
        assertThat(content).contains("- Stock=200");
        // Gadget 那行不包含 "99" 或其他列的值
        String gadgetLine = content.lines()
                .filter(line -> line.contains("Product=Gadget"))
                .findFirst()
                .orElseThrow();
        assertThat(gadgetLine).doesNotContain("99");
        assertThat(gadgetLine).doesNotContain("200");
    }

    /**
     * 验证空 cell 被跳过。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldSkipEmptyCells() throws IOException {
        Path excelPath = tempDir.resolve("sparse.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sparse");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ColA");
            header.createCell(1).setCellValue("ColB");
            header.createCell(2).setCellValue("ColC");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("ValA");
            row.createCell(1).setCellValue("");
            row.createCell(2).setCellValue("ValC");
            try (OutputStream os = Files.newOutputStream(excelPath)) {
                workbook.write(os);
            }
        }

        SourceExtractionResult result = new ExcelTextExtractor().extract(excelPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        assertThat(content).contains("- ColA=ValA");
        assertThat(content).contains("- ColC=ValC");
        assertThat(content).doesNotContain("ColB=");
    }

    /**
     * 验证多维表 sheet 和 row 元数据都存在。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldIncludeSheetAndRowMetadata() throws IOException {
        Path excelPath = tempDir.resolve("two-rows.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Catalog");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Code");
            header.createCell(1).setCellValue("Label");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("X1");
            row1.createCell(1).setCellValue("First");
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("X2");
            row2.createCell(1).setCellValue("Second");
            try (OutputStream os = Files.newOutputStream(excelPath)) {
                workbook.write(os);
            }
        }

        SourceExtractionResult result = new ExcelTextExtractor().extract(excelPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        // 多行都有 sheet 和 row 元数据
        assertThat(content).contains("- sheet=Catalog");
        long sheetCount = content.lines()
                .filter(line -> line.equals("- sheet=Catalog"))
                .count();
        assertThat(sheetCount).isEqualTo(2);
        assertThat(content).contains("- row=2");
        assertThat(content).contains("- row=3");
    }

    private void writeSimpleWorkbook(Path excelPath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Items");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ItemCode");
            header.createCell(1).setCellValue("ItemName");
            header.createCell(2).setCellValue("Category");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("A001");
            row.createCell(1).setCellValue("Widget");
            row.createCell(2).setCellValue("Hardware");
            try (OutputStream os = Files.newOutputStream(excelPath)) {
                workbook.write(os);
            }
        }
    }
}
