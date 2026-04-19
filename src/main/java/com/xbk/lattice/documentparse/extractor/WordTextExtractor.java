package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Word 文本抽取器
 *
 * 职责：把 docx 文档抽取为按正文顺序组织的规范化文本与元数据
 *
 * @author xiexu
 */
public class WordTextExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 抽取 Word 文本。
     *
     * @param wordPath Word 路径
     * @return 抽取结果；无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public SourceExtractionResult extract(Path wordPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(wordPath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder contentBuilder = new StringBuilder();
            int paragraphCount = 0;
            int tableCount = 0;
            int tableIndex = 0;
            for (IBodyElement bodyElement : document.getBodyElements()) {
                if (bodyElement.getElementType() == BodyElementType.PARAGRAPH) {
                    XWPFParagraph paragraph = (XWPFParagraph) bodyElement;
                    String paragraphText = normalizeText(paragraph.getText());
                    if (paragraphText.isBlank()) {
                        continue;
                    }
                    appendSection(contentBuilder, paragraphText);
                    paragraphCount++;
                    continue;
                }
                if (bodyElement.getElementType() == BodyElementType.TABLE) {
                    XWPFTable table = (XWPFTable) bodyElement;
                    String tableText = normalizeTable(table);
                    if (tableText.isBlank()) {
                        continue;
                    }
                    tableIndex++;
                    appendSection(contentBuilder, "=== Table: " + tableIndex + " ===\n" + tableText);
                    tableCount++;
                }
            }
            if (contentBuilder.length() == 0) {
                return null;
            }
            return new SourceExtractionResult(contentBuilder.toString(), buildMetadataJson(paragraphCount, tableCount), true);
        }
    }

    /**
     * 规范化表格文本。
     *
     * @param table Word 表格
     * @return 表格文本
     */
    private String normalizeTable(XWPFTable table) {
        List<String> lines = new ArrayList<String>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<String>();
            row.getTableCells().forEach(cell -> cells.add(normalizeText(cell.getText())));
            boolean allBlank = cells.stream().allMatch(String::isBlank);
            if (!allBlank) {
                lines.add(String.join(" | ", cells));
            }
        }
        return String.join("\n", lines).trim();
    }

    /**
     * 向正文中追加分段。
     *
     * @param contentBuilder 正文构建器
     * @param sectionText 分段文本
     */
    private void appendSection(StringBuilder contentBuilder, String sectionText) {
        if (contentBuilder.length() > 0) {
            contentBuilder.append("\n\n");
        }
        contentBuilder.append(sectionText);
    }

    /**
     * 规范化文本。
     *
     * @param value 原始文本
     * @return 规范化文本
     */
    private String normalizeText(String value) {
        return String.valueOf(value)
                .replace('\u000b', '\n')
                .replace('\r', '\n')
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 构建 Word 元数据 JSON。
     *
     * @param paragraphCount 段落数
     * @param tableCount 表格数
     * @return 元数据 JSON
     */
    private String buildMetadataJson(int paragraphCount, int tableCount) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("paragraphCount", paragraphCount);
        metadata.put("tableCount", tableCount);
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建 Word metadata 失败", ex);
        }
    }
}
