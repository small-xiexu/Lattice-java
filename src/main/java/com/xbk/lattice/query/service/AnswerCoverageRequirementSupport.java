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
 * 答案覆盖要求收集支持。
 *
 * 职责：从结构化事实卡中提取枚举、对照、顺序、状态和规则覆盖要求。
 *
 * @author xiexu
 */
abstract class AnswerCoverageRequirementSupport extends AnswerCoverageBaseSupport {

    /**
     * 收集当前答案形态对应的覆盖要求。
     *
     * @param question 原始问题
     * @param answerShape 答案形态
     * @param factCards 事实证据卡
     * @return 覆盖要求
     */
    protected List<CoverageRequirement> collectRequirements(
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
    protected List<CoverageRequirement> collectEnumRequirements(JsonNode rootNode, FactCardRecord factCard) {
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
    protected List<CoverageRequirement> collectCompareRequirements(JsonNode rootNode, FactCardRecord factCard) {
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
    protected List<CoverageRequirement> collectSequenceRequirements(JsonNode rootNode, FactCardRecord factCard) {
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
    protected List<CoverageRequirement> collectStatusRequirements(JsonNode rootNode, FactCardRecord factCard) {
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
    protected List<CoverageRequirement> collectPolicyRequirements(JsonNode rootNode, FactCardRecord factCard) {
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
    protected List<CoverageRequirement> filterStatusRequirementsByQuestion(
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
    protected boolean matchesShape(FactCardRecord factCard, AnswerShape answerShape) {
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
    protected void addGenericRequirement(
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
    protected void addSequenceRequirement(
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
}
