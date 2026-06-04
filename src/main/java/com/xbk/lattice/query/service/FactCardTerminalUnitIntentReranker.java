package com.xbk.lattice.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.shared.json.JsonMappers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Terminal unit FTS hit 字段意图重排器
 *
 * 职责：在 terminal unit FTS 检索结果内部进行通用 lexical rerank，使
 * terminalKey / fieldLabel / fieldAliases / keyPath 与 query token 更好对齐，
 * 减少同卡 sibling value_text 抢排。
 *
 * 只作用于 terminal unit FTS hits，不影响其他 channel。
 *
 * @author xiexu
 */
public class FactCardTerminalUnitIntentReranker {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private static final double FIELD_TOKEN_WEIGHT = 1.0;

    private static final double VALUE_TOKEN_WEIGHT = 0.1;

    private static final double NUMERIC_VALUE_TYPE_BONUS = 0.5;

    private static final double SIBLING_FIELD_BOOST = 6.0;

    private final QuerySemanticRules semanticRules;

    /**
     * 创建字段意图重排器。
     *
     * @param semanticRules 查询语义规则
     */
    public FactCardTerminalUnitIntentReranker(QuerySemanticRules semanticRules) {
        this.semanticRules = semanticRules == null ? new QuerySemanticRules() : semanticRules;
    }

    /**
     * 对 terminal unit FTS hits 按字段意图重排。
     *
     * @param hits     原始 FTS 命中列表
     * @param question 查询问题
     * @return 重排后的命中列表
     */
    public List<QueryArticleHit> rerank(List<QueryArticleHit> hits, String question) {
        if (hits == null || hits.size() <= 1) {
            return hits;
        }
        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return hits;
        }

        boolean queryHasNumericIntent = hasNumericQuestionSignal(question);

        List<HitProfile> profiles = new ArrayList<HitProfile>();
        for (int i = 0; i < hits.size(); i++) {
            HitProfile profile = parseProfile(hits.get(i));
            if (profile == null) {
                return hits;
            }
            profile.originalIndex = i;
            profile.originalScore = hits.get(i).getScore();
            profiles.add(profile);
        }

        for (HitProfile p : profiles) {
            p.fieldMatchCount = countFieldMatches(p, queryTokens);
            p.terminalKeyMatchCount = countTerminalKeyMatches(p, queryTokens);
            p.valueMatchCount = countValueOnlyMatches(p, queryTokens);

            double adj = p.originalScore;
            adj += p.fieldMatchCount * FIELD_TOKEN_WEIGHT;
            adj += Math.min(p.valueMatchCount, 5) * VALUE_TOKEN_WEIGHT;
            if (queryHasNumericIntent && isNumericLikeType(p.valueType)) {
                adj += NUMERIC_VALUE_TYPE_BONUS;
            }
            p.adjustedScore = adj;
        }

