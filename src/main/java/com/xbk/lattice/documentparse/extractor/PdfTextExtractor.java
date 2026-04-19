package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
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
            StringBuilder contentBuilder = new StringBuilder();
            int pageCount = document.getNumberOfPages();
            for (int pageIndex = 1; pageIndex <= pageCount; pageIndex++) {
                textStripper.setStartPage(pageIndex);
                textStripper.setEndPage(pageIndex);
                String pageText = textStripper.getText(document).trim();
                if (pageText.isEmpty()) {
                    continue;
                }
                if (contentBuilder.length() > 0) {
                    contentBuilder.append("\n\n");
                }
                contentBuilder.append("=== Page: ").append(pageIndex).append(" ===").append("\n");
                contentBuilder.append(pageText);
            }
            if (contentBuilder.length() == 0) {
                return null;
            }
            return new SourceExtractionResult(contentBuilder.toString(), buildMetadataJson(pageCount), true);
        }
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
