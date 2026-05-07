package com.xbk.lattice.documentparse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
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
public class DocumentParseResultNormalizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 标准化为 RawSource。
     *
     * @param parseOutput 文档解析输出
     * @return RawSource
     */
    public RawSource normalize(ParseOutput parseOutput) {
        String metadataJson = mergeMetadata(parseOutput);
        return RawSource.parsed(
                parseOutput.getSourceId(),
                parseOutput.getRelativePath(),
                parseOutput.resolveContent(),
                parseOutput.getFormat(),
                parseOutput.getFileSize(),
                metadataJson,
                parseOutput.isVerbatim(),
                parseOutput.getRawPath(),
                parseOutput.getParseMode().getCode(),
                parseOutput.getParseProvider()
        );
    }

    /**
     * 合并统一元数据。
     *
     * @param parseOutput 文档解析输出
     * @return 合并后的元数据 JSON
     */
    private String mergeMetadata(ParseOutput parseOutput) {
        ObjectNode rootNode = parseMetadataObject(parseOutput.getMetadataJson());
        rootNode.put("relativePath", parseOutput.getRelativePath());
        rootNode.put("parseMode", parseOutput.getParseMode().getCode());
        rootNode.put("parseProvider", parseOutput.getParseProvider());
        rootNode.put("ocrApplied", parseOutput.getParseMode().getCode().startsWith("ocr_"));
        rootNode.put("contentFormat", parseOutput.resolveContentFormat());
        if (StringUtils.hasText(parseOutput.getStructuredContentJson())) {
            rootNode.put("structuredContentJson", parseOutput.getStructuredContentJson());
        }
        return rootNode.toString();
    }

    /**
     * 解析元数据对象。
     *
     * @param metadataJson 元数据 JSON
     * @return 元数据对象
     */
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
