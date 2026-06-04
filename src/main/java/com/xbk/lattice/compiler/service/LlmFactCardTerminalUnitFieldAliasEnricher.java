package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.prompt.CompilerPromptProvider;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import com.xbk.lattice.shared.json.JsonMappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LLM Terminal unit 字段别名增强器。
 *
 * 职责：仅为缺少 CJK alias 的英文字段 terminal unit 调用编译期 LLM 生成中文检索别名。
 *
 * @author xiexu
 */
@Service
@Slf4j
public class LlmFactCardTerminalUnitFieldAliasEnricher implements FactCardTerminalUnitFieldAliasEnricher {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.moduleAwareMapper();

    private static final String COMPILE_SCENE = "compile";

    private static final String FIELD_ALIAS_ENRICHER_ROLE = "field-alias-enricher";

    private static final String FIELD_ALIAS_ENRICHER_PURPOSE = "enrich-field-aliases";

    private static final int MAX_GENERATED_ALIASES_PER_FIELD = 5;

    private static final int MAX_TOTAL_ALIASES_PER_FIELD = 20;

    private static final int MAX_ALIAS_LENGTH = 20;

    private static final int MAX_CONTEXT_TEXT_LENGTH = 240;

    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]");

    private static final Pattern LATIN_PATTERN = Pattern.compile("[A-Za-z]");

    private final LlmGateway llmGateway;

    private final CompilerPromptProvider promptProvider;

    private final FactCardTerminalUnitMaterializer terminalUnitMaterializer;

    /**
     * 创建 LLM 字段别名增强器。
     *
     * @param llmGateway LLM 网关
     * @param promptProvider 编译期 prompt 提供者
     * @param terminalUnitMaterializer terminal unit 物化器
     */
    public LlmFactCardTerminalUnitFieldAliasEnricher(
            LlmGateway llmGateway,
            CompilerPromptProvider promptProvider,
            FactCardTerminalUnitMaterializer terminalUnitMaterializer
    ) {
        this.llmGateway = llmGateway;
        this.promptProvider = promptProvider;
        this.terminalUnitMaterializer = terminalUnitMaterializer;
    }

    /**
     * 为 terminal unit 列表增强字段别名。
     *
     * @param records        terminal unit 记录（不可原地修改）
     * @param factCardRecord 所属事实卡
     * @return 增强后的记录列表；任意失败场景返回原记录
     */
    @Override
    public List<FactCardTerminalUnitRecord> enrich(
            List<FactCardTerminalUnitRecord> records,
            FactCardRecord factCardRecord
    ) {
        return doEnrich(records, null);
    }

    /**
     * 在 compile job scope 下增强字段别名。
     *
     * @param records        terminal unit 记录
     * @param factCardRecord 所属事实卡
     * @param scopeId        compile job scope
     * @return 增强后的记录列表
     */
    @Override
    public List<FactCardTerminalUnitRecord> enrich(
            List<FactCardTerminalUnitRecord> records,
            FactCardRecord factCardRecord,
            String scopeId
    ) {
        return doEnrich(records, scopeId);
    }

    /**
     * 统一增强入口：有 scope 时使用 scoped route，无 scope 时保持 fail-closed。
     *
     * @param records terminal unit 记录
     * @param scopeId compile job scope，null 表示无 scope
     * @return 增强后的记录列表
     */
    private List<FactCardTerminalUnitRecord> doEnrich(
            List<FactCardTerminalUnitRecord> records,
            String scopeId
    ) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        Map<String, List<FactCardTerminalUnitRecord>> recordsByParentPath = groupByParentPath(records);
        if (!hasAnyCandidate(recordsByParentPath)) {
            return records;
        }
        if (!isLlmRouteAvailable(scopeId)) {
            return records;
        }
        List<FactCardTerminalUnitRecord> enrichedRecords = null;
        Map<FactCardTerminalUnitRecord, Integer> indexByRecord = buildIndexByRecord(records);
        for (List<FactCardTerminalUnitRecord> groupRecords : recordsByParentPath.values()) {
            List<FactCardTerminalUnitRecord> candidates = filterCandidates(groupRecords);
            if (candidates.isEmpty()) {
                continue;
            }
            Map<String, List<String>> aliasesByTerminalKey = requestAliases(groupRecords, candidates, scopeId);
            if (aliasesByTerminalKey.isEmpty()) {
                continue;
            }
            for (FactCardTerminalUnitRecord candidate : candidates) {
                FactCardTerminalUnitRecord enrichedRecord = mergeAliases(candidate, aliasesByTerminalKey);
                if (enrichedRecord == candidate) {
                    continue;
                }
                Integer index = indexByRecord.get(candidate);
                if (index != null) {
                    if (enrichedRecords == null) {
                        enrichedRecords = new ArrayList<FactCardTerminalUnitRecord>(records);
                    }
                    enrichedRecords.set(index.intValue(), enrichedRecord);
                }
            }
        }
        return enrichedRecords == null ? records : enrichedRecords;
    }

    /**
     * 判断分组中是否存在需要增强的候选记录。
     *
     * @param recordsByParentPath parentPath 到记录列表的映射
     * @return 存在候选返回 true
     */
    private boolean hasAnyCandidate(Map<String, List<FactCardTerminalUnitRecord>> recordsByParentPath) {
        for (List<FactCardTerminalUnitRecord> records : recordsByParentPath.values()) {
            if (!filterCandidates(records).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 LLM 路由是否可用。
     *
     * @param scopeId compile job scope，null 表示无 scope（使用 bootstrap route）
     * @return 可用返回 true
     */
    private boolean isLlmRouteAvailable(String scopeId) {
        try {
            LlmRouteResolution routeResolution = hasText(scopeId)
                    ? llmGateway.routeResolutionFor(scopeId, COMPILE_SCENE, FIELD_ALIAS_ENRICHER_ROLE)
                    : llmGateway.routeResolution(COMPILE_SCENE, FIELD_ALIAS_ENRICHER_ROLE);
            if (routeResolution == null) {
                return false;
            }
            String modelName = routeResolution.getModelName();
            return hasText(modelName)
                    && !"fallback".equalsIgnoreCase(modelName.trim())
                    && !"unknown".equalsIgnoreCase(modelName.trim());
        } catch (RuntimeException exception) {
            log.warn("Skip terminal unit field alias enrichment because LLM route is unavailable: {}",
                    exception.getMessage());
            return false;
        }
    }

    /**
     * 按 parentPath 对 terminal unit 分组。
     *
     * @param records terminal unit 记录
     * @return parentPath 到记录列表的映射
     */
    private Map<String, List<FactCardTerminalUnitRecord>> groupByParentPath(List<FactCardTerminalUnitRecord> records) {
        Map<String, List<FactCardTerminalUnitRecord>> groups = new LinkedHashMap<String, List<FactCardTerminalUnitRecord>>();
        for (FactCardTerminalUnitRecord record : records) {
            String parentPath = safeText(record.getParentPath());
            groups.computeIfAbsent(parentPath, ignored -> new ArrayList<FactCardTerminalUnitRecord>()).add(record);
        }
        return groups;
    }

    /**
     * 构建记录对象到原始序号的映射。
     *
     * @param records terminal unit 记录
     * @return 记录对象到序号的映射
     */
    private Map<FactCardTerminalUnitRecord, Integer> buildIndexByRecord(List<FactCardTerminalUnitRecord> records) {
        Map<FactCardTerminalUnitRecord, Integer> indexByRecord = new LinkedHashMap<FactCardTerminalUnitRecord, Integer>();
        for (int index = 0; index < records.size(); index++) {
            indexByRecord.put(records.get(index), Integer.valueOf(index));
        }
        return indexByRecord;
    }

    /**
     * 筛选需要 LLM 增强的英文字段 terminal unit。
     *
     * @param records 同 parentPath 下的记录
     * @return 需要增强的记录
     */
    private List<FactCardTerminalUnitRecord> filterCandidates(List<FactCardTerminalUnitRecord> records) {
        List<FactCardTerminalUnitRecord> candidates = new ArrayList<FactCardTerminalUnitRecord>();
        for (FactCardTerminalUnitRecord record : records) {
            if (shouldEnrich(record)) {
                candidates.add(record);
            }
        }
        return candidates;
    }

    /**
     * 判断单条记录是否需要增强。
     *
     * @param record terminal unit 记录
     * @return 需要增强返回 true
     */
    private boolean shouldEnrich(FactCardTerminalUnitRecord record) {
        if (record == null) {
            return false;
        }
        if (containsCjk(record.getFieldLabel()) || containsCjk(record.getTerminalKey())) {
            return false;
        }
        if (aliasesContainCjk(record.getFieldAliasesJson())) {
            return false;
        }
        return containsLatin(record.getFieldLabel()) || containsLatin(record.getTerminalKey());
    }

    /**
     * 调用 LLM 生成字段别名。
     *
     * @param groupRecords 同 parentPath 下的所有记录
     * @param candidates 需要增强的记录
     * @param scopeId compile job scope，null 表示无 scope
     * @return terminalKey 到别名列表的映射
     */
    private Map<String, List<String>> requestAliases(
            List<FactCardTerminalUnitRecord> groupRecords,
            List<FactCardTerminalUnitRecord> candidates,
            String scopeId
    ) {
        String userPrompt = buildUserPrompt(groupRecords, candidates);
        if (!hasText(userPrompt)) {
            return Map.of();
        }
        try {
            String response = hasText(scopeId)
                    ? llmGateway.generateTextWithScope(
                            scopeId,
                            COMPILE_SCENE,
                            FIELD_ALIAS_ENRICHER_ROLE,
                            FIELD_ALIAS_ENRICHER_PURPOSE,
                            promptProvider.fieldAliasEnricherPrompt(),
                            userPrompt
                    )
                    : llmGateway.generateText(
                            COMPILE_SCENE,
                            FIELD_ALIAS_ENRICHER_ROLE,
                            FIELD_ALIAS_ENRICHER_PURPOSE,
                            promptProvider.fieldAliasEnricherPrompt(),
                            userPrompt
                    );
            return parseAliasResponse(response);
        } catch (RuntimeException exception) {
            log.warn("LLM terminal unit field alias enrichment failed: {}", exception.getMessage());
            return Map.of();
        }
    }

    /**
     * 构建仅包含通用结构信息的 LLM 用户输入。
     *
     * @param groupRecords 同 parentPath 下的所有记录
     * @param candidates 需要增强的记录
     * @return JSON 用户输入
     */
    private String buildUserPrompt(
            List<FactCardTerminalUnitRecord> groupRecords,
            List<FactCardTerminalUnitRecord> candidates
    ) {
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode fieldsNode = rootNode.putArray("fields");
        for (FactCardTerminalUnitRecord candidate : candidates) {
            fieldsNode.add(buildFieldNode(candidate));
        }
        ArrayNode siblingsNode = rootNode.putArray("siblings");
        for (FactCardTerminalUnitRecord record : groupRecords) {
            siblingsNode.add(buildSiblingNode(record));
        }
        return writeJson(rootNode);
    }

    /**
     * 构建待增强字段 JSON。
     *
     * @param record terminal unit 记录
     * @return 字段 JSON
     */
    private ObjectNode buildFieldNode(FactCardTerminalUnitRecord record) {
        ObjectNode fieldNode = OBJECT_MAPPER.createObjectNode();
        fieldNode.put("terminalKey", safeText(record.getTerminalKey()));
        fieldNode.put("fieldLabel", safeText(record.getFieldLabel()));
        fieldNode.put("keyPath", safeText(record.getKeyPath()));
        fieldNode.put("parentPath", safeText(record.getParentPath()));
        fieldNode.set("pathSegments", readStringArray(record.getPathSegmentsJson()));
        fieldNode.put("valueType", safeText(record.getValueType()));
        fieldNode.put("displayText", truncate(record.getDisplayText(), MAX_CONTEXT_TEXT_LENGTH));
        String raw = rawLine(record.getSourceRefsJson());
        if (hasText(raw)) {
            fieldNode.put("raw", truncate(raw, MAX_CONTEXT_TEXT_LENGTH));
        }
        return fieldNode;
    }

    /**
     * 构建 sibling JSON。
     *
     * @param record terminal unit 记录
     * @return sibling JSON
     */
    private ObjectNode buildSiblingNode(FactCardTerminalUnitRecord record) {
        ObjectNode siblingNode = OBJECT_MAPPER.createObjectNode();
        siblingNode.put("terminalKey", safeText(record.getTerminalKey()));
        siblingNode.put("fieldLabel", safeText(record.getFieldLabel()));
        siblingNode.put("value", truncate(record.getValueText(), MAX_CONTEXT_TEXT_LENGTH));
        siblingNode.put("valueType", safeText(record.getValueType()));
        return siblingNode;
    }

    /**
     * 解析 LLM JSON 响应。
     *
     * @param response LLM 响应
     * @return terminalKey 到别名列表的映射
     */
    private Map<String, List<String>> parseAliasResponse(String response) {
        if (!hasText(response)) {
            return Map.of();
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(response);
            JsonNode aliasesNode = rootNode.path("aliases");
            if (!aliasesNode.isObject()) {
                return Map.of();
            }
            Map<String, List<String>> aliasesByTerminalKey = new LinkedHashMap<String, List<String>>();
            aliasesNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isArray()) {
                    aliasesByTerminalKey.put(entry.getKey(), List.of());
                    return;
                }
                List<String> aliases = new ArrayList<String>();
                for (JsonNode aliasNode : entry.getValue()) {
                    if (!aliasNode.isNull()) {
                        aliases.add(aliasNode.asText(""));
                    }
                }
                aliasesByTerminalKey.put(entry.getKey(), aliases);
            });
            return aliasesByTerminalKey;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /**
     * 合并 LLM 生成别名并重建 ftsText。
     *
     * @param record terminal unit 记录
     * @param aliasesByTerminalKey terminalKey 到候选别名的映射
     * @return 合并后的记录；无有效别名时返回原记录
     */
    private FactCardTerminalUnitRecord mergeAliases(
            FactCardTerminalUnitRecord record,
            Map<String, List<String>> aliasesByTerminalKey
    ) {
        List<String> generatedAliases = aliasesByTerminalKey.get(record.getTerminalKey());
        if (generatedAliases == null || generatedAliases.isEmpty()) {
            return record;
        }
        List<String> existingAliases = parseAliases(record.getFieldAliasesJson());
        Set<String> mergedAliases = new LinkedHashSet<String>(existingAliases);
        int acceptedGeneratedCount = 0;
        for (String generatedAlias : generatedAliases) {
            if (acceptedGeneratedCount >= MAX_GENERATED_ALIASES_PER_FIELD
                    || mergedAliases.size() >= MAX_TOTAL_ALIASES_PER_FIELD) {
                break;
            }
            String normalizedAlias = normalizeAlias(generatedAlias);
            if (!isAllowedGeneratedAlias(normalizedAlias, record)) {
                continue;
            }
            if (mergedAliases.add(normalizedAlias)) {
                acceptedGeneratedCount++;
            }
        }
        if (mergedAliases.size() == existingAliases.size()) {
            return record;
        }
        List<String> aliases = new ArrayList<String>(mergedAliases);
        String aliasesJson = writeJsonArray(aliases);
        String ftsText = terminalUnitMaterializer.rebuildFtsText(record, aliases);
        String updatedMetadataJson = rebuildMetadataJsonFieldAliases(record.getMetadataJson(), aliases);
        return record.withFieldAliasesFtsTextAndMetadata(aliasesJson, ftsText, updatedMetadataJson);
    }

    /**
     * 用新别名列表重建 metadataJson 中的 fieldAliases 数组。
     *
     * @param originalMetadataJson 原始 metadata JSON
     * @param aliases 新别名列表
     * @return 更新 fieldAliases 后的 metadata JSON
     */
    private String rebuildMetadataJsonFieldAliases(String originalMetadataJson, List<String> aliases) {
        if (!hasText(originalMetadataJson)) {
            return originalMetadataJson;
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(originalMetadataJson);
            if (!rootNode.isObject()) {
                return originalMetadataJson;
            }
            ObjectNode metadataNode = (ObjectNode) rootNode;
            ArrayNode fieldAliasesNode = metadataNode.putArray("fieldAliases");
            for (String alias : aliases) {
                fieldAliasesNode.add(alias);
            }
            return OBJECT_MAPPER.writeValueAsString(metadataNode);
        } catch (Exception exception) {
            return originalMetadataJson;
        }
    }

    /**
     * 校验生成别名是否可接受。
     *
     * @param alias 候选别名
     * @param record terminal unit 记录
     * @return 可接受返回 true
     */
    private boolean isAllowedGeneratedAlias(String alias, FactCardTerminalUnitRecord record) {
        if (!hasText(alias) || alias.length() > MAX_ALIAS_LENGTH || !containsCjk(alias)) {
            return false;
        }
        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        return !containsValueText(normalizedAlias, record.getValueText())
                && !containsValueText(normalizedAlias, record.getNormalizedValue());
    }

    /**
     * 判断别名是否包含字段值。
     *
     * @param alias 候选别名
     * @param valueText 字段值
     * @return 包含返回 true
     */
    private boolean containsValueText(String alias, String valueText) {
        if (!hasText(alias) || !hasText(valueText)) {
            return false;
        }
        String normalizedValue = valueText.trim().toLowerCase(Locale.ROOT);
        return normalizedValue.length() >= 2 && alias.contains(normalizedValue);
    }

    /**
     * 判断已有 aliases 是否包含 CJK。
     *
     * @param aliasesJson 别名 JSON 数组
     * @return 包含 CJK 返回 true
     */
    private boolean aliasesContainCjk(String aliasesJson) {
        for (String alias : parseAliases(aliasesJson)) {
            if (containsCjk(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析字段别名 JSON。
     *
     * @param aliasesJson 别名 JSON 数组
     * @return 别名列表
     */
    private List<String> parseAliases(String aliasesJson) {
        if (!hasText(aliasesJson)) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(aliasesJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> aliases = new ArrayList<String>();
            for (JsonNode aliasNode : node) {
                if (!aliasNode.isNull()) {
                    String alias = normalizeAlias(aliasNode.asText(""));
                    if (hasText(alias)) {
                        aliases.add(alias);
                    }
                }
            }
            return aliases;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    /**
     * 读取路径片段数组。
     *
     * @param pathSegmentsJson 路径片段 JSON
     * @return 路径片段数组节点
     */
    private ArrayNode readStringArray(String pathSegmentsJson) {
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        if (!hasText(pathSegmentsJson)) {
            return arrayNode;
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(pathSegmentsJson);
            if (!rootNode.isArray()) {
                return arrayNode;
            }
            for (JsonNode item : rootNode) {
                if (!item.isNull()) {
                    String text = item.asText("");
                    if (hasText(text)) {
                        arrayNode.add(text.trim());
                    }
                }
            }
            return arrayNode;
        } catch (JsonProcessingException exception) {
            return arrayNode;
        }
    }

    /**
     * 从 sourceRefsJson 读取 raw 行。
     *
     * @param sourceRefsJson 来源回指 JSON
     * @return raw 行
     */
    private String rawLine(String sourceRefsJson) {
        if (!hasText(sourceRefsJson)) {
            return "";
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(sourceRefsJson);
            return rootNode.path("raw").asText("");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    /**
     * 写出字符串数组 JSON。
     *
     * @param aliases 别名列表
     * @return JSON 字符串
     */
    private String writeJsonArray(List<String> aliases) {
        try {
            return OBJECT_MAPPER.writeValueAsString(aliases);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    /**
     * 写出 JSON 节点。
     *
     * @param node JSON 节点
     * @return JSON 字符串
     */
    private String writeJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    /**
     * 归一化别名文本。
     *
     * @param alias 原始别名
     * @return 归一化别名
     */
    private String normalizeAlias(String alias) {
        return alias == null ? "" : alias.trim();
    }

    /**
     * 截断上下文文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后文本
     */
    private String truncate(String value, int maxLength) {
        String text = safeText(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /**
     * 判断文本是否包含 CJK。
     *
     * @param value 文本
     * @return 包含 CJK 返回 true
     */
    private boolean containsCjk(String value) {
        return hasText(value) && CJK_PATTERN.matcher(value).find();
    }

    /**
     * 判断文本是否包含拉丁字母。
     *
     * @param value 文本
     * @return 包含拉丁字母返回 true
     */
    private boolean containsLatin(String value) {
        return hasText(value) && LATIN_PATTERN.matcher(value).find();
    }

    /**
     * 判断文本是否非空。
     *
     * @param value 文本
     * @return 非空返回 true
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 返回安全文本。
     *
     * @param value 文本
     * @return 安全文本
     */
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
