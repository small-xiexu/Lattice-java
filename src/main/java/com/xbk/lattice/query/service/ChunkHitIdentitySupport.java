package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.shared.json.JsonMappers;

/**
 * Chunk 命中身份支持
 *
 * 职责：为 chunk 级检索命中生成稳定身份与通用展示线索
 *
 * @author xiexu
 */
final class ChunkHitIdentitySupport {

    private static final String ARTICLE_CHUNK_PREFIX = "ARTICLE_CHUNK:";

    private static final int MAX_HEADING_LENGTH = 80;

    private ChunkHitIdentitySupport() {
    }

    /**
     * 构建 article chunk 稳定身份。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param chunkIndex chunk 序号
     * @return chunk 身份
     */
    static String articleChunkIdentity(String articleKey, String conceptId, Integer chunkIndex) {
        String articleIdentity = firstText(articleKey, conceptId);
        if (isBlank(articleIdentity) || chunkIndex == null) {
            return "";
        }
        return ARTICLE_CHUNK_PREFIX + articleIdentity + "#" + chunkIndex;
    }

    /**
     * 从命中 metadata 中解析 chunk 身份。
     *
     * @param metadataJson 元数据 JSON
     * @return chunk 身份
     */
    static String readChunkIdentity(String metadataJson) {
        JsonNode metadataNode = readMetadata(metadataJson);
        String chunkIdentity = readText(metadataNode, "chunkIdentity");
        if (!isBlank(chunkIdentity)) {
            return chunkIdentity;
        }
        String articleKey = readText(metadataNode, "articleKey");
        String conceptId = readText(metadataNode, "conceptId");
        Integer chunkIndex = readInteger(metadataNode, "chunkIndex");
        return articleChunkIdentity(articleKey, conceptId, chunkIndex);
    }

    /**
     * 给 metadata 补充 chunk 身份字段。
     *
     * @param metadataJson 原始 metadata
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param chunkIndex chunk 序号
     * @param channel 检索通道
     * @param sectionAnchor 章节锚点
     * @return 增强后的 metadata
     */
    static String enrichMetadata(
            String metadataJson,
            String articleKey,
            String conceptId,
            Integer chunkIndex,
            String channel,
            String sectionAnchor
    ) {
        ObjectNode rootNode = readObjectMetadata(metadataJson);
        if (!isBlank(channel)) {
            rootNode.put("channel", channel);
        }
        if (!isBlank(articleKey)) {
            rootNode.put("articleKey", articleKey);
        }
        if (!isBlank(conceptId)) {
            rootNode.put("conceptId", conceptId);
        }
        if (chunkIndex != null) {
            rootNode.put("chunkIndex", chunkIndex);
        }
        String chunkIdentity = articleChunkIdentity(articleKey, conceptId, chunkIndex);
        if (!isBlank(chunkIdentity)) {
            rootNode.put("chunkIdentity", chunkIdentity);
        }
        if (!isBlank(sectionAnchor)) {
            rootNode.put("sectionAnchor", sectionAnchor);
        }
        return writeMetadata(rootNode);
    }

    /**
     * 给 metadata 补充原 article metadata 与 chunk 身份。
     *
     * @param articleMetadataJson article 原始 metadata
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param chunkIndex chunk 序号
     * @param channel 检索通道
     * @param sectionAnchor 章节锚点
     * @return 增强后的 metadata
     */
    static String enrichArticleChunkMetadata(
            String articleMetadataJson,
            String articleKey,
            String conceptId,
            Integer chunkIndex,
            String channel,
            String sectionAnchor
    ) {
        ObjectNode rootNode = readObjectMetadata("{}");
        if (!isBlank(channel)) {
            rootNode.put("channel", channel);
        }
        if (!isBlank(articleKey)) {
            rootNode.put("articleKey", articleKey);
        }
        if (!isBlank(conceptId)) {
            rootNode.put("conceptId", conceptId);
        }
        if (chunkIndex != null) {
            rootNode.put("chunkIndex", chunkIndex);
        }
        String chunkIdentity = articleChunkIdentity(articleKey, conceptId, chunkIndex);
        if (!isBlank(chunkIdentity)) {
            rootNode.put("chunkIdentity", chunkIdentity);
        }
        if (!isBlank(sectionAnchor)) {
            rootNode.put("sectionAnchor", sectionAnchor);
        }
        JsonNode articleMetadataNode = readMetadata(articleMetadataJson);
        if (!articleMetadataNode.isMissingNode() && !articleMetadataNode.isNull()) {
            rootNode.set("articleMetadata", articleMetadataNode);
        }
        return writeMetadata(rootNode);
    }

