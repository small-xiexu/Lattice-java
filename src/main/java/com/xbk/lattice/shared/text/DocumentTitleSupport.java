package com.xbk.lattice.shared.text;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档标题提取支撑工具。
 *
 * 职责：统一从文本内容、解析 metadata 与文件名中提取单文件级 documentTitle
 *
 * @author xiexu
 */
public final class DocumentTitleSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private static final Yaml YAML = new Yaml();

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("\\A---\\R(.*?)\\R---\\R?.*\\z", Pattern.DOTALL);

    private DocumentTitleSupport() {
    }

    /**
     * 为文本类文件解析 documentTitle。
     *
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param metadataJson 现有 metadata JSON
     * @return 解析后的 documentTitle
     */
    public static String resolveTextDocumentTitle(String relativePath, String content, String metadataJson) {
        String frontmatterTitle = extractFrontmatterTitle(content);
        if (hasText(frontmatterTitle)) {
            return frontmatterTitle;
        }
        String topLevelHeadingTitle = extractFirstTopLevelHeadingTitle(content);
        if (hasText(topLevelHeadingTitle)) {
            return topLevelHeadingTitle;
        }
        return resolveDocumentTitle(relativePath, metadataJson);
    }

    /**
     * 基于解析 metadata 与文件名解析 documentTitle。
     *
     * @param relativePath 相对路径
     * @param metadataJson 现有 metadata JSON
     * @return 解析后的 documentTitle
     */
    public static String resolveDocumentTitle(String relativePath, String metadataJson) {
        String metadataTitle = extractMetadataTitle(metadataJson);
        if (hasText(metadataTitle)) {
            return metadataTitle;
        }
        return deriveFileNameTitle(relativePath);
    }

    /**
     * 仅从 metadata 中提取标题候选，不做文件名回退。
     *
     * @param metadataJson metadata JSON
     * @return metadata 标题候选
     */
    public static String resolveMetadataDocumentTitle(String metadataJson) {
        return extractMetadataTitle(metadataJson);
    }

    /**
     * 从路径派生文件名标题。
     *
     * @param relativePath 相对路径
     * @return 去扩展名后的文件名
     */
    public static String resolveFileNameTitle(String relativePath) {
        return deriveFileNameTitle(relativePath);
    }

    /**
     * 将 documentTitle 写回 metadata JSON。
     *
     * @param metadataJson 原始 metadata JSON
     * @param documentTitle 文档标题
     * @return 写回后的 metadata JSON
     */
    public static String upsertDocumentTitle(String metadataJson, String documentTitle) {
        ObjectNode metadataNode = parseMetadataObject(metadataJson);
        if (hasText(documentTitle)) {
            metadataNode.put("documentTitle", documentTitle);
        }
        return metadataNode.toString();
    }

    /**
     * 从 frontmatter 中提取标题。
     *
     * @param content 文件内容
     * @return frontmatter 标题；不存在时返回空串
     */
    private static String extractFrontmatterTitle(String content) {
        if (!hasText(content)) {
            return "";
        }
        String normalizedContent = normalizeLineEndings(content).trim();
        Matcher matcher = FRONTMATTER_PATTERN.matcher(normalizedContent);
        if (!matcher.matches()) {
            return "";
        }
        Map<String, Object> frontmatterValues = parseFrontmatterValues(matcher.group(1));
        return readStringValue(frontmatterValues.get("title"));
    }

    /**
     * 提取第一个顶级 H1 标题。
     *
     * @param content 文件内容
     * @return H1 标题；不存在时返回空串
     */
    private static String extractFirstTopLevelHeadingTitle(String content) {
        if (!hasText(content)) {
            return "";
        }
        for (String line : splitLines(content)) {
            String trimmedLine = line == null ? "" : line.trim();
            if (!trimmedLine.startsWith("# ")) {
                continue;
            }
            String heading = trimmedLine.substring(2).replaceAll("\\s+#+\\s*$", "").trim();
            if (hasText(heading)) {
                return heading;
            }
        }
        return "";
    }

    /**
     * 从 metadata 中提取标题候选。
     *
     * @param metadataJson metadata JSON
     * @return 标题候选；不存在时返回空串
     */
    private static String extractMetadataTitle(String metadataJson) {
        ObjectNode metadataNode = parseMetadataObject(metadataJson);
        List<String> directFields = List.of("documentTitle", "title", "primaryTitle", "displayName");
        for (String fieldName : directFields) {
            String title = readJsonText(metadataNode.get(fieldName));
            if (hasText(title)) {
                return title;
            }
        }
        List<String> arrayFields = List.of("titleHints", "slideTitles", "sheetNames");
        for (String fieldName : arrayFields) {
            String title = extractArrayFirstText(metadataNode, fieldName);
            if (hasText(title)) {
                return title;
            }
        }
        return "";
    }

    /**
     * 从数组字段中读取首个非空文本。
     *
     * @param metadataNode metadata 对象
     * @param fieldName 字段名
     * @return 首个非空文本；不存在时返回空串
     */
    private static String extractArrayFirstText(ObjectNode metadataNode, String fieldName) {
        if (metadataNode == null || fieldName == null || fieldName.isBlank()) {
            return "";
        }
        JsonNode arrayNode = metadataNode.get(fieldName);
        if (arrayNode == null || !arrayNode.isArray()) {
            return "";
        }
        for (JsonNode itemNode : arrayNode) {
            String value = readJsonText(itemNode);
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 解析 metadata JSON 为对象节点。
     *
     * @param metadataJson metadata JSON
     * @return 可写对象节点
     */
    private static ObjectNode parseMetadataObject(String metadataJson) {
        if (!hasText(metadataJson)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(metadataJson);
            if (rootNode instanceof ObjectNode objectNode) {
                return objectNode.deepCopy();
            }
        }
        catch (Exception ignored) {
            // 兼容历史 metadata 非对象场景，降级为包装对象
        }
        ObjectNode wrapperNode = OBJECT_MAPPER.createObjectNode();
        wrapperNode.put("rawMetadata", metadataJson);
        return wrapperNode;
    }

    /**
     * 从路径派生文件名标题。
     *
     * @param relativePath 相对路径
     * @return 去扩展名后的文件名
     */
    private static String deriveFileNameTitle(String relativePath) {
        if (!hasText(relativePath)) {
            return "";
        }
        String normalizedPath = relativePath.replace('\\', '/').trim();
        int slashIndex = normalizedPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            return fileName.substring(0, extensionIndex).trim();
        }
        return fileName.trim();
    }

    /**
     * 解析 frontmatter 字段。
     *
     * @param rawFrontmatter frontmatter 文本
     * @return 归一后的字段映射
     */
    private static Map<String, Object> parseFrontmatterValues(String rawFrontmatter) {
        if (!hasText(rawFrontmatter)) {
            return Map.of();
        }
        try {
            Object parsed = YAML.load(rawFrontmatter);
            if (!(parsed instanceof Map<?, ?> parsedMap)) {
                return Map.of();
            }
            Map<String, Object> normalizedValues = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalizedValues.put(String.valueOf(entry.getKey()).trim().toLowerCase(), entry.getValue());
            }
            return normalizedValues;
        }
        catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * 将文本拆分为逻辑行。
     *
     * @param content 原始文本
     * @return 行集合
     */
    private static List<String> splitLines(String content) {
        String normalizedContent = normalizeLineEndings(content);
        return List.of(normalizedContent.split("\n", -1));
    }

    /**
     * 归一换行符。
     *
     * @param content 原始文本
     * @return 使用 LF 的文本
     */
    private static String normalizeLineEndings(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 读取对象值为字符串。
     *
     * @param value 原始值
     * @return 规范化字符串
     */
    private static String readStringValue(Object value) {
        if (value == null) {
            return "";
        }
        String normalizedValue = String.valueOf(value).trim();
        return StringUtils.hasText(normalizedValue) ? normalizedValue : "";
    }

    /**
     * 读取 JsonNode 的文本值。
     *
     * @param jsonNode JSON 节点
     * @return 文本值
     */
    private static String readJsonText(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull() || jsonNode.isContainerNode()) {
            return "";
        }
        return readStringValue(jsonNode.asText(""));
    }

    /**
     * 判断字符串是否有效。
     *
     * @param value 字符串
     * @return 有效时返回 true
     */
    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