        Map<String, List<HitProfile>> byParent = profiles.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.parentPath != null && !p.parentPath.isBlank())
                                ? p.parentPath : "__no_parent__",
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<HitProfile> group : byParent.values()) {
            if (group.size() <= 1) {
                continue;
            }
            boolean anyHasTerminalKeyMatch = group.stream()
                    .anyMatch(p -> p.terminalKeyMatchCount > 0);
            if (anyHasTerminalKeyMatch) {
                for (HitProfile p : group) {
                    if (p.terminalKeyMatchCount > 0) {
                        p.adjustedScore += SIBLING_FIELD_BOOST;
                    }
                }
            }
        }


        profiles.sort(Comparator
                .comparingInt((HitProfile p) -> p.getFieldIntentSignal() > 0 ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(HitProfile::getAdjustedScore).reversed())
                .thenComparingInt(HitProfile::getOriginalIndex));

        return profiles.stream().map(p -> p.hit).collect(Collectors.toList());
    }

    /**
     * 判断 query 是否包含通用数值问法信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    private boolean hasNumericQuestionSignal(String question) {
        if (question == null) {
            return false;
        }
        if (semanticRules.containsAnyNumericValueIntentSignal(question)) {
            return true;
        }
        List<String> tokens = QueryTokenExtractor.extract(question);
        return tokens.stream().anyMatch(t -> t.matches("\\d+") && !t.equals("0"));
    }

    /**
     * 判断 valueType 是否为数值类。
     *
     * @param valueType 值类型
     * @return 数值类返回 true
     */
    private boolean isNumericLikeType(String valueType) {
        if (valueType == null) {
            return false;
        }
        return "number".equalsIgnoreCase(valueType) || "version".equalsIgnoreCase(valueType);
    }

    // region metadata parsing

    /**
     * 从命中 metadata JSON 解析字段画像。
     *
     * @param hit 命中
     * @return 字段画像；解析失败返回 null
     */
    private HitProfile parseProfile(QueryArticleHit hit) {
        String metadataJson = hit.getMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(metadataJson);
            HitProfile p = new HitProfile();
            p.hit = hit;
            p.terminalKey = textValue(node, "terminalKey");
            p.fieldLabel = textValue(node, "fieldLabel");
            p.fieldAliases = parseStringArray(node, "fieldAliases");
            p.keyPath = textValue(node, "keyPath");
            p.parentPath = textValue(node, "parentPath");
            p.value = textValue(node, "value");
            p.valueType = textValue(node, "valueType");
            p.displayText = textValue(node, "displayText");
            return p;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return "";
        }
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        return valueNode.asText("");
    }

    private List<String> parseStringArray(JsonNode node, String fieldName) {
        JsonNode arrayNode = node.path(fieldName);
        if (arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<String>();
        for (JsonNode item : arrayNode) {
            if (!item.isNull()) {
                result.add(item.asText(""));
            }
        }
        return result;
    }

    // endregion

    // region token matching

    /**
     * 统计 query token 中命中终端键（terminalKey/fieldLabel/aliases，不含 keyPath）的数量。
     * 用于同 parentPath sibling 竞争时的精准区分。
     */
    private int countTerminalKeyMatches(HitProfile p, List<String> queryTokens) {
        Set<String> terminalTokenSet = new HashSet<String>();
        addFieldTokens(terminalTokenSet, p.terminalKey);
        addFieldTokens(terminalTokenSet, p.fieldLabel);
        for (String alias : p.fieldAliases) {
            addFieldTokens(terminalTokenSet, alias);
        }
        int count = 0;
        for (String token : queryTokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (terminalTokenSet.contains(lower)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计 query token 中命中字段元数据（含 keyPath）的数量。
     */
    private int countFieldMatches(HitProfile p, List<String> queryTokens) {
        Set<String> fieldTokenSet = buildFieldTokenSet(p);
        int count = 0;
        for (String token : queryTokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (fieldTokenSet.contains(lower)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计只命中 value/displayText 而未命中字段元数据的 query token 数量。
     */
    private int countValueOnlyMatches(HitProfile p, List<String> queryTokens) {
        Set<String> fieldTokenSet = buildFieldTokenSet(p);
        int count = 0;
        for (String token : queryTokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            boolean matchesValue = containsIgnoreCase(p.value, lower)
                    || containsIgnoreCase(p.displayText, lower);
            boolean matchesField = fieldTokenSet.contains(lower);
            if (!matchesField) {
                for (String ft : fieldTokenSet) {
                    if (ft.contains(lower) || lower.contains(ft)) {
                        matchesField = true;
                        break;
                    }
                }
            }
            if (matchesValue && !matchesField) {
                count++;
            }
        }
        return count;
    }

    /**
     * 构建字段侧 token 集合。
     */
    private Set<String> buildFieldTokenSet(HitProfile p) {
        Set<String> tokens = new HashSet<String>();
        addFieldTokens(tokens, p.terminalKey);
        addFieldTokens(tokens, p.fieldLabel);
        for (String alias : p.fieldAliases) {
            addFieldTokens(tokens, alias);
        }
        addFieldTokens(tokens, p.keyPath);
        return tokens;
    }

    private void addFieldTokens(Set<String> tokens, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        tokens.add(lower);
        for (String part : splitIdentifier(text)) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
    }

    /**
     * 通用标识符拆分：snake、kebab、camelCase、dot、slash。
     */
    private List<String> splitIdentifier(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<String>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String segment : lower.split("[._\\-/\\[\\]]+")) {
            if (segment.isBlank()) {
                continue;
            }
            parts.add(segment);
            for (String sub : segment.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                String s = sub.toLowerCase(Locale.ROOT).trim();
                if (!s.isBlank() && s.length() >= 2) {
                    parts.add(s);
                }
            }
        }
        return parts;
    }

    private boolean containsIgnoreCase(String text, String token) {
        if (text == null || token == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(token);
    }

    // endregion

    /**
     * 命中字段画像。
     */
    private static class HitProfile {

        QueryArticleHit hit;

        int originalIndex;

        double originalScore;

        double adjustedScore;

        int fieldMatchCount;

        int terminalKeyMatchCount;

        int valueMatchCount;

        String terminalKey;

        String fieldLabel;

        List<String> fieldAliases = List.of();

        String keyPath;

        String parentPath;

        String value;

        String valueType;

        String displayText;

        int getFieldIntentSignal() {
            return (terminalKeyMatchCount > 0 || fieldMatchCount > 0) ? 1 : 0;
        }

        double getAdjustedScore() {
            return adjustedScore;
        }

        int getOriginalIndex() {
            return originalIndex;
        }
    }
}
