package com.xbk.lattice.query.service;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 答案覆盖校验基础支持。
 *
 * 职责：承载覆盖要求模型、JSON 读取、文本归一化和底层匹配工具。
 *
 * @author xiexu
 */
abstract class AnswerCoverageBaseSupport {

    protected static final ObjectMapper OBJECT_MAPPER = JsonMappers.moduleAwareMapper();

    protected static final List<String> IGNORED_FIELDS = List.of(
            "structure",
            "order",
            "position",
            "index",
            "sourceChunkIds",
            "articleIds",
            "statusGroup"
    );

    protected static final List<String> ENUM_TEXT_FIELDS = List.of(
            "label",
            "name",
            "key",
            "item",
            "title",
            "subject",
            "text",
            "value"
    );

    protected static final List<String> STATUS_SUBJECT_FIELDS = List.of(
            "subject",
            "name",
            "label",
            "item",
            "key",
            "title"
    );

    protected static final List<String> STATUS_VALUE_FIELDS = List.of(
            "status",
            "state",
            "value",
            "result"
    );

    protected static final List<String> POLICY_CONSTRAINT_FIELDS = List.of(
            "constraint",
            "rule",
            "requirement",
            "text",
            "raw"
    );

    protected static final List<String> POLICY_SCOPE_FIELDS = List.of(
            "scope",
            "range",
            "text",
            "raw"
    );

