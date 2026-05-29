package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import com.xbk.lattice.shared.json.JsonMappers;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实证据卡终端字段证据单元物化器
 *
 * 职责：从结构化 fact card items_json 中展开通用 scalar terminal assignment
 *
 * @author xiexu
 */
@Service
public class FactCardTerminalUnitMaterializer {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.moduleAwareMapper();

    private static final String TERMINAL_UNIT_IDENTITY_PREFIX = "terminal-unit:";

    private static final int MAX_VALUE_LENGTH = 240;

    private static final Pattern CAMEL_PART_PATTERN = Pattern.compile("[A-Z]?[a-z0-9]+|[A-Z]+(?=[A-Z]|$)");

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)^v?\\d+(?:\\.\\d+){1,3}(?:[-+][A-Za-z0-9._-]+)?$");

    private static final Pattern DATE_LIKE_PATTERN = Pattern.compile("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$");

    private static final Pattern BRACKET_CONTENT_PATTERN = Pattern.compile("[（(][^）)]*[）)]");

    private static final Pattern CJK_RUN_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]{2,}");

    /**
     * 从事实证据卡展开终端字段证据单元。
     *
     * @param factCardRecord 已持久化事实证据卡
     * @return 终端字段证据单元列表
     */
    public List<FactCardTerminalUnitRecord> materialize(FactCardRecord factCardRecord) {
        if (!isEligibleFactCard(factCardRecord)) {
            return List.of();
        }
        JsonNode rootNode = readItemsJson(factCardRecord.getItemsJson());
        if (rootNode.isMissingNode() || rootNode.isNull()) {
            return List.of();
        }
        String structure = textValue(rootNode, "structure");
        if (!"key_value_list".equals(structure)) {
            return List.of();
        }
        JsonNode itemsNode = rootNode.path("items");
        if (!itemsNode.isArray()) {
            return List.of();
        }
        List<FactCardTerminalUnitRecord> records = new ArrayList<FactCardTerminalUnitRecord>();
        for (int index = 0; index < itemsNode.size(); index++) {
            JsonNode itemNode = itemsNode.get(index);
            if (!itemNode.isObject()) {
                continue;
            }
            FactCardTerminalUnitRecord record = materializeItem(factCardRecord, structure, itemNode, index);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    /**
     * 判断事实卡是否属于本阶段可展开范围。
     *
     * @param factCardRecord 事实证据卡
     * @return 可展开返回 true
     */
    private boolean isEligibleFactCard(FactCardRecord factCardRecord) {
        return factCardRecord != null
                && factCardRecord.getId() != null
                && factCardRecord.getCardType() == FactCardType.FACT_ENUM
                && hasText(factCardRecord.getCardId())
                && hasText(factCardRecord.getItemsJson());
    }

    /**
     * 从单个 item 生成终端字段证据单元。
     *
     * @param factCardRecord 事实证据卡
     * @param structure 来源结构
     * @param itemNode item JSON
     * @param itemIndex item 序号
     * @return 终端字段证据单元；不符合条件时返回 null
     */
    private FactCardTerminalUnitRecord materializeItem(
            FactCardRecord factCardRecord,
            String structure,
            JsonNode itemNode,
            int itemIndex
    ) {
        String valueText = normalizeValue(textValue(itemNode, "value"));
        if (shouldSkipValue(valueText)) {
            return null;
        }
        String terminalKey = firstText(textValue(itemNode, "key"), lastPathSegment(textValue(itemNode, "keyPath")));
        if (!hasText(terminalKey)) {
            return null;
        }
        String keyPath = firstText(textValue(itemNode, "keyPath"), terminalKey);
        String parentPath = textValue(itemNode, "parentPath");
        String displayText = firstText(textValue(itemNode, "displayText"), keyPath + " = " + valueText);
        String normalizedValue = normalizeValue(valueText);
        String valueType = inferValueType(normalizedValue);
        List<String> pathSegments = readPathSegments(itemNode.path("pathSegments"));
        if (pathSegments.isEmpty()) {
            pathSegments = readPathSegmentsFromKeyPath(keyPath);
        }
        String pathSegmentsJson = writeJsonArray(pathSegments);
        String fieldLabel = terminalKey;
        List<String> fieldAliases = buildFieldAliases(fieldLabel, keyPath, parentPath, pathSegments);
        String fieldAliasesJson = writeJsonArray(fieldAliases);
        String fieldDescription = buildFieldDescription(parentPath, fieldLabel, valueType);
        String sourceRefsJson = buildSourceRefsJson(factCardRecord, itemNode, itemIndex);
        String ftsText = buildFtsText(
                factCardRecord,
                structure,
                fieldLabel,
                fieldAliases,
                keyPath,
                parentPath,
                terminalKey,
                displayText,
                valueText,
                normalizedValue,
                valueType,
                fieldDescription
        );
        String contentHash = sha256Hex(String.join(
                "\n",
                factCardRecord.getCardId(),
                structure,
                keyPath,
                normalizedValue,
                displayText
        ));
        String unitId = buildUnitId(factCardRecord, itemIndex, keyPath, normalizedValue, contentHash);
        String terminalUnitIdentity = TERMINAL_UNIT_IDENTITY_PREFIX + unitId;
        String metadataJson = buildMetadataJson(
                factCardRecord,
                structure,
                itemIndex,
                unitId,
                terminalUnitIdentity,
                keyPath,
                parentPath,
                terminalKey,
                pathSegments,
                fieldLabel,
                fieldAliases,
                fieldDescription,
                valueText,
                normalizedValue,
                valueType,
                displayText,
                sourceRefsJson
        );
        FactCardReviewStatus reviewStatus = factCardRecord.getReviewStatus() == null
                ? FactCardReviewStatus.LOW_CONFIDENCE
                : factCardRecord.getReviewStatus();
        return new FactCardTerminalUnitRecord(
                unitId,
                terminalUnitIdentity,
                factCardRecord.getId(),
                factCardRecord.getCardId(),
                factCardRecord.getSourceId(),
                factCardRecord.getSourceFileId(),
                factCardRecord.getSourceChunkIds(),
                factCardRecord.getArticleIds(),
                factCardRecord.getCardType(),
                factCardRecord.getAnswerShape(),
                structure,
                itemIndex,
                keyPath,
                parentPath,
                terminalKey,
                pathSegmentsJson,
                fieldLabel,
                fieldAliasesJson,
                fieldDescription,
                displayText,
                valueText,
                normalizedValue,
                valueType,
                sourceRefsJson,
                ftsText,
                metadataJson,
                reviewStatus,
                factCardRecord.getConfidence(),
                contentHash
        );
    }

    /**
     * 判断值是否应跳过。
     *
     * @param valueText 值文本
     * @return 应跳过返回 true
     */
    private boolean shouldSkipValue(String valueText) {
        if (!hasText(valueText)) {
            return true;
        }
        if (valueText.length() > MAX_VALUE_LENGTH) {
            return true;
        }
        String normalizedValue = valueText.trim();
        return "{}".equals(normalizedValue)
                || "[]".equals(normalizedValue)
                || normalizedValue.startsWith("{")
                || normalizedValue.startsWith("[");
    }

    /**
     * 构建字段别名。
     *
     * @param fieldLabel 字段展示名
     * @param keyPath 完整路径
     * @param parentPath 父级路径
     * @param pathSegments 路径片段
     * @return 字段别名列表
     */
    private List<String> buildFieldAliases(
            String fieldLabel,
            String keyPath,
            String parentPath,
            List<String> pathSegments
    ) {
        Set<String> aliases = new LinkedHashSet<String>();
        addAlias(aliases, fieldLabel);
        addAlias(aliases, keyPath);
        addAlias(aliases, parentPath);
        addAlias(aliases, parentTailWithLabel(parentPath, fieldLabel));
        for (String pathSegment : pathSegments) {
            addAlias(aliases, pathSegment);
        }
        addSplitAliases(aliases, fieldLabel);
        addSplitAliases(aliases, keyPath);
        addChineseNgramAliases(aliases, fieldLabel);
        addChineseNgramAliases(aliases, keyPath);
        return new ArrayList<String>(aliases);
    }

    /**
     * 添加字段别名。
     *
     * @param aliases 别名集合
     * @param value 候选文本
     */
    private void addAlias(Set<String> aliases, String value) {
        if (!hasText(value)) {
            return;
        }
        String normalized = value.trim();
        aliases.add(normalized);
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        aliases.add(lowerCase);
        aliases.add(lowerCase.replace('_', ' '));
        aliases.add(lowerCase.replace('-', ' '));
        aliases.add(lowerCase.replace('.', ' '));
    }

    /**
     * 添加通用拆词别名。
     *
     * @param aliases 别名集合
     * @param value 候选文本
     */
    private void addSplitAliases(Set<String> aliases, String value) {
        if (!hasText(value)) {
            return;
        }
        String[] simpleParts = value.split("[._\\-\\s]+");
        for (String simplePart : simpleParts) {
            addTokenAlias(aliases, simplePart);
            Matcher matcher = CAMEL_PART_PATTERN.matcher(simplePart);
            while (matcher.find()) {
                addTokenAlias(aliases, matcher.group());
            }
        }
    }

    /**
     * 添加单个 token 别名。
     *
     * @param aliases 别名集合
     * @param token token
     */
    private void addTokenAlias(Set<String> aliases, String token) {
        if (!hasText(token)) {
            return;
        }
        String normalized = token.trim();
        if (normalized.length() < 2 || normalized.startsWith("[")) {
            return;
        }
        aliases.add(normalized);
        aliases.add(normalized.toLowerCase(Locale.ROOT));
    }

    /**
     * 对包含中文字符的文本生成 bigram + trigram 别名。
     *
     * 只对 2-8 个中文字符的纯中文片段做 N-gram，跳过单字和超长句子。
     * 括号内内容（如 "(天)"）会先被移除。
     *
     * @param aliases 别名集合
     * @param value 候选文本
     */
    private void addChineseNgramAliases(Set<String> aliases, String value) {
        if (!hasText(value)) {
            return;
        }
        String cleaned = BRACKET_CONTENT_PATTERN.matcher(value).replaceAll("");
        Matcher cjkRunMatcher = CJK_RUN_PATTERN.matcher(cleaned);
        while (cjkRunMatcher.find()) {
            String cjkRun = cjkRunMatcher.group();
            if (cjkRun.length() < 2 || cjkRun.length() > 8) {
                continue;
            }
            aliases.add(cjkRun);
            for (int i = 0; i + 2 <= cjkRun.length(); i++) {
                aliases.add(cjkRun.substring(i, i + 2));
            }
            for (int i = 0; i + 3 <= cjkRun.length(); i++) {
                aliases.add(cjkRun.substring(i, i + 3));
            }
        }
    }

    /**
     * 构建父路径末段加字段名别名。
     *
     * @param parentPath 父路径
     * @param fieldLabel 字段展示名
     * @return 组合别名
     */
    private String parentTailWithLabel(String parentPath, String fieldLabel) {
        if (!hasText(parentPath) || !hasText(fieldLabel)) {
            return "";
        }
        String tail = lastPathSegment(parentPath);
        if (!hasText(tail)) {
            return "";
        }
        return tail + " " + fieldLabel;
    }

    /**
     * 构建字段上下文描述。
     *
     * @param parentPath 父路径
     * @param fieldLabel 字段展示名
     * @param valueType 值形态
     * @return 字段上下文描述
     */
    private String buildFieldDescription(String parentPath, String fieldLabel, String valueType) {
        List<String> parts = new ArrayList<String>();
        if (hasText(parentPath)) {
            parts.add("parentPath: " + parentPath);
        }
        if (hasText(fieldLabel)) {
            parts.add("field: " + fieldLabel);
        }
        if (hasText(valueType)) {
            parts.add("valueType: " + valueType);
        }
        return String.join("; ", parts);
    }

    /**
     * 构建 FTS 检索文本。
     *
     * @param factCardRecord 事实卡
     * @param structure 来源结构
     * @param fieldLabel 字段展示名
     * @param fieldAliases 字段别名
     * @param keyPath 完整路径
     * @param parentPath 父路径
     * @param terminalKey 末级字段
     * @param displayText 展示文本
     * @param valueText 原始值
     * @param normalizedValue 归一化值
     * @param valueType 值形态
     * @param fieldDescription 字段描述
     * @return 检索文本
     */
    private String buildFtsText(
            FactCardRecord factCardRecord,
            String structure,
            String fieldLabel,
            List<String> fieldAliases,
            String keyPath,
            String parentPath,
            String terminalKey,
            String displayText,
            String valueText,
            String normalizedValue,
            String valueType,
            String fieldDescription
    ) {
        return String.join(
                " ",
                factCardRecord.getCardType().name(),
                factCardRecord.getAnswerShape().name(),
                safeText(structure),
                safeText(fieldLabel),
                String.join(" ", fieldAliases),
                safeText(keyPath),
                safeText(parentPath),
                safeText(terminalKey),
                safeText(displayText),
                safeText(valueText),
                safeText(normalizedValue),
                safeText(valueType),
                safeText(fieldDescription)
        ).trim();
    }

    /**
     * 构建来源回指 JSON。
     *
     * @param factCardRecord 事实卡
     * @param itemNode item JSON
     * @param itemIndex item 序号
     * @return 来源回指 JSON
     */
    private String buildSourceRefsJson(FactCardRecord factCardRecord, JsonNode itemNode, int itemIndex) {
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        putLong(rootNode, "sourceFileId", factCardRecord.getSourceFileId());
        ArrayNode sourceChunkIdsNode = rootNode.putArray("sourceChunkIds");
        for (Long sourceChunkId : factCardRecord.getSourceChunkIds()) {
            if (sourceChunkId != null) {
                sourceChunkIdsNode.add(sourceChunkId.longValue());
            }
        }
        rootNode.put("itemIndex", itemIndex);
        int lineIndex = intValue(itemNode, "lineIndex", -1);
        if (lineIndex >= 0) {
            rootNode.put("lineIndex", lineIndex);
        }
        String raw = textValue(itemNode, "raw");
        if (hasText(raw)) {
            rootNode.put("raw", truncate(raw, MAX_VALUE_LENGTH));
        }
        return writeJsonObject(rootNode);
    }

    /**
     * 构建查询 metadata JSON。
     *
     * @param factCardRecord 事实卡
     * @param structure 来源结构
     * @param itemIndex item 序号
     * @param unitId unit 标识
     * @param terminalUnitIdentity 检索融合身份
     * @param keyPath 完整路径
     * @param parentPath 父路径
     * @param terminalKey 末级字段
     * @param pathSegments 路径片段
     * @param fieldLabel 字段展示名
     * @param fieldAliases 字段别名
     * @param fieldDescription 字段描述
     * @param valueText 原始值
     * @param normalizedValue 归一化值
     * @param valueType 值形态
     * @param displayText 展示文本
     * @param sourceRefsJson 来源回指 JSON
     * @return metadata JSON
     */
    private String buildMetadataJson(
            FactCardRecord factCardRecord,
            String structure,
            int itemIndex,
            String unitId,
            String terminalUnitIdentity,
            String keyPath,
            String parentPath,
            String terminalKey,
            List<String> pathSegments,
            String fieldLabel,
            List<String> fieldAliases,
            String fieldDescription,
            String valueText,
            String normalizedValue,
            String valueType,
            String displayText,
            String sourceRefsJson
    ) {
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        rootNode.put("channel", "fact_card_terminal_fts");
        rootNode.putNull("terminalUnitId");
        rootNode.put("unitId", unitId);
        rootNode.put("terminalUnitIdentity", terminalUnitIdentity);
        putLong(rootNode, "factCardId", factCardRecord.getId());
        rootNode.put("cardId", factCardRecord.getCardId());
        putLong(rootNode, "sourceFileId", factCardRecord.getSourceFileId());
        rootNode.put("cardType", factCardRecord.getCardType().name());
        rootNode.put("answerShape", factCardRecord.getAnswerShape().name());
        rootNode.put("structure", safeText(structure));
        rootNode.put("itemIndex", itemIndex);
        rootNode.put("keyPath", safeText(keyPath));
        rootNode.put("parentPath", safeText(parentPath));
        rootNode.put("terminalKey", safeText(terminalKey));
        rootNode.put("fieldLabel", safeText(fieldLabel));
        rootNode.put("fieldDescription", safeText(fieldDescription));
        rootNode.put("value", safeText(valueText));
        rootNode.put("normalizedValue", safeText(normalizedValue));
        rootNode.put("valueType", safeText(valueType));
        rootNode.put("displayText", safeText(displayText));
        ArrayNode pathSegmentsNode = rootNode.putArray("pathSegments");
        for (String pathSegment : pathSegments) {
            pathSegmentsNode.add(pathSegment);
        }
        ArrayNode fieldAliasesNode = rootNode.putArray("fieldAliases");
        for (String fieldAlias : fieldAliases) {
            fieldAliasesNode.add(fieldAlias);
        }
        ArrayNode sourceChunkIdsNode = rootNode.putArray("sourceChunkIds");
        for (Long sourceChunkId : factCardRecord.getSourceChunkIds()) {
            if (sourceChunkId != null) {
                sourceChunkIdsNode.add(sourceChunkId.longValue());
            }
        }
        ArrayNode articleIdsNode = rootNode.putArray("articleIds");
        for (Long articleId : factCardRecord.getArticleIds()) {
            if (articleId != null) {
                articleIdsNode.add(articleId.longValue());
            }
        }
        JsonNode sourceRefsNode = readJson(sourceRefsJson);
        rootNode.set("sourceRefs", sourceRefsNode);
        return writeJsonObject(rootNode);
    }

    /**
     * 构建稳定 unit id。
     *
     * @param factCardRecord 事实卡
     * @param itemIndex item 序号
     * @param keyPath 完整路径
     * @param normalizedValue 归一化值
     * @param contentHash 内容哈希
     * @return 稳定 unit id
     */
    private String buildUnitId(
            FactCardRecord factCardRecord,
            int itemIndex,
            String keyPath,
            String normalizedValue,
            String contentHash
    ) {
        String identityText = String.join(
                "\n",
                factCardRecord.getCardId(),
                String.valueOf(itemIndex),
                safeText(keyPath),
                safeText(normalizedValue),
                safeText(contentHash)
        );
        String hash = sha256Hex(identityText).substring(0, 24);
        return "fact-card-terminal:" + factCardRecord.getCardId() + ":" + itemIndex + ":" + hash;
    }

    /**
     * 推断通用值类型。
     *
     * @param value 原始值
     * @return 值类型
     */
    private String inferValueType(String value) {
        if (!hasText(value)) {
            return "empty";
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return "boolean";
        }
        if (normalized.matches("[-+]?\\d+(?:\\.\\d+)?")) {
            return "number";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return "url";
        }
        if (normalized.contains("/") || normalized.contains("\\")) {
            return "path";
        }
        if (VERSION_PATTERN.matcher(normalized).matches()) {
            return "version";
        }
        if (DATE_LIKE_PATTERN.matcher(normalized).matches()) {
            return "date_like";
        }
        return "string";
    }

    /**
     * 读取 items JSON。
     *
     * @param itemsJson items JSON
     * @return JSON 节点
     */
    private JsonNode readItemsJson(String itemsJson) {
        return readJson(itemsJson);
    }

    /**
     * 读取 JSON。
     *
     * @param json JSON 文本
     * @return JSON 节点
     */
    private JsonNode readJson(String json) {
        if (!hasText(json)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        }
        catch (JsonProcessingException exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    /**
     * 读取文本字段。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return 文本
     */
    private String textValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return "";
        }
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull() || valueNode.isContainerNode()) {
            return "";
        }
        return valueNode.asText("");
    }

    /**
     * 读取整数字段。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 整数值
     */
    private int intValue(JsonNode node, String fieldName, int defaultValue) {
        if (node == null || fieldName == null) {
            return defaultValue;
        }
        JsonNode valueNode = node.path(fieldName);
        if (!valueNode.canConvertToInt()) {
            return defaultValue;
        }
        return valueNode.asInt(defaultValue);
    }

    /**
     * 读取路径片段。
     *
     * @param pathSegmentsNode 路径片段 JSON
     * @return 路径片段列表
     */
    private List<String> readPathSegments(JsonNode pathSegmentsNode) {
        if (pathSegmentsNode == null || !pathSegmentsNode.isArray()) {
            return List.of();
        }
        List<String> pathSegments = new ArrayList<String>();
        for (JsonNode pathSegmentNode : pathSegmentsNode) {
            if (pathSegmentNode != null && pathSegmentNode.isValueNode()) {
                String pathSegment = pathSegmentNode.asText("");
                if (hasText(pathSegment)) {
                    pathSegments.add(pathSegment.trim());
                }
            }
        }
        return pathSegments;
    }

    /**
     * 从 keyPath 推导路径片段。
     *
     * @param keyPath 完整路径
     * @return 路径片段列表
     */
    private List<String> readPathSegmentsFromKeyPath(String keyPath) {
        if (!hasText(keyPath)) {
            return List.of();
        }
        String[] rawSegments = keyPath.trim().split("\\.");
        List<String> pathSegments = new ArrayList<String>();
        for (String rawSegment : rawSegments) {
            if (hasText(rawSegment)) {
                pathSegments.add(rawSegment.trim());
            }
        }
        return pathSegments;
    }

    /**
     * 序列化 JSON 数组。
     *
     * @param node JSON 节点
     * @return JSON 数组文本
     */
    private String writeJsonArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        }
        catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    /**
     * 序列化字符串列表为 JSON 数组。
     *
     * @param values 字符串列表
     * @return JSON 数组文本
     */
    private String writeJsonArray(List<String> values) {
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        if (values != null) {
            for (String value : values) {
                arrayNode.add(value);
            }
        }
        return writeJsonArray(arrayNode);
    }

    /**
     * 序列化 JSON 对象。
     *
     * @param objectNode JSON 对象
     * @return JSON 文本
     */
    private String writeJsonObject(ObjectNode objectNode) {
        try {
            return OBJECT_MAPPER.writeValueAsString(objectNode);
        }
        catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    /**
     * 写入非空 Long 字段。
     *
     * @param objectNode JSON 对象
     * @param fieldName 字段名
     * @param value Long 值
     */
    private void putLong(ObjectNode objectNode, String fieldName, Long value) {
        if (value != null) {
            objectNode.put(fieldName, value.longValue());
        }
    }

    /**
     * 读取首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本
     */
    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 读取路径末级片段。
     *
     * @param path 路径
     * @return 末级片段
     */
    private String lastPathSegment(String path) {
        if (!hasText(path)) {
            return "";
        }
        String normalized = path.trim();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex + 1 < normalized.length()) {
            return normalized.substring(dotIndex + 1);
        }
        return normalized;
    }

    /**
     * 通用值归一化。
     *
     * @param value 原始值
     * @return 归一化值
     */
    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.endsWith(",")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.length() >= 2) {
            boolean doubleQuoted = normalized.startsWith("\"") && normalized.endsWith("\"");
            boolean singleQuoted = normalized.startsWith("'") && normalized.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        return normalized;
    }

    /**
     * 截断文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后文本
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 计算 SHA-256 十六进制哈希。
     *
     * @param value 原始值
     * @return 哈希值
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safeText(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", Byte.valueOf(item)));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * 返回空安全文本。
     *
     * @param value 原始文本
     * @return 空安全文本
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断文本是否有内容。
     *
     * @param value 文本
     * @return 是否有内容
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
