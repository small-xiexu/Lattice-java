package com.xbk.lattice.documentparse.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
             WordExtractor extractor = new WordExtractor(document)) {
            String content = normalizeText(extractor.getText());
            if (content.isBlank()) {
                return null;
            }
            int paragraphCount = content.split("\\n\\n").length;
            return new SourceExtractionResult(content, buildMetadataJson(paragraphCount), true);
        }
    }

    private String normalizeText(String content) {
        return String.valueOf(content)
                .replace('\u000b', '\n')
                .replace('\r', '\n')
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String buildMetadataJson(int paragraphCount) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("paragraphCount", paragraphCount);
        metadata.put("legacyWord", Boolean.TRUE);
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建 DOC metadata 失败", ex);
        }
    }
}