    /**
     * 解析 JSON 根节点。
     *
     * @param itemsJson 结构化条目 JSON
     * @return JSON 根节点
     */
    protected JsonNode readRootNode(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(itemsJson);
        }
        catch (JsonProcessingException exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }
    /**
     * 读取多个候选字段中的数组节点。
     *
     * @param rootNode JSON 根节点
     * @param fieldNames 字段名
     * @return 数组项
     */
    protected List<JsonNode> readArrayNodes(JsonNode rootNode, String... fieldNames) {
        if (rootNode == null || rootNode.isMissingNode() || rootNode.isNull()) {
            return List.of();
        }
        if (rootNode.isArray()) {
            return toNodeList(rootNode);
        }
        for (String fieldName : fieldNames) {
            JsonNode arrayNode = rootNode.get(fieldName);
            if (arrayNode != null && arrayNode.isArray()) {
                return toNodeList(arrayNode);
            }
        }
        return List.of();
    }
    /**
     * 把数组节点转换为列表。
     *
     * @param arrayNode 数组节点
     * @return 节点列表
     */
    protected List<JsonNode> toNodeList(JsonNode arrayNode) {
        List<JsonNode> nodes = new ArrayList<JsonNode>();
        for (JsonNode itemNode : arrayNode) {
            nodes.add(itemNode);
        }
        return nodes;
    }
    /**
     * 收集优先字段中的文本值。
     *
     * @param node JSON 节点
     * @param fieldNames 字段名
     * @return 文本值
     */
    protected List<String> collectPreferredTextValues(JsonNode node, List<String> fieldNames) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        if (node.isValueNode()) {
            return List.of(node.asText(""));
        }
        List<String> values = new ArrayList<String>();
        for (String fieldName : fieldNames) {
            String value = readText(node, fieldName);
            if (!value.isBlank()) {
                addUnique(values, value);
            }
        }
        return values;
    }
    /**
     * 收集节点下全部可用于覆盖校验的文本值。
     *
     * @param node JSON 节点
     * @return 文本值
     */
    protected List<String> collectAllTextValues(JsonNode node) {
        List<String> values = new ArrayList<String>();
        collectAllTextValues(node, values);
        return values;
    }
    /**
     * 递归收集文本值。
     *
     * @param node JSON 节点
     * @param values 文本值集合
     */
    protected void collectAllTextValues(JsonNode node, List<String> values) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isValueNode()) {
            String value = node.asText("");
            if (!value.isBlank()) {
                addUnique(values, value);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode itemNode : node) {
                collectAllTextValues(itemNode, values);
            }
            return;
        }
        node.fields().forEachRemaining(entry -> {
            if (!shouldIgnoreField(entry.getKey())) {
                collectAllTextValues(entry.getValue(), values);
            }
        });
    }
    /**
     * 判断字段是否不参与覆盖校验。
     *
     * @param fieldName 字段名
     * @return 不参与返回 true
     */
    protected boolean shouldIgnoreField(String fieldName) {
        for (String ignoredField : IGNORED_FIELDS) {
            if (ignoredField.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 从候选字段读取第一个非空文本。
     *
     * @param node JSON 节点
     * @param fieldNames 字段名
     * @return 文本值
     */
    protected String readFirstText(JsonNode node, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            String value = readText(node, fieldName);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
    /**
     * 读取文本字段。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return 文本值
     */
    protected String readText(JsonNode node, String fieldName) {
        JsonNode valueNode = node == null ? null : node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !valueNode.isValueNode()) {
            return "";
        }
        return valueNode.asText("").trim();
    }
    /**
     * 读取整数字段。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return 整数值
     */
    protected Integer readInteger(JsonNode node, String fieldName) {
        JsonNode valueNode = node == null ? null : node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !valueNode.canConvertToInt()) {
            return null;
        }
        return Integer.valueOf(valueNode.intValue());
    }
    /**
     * 构造缺失项展示文本。
     *
     * @param phrases 短语列表
     * @param factCard 事实证据卡
     * @return 展示文本
     */
    protected String buildDisplayText(List<String> phrases, FactCardRecord factCard) {
        List<String> safePhrases = normalizePhrases(phrases);
        if (!safePhrases.isEmpty()) {
            return String.join(" / ", safePhrases);
        }
        if (factCard != null && factCard.getTitle() != null && !factCard.getTitle().isBlank()) {
            return factCard.getTitle().trim();
        }
        return "结构化证据卡要点";
    }
    /**
     * 判断全部短语是否已覆盖。
     *
     * @param phrases 短语列表
     * @param normalizedAnswer 归一化答案
     * @return 已覆盖返回 true
     */
    protected boolean areAllPhrasesCovered(List<String> phrases, String normalizedAnswer) {
        for (String phrase : phrases) {
            if (!isPhraseCovered(phrase, normalizedAnswer)) {
                return false;
            }
        }
        return true;
    }
    /**
     * 判断任一短语是否已被答案覆盖。
     *
     * @param phrases 短语列表
     * @param normalizedAnswer 归一化答案
     * @return 任一短语已覆盖返回 true
     */
    protected boolean isAnyPhraseCovered(List<String> phrases, String normalizedAnswer) {
        for (String phrase : phrases) {
            if (isPhraseCovered(phrase, normalizedAnswer)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 判断短语是否已被答案覆盖。
     *
     * @param phrase 短语
     * @param normalizedAnswer 归一化答案
     * @return 已覆盖返回 true
     */
    protected boolean isPhraseCovered(String phrase, String normalizedAnswer) {
        String normalizedPhrase = normalizeForSearch(phrase);
        if (normalizedPhrase.isBlank()) {
            return true;
        }
        if (normalizedAnswer.contains(normalizedPhrase)) {
            return true;
        }
        String compactAnswer = compact(normalizedAnswer);
        String compactPhrase = compact(normalizedPhrase);
        if (compactPhrase.length() >= 2 && compactAnswer.contains(compactPhrase)) {
            return true;
        }
        List<String> tokens = splitMeaningfulTokens(normalizedPhrase);
        if (tokens.size() < 2) {
            return false;
        }
        for (String token : tokens) {
            if (!normalizedAnswer.contains(token) && !compactAnswer.contains(compact(token))) {
                return false;
            }
        }
        return true;
    }
    /**
     * 查找覆盖要求在答案中的位置。
     *
     * @param requirement 覆盖要求
     * @param normalizedAnswer 归一化答案
     * @return 首次出现位置，未出现返回 -1
     */
    protected int findRequirementPosition(CoverageRequirement requirement, String normalizedAnswer) {
        int position = Integer.MAX_VALUE;
        for (String phrase : requirement.getPhrases()) {
            int phrasePosition = findPhrasePosition(phrase, normalizedAnswer);
            if (phrasePosition < 0) {
                return -1;
            }
            position = Math.min(position, phrasePosition);
        }
        return position == Integer.MAX_VALUE ? -1 : position;
    }
    /**
     * 查找短语在答案中的位置。
     *
     * @param phrase 短语
     * @param normalizedAnswer 归一化答案
     * @return 首次出现位置，未出现返回 -1
     */
    protected int findPhrasePosition(String phrase, String normalizedAnswer) {
        String normalizedPhrase = normalizeForSearch(phrase);
        int directIndex = normalizedAnswer.indexOf(normalizedPhrase);
        if (directIndex >= 0) {
            return directIndex;
        }
        for (String token : splitMeaningfulTokens(normalizedPhrase)) {
            int tokenIndex = normalizedAnswer.indexOf(token);
            if (tokenIndex >= 0) {
                return tokenIndex;
            }
        }
        return -1;
    }
    /**
     * 判断整数序列是否非递减。
     *
     * @param positions 出现位置
     * @return 非递减返回 true
     */
    protected boolean isNonDecreasing(List<Integer> positions) {
        int lastPosition = -1;
        for (Integer position : positions) {
            int currentPosition = position == null ? -1 : position.intValue();
            if (currentPosition < lastPosition) {
                return false;
            }
            lastPosition = currentPosition;
        }
        return true;
    }
    /**
     * 构造顺序错乱提示。
     *
     * @param requirements 覆盖要求
     * @return 顺序错乱提示
     */
    protected String buildSequenceOrderIssue(List<CoverageRequirement> requirements) {
        List<String> orderedDisplays = new ArrayList<String>();
        for (CoverageRequirement requirement : requirements) {
            if (requirement.getKind() == RequirementKind.SEQUENCE_STEP) {
                addUnique(orderedDisplays, requirement.getDisplayText());
            }
        }
        return "顺序不一致：" + String.join(" -> ", orderedDisplays);
    }
    /**
     * 拆分答案片段。
     *
     * @param answerMarkdown 答案 Markdown
     * @return 归一化答案片段
     */
    protected List<String> splitAnswerSegments(String answerMarkdown) {
        String safeAnswer = answerMarkdown == null ? "" : answerMarkdown;
        String[] rawSegments = safeAnswer.split("[\\n\\r。；;，,]+");
        List<String> segments = new ArrayList<String>();
        for (String rawSegment : rawSegments) {
            String normalizedSegment = normalizeForSearch(rawSegment);
            if (!normalizedSegment.isBlank()) {
                segments.add(normalizedSegment);
            }
        }
        if (segments.isEmpty()) {
            segments.add(normalizeForSearch(safeAnswer));
        }
        return segments;
    }
    /**
     * 归一化短语列表。
     *
     * @param phrases 原始短语
     * @return 归一化后的非空短语
     */
    protected List<String> normalizePhrases(List<String> phrases) {
        if (phrases == null || phrases.isEmpty()) {
            return List.of();
        }
        List<String> normalizedPhrases = new ArrayList<String>();
        for (String phrase : phrases) {
            String safePhrase = phrase == null ? "" : phrase.trim();
            if (!safePhrase.isBlank()) {
                addUnique(normalizedPhrases, safePhrase);
            }
        }
        return normalizedPhrases;
    }
    /**
     * 将文本归一化为便于包含判断的形式。
     *
     * @param value 原始文本
     * @return 归一化文本
     */
    protected String normalizeForSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("\\[[^\\]]*]", " ")
                .replaceAll("[`*_#>\\-|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
    /**
     * 压缩空白便于对比 Markdown 与表格文本。
     *
     * @param value 原始文本
     * @return 压缩后文本
     */
    protected String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s+", "");
    }
    /**
     * 拆分有意义的英文或数字 token。
     *
     * @param normalizedPhrase 归一化短语
     * @return token 列表
     */
    protected List<String> splitMeaningfulTokens(String normalizedPhrase) {
        String[] rawTokens = normalizedPhrase.split("[\\s/:：=]+");
        List<String> tokens = new ArrayList<String>();
        for (String rawToken : rawTokens) {
            String token = rawToken.trim();
            if (token.length() >= 2) {
                addUnique(tokens, token);
            }
        }
        return tokens;
    }
    /**
     * 返回安全事实证据卡列表。
     *
     * @param factCards 原始事实证据卡
     * @return 安全事实证据卡列表
     */
    protected List<FactCardRecord> safeFactCards(List<FactCardRecord> factCards) {
        if (factCards == null || factCards.isEmpty()) {
            return List.of();
        }
        return factCards;
    }
    /**
     * 比较两个文本是否一致。
     *
     * @param left 左侧文本
     * @param right 右侧文本
     * @return 一致返回 true
     */
    protected boolean sameText(String left, String right) {
        return normalizeForSearch(left).equals(normalizeForSearch(right));
    }
    /**
     * 向列表追加唯一值。
     *
     * @param values 目标列表
     * @param value 待追加值
     */
    protected void addUnique(List<String> values, String value) {
        if (value == null || value.isBlank() || values.contains(value)) {
            return;
        }
        values.add(value);
    }
    /**
     * 覆盖要求类型。
     */
    protected enum RequirementKind {

        GENERIC,

        SEQUENCE_STEP,

        STATUS_ITEM
    }

    /**
     * 覆盖要求。
     */
    protected static final class CoverageRequirement {

        protected final RequirementKind kind;

        protected final String displayText;

        protected final List<String> phrases;

        protected final Integer sequencePosition;

        protected final String subject;

        protected final String status;

        protected final String statusGroup;

        /**
         * 创建覆盖要求。
         *
         * @param kind 覆盖要求类型
         * @param displayText 展示文本
         * @param phrases 必须覆盖的短语
         * @param sequencePosition 顺序位置
         * @param subject 状态主语
         * @param status 状态值
         * @param statusGroup 状态分组
         */
        protected CoverageRequirement(
                RequirementKind kind,
                String displayText,
                List<String> phrases,
                Integer sequencePosition,
                String subject,
                String status,
                String statusGroup
        ) {
            this.kind = kind;
            this.displayText = displayText == null ? "" : displayText;
            this.phrases = phrases == null ? List.of() : List.copyOf(phrases);
            this.sequencePosition = sequencePosition;
            this.subject = subject == null ? "" : subject;
            this.status = status == null ? "" : status;
            this.statusGroup = statusGroup == null ? "" : statusGroup;
        }

        /**
         * 创建普通覆盖要求。
         *
         * @param displayText 展示文本
         * @param phrases 必须覆盖的短语
         * @return 覆盖要求
         */
        protected static CoverageRequirement generic(String displayText, List<String> phrases) {
            return new CoverageRequirement(
                    RequirementKind.GENERIC,
                    displayText,
                    phrases,
                    null,
                    "",
                    "",
                    ""
            );
        }

        /**
         * 创建顺序覆盖要求。
         *
         * @param displayText 展示文本
         * @param phrases 必须覆盖的短语
         * @param sequencePosition 顺序位置
         * @return 覆盖要求
         */
        protected static CoverageRequirement sequence(
                String displayText,
                List<String> phrases,
                Integer sequencePosition
        ) {
            return new CoverageRequirement(
                    RequirementKind.SEQUENCE_STEP,
                    displayText,
                    phrases,
                    sequencePosition,
                    "",
                    "",
                    ""
            );
        }

        /**
         * 创建状态覆盖要求。
         *
         * @param displayText 展示文本
         * @param subject 状态主语
         * @param status 状态值
         * @param statusGroup 状态分组
         * @return 覆盖要求
         */
        protected static CoverageRequirement status(
                String displayText,
                String subject,
                String status,
                String statusGroup
        ) {
            return new CoverageRequirement(
                    RequirementKind.STATUS_ITEM,
                    displayText,
                    List.of(subject, status),
                    null,
                    subject,
                    status,
                    statusGroup
            );
        }

        /**
         * 获取覆盖要求类型。
         *
         * @return 覆盖要求类型
         */
        protected RequirementKind getKind() {
            return kind;
        }

        /**
         * 获取展示文本。
         *
         * @return 展示文本
         */
        protected String getDisplayText() {
            return displayText;
        }

        /**
         * 获取必须覆盖的短语。
         *
         * @return 必须覆盖的短语
         */
        protected List<String> getPhrases() {
            return phrases;
        }

        /**
         * 获取顺序位置。
         *
         * @return 顺序位置
         */
        protected Integer getSequencePosition() {
            return sequencePosition;
        }

        /**
         * 获取状态主语。
         *
         * @return 状态主语
         */
        protected String getSubject() {
            return subject;
        }

        /**
         * 获取状态值。
         *
         * @return 状态值
         */
        protected String getStatus() {
            return status;
        }

        /**
         * 获取状态分组。
         *
         * @return 状态分组
         */
        protected String getStatusGroup() {
            return statusGroup;
        }
    }

    /**
     * 覆盖评估结果。
     */
    protected static final class CoverageEvaluation {

        protected final boolean covered;

        protected final int answerPosition;

        protected final boolean evidenceTouched;

        protected final String missingItem;

        /**
         * 创建覆盖评估结果。
         *
         * @param covered 是否覆盖
         * @param answerPosition 答案中出现位置
         * @param missingItem 缺失项
         */
        protected CoverageEvaluation(boolean covered, int answerPosition, boolean evidenceTouched, String missingItem) {
            this.covered = covered;
            this.answerPosition = answerPosition;
            this.evidenceTouched = evidenceTouched;
            this.missingItem = missingItem == null ? "" : missingItem;
        }

        /**
         * 创建已覆盖结果。
         *
         * @param answerPosition 答案中出现位置
         * @return 覆盖评估结果
         */
        protected static CoverageEvaluation covered(int answerPosition) {
            return new CoverageEvaluation(true, answerPosition, true, "");
        }

        /**
         * 创建缺失结果。
         *
         * @param missingItem 缺失项
         * @return 覆盖评估结果
         */
        protected static CoverageEvaluation missing(String missingItem) {
            return new CoverageEvaluation(false, -1, false, missingItem);
        }

        /**
         * 创建缺失结果。
         *
         * @param missingItem 缺失项
         * @param evidenceTouched 答案是否已触达该证据要求的部分短语
         * @return 覆盖评估结果
         */
        protected static CoverageEvaluation missing(String missingItem, boolean evidenceTouched) {
            return new CoverageEvaluation(false, -1, evidenceTouched, missingItem);
        }

        /**
         * 返回是否覆盖。
         *
         * @return 是否覆盖
         */
        protected boolean isCovered() {
            return covered;
        }

        /**
         * 获取答案中出现位置。
         *
         * @return 答案中出现位置
         */
        protected int getAnswerPosition() {
            return answerPosition;
        }

        /**
         * 返回答案是否已触达该证据要求的部分短语。
         *
         * @return 已触达返回 true
         */
        protected boolean isEvidenceTouched() {
            return evidenceTouched;
        }

        /**
         * 获取缺失项。
         *
         * @return 缺失项
         */
        protected String getMissingItem() {
            return missingItem;
        }
    }

    /**
     * 覆盖评估摘要。
     */
    protected static final class CoverageEvaluationSummary {

        protected final int coveredCount;

        protected final List<String> missingItems;

        /**
         * 创建覆盖评估摘要。
         *
         * @param coveredCount 已覆盖数量
         * @param missingItems 缺失项
         */
        protected CoverageEvaluationSummary(int coveredCount, List<String> missingItems) {
            this.coveredCount = coveredCount;
            this.missingItems = missingItems == null ? List.of() : List.copyOf(missingItems);
        }

        /**
         * 获取已覆盖数量。
         *
         * @return 已覆盖数量
         */
        protected int getCoveredCount() {
            return coveredCount;
        }

        /**
         * 获取缺失项。
         *
         * @return 缺失项
         */
        protected List<String> getMissingItems() {
            return missingItems;
        }
    }
}
