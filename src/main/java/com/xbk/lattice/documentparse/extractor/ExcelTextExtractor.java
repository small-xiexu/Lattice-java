package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 文本抽取器
 *
 * 职责：把 Excel 工作簿抽取为按 sheet 组织的规范化正文与元数据
 *
 * @author xiexu
 */
public class ExcelTextExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int STRUCTURED_CELL_MAX_CHARS = 500;

    /**
     * 抽取 Excel 文本。
     *
     * @param excelPath Excel 路径
     * @return 抽取结果；无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public SourceExtractionResult extract(Path excelPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter dataFormatter = new DataFormatter();
            StringBuilder contentBuilder = new StringBuilder();
            List<String> sheetNames = new ArrayList<String>();
            int sheetCount = workbook.getNumberOfSheets();
            for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }
                String sheetText = toSheetText(sheet, dataFormatter);
                if (sheetText.isBlank()) {
                    continue;
                }
                sheetNames.add(sheet.getSheetName());
                if (contentBuilder.length() > 0) {
                    contentBuilder.append("\n\n");
                }
                contentBuilder.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===").append("\n");
                contentBuilder.append(sheetText);
            }
            if (contentBuilder.length() == 0) {
                return null;
            }
            return new SourceExtractionResult(contentBuilder.toString(), buildMetadataJson(sheetNames), true);
        }
    }

    /**
     * 将 sheet 转成兼容 CSV 且适合检索的文本。
     *
     * @param sheet 工作表
     * @param dataFormatter 单元格格式化器
     * @return 表格文本
     */
    private String toSheetText(Sheet sheet, DataFormatter dataFormatter) {
        List<List<String>> rows = readRows(sheet, dataFormatter);
        if (rows.isEmpty()) {
            return "";
        }
        String csvLikeText = toCsvLikeText(rows);
        String structuredRowsText = toStructuredRowsText(sheet, rows);
        if (structuredRowsText.isBlank()) {
            return csvLikeText;
        }
        return csvLikeText + "\n\n--- Structured Rows: " + sheet.getSheetName() + " ---\n" + structuredRowsText;
    }

    /**
     * 读取非空行。
     *
     * @param sheet 工作表
     * @param dataFormatter 单元格格式化器
     * @return 行数据
     */
    private List<List<String>> readRows(Sheet sheet, DataFormatter dataFormatter) {
        List<List<String>> rows = new ArrayList<List<String>>();
        int firstRowNum = sheet.getFirstRowNum();
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = firstRowNum; rowIndex <= lastRowNum; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            short lastCellNum = row.getLastCellNum();
            if (lastCellNum <= 0) {
                continue;
            }
            List<String> cells = new ArrayList<String>();
            for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                Cell cell = row.getCell(cellIndex);
                String value = cell == null ? "" : dataFormatter.formatCellValue(cell).trim();
                cells.add(value);
            }
            if (cells.stream().allMatch(String::isEmpty)) {
                continue;
            }
            rows.add(cells);
        }
        return rows;
    }

    /**
     * 将行数据转为 CSV 风格文本。
     *
     * @param rows 行数据
     * @return CSV 风格文本
     */
    private String toCsvLikeText(List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        for (List<String> cells : rows) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(String.join(",", cells));
        }
        return builder.toString();
    }

    /**
     * 为每一行生成带表头的紧凑索引文本，提升表格问答的精确召回。
     *
     * @param sheet 工作表
     * @param rows 行数据
     * @return 结构化行文本
     */
    private String toStructuredRowsText(Sheet sheet, List<List<String>> rows) {
        if (rows.size() <= 1) {
            return "";
        }
        List<String> headers = rows.get(0);
        StringBuilder builder = new StringBuilder();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.stream().allMatch(String::isEmpty)) {
                continue;
            }
            String rowText = buildStructuredRowText(sheet.getSheetName(), rowIndex + 1, headers, row);
            if (rowText.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("- ").append(rowText);
        }
        return builder.toString();
    }

    /**
     * 构建单行结构化文本。
     *
     * @param sheetName sheet 名称
     * @param excelRowNumber Excel 行号
     * @param headers 表头
     * @param row 行数据
     * @return 结构化行文本
     */
    private String buildStructuredRowText(
            String sheetName,
            int excelRowNumber,
            List<String> headers,
            List<String> row
    ) {
        List<String> parts = new ArrayList<String>();
        parts.add("sheet=" + sheetName);
        parts.add("row=" + excelRowNumber);
        int columnCount = Math.max(headers.size(), row.size());
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            String value = columnIndex < row.size() ? row.get(columnIndex) : "";
            if (value.isBlank()) {
                continue;
            }
            String header = columnIndex < headers.size() ? headers.get(columnIndex) : "";
            String normalizedHeader = normalizeHeader(header, columnIndex);
            String compactValue = compactCellValue(value);
            parts.add(normalizedHeader + "=" + compactValue);
        }
        return String.join("; ", parts);
    }

    /**
     * 归一化表头。
     *
     * @param header 原始表头
     * @param columnIndex 列序号
     * @return 表头
     */
    private String normalizeHeader(String header, int columnIndex) {
        if (header == null || header.isBlank()) {
            return "column_" + (columnIndex + 1);
        }
        return header.trim();
    }

    /**
     * 压缩单元格内容，避免长脚本把同一行的重要字段挤出检索窗口。
     *
     * @param value 单元格内容
     * @return 压缩后的内容
     */
    private String compactCellValue(String value) {
        String compacted = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (compacted.length() <= STRUCTURED_CELL_MAX_CHARS) {
            return compacted;
        }
        return compacted.substring(0, STRUCTURED_CELL_MAX_CHARS) + "...";
    }

    /**
     * 构建 Excel 元数据 JSON。
     *
     * @param sheetNames sheet 名列表
     * @return 元数据 JSON
     */
    private String buildMetadataJson(List<String> sheetNames) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("sheetCount", sheetNames.size());
        metadata.put("sheetNames", sheetNames);
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建 Excel metadata 失败", ex);
        }
    }
}
