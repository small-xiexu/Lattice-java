package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PPT 文本抽取器
 *
 * 职责：把 pptx 演示文稿抽取为按页组织的规范化文本与元数据
 *
 * @author xiexu
 */
public class PptTextExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 抽取 PPT 文本。
     *
     * @param pptPath PPT 路径
     * @return 抽取结果；无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public SourceExtractionResult extract(Path pptPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(pptPath);
             XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            StringBuilder contentBuilder = new StringBuilder();
            int extractedSlideCount = 0;
            List<String> slideTitles = new ArrayList<String>();
            List<XSLFSlide> slides = slideShow.getSlides();
            for (int slideIndex = 0; slideIndex < slides.size(); slideIndex++) {
                XSLFSlide slide = slides.get(slideIndex);
                List<String> lines = new ArrayList<String>();
                for (XSLFShape shape : slide.getShapes()) {
                    collectShapeText(shape, lines);
                }
                lines.removeIf(String::isBlank);
                if (lines.isEmpty()) {
                    continue;
                }
                extractedSlideCount++;
                String title = normalizeText(slide.getTitle());
                if (!title.isBlank()) {
                    slideTitles.add(title);
                }
                if (contentBuilder.length() > 0) {
                    contentBuilder.append("\n\n");
                }
                contentBuilder.append("=== Slide: ").append(slideIndex + 1).append(" ===").append("\n");
                contentBuilder.append(String.join("\n", lines));
            }
            if (contentBuilder.length() == 0) {
                return null;
            }
            return new SourceExtractionResult(
                    contentBuilder.toString(),
                    buildMetadataJson(slides.size(), extractedSlideCount, slideTitles),
                    true
            );
        }
    }

    /**
     * 收集单个形状中的文本。
     *
     * @param shape 形状
     * @param lines 文本行集合
     */
    private void collectShapeText(XSLFShape shape, List<String> lines) {
        if (shape instanceof XSLFTextShape textShape) {
            String text = normalizeText(textShape.getText());
            if (!text.isBlank()) {
                lines.add(text);
            }
            return;
        }
        if (shape instanceof XSLFTable table) {
            String tableText = normalizeTable(table);
            if (!tableText.isBlank()) {
                lines.add(tableText);
            }
        }
    }

    /**
     * 规范化表格文本。
     *
     * @param table PPT 表格
     * @return 表格文本
     */
    private String normalizeTable(XSLFTable table) {
        List<String> rows = new ArrayList<String>();
        for (XSLFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<String>();
            for (XSLFTableCell cell : row.getCells()) {
                cells.add(normalizeText(cell.getText()));
            }
            boolean allBlank = cells.stream().allMatch(String::isBlank);
            if (!allBlank) {
                rows.add(String.join(" | ", cells));
            }
        }
        return String.join("\n", rows).trim();
    }

    /**
     * 规范化文本。
     *
     * @param value 原始文本
     * @return 规范化文本
     */
    private String normalizeText(String value) {
        return String.valueOf(value)
                .replace('\r', '\n')
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 构建 PPT 元数据 JSON。
     *
     * @param slideCount 总页数
     * @param extractedSlideCount 有文本页数
     * @param slideTitles 标题列表
     * @return 元数据 JSON
     */
    private String buildMetadataJson(int slideCount, int extractedSlideCount, List<String> slideTitles) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("slideCount", slideCount);
        metadata.put("extractedSlideCount", extractedSlideCount);
        metadata.put("slideTitles", slideTitles);
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建 PPT metadata 失败", ex);
        }
    }
}
