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
                String sheetText = toCsvLikeText(sheet, dataFormatter);
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
     * 将 sheet 转成 CSV 风格文本。
     *
     * @param sheet 工作表
     * @param dataFormatter 单元格格式化器
     * @return CSV 风格文本
     */
    private String toCsvLikeText(Sheet sheet, DataFormatter dataFormatter) {
        StringBuilder builder = new StringBuilder();
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
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(String.join(",", cells));
        }
        return builder.toString();
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
