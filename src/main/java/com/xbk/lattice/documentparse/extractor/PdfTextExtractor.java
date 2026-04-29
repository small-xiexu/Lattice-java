package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF 文本抽取器
 *
 * 职责：把文本型 PDF 抽取为按页组织的规范化正文与元数据
 *
 * @author xiexu
 */
public class PdfTextExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PaginatedTextCleaner paginatedTextCleaner = new PaginatedTextCleaner();

    private final PdfPositionedTextTableExtractor tableExtractor = new PdfPositionedTextTableExtractor();

    /**
     * 抽取 PDF 文本。
     *
     * @param pdfPath PDF 路径
     * @return 抽取结果；无可提取文本时返回 null
     * @throws IOException IO 异常
     */
    public SourceExtractionResult extract(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            List<String> pageTexts = new ArrayList<String>();
            List<String> pageTableTexts = tableExtractor.extractPageTables(document);
            int pageCount = document.getNumberOfPages();
            for (int pageIndex = 1; pageIndex <= pageCount; pageIndex++) {
                textStripper.setStartPage(pageIndex);
                textStripper.setEndPage(pageIndex);
                String rawPageText = textStripper.getText(document).trim();
                String pageTableText = pageTableTexts.get(pageIndex - 1);
                String pageText = combinePageText(rawPageText, pageTableText);
                pageTexts.add(pageText);
            }
            String content = paginatedTextCleaner.cleanAndJoin(pageTexts);
            if (content.isBlank()) {
                return null;
            }
            return new SourceExtractionResult(content, buildMetadataJson(pageCount), true);
        }
    }

    /**
     * 合并普通文本与坐标表格补充文本。
     *
     * @param rawPageText 普通文本
     * @param pageTableText 坐标表格补充文本
     * @return 合并后的单页文本
     */
    private String combinePageText(String rawPageText, String pageTableText) {
        if (pageTableText == null || pageTableText.isBlank()) {
            return rawPageText == null ? "" : rawPageText;
        }
        if (rawPageText == null || rawPageText.isBlank()) {
            return pageTableText.trim();
        }
        return rawPageText.trim() + "\n\n" + pageTableText.trim();
    }

    /**
     * 构建 PDF 元数据 JSON。
     *
     * @param pageCount 页数
     * @return 元数据 JSON
     */
    private String buildMetadataJson(int pageCount) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("pageCount", pageCount);
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建 PDF metadata 失败", ex);
        }
    }
}
