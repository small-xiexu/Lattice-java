package com.xbk.lattice.query.service;

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
 * 答案覆盖校验服务
 *
 * 职责：基于通用结构化证据卡校验最终答案是否覆盖枚举、对照、顺序、状态和规则要点
 *
 * @author xiexu
 */
@Service
public class AnswerCoverageCheckService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final List<String> IGNORED_FIELDS = List.of(
            "structure",
            "order",
            "position",
            "index",
            "sourceChunkIds",
            "articleIds",
            "statusGroup"
    );

    private static final List<String> ENUM_TEXT_FIELDS = List.of(
            "label",
            "name",
            "key",
            "item",
            "title",
            "subject",
            "text",
            "value"
    );

    private static final List<String> STATUS_SUBJECT_FIELDS = List.of(
            "subject",
            "name",
            "label",
            "item",
            "key",
            "title"
    );

    private static final List<String> STATUS_VALUE_FIELDS = List.of(
            "status",
            "state",
            "value",
            "result"
    );

    private static final List<String> POLICY_CONSTRAINT_FIELDS = List.of(
            "constraint",
            "rule",
            "requirement",
            "text",
            "raw"
    );

    private static final List<String> POLICY_SCOPE_FIELDS = List.of(
            "scope",
            "range",
            "text",
            "raw"
    );

    /**
     * 校验结构化证据卡在答案中的覆盖程度。
     *
     * @param question 原始问题
     * @param answerShape 答案形态
     * @param factCards 命中的事实证据卡
     * @param answerMarkdown 生成后的 Markdown 答案
     * @return 答案覆盖校验结果
     */
    public AnswerCoverageCheckResult check(
            String question,
            AnswerShape answerShape,
            List<FactCardRecord> factCards,
            String answerMarkdown
    ) {
        AnswerShape effectiveShape = answerShape == null ? AnswerShape.GENERAL : answerShape;
        if (effectiveShape == AnswerShape.GENERAL) {
            return AnswerCoverageCheckResult.notApplicable();
        }
        List<CoverageRequirement> requirements = collectRequirements(question, effectiveShape, safeFactCards(factCards));
        if (requirements.isEmpty()) {
            return AnswerCoverageCheckResult.missing(List.of("未命中可校验的结构化证据卡要点"));
        }
        CoverageEvaluationSummary evaluationSummary = evaluateRequirements(effectiveShape, requirements, answerMarkdown);
        if (evaluationSummary.getMissingItems().isEmpty()) {
            return AnswerCoverageCheckResult.covered();
        }
        if (evaluationSummary.getCoveredCount() == 0) {
            return AnswerCoverageCheckResult.missing(evaluationSummary.getMissingItems());
        }
        return AnswerCoverageCheckResult.partial(evaluationSummary.getMissingItems());
    }

    /**
     * 汇总所有覆盖要求的答案覆盖结果。
     *
     * @param answerShape 答案形态
     * @param requirements 覆盖要求
     * @param answerMarkdown 答案 Markdown
     * @return 覆盖评估摘要
     */
    private CoverageEvaluationSummary evaluateRequirements(
            AnswerShape answerShape,
            List<CoverageRequirement> requirements,
            String answerMarkdown
    ) {
        String normalizedAnswer = normalizeForSearch(answerMarkdown);
        List<String> answerSegments = splitAnswerSegments(answerMarkdown);
        List<String> missingItems = new ArrayList<String>();
        int coveredCount = 0;
        List<Integer> sequencePositions = new ArrayList<Integer>();
        for (CoverageRequirement requirement : requirements) {
            CoverageEvaluation evaluation = evaluateRequirement(requirement, normalizedAnswer, answerSegments, requirements);
            if (evaluation.isCovered()) {
                coveredCount++;
                if (requirement.getKind() == RequirementKind.SEQUENCE_STEP) {
                    sequencePositions.add(Integer.valueOf(evaluation.getAnswerPosition()));
                }
            }
            else {
                if (evaluation.isEvidenceTouched()) {
                    coveredCount++;
                }
                addUnique(missingItems, evaluation.getMissingItem());
            }
        }
        if (answerShape == AnswerShape.SEQUENCE
                && missingItems.isEmpty()
                && !isNonDecreasing(sequencePositions)) {
            addUnique(missingItems, buildSequenceOrderIssue(requirements));
        }
        return new CoverageEvaluationSummary(coveredCount, missingItems);
    }

    /**
     * 校验单个覆盖要求。
     *
     * @param requirement 覆盖要求
     * @param normalizedAnswer 归一化答案
     * @param answerSegments 答案片段
     * @param allRequirements 全部覆盖要求
     * @return 覆盖评估结果
     */
    private CoverageEvaluation evaluateRequirement(
            CoverageRequirement requirement,
            String normalizedAnswer,
            List<String> answerSegments,
            List<CoverageRequirement> allRequirements
    ) {
        if (requirement.getKind() == RequirementKind.STATUS_ITEM) {
            return evaluateStatusRequirement(requirement, answerSegments, allRequirements);
        }
        int answerPosition = findRequirementPosition(requirement, normalizedAnswer);
        if (answerPosition >= 0 && areAllPhrasesCovered(requirement.getPhrases(), normalizedAnswer)) {
            return CoverageEvaluation.covered(answerPosition);
        }
        boolean evidenceTouched = isAnyPhraseCovered(requirement.getPhrases(), normalizedAnswer);
        return CoverageEvaluation.missing(requirement.getDisplayText(), evidenceTouched);
    }

    /**
     * 校验状态条目覆盖与互斥状态混淆。
     *
     * @param requirement 状态覆盖要求
     * @param answerSegments 答案片段
     * @param allRequirements 全部覆盖要求
     * @return 覆盖评估结果
     */
    private CoverageEvaluation evaluateStatusRequirement(
            CoverageRequirement requirement,
            List<String> answerSegments,
            List<CoverageRequirement> allRequirements
    ) {
        String normalizedSubject = normalizeForSearch(requirement.getSubject());
        String normalizedStatus = normalizeForSearch(requirement.getStatus());
        for (String answerSegment : answerSegments) {
            if (answerSegment.contains(normalizedSubject) && answerSegment.contains(normalizedStatus)) {
                return CoverageEvaluation.covered(0);
            }
        }
        if (hasConflictingStatus(requirement, answerSegments, allRequirements)) {
            return CoverageEvaluation.missing(
                    "状态混淆：" + requirement.getSubject() + " 应为 " + requirement.getStatus()
            );
        }
        return CoverageEvaluation.missing(requirement.getDisplayText());
    }

    /**
     * 判断状态条目是否被答案写入互斥状态。
     *
     * @param requirement 状态覆盖要求
     * @param answerSegments 答案片段
     * @param allRequirements 全部覆盖要求
     * @return 存在互斥状态返回 true
     */
    private boolean hasConflictingStatus(
            CoverageRequirement requirement,
            List<String> answerSegments,
            List<CoverageRequirement> allRequirements
    ) {
        String normalizedSubject = normalizeForSearch(requirement.getSubject());
        for (String answerSegment : answerSegments) {
            if (!answerSegment.contains(normalizedSubject)) {
                continue;
            }
            for (CoverageRequirement candidate : allRequirements) {
                if (candidate.getKind() != RequirementKind.STATUS_ITEM
                        || sameText(candidate.getStatusGroup(), requirement.getStatusGroup())) {
                    continue;
                }
                String normalizedCandidateStatus = normalizeForSearch(candidate.getStatus());
                if (!normalizedCandidateStatus.isBlank() && answerSegment.contains(normalizedCandidateStatus)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 收集当前答案形态对应的覆盖要求。
     *
     * @param question 原始问题
     * @param answerShape 答案形态
     * @param factCards 事实证据卡
     * @return 覆盖要求
     */
    private List<CoverageRequirement> collectRequirements(
            String question,
            AnswerShape answerShape,
            List<FactCardRecord> factCards
    ) {
        List<CoverageRequirement> requirements = new ArrayList<CoverageRequirement>();
        for (FactCardRecord factCard : factCards) {
            if (!matchesShape(factCard, answerShape)) {
                continue;
            }
            JsonNode rootNode = readRootNode(factCard.getItemsJson());
            if (answerShape == AnswerShape.ENUM) {
                requirements.addAll(collectEnumRequirements(rootNode, factCard));
            }
            else if (answerShape == AnswerShape.COMPARE) {
                requirements.addAll(collectCompareRequirements(rootNode, factCard));
            }
            else if (answerShape == AnswerShape.SEQUENCE) {
                requirements.addAll(collectSequenceRequirements(rootNode, factCard));
            }
            else if (answerShape == AnswerShape.STATUS) {
                requirements.addAll(collectStatusRequirements(rootNode, factCard));
            }
            else if (answerShape == AnswerShape.POLICY) {
                requirements.addAll(collectPolicyRequirements(rootNode, factCard));
            }
        }
        if (answerShape == AnswerShape.STATUS) {
            return filterStatusRequirementsByQuestion(question, requirements);
        }
        return requirements;
    }

    /**
     * 收集枚举答案覆盖要求。
     *
     * @param rootNode 结构化 JSON 根节点
     * @param factCard 事实证据卡
     * @return 覆盖要求
     */
    private List<CoverageRequirement> collectEnumRequirements(JsonNode rootNode, FactCardRecord factCard) {
        List<JsonNode> itemNodes = readArrayNodes(rootNode, "items", "rows");
        List<CoverageRequirement> requirements = new ArrayList<CoverageRequirement>();
        for (JsonNode itemNode : itemNodes) {
            List<String> phrases = collectPreferredTextValues(itemNode, ENUM_TEXT_FIELDS);
            if (phrases.isEmpty()) {
                phrases = collectAllTextValues(itemNode);
            }
            addGenericRequirement(requirements, buildDisplayText(phrases, factCard), phrases);
        }
        return requirements;
    }

    /**
     * 收集对照答案覆盖要求。
     *
     * @param rootNode 结构化 JSON 根节点
     * @param factCard 事实证据卡
     * @return 覆盖要求
     */
    private List<CoverageRequirement> collectCompareRequirements(JsonNode rootNode, FactCardRecord factCard) {
        List<JsonNode> rowNodes = readArrayNodes(rootNode, "rows", "items");
        List<CoverageRequirement> requirements = new ArrayList<CoverageRequirement>();
        for (JsonNode rowNode : rowNodes) {
            List<String> phrases = collectAllTextValues(rowNode);
            addGenericRequirement(requirements, buildDisplayText(phrases, factCard), phrases);
        }
        return requirements;
    }

    /**
     * 收集顺序答案覆盖要求。
     *
     * @param rootNode 结构化 JSON 根节点
     * @param factCard 事实证据卡
     * @return 覆盖要求
     */
    private List<CoverageRequirement> collectSequenceRequirements(JsonNode rootNode, FactCardRecord factCard) {
        List<JsonNode> stepNodes = readArrayNodes(rootNode, "steps", "items");
        List<CoverageRequirement> requirements = new ArrayList<CoverageRequirement>();
        int fallbackPosition = 1;
        for (JsonNode stepNode : stepNodes) {
            List<String> phrases = collectPreferredTextValues(stepNode, List.of("text", "label", "name", "value"));
            if (phrases.isEmpty()) {
                phrases = collectAllTextValues(stepNode);
            }
            Integer position = readInteger(stepNode, "position");
            Integer effectivePosition = position == null ? Integer.valueOf(fallbackPosition) : position;
            String displayText = "第" + effectivePosition + "步：" + buildDisplayText(phrases, factCard);
            addSequenceRequirement(requirements, displayText, phrases, effectivePosition);
            fallbackPosition++;
        }
        return requirements;
    }

    /**
     * 收集状态答案覆盖要求。
     *
     * @param rootNode 结构化 JSON 根节点
     * @param factCard 事实证据卡
     * @return 覆盖要求
     */
    private List<CoverageRequirement> collectStatusRequirements(JsonNode rootNode, FactCardRecord factCard) {
        List<JsonNode> itemNodes = readArrayNodes(rootNode, "items", "rows");
        List<CoverageRequirement> requirements = new ArrayList<CoverageRequirement>();
        for (JsonNode itemNode : itemNodes) {
            String subject = readFirstText(itemNode, STATUS_SUBJECT_FIELDS);
            String status = readFirstText(itemNode, STATUS_VALUE_FIELDS);
            String statusGroup = readText(itemNode, "statusGroup");
            if (subject.isBlank() || status.isBlank()) {
                continue;
            }
            String displayText = subject + " = " + status;
            requirements.add(CoverageRequirement.status(displayText, subject, status, statusGroup));
        }
        return requirements;
    }

    /**
     * 收集规则答案覆盖要求。
     *
     * @param rootNode 结构化 JSON 根节点
     * @param factCard 事实证据卡
     * @return 覆盖要求
     */
    private List<CoverageRequirement> collectPolicyRequirements(JsonNode rootNode, FactCardRecord factCard) {
        List<CoverageRequirement> requirements = new ArrayList<CoverageRequirement>();
        List<JsonNode> constraintNodes = readArrayNodes(rootNode, "constraints", "rules", "requirements", "items");
        for (JsonNode constraintNode : constraintNodes) {
            List<String> phrases = collectPreferredTextValues(constraintNode, POLICY_CONSTRAINT_FIELDS);
            if (phrases.isEmpty()) {
                phrases = collectAllTextValues(constraintNode);
            }
            addGenericRequirement(requirements, buildDisplayText(phrases, factCard), phrases);
        }
        List<JsonNode> scopeNodes = readArrayNodes(rootNode, "scopes", "scope", "ranges");
        for (JsonNode scopeNode : scopeNodes) {
            List<String> phrases = collectPreferredTextValues(scopeNode, POLICY_SCOPE_FIELDS);
            if (phrases.isEmpty()) {
                phrases = collectAllTextValues(scopeNode);
            }
            addGenericRequirement(requirements, "适用范围：" + buildDisplayText(phrases, factCard), phrases);
        }
        return requirements;
    }

    /**
     * 按问题中点名的状态值收窄状态校验范围。
     *
     * @param question 原始问题
     * @param requirements 覆盖要求
     * @return 收窄后的覆盖要求
     */
    private List<CoverageRequirement> filterStatusRequirementsByQuestion(
            String question,
            List<CoverageRequirement> requirements
    ) {
        String normalizedQuestion = normalizeForSearch(question);
        Set<String> focusedGroups = new LinkedHashSet<String>();
        for (CoverageRequirement requirement : requirements) {
            String normalizedStatus = normalizeForSearch(requirement.getStatus());
            if (!normalizedStatus.isBlank() && normalizedQuestion.contains(normalizedStatus)) {
                focusedGroups.add(requirement.getStatusGroup());
            }
        }
        if (focusedGroups.isEmpty()) {
            return requirements;
        }
        List<CoverageRequirement> focusedRequirements = new ArrayList<CoverageRequirement>();
        for (CoverageRequirement requirement : requirements) {
            if (focusedGroups.contains(requirement.getStatusGroup())) {
                focusedRequirements.add(requirement);
            }
        }
        return focusedRequirements;
    }

    /**
     * 判断事实证据卡是否匹配目标答案形态。
     *
     * @param factCard 事实证据卡
     * @param answerShape 答案形态
     * @return 匹配返回 true
     */
    private boolean matchesShape(FactCardRecord factCard, AnswerShape answerShape) {
        if (factCard == null) {
            return false;
        }
        if (factCard.getAnswerShape() == answerShape) {
            return true;
        }
        FactCardType cardType = factCard.getCardType();
        return (answerShape == AnswerShape.ENUM && cardType == FactCardType.FACT_ENUM)
                || (answerShape == AnswerShape.COMPARE && cardType == FactCardType.FACT_COMPARE)
                || (answerShape == AnswerShape.SEQUENCE && cardType == FactCardType.FACT_SEQUENCE)
                || (answerShape == AnswerShape.STATUS && cardType == FactCardType.FACT_STATUS)
                || (answerShape == AnswerShape.POLICY && cardType == FactCardType.FACT_POLICY);
    }

    /**
     * 增加普通覆盖要求。
     *
     * @param requirements 覆盖要求集合
     * @param displayText 展示文本
     * @param phrases 必须覆盖的短语
     */
    private void addGenericRequirement(
            List<CoverageRequirement> requirements,
            String displayText,
            List<String> phrases
    ) {
        List<String> normalizedPhrases = normalizePhrases(phrases);
        if (!normalizedPhrases.isEmpty()) {
            requirements.add(CoverageRequirement.generic(displayText, normalizedPhrases));
        }
    }

    /**
     * 增加顺序覆盖要求。
     *
     * @param requirements 覆盖要求集合
     * @param displayText 展示文本
     * @param phrases 必须覆盖的短语
     * @param position 顺序位置
     */
    private void addSequenceRequirement(
            List<CoverageRequirement> requirements,
            String displayText,
            List<String> phrases,
            Integer position
    ) {
        List<String> normalizedPhrases = normalizePhrases(phrases);
        if (!normalizedPhrases.isEmpty()) {
            requirements.add(CoverageRequirement.sequence(displayText, normalizedPhrases, position));
        }
    }

    /**
     * 解析 JSON 根节点。
     *
     * @param itemsJson 结构化条目 JSON
     * @return JSON 根节点
     */
    private JsonNode readRootNode(String itemsJson) {
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
    private List<JsonNode> readArrayNodes(JsonNode rootNode, String... fieldNames) {
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
    private List<JsonNode> toNodeList(JsonNode arrayNode) {
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
    private List<String> collectPreferredTextValues(JsonNode node, List<String> fieldNames) {
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
    private List<String> collectAllTextValues(JsonNode node) {
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
    private void collectAllTextValues(JsonNode node, List<String> values) {
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
    private boolean shouldIgnoreField(String fieldName) {
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
    private String readFirstText(JsonNode node, List<String> fieldNames) {
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
    private String readText(JsonNode node, String fieldName) {
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
    private Integer readInteger(JsonNode node, String fieldName) {
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
    private String buildDisplayText(List<String> phrases, FactCardRecord factCard) {
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
    private boolean areAllPhrasesCovered(List<String> phrases, String normalizedAnswer) {
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
    private boolean isAnyPhraseCovered(List<String> phrases, String normalizedAnswer) {
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
    private boolean isPhraseCovered(String phrase, String normalizedAnswer) {
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
    private int findRequirementPosition(CoverageRequirement requirement, String normalizedAnswer) {
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
    private int findPhrasePosition(String phrase, String normalizedAnswer) {
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
    private boolean isNonDecreasing(List<Integer> positions) {
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
    private String buildSequenceOrderIssue(List<CoverageRequirement> requirements) {
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
    private List<String> splitAnswerSegments(String answerMarkdown) {
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
    private List<String> normalizePhrases(List<String> phrases) {
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
    private String normalizeForSearch(String value) {
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
    private String compact(String value) {
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
    private List<String> splitMeaningfulTokens(String normalizedPhrase) {
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
    private List<FactCardRecord> safeFactCards(List<FactCardRecord> factCards) {
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
    private boolean sameText(String left, String right) {
        return normalizeForSearch(left).equals(normalizeForSearch(right));
    }

    /**
     * 向列表追加唯一值。
     *
     * @param values 目标列表
     * @param value 待追加值
     */
    private void addUnique(List<String> values, String value) {
        if (value == null || value.isBlank() || values.contains(value)) {
            return;
        }
        values.add(value);
    }

    /**
     * 覆盖要求类型。
     */
    private enum RequirementKind {

        GENERIC,

        SEQUENCE_STEP,

        STATUS_ITEM
    }

    /**
     * 覆盖要求。
     */
    private static final class CoverageRequirement {

        private final RequirementKind kind;

        private final String displayText;

        private final List<String> phrases;

        private final Integer sequencePosition;

        private final String subject;

        private final String status;

        private final String statusGroup;

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
        private CoverageRequirement(
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
        private static CoverageRequirement generic(String displayText, List<String> phrases) {
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
        private static CoverageRequirement sequence(
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
        private static CoverageRequirement status(
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
        private RequirementKind getKind() {
            return kind;
        }

        /**
         * 获取展示文本。
         *
         * @return 展示文本
         */
        private String getDisplayText() {
            return displayText;
        }

        /**
         * 获取必须覆盖的短语。
         *
         * @return 必须覆盖的短语
         */
        private List<String> getPhrases() {
            return phrases;
        }

        /**
         * 获取顺序位置。
         *
         * @return 顺序位置
         */
        private Integer getSequencePosition() {
            return sequencePosition;
        }

        /**
         * 获取状态主语。
         *
         * @return 状态主语
         */
        private String getSubject() {
            return subject;
        }

        /**
         * 获取状态值。
         *
         * @return 状态值
         */
        private String getStatus() {
            return status;
        }

        /**
         * 获取状态分组。
         *
         * @return 状态分组
         */
        private String getStatusGroup() {
            return statusGroup;
        }
    }

    /**
     * 覆盖评估结果。
     */
    private static final class CoverageEvaluation {

        private final boolean covered;

        private final int answerPosition;

        private final boolean evidenceTouched;

        private final String missingItem;

        /**
         * 创建覆盖评估结果。
         *
         * @param covered 是否覆盖
         * @param answerPosition 答案中出现位置
         * @param missingItem 缺失项
         */
        private CoverageEvaluation(boolean covered, int answerPosition, boolean evidenceTouched, String missingItem) {
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
        private static CoverageEvaluation covered(int answerPosition) {
            return new CoverageEvaluation(true, answerPosition, true, "");
        }

        /**
         * 创建缺失结果。
         *
         * @param missingItem 缺失项
         * @return 覆盖评估结果
         */
        private static CoverageEvaluation missing(String missingItem) {
            return new CoverageEvaluation(false, -1, false, missingItem);
        }

        /**
         * 创建缺失结果。
         *
         * @param missingItem 缺失项
         * @param evidenceTouched 答案是否已触达该证据要求的部分短语
         * @return 覆盖评估结果
         */
        private static CoverageEvaluation missing(String missingItem, boolean evidenceTouched) {
            return new CoverageEvaluation(false, -1, evidenceTouched, missingItem);
        }

        /**
         * 返回是否覆盖。
         *
         * @return 是否覆盖
         */
        private boolean isCovered() {
            return covered;
        }

        /**
         * 获取答案中出现位置。
         *
         * @return 答案中出现位置
         */
        private int getAnswerPosition() {
            return answerPosition;
        }

        /**
         * 返回答案是否已触达该证据要求的部分短语。
         *
         * @return 已触达返回 true
         */
        private boolean isEvidenceTouched() {
            return evidenceTouched;
        }

        /**
         * 获取缺失项。
         *
         * @return 缺失项
         */
        private String getMissingItem() {
            return missingItem;
        }
    }

    /**
     * 覆盖评估摘要。
     */
    private static final class CoverageEvaluationSummary {

        private final int coveredCount;

        private final List<String> missingItems;

        /**
         * 创建覆盖评估摘要。
         *
         * @param coveredCount 已覆盖数量
         * @param missingItems 缺失项
         */
        private CoverageEvaluationSummary(int coveredCount, List<String> missingItems) {
            this.coveredCount = coveredCount;
            this.missingItems = missingItems == null ? List.of() : List.copyOf(missingItems);
        }

        /**
         * 获取已覆盖数量。
         *
         * @return 已覆盖数量
         */
        private int getCoveredCount() {
            return coveredCount;
        }

        /**
         * 获取缺失项。
         *
         * @return 缺失项
         */
        private List<String> getMissingItems() {
            return missingItems;
        }
    }
}
