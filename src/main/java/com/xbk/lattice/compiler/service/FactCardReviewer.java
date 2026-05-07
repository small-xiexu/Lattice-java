package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 事实证据卡审查器
 *
 * 职责：对结构化证据卡执行通用完整性、source 回指、对照、顺序、状态和规则边界审查
 *
 * @author xiexu
 */
@Service
public class FactCardReviewer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.75D;

    /**
     * 审查事实证据卡。
     *
     * @param factCardRecord 事实证据卡
     * @param sourceChunkTexts source chunk 文本
     * @return 审查结果
     */
    public FactCardReviewResult review(FactCardRecord factCardRecord, List<String> sourceChunkTexts) {
        if (factCardRecord == null) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.NEEDS_HUMAN_REVIEW,
                    List.of("事实证据卡为空")
            );
        }
        JsonNode rootNode = readRootNode(factCardRecord.getItemsJson());
        FactCardReviewResult structureResult = reviewStructure(factCardRecord, rootNode);
        if (structureResult.getReviewStatus() != FactCardReviewStatus.VALID) {
            return structureResult;
        }
        FactCardReviewResult sourceReferenceResult = reviewSourceReference(factCardRecord, sourceChunkTexts);
        if (sourceReferenceResult.getReviewStatus() != FactCardReviewStatus.VALID) {
            return sourceReferenceResult;
        }
        return FactCardReviewResult.valid();
    }

    /**
     * 审查结构化字段完整性。
     *
     * @param factCardRecord 事实证据卡
     * @param rootNode 结构化 JSON 根节点
     * @return 审查结果
     */
    private FactCardReviewResult reviewStructure(FactCardRecord factCardRecord, JsonNode rootNode) {
        List<String> missingReasons = validateRequiredFields(factCardRecord, rootNode);
        if (!missingReasons.isEmpty()) {
            return FactCardReviewResult.of(FactCardReviewStatus.INCOMPLETE, missingReasons);
        }
        AnswerShape answerShape = factCardRecord.getAnswerShape();
        if (answerShape == AnswerShape.ENUM || factCardRecord.getCardType() == FactCardType.FACT_ENUM) {
            return reviewEnumStructure(rootNode);
        }
        if (answerShape == AnswerShape.COMPARE || factCardRecord.getCardType() == FactCardType.FACT_COMPARE) {
            return reviewCompareStructure(rootNode);
        }
        if (answerShape == AnswerShape.SEQUENCE || factCardRecord.getCardType() == FactCardType.FACT_SEQUENCE) {
            return reviewSequenceStructure(rootNode);
        }
        if (answerShape == AnswerShape.STATUS || factCardRecord.getCardType() == FactCardType.FACT_STATUS) {
            return reviewStatusStructure(rootNode);
        }
        if (answerShape == AnswerShape.POLICY || factCardRecord.getCardType() == FactCardType.FACT_POLICY) {
            return reviewPolicyStructure(rootNode);
        }
        return FactCardReviewResult.of(
                FactCardReviewStatus.NEEDS_HUMAN_REVIEW,
                List.of("未知事实证据卡形态")
        );
    }

    /**
     * 校验事实证据卡必填字段。
     *
     * @param factCardRecord 事实证据卡
     * @param rootNode 结构化 JSON 根节点
     * @return 缺失原因
     */
    private List<String> validateRequiredFields(FactCardRecord factCardRecord, JsonNode rootNode) {
        List<String> reasons = new ArrayList<String>();
        if (isBlank(factCardRecord.getCardId())) {
            reasons.add("cardId 为空");
        }
        if (factCardRecord.getCardType() == null) {
            reasons.add("cardType 为空");
        }
        if (factCardRecord.getAnswerShape() == null) {
            reasons.add("answerShape 为空");
        }
        if (isBlank(factCardRecord.getEvidenceText())) {
            reasons.add("evidenceText 为空");
        }
        if (rootNode == null || rootNode.isMissingNode() || rootNode.isNull() || rootNode.isEmpty()) {
            reasons.add("itemsJson 为空或不可解析");
        }
        return reasons;
    }

    /**
     * 审查枚举卡结构。
     *
     * @param rootNode 结构化 JSON 根节点
     * @return 审查结果
     */
    private FactCardReviewResult reviewEnumStructure(JsonNode rootNode) {
        List<JsonNode> itemNodes = readArrayNodes(rootNode, "items", "rows");
        if (itemNodes.isEmpty()) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.INCOMPLETE,
                    List.of("枚举卡缺少 items")
            );
        }
        for (JsonNode itemNode : itemNodes) {
            if (collectTextValues(itemNode).isEmpty()) {
                return FactCardReviewResult.of(
                        FactCardReviewStatus.INCOMPLETE,
                        List.of("枚举卡存在空条目")
                );
            }
        }
        return FactCardReviewResult.valid();
    }

    /**
     * 审查对照卡结构。
     *
     * @param rootNode 结构化 JSON 根节点
     * @return 审查结果
     */
    private FactCardReviewResult reviewCompareStructure(JsonNode rootNode) {
        List<JsonNode> rowNodes = readArrayNodes(rootNode, "rows", "items");
        if (rowNodes.isEmpty()) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.INCOMPLETE,
                    List.of("对照卡缺少 rows")
            );
        }
        for (JsonNode rowNode : rowNodes) {
            if (collectTextValues(rowNode).size() < 2 || hasBlankObjectTextField(rowNode)) {
                return FactCardReviewResult.of(
                        FactCardReviewStatus.INCOMPLETE,
                        List.of("对照卡存在缺侧行")
                );
            }
        }
        return FactCardReviewResult.valid();
    }

    /**
     * 审查顺序卡结构。
     *
     * @param rootNode 结构化 JSON 根节点
     * @return 审查结果
     */
    private FactCardReviewResult reviewSequenceStructure(JsonNode rootNode) {
        List<JsonNode> stepNodes = readArrayNodes(rootNode, "steps", "items");
        if (stepNodes.size() < 2) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.INCOMPLETE,
                    List.of("顺序卡步骤不足")
            );
        }
        int lastPosition = 0;
        Set<Integer> seenPositions = new LinkedHashSet<Integer>();
        for (JsonNode stepNode : stepNodes) {
            Integer position = readInteger(stepNode, "position");
            String text = readFirstText(stepNode, "text", "label", "name", "value");
            if (position == null || isBlank(text)) {
                return FactCardReviewResult.of(
                        FactCardReviewStatus.INCOMPLETE,
                        List.of("顺序卡步骤缺少 position 或 text")
                );
            }
            if (position.intValue() <= lastPosition || seenPositions.contains(position)) {
                return FactCardReviewResult.of(
                        FactCardReviewStatus.CONFLICT,
                        List.of("顺序卡步骤位置重复或倒序")
                );
            }
            seenPositions.add(position);
            lastPosition = position.intValue();
        }
        return FactCardReviewResult.valid();
    }

    /**
     * 审查状态卡结构。
     *
     * @param rootNode 结构化 JSON 根节点
     * @return 审查结果
     */
    private FactCardReviewResult reviewStatusStructure(JsonNode rootNode) {
        List<JsonNode> itemNodes = readArrayNodes(rootNode, "items", "rows");
        if (itemNodes.isEmpty()) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.INCOMPLETE,
                    List.of("状态卡缺少 items")
            );
        }
        Map<String, Set<String>> groupsBySubject = new LinkedHashMap<String, Set<String>>();
        for (JsonNode itemNode : itemNodes) {
            String subject = readFirstText(itemNode, "subject", "name", "label", "key", "item");
            String status = readFirstText(itemNode, "status", "state", "value", "result");
            String statusGroup = readFirstText(itemNode, "statusGroup", "group");
            if (isBlank(subject) || isBlank(status)) {
                return FactCardReviewResult.of(
                        FactCardReviewStatus.INCOMPLETE,
                        List.of("状态卡条目缺少 subject 或 status")
                );
            }
            String subjectKey = normalizeKey(subject);
            String group = isBlank(statusGroup) ? normalizeKey(status) : statusGroup;
            groupsBySubject.computeIfAbsent(subjectKey, ignored -> new LinkedHashSet<String>()).add(group);
        }
        List<String> conflictSubjects = readConflictSubjects(rootNode);
        if (!conflictSubjects.isEmpty() || hasStatusGroupConflict(groupsBySubject)) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.CONFLICT,
                    List.of("状态卡存在互斥状态冲突")
            );
        }
        return FactCardReviewResult.valid();
    }

    /**
     * 审查规则卡结构。
     *
     * @param rootNode 结构化 JSON 根节点
     * @return 审查结果
     */
    private FactCardReviewResult reviewPolicyStructure(JsonNode rootNode) {
        List<JsonNode> constraintNodes = readArrayNodes(rootNode, "constraints", "rules", "requirements", "items");
        if (constraintNodes.isEmpty()) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.INCOMPLETE,
                    List.of("规则卡缺少 constraints")
            );
        }
        for (JsonNode constraintNode : constraintNodes) {
            String constraint = readFirstText(constraintNode, "constraint", "rule", "requirement", "text", "raw");
            if (isBlank(constraint)) {
                return FactCardReviewResult.of(
                        FactCardReviewStatus.INCOMPLETE,
                        List.of("规则卡存在空约束")
                );
            }
        }
        List<JsonNode> scopeNodes = readArrayNodes(rootNode, "scopes", "scope", "ranges");
        if (scopeNodes.isEmpty()) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.INCOMPLETE,
                    List.of("规则卡缺少适用范围")
            );
        }
        return FactCardReviewResult.valid();
    }

    /**
     * 审查 source 回指质量。
     *
     * @param factCardRecord 事实证据卡
     * @param sourceChunkTexts source chunk 文本
     * @return 审查结果
     */
    private FactCardReviewResult reviewSourceReference(
            FactCardRecord factCardRecord,
            List<String> sourceChunkTexts
    ) {
        if (factCardRecord.getConfidence() < HIGH_CONFIDENCE_THRESHOLD) {
            return FactCardReviewResult.valid();
        }
        if (factCardRecord.getSourceChunkIds().isEmpty()) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.LOW_CONFIDENCE,
                    List.of("高置信卡缺少 sourceChunkIds")
            );
        }
        if (!isEvidenceLocated(factCardRecord.getEvidenceText(), sourceChunkTexts)) {
            return FactCardReviewResult.of(
                    FactCardReviewStatus.LOW_CONFIDENCE,
                    List.of("evidenceText 未能在 source chunk 中定位")
            );
        }
        return FactCardReviewResult.valid();
    }

    /**
     * 判断证据文本是否能在 source chunk 中定位。
     *
     * @param evidenceText 证据文本
     * @param sourceChunkTexts source chunk 文本
     * @return 已定位返回 true
     */
    private boolean isEvidenceLocated(String evidenceText, List<String> sourceChunkTexts) {
        String normalizedEvidence = normalizeEvidence(evidenceText);
        if (normalizedEvidence.isBlank()) {
            return false;
        }
        for (String sourceChunkText : safeTexts(sourceChunkTexts)) {
            String normalizedChunkText = normalizeEvidence(sourceChunkText);
            if (normalizedChunkText.contains(normalizedEvidence)) {
                return true;
            }
        }
        String normalizedAdjacentChunkText = normalizeEvidence(String.join("\n", safeTexts(sourceChunkTexts)));
        if (normalizedAdjacentChunkText.contains(normalizedEvidence)) {
            return true;
        }
        return false;
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
     * @param fieldNames 候选字段名
     * @return 节点列表
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
     * 把 JSON 数组节点转换为列表。
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
     * 读取整数值。
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
     * 从候选字段读取第一个非空文本。
     *
     * @param node JSON 节点
     * @param fieldNames 候选字段名
     * @return 文本
     */
    private String readFirstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isValueNode()) {
            return node.asText("").trim();
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.get(fieldName);
            if (valueNode != null && valueNode.isValueNode()) {
                String value = valueNode.asText("").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    /**
     * 读取状态冲突主语列表。
     *
     * @param rootNode JSON 根节点
     * @return 冲突主语
     */
    private List<String> readConflictSubjects(JsonNode rootNode) {
        List<String> subjects = new ArrayList<String>();
        JsonNode conflictSubjectsNode = rootNode == null ? null : rootNode.get("conflictSubjects");
        if (conflictSubjectsNode == null || !conflictSubjectsNode.isArray()) {
            return subjects;
        }
        for (JsonNode subjectNode : conflictSubjectsNode) {
            String subject = subjectNode.asText("").trim();
            if (!subject.isBlank()) {
                subjects.add(subject);
            }
        }
        return subjects;
    }

    /**
     * 判断状态分组是否存在冲突。
     *
     * @param groupsBySubject 每个主语对应的状态分组
     * @return 存在冲突返回 true
     */
    private boolean hasStatusGroupConflict(Map<String, Set<String>> groupsBySubject) {
        for (Set<String> groups : groupsBySubject.values()) {
            if (groups.size() > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集 JSON 节点中的非空文本值。
     *
     * @param node JSON 节点
     * @return 文本值
     */
    private List<String> collectTextValues(JsonNode node) {
        List<String> values = new ArrayList<String>();
        collectTextValues(node, values);
        return values;
    }

    /**
     * 判断对象文本字段是否存在空值。
     *
     * @param node JSON 节点
     * @return 存在空值返回 true
     */
    private boolean hasBlankObjectTextField(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        final boolean[] blank = new boolean[] {false};
        node.fields().forEachRemaining(entry -> {
            JsonNode valueNode = entry.getValue();
            if (valueNode != null && valueNode.isValueNode() && valueNode.asText("").trim().isBlank()) {
                blank[0] = true;
            }
        });
        return blank[0];
    }

    /**
     * 递归收集 JSON 节点文本。
     *
     * @param node JSON 节点
     * @param values 文本值集合
     */
    private void collectTextValues(JsonNode node, List<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            String value = node.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode itemNode : node) {
                collectTextValues(itemNode, values);
            }
            return;
        }
        node.fields().forEachRemaining(entry -> collectTextValues(entry.getValue(), values));
    }

    /**
     * 返回安全文本列表。
     *
     * @param sourceChunkTexts source chunk 文本
     * @return 文本列表
     */
    private List<String> safeTexts(List<String> sourceChunkTexts) {
        if (sourceChunkTexts == null || sourceChunkTexts.isEmpty()) {
            return List.of();
        }
        return sourceChunkTexts;
    }

    /**
     * 归一化证据文本。
     *
     * @param value 原始文本
     * @return 归一化文本
     */
    private String normalizeEvidence(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * 归一化主语 key。
     *
     * @param value 原始文本
     * @return 主语 key
     */
    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s+", "")
                .replaceAll("[：:，,；;\\-—>]", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 判断文本是否为空。
     *
     * @param value 文本
     * @return 为空返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