    /**
     * 构建带章节锚点的展示标题。
     *
     * @param articleTitle article 标题
     * @param sectionAnchor 章节锚点
     * @return 展示标题
     */
    static String displayTitle(String articleTitle, String sectionAnchor) {
        if (isBlank(sectionAnchor)) {
            return articleTitle;
        }
        if (isBlank(articleTitle)) {
            return sectionAnchor;
        }
        if (articleTitle.equals(sectionAnchor)) {
            return articleTitle;
        }
        return articleTitle + " / " + sectionAnchor;
    }

    /**
     * 从 chunk 文本中提取通用 Markdown / 引用锚点标题。
     *
     * @param chunkText chunk 文本
     * @return 章节锚点
     */
    static String extractSectionAnchor(String chunkText) {
        if (isBlank(chunkText)) {
            return "";
        }
        String[] lines = chunkText.split("\\R");
        for (String line : lines) {
            String heading = extractHeadingFromLine(line);
            if (!isBlank(heading)) {
                return heading;
            }
        }
        return "";
    }

    /**
     * 从单行文本中提取标题。
     *
     * @param line 文本行
     * @return 标题
     */
    private static String extractHeadingFromLine(String line) {
        if (isBlank(line)) {
            return "";
        }
        String trimmedLine = line.trim();
        if (trimmedLine.startsWith("#")) {
            return normalizeHeading(trimmedLine.replaceFirst("^#{1,6}\\s*", ""));
        }
        int markerIndex = trimmedLine.indexOf(", ");
        if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]") && markerIndex >= 0) {
            return normalizeHeading(trimmedLine.substring(markerIndex + 2, trimmedLine.length() - 1));
        }
        return "";
    }

    /**
     * 规范化标题文本。
     *
     * @param heading 原始标题
     * @return 规范化标题
     */
    private static String normalizeHeading(String heading) {
        if (isBlank(heading)) {
            return "";
        }
        String normalizedHeading = heading.trim();
        while (normalizedHeading.endsWith("#")) {
            normalizedHeading = normalizedHeading.substring(0, normalizedHeading.length() - 1).trim();
        }
        if (normalizedHeading.length() <= MAX_HEADING_LENGTH) {
            return normalizedHeading;
        }
        return normalizedHeading.substring(0, MAX_HEADING_LENGTH).trim();
    }

    /**
     * 读取 metadata 对象。
     *
     * @param metadataJson metadata JSON
     * @return JSON 节点
     */
    private static JsonNode readMetadata(String metadataJson) {
        if (isBlank(metadataJson)) {
            return JsonMappers.defaultMapper().createObjectNode();
        }
        try {
            return JsonMappers.defaultMapper().readTree(metadataJson);
        }
        catch (Exception ignored) {
            return JsonMappers.defaultMapper().createObjectNode();
        }
    }

    /**
     * 读取 metadata 对象节点。
     *
     * @param metadataJson metadata JSON
     * @return 对象节点
     */
    private static ObjectNode readObjectMetadata(String metadataJson) {
        JsonNode metadataNode = readMetadata(metadataJson);
        if (metadataNode.isObject()) {
            return (ObjectNode) metadataNode.deepCopy();
        }
        return JsonMappers.defaultMapper().createObjectNode();
    }

    /**
     * 输出 metadata JSON。
     *
     * @param metadata 元数据
     * @return JSON 文本
     */
    private static String writeMetadata(ObjectNode metadata) {
        try {
            return JsonMappers.defaultMapper().writeValueAsString(metadata);
        }
        catch (Exception ignored) {
            return "{}";
        }
    }

    /**
     * 读取文本字段。
     *
     * @param metadataNode 元数据节点
     * @param fieldName 字段名
     * @return 文本值
     */
    private static String readText(JsonNode metadataNode, String fieldName) {
        JsonNode valueNode = metadataNode == null ? null : metadataNode.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return "";
        }
        return valueNode.asText("");
    }

    /**
     * 读取整数字段。
     *
     * @param metadataNode 元数据节点
     * @param fieldName 字段名
     * @return 整数值
     */
    private static Integer readInteger(JsonNode metadataNode, String fieldName) {
        JsonNode valueNode = metadataNode == null ? null : metadataNode.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isInt() || valueNode.isLong()) {
            return Integer.valueOf(valueNode.asInt());
        }
        String text = valueNode.asText("");
        if (isBlank(text)) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 返回首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本
     */
    private static String firstText(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 判断文本是否为空。
     *
     * @param value 文本
     * @return 为空返回 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
