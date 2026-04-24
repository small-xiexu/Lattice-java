package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 旧版 Word 文本抽取器
 *
 * 职责：把 `.doc` 文档抽取为规范化正文与元数据
 *
 * @author xiexu
 */
@Slf4j
public class DocTextExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 抽取 `.doc` 文本。
     *
     * @param docPath 文档路径
     * @return 抽取结果；无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public SourceExtractionResult extract(Path docPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(docPath);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor wordExtractor = new WordExtractor(document)) {
            LegacyDocTextSource textSource = new PoiLegacyDocTextSource(wordExtractor);
            String sourceName = docPath.toString();
            try {
                return extract(textSource, sourceName);
            }
            catch (RuntimeException ex) {
                throw new IOException("提取旧版 DOC 文本失败 path: " + docPath, ex);
            }
        }
    }

    /**
     * 基于可替换文本源抽取旧版 DOC 正文。
     *
     * @param textSource 文本源
     * @param sourceName 来源标识
     * @return 抽取结果；无有效正文时返回 null
     */
    SourceExtractionResult extract(LegacyDocTextSource textSource, String sourceName) {
        RuntimeException paragraphException = null;
        SourceExtractionResult paragraphResult = null;
        try {
            paragraphResult = extractFromParagraphs(textSource);
        }
        catch (RuntimeException ex) {
            paragraphException = ex;
        }
        if (paragraphResult != null) {
            return paragraphResult;
        }
        SourceExtractionResult piecesResult = extractFromTextPieces(textSource);
        if (piecesResult != null) {
            logFallback(sourceName, paragraphException);
            return piecesResult;
        }
        if (paragraphException != null) {
            throw paragraphException;
        }
        return null;
    }

    /**
     * 按段落优先提取旧版 DOC 正文。
     *
     * @param textSource 文本源
     * @return 抽取结果；无有效正文时返回 null
     */
    private SourceExtractionResult extractFromParagraphs(LegacyDocTextSource textSource) {
        String[] paragraphTexts = textSource.getParagraphText();
        StringBuilder contentBuilder = new StringBuilder();
        int paragraphCount = 0;
        for (String paragraphText : paragraphTexts) {
            String normalizedParagraph = normalizeText(paragraphText);
            if (normalizedParagraph.isBlank()) {
                continue;
            }
            appendSection(contentBuilder, normalizedParagraph);
            paragraphCount++;
        }
        if (contentBuilder.length() == 0) {
            return null;
        }
        String metadataJson = buildMetadataJson(paragraphCount, "paragraph_text");
        return new SourceExtractionResult(contentBuilder.toString(), metadataJson, true);
    }

    /**
     * 按 text pieces 降级提取旧版 DOC 正文。
     *
     * @param textSource 文本源
     * @return 抽取结果；无有效正文时返回 null
     */
    private SourceExtractionResult extractFromTextPieces(LegacyDocTextSource textSource) {
        String content = normalizeText(textSource.getTextFromPieces());
        if (content.isBlank()) {
            return null;
        }
        int paragraphCount = countLogicalParagraphs(content);
        String metadataJson = buildMetadataJson(paragraphCount, "text_pieces_fallback");
        return new SourceExtractionResult(content, metadataJson, true);
    }

    /**
     * 记录旧版 DOC 降级提取日志。
     *
     * @param sourceName 来源标识
     * @param paragraphException 段落提取异常
     */
    private void logFallback(String sourceName, RuntimeException paragraphException) {
        if (paragraphException != null) {
            String reason = paragraphException.getMessage();
            log.warn("Legacy DOC paragraph extraction degraded to text pieces path: {}, reason: {}", sourceName, reason);
            return;
        }
        log.warn("Legacy DOC paragraph extraction returned no usable content, fallback to text pieces path: {}", sourceName);
    }

    /**
     * 规范化文本。
     *
     * @param content 原始文本
     * @return 规范化文本
     */
    private String normalizeText(String content) {
        return String.valueOf(content)
                .replace('\u000b', '\n')
                .replace('\r', '\n')
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 向正文构建器追加段落。
     *
     * @param contentBuilder 正文构建器
     * @param sectionText 段落文本
     */
    private void appendSection(StringBuilder contentBuilder, String sectionText) {
        if (contentBuilder.length() > 0) {
            contentBuilder.append("\n\n");
        }
        contentBuilder.append(sectionText);
    }

    /**
     * 统计逻辑段落数。
     *
     * @param content 正文
     * @return 逻辑段落数
     */
    private int countLogicalParagraphs(String content) {
        if (content.isBlank()) {
            return 0;
        }
        return content.split("\\n\\n").length;
    }

    /**
     * 构建旧版 DOC 元数据 JSON。
     *
     * @param paragraphCount 段落数
     * @param extractionStrategy 抽取策略
     * @return 元数据 JSON
     */
    private String buildMetadataJson(int paragraphCount, String extractionStrategy) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("paragraphCount", paragraphCount);
        metadata.put("legacyWord", Boolean.TRUE);
        metadata.put("extractionStrategy", extractionStrategy);
        metadata.put("listFormattingPreserved", Boolean.FALSE);
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建 DOC metadata 失败", ex);
        }
    }
}

/**
 * 旧版 DOC 文本源
 *
 * 职责：为 `DocTextExtractor` 提供可替换的段落文本与降级文本读取能力
 *
 * @author xiexu
 */
interface LegacyDocTextSource {

    /**
     * 获取段落文本数组。
     *
     * @return 段落文本数组
     */
    String[] getParagraphText();

    /**
     * 获取 text pieces 文本。
     *
     * @return text pieces 文本
     */
    String getTextFromPieces();
}

/**
 * 基于 Apache POI 的旧版 DOC 文本源
 *
 * 职责：适配 `WordExtractor`，向 `DocTextExtractor` 暴露统一读取接口
 *
 * @author xiexu
 */
final class PoiLegacyDocTextSource implements LegacyDocTextSource {

    private final WordExtractor wordExtractor;

    /**
     * 创建 POI 旧版 DOC 文本源。
     *
     * @param wordExtractor Word 抽取器
     */
    PoiLegacyDocTextSource(WordExtractor wordExtractor) {
        this.wordExtractor = wordExtractor;
    }

    /**
     * 获取段落文本数组。
     *
     * @return 段落文本数组
     */
    @Override
    public String[] getParagraphText() {
        return wordExtractor.getParagraphText();
    }

    /**
     * 获取 text pieces 文本。
     *
     * @return text pieces 文本
     */
    @Override
    public String getTextFromPieces() {
        return wordExtractor.getTextFromPieces();
    }
}
