package com.xbk.lattice.documentparse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.DocumentParseResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 文档解析结果标准化器
 *
 * 职责：把文档解析层结果转换为编译层统一消费的 RawSource 契约
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseResultNormalizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 标准化为 RawSource。
     *
     * @param result 文档解析结果
     * @return RawSource
     */
    public RawSource normalize(DocumentParseResult result) {
        String metadataJson = mergeMetadata(result);
        return RawSource.parsed(
                result.getSourceId(),
                result.getRelativePath(),
                result.getExtractedText(),
                result.getFormat(),
                result.getFileSize(),
                metadataJson,
                result.isVerbatim(),
                result.getRawPath(),
                result.getParseMode().getCode(),
                result.getParseProvider()
        );
    }

    private String mergeMetadata(DocumentParseResult result) {
        ObjectNode rootNode = parseMetadataObject(result.getMetadataJson());
        rootNode.put("relativePath", result.getRelativePath());
        rootNode.put("parseMode", result.getParseMode().getCode());
        rootNode.put("parseProvider", result.getParseProvider());
        rootNode.put("ocrApplied", result.getParseMode().getCode().startsWith("ocr_"));
        return rootNode.toString();
    }

    private ObjectNode parseMetadataObject(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(metadataJson);
            if (rootNode instanceof ObjectNode objectNode) {
                return objectNode.deepCopy();
            }
        }
        catch (Exception ignored) {
            // 兼容历史 metadata 不是对象的场景，降级为包装对象
        }
        ObjectNode wrapper = OBJECT_MAPPER.createObjectNode();
        wrapper.put("rawMetadata", metadataJson);
        return wrapper;
    }
}
