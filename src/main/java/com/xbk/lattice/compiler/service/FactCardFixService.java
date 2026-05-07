package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 事实证据卡修复服务
 *
 * 职责：对结构化证据卡执行保守字段归一与结构整理，不创造 source 中不存在的事实
 *
 * @author xiexu
 */
@Service
public class FactCardFixService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final FactCardReviewer factCardReviewer;

    /**
     * 创建事实证据卡修复服务。
     */
    public FactCardFixService() {
        this(new FactCardReviewer());
    }

    /**
     * 创建事实证据卡修复服务。
     *
     * @param factCardReviewer 事实证据卡审查器
     */
    public FactCardFixService(FactCardReviewer factCardReviewer) {
        this.factCardReviewer = factCardReviewer == null ? new FactCardReviewer() : factCardReviewer;
    }

    /**
     * 修复事实证据卡结构并执行复核。
     *
     * @param factCardRecord 原始事实证据卡
     * @param sourceChunkTexts source chunk 文本
     * @return 修复结果
     */
    public FactCardFixResult fix(FactCardRecord factCardRecord, List<String> sourceChunkTexts) {
        if (factCardRecord == null) {
            FactCardReviewResult reviewResult = factCardReviewer.review(null, sourceChunkTexts);
            return new FactCardFixResult(null, List.of(), reviewResult);
        }
        JsonNode rootNode = readRootNode(factCardRecord.getItemsJson());
        List<String> actions = new ArrayList<String>();
        JsonNode fixedNode = fixByShape(factCardRecord, rootNode, actions);
        FactCardRecord fixedRecord = rebuildRecord(factCardRecord, fixedNode);
        FactCardReviewResult reviewResult = factCardReviewer.review(fixedRecord, sourceChunkTexts);
        return new FactCardFixResult(fixedRecord, actions, reviewResult);
    }

    /**
     * 按答案形态修复 JSON 结构。
     *
     * @param factCardRecord 事实证据卡
     * @param rootNode JSON 根节点
     * @param actions 修复动作
     * @return 修复后的 JSON
     */
    private JsonNode fixByShape(FactCardRecord factCardRecord, JsonNode rootNode, List<String> actions) {
        if (factCardRecord.getAnswerShape() == AnswerShape.ENUM
                || factCardRecord.getCardType() == FactCardType.FACT_ENUM) {
            return fixEnumNode(rootNode, actions);
        }
        if (factCardRecord.getAnswerShape() == AnswerShape.COMPARE
                || factCardRecord.getCardType() == FactCardType.FACT_COMPARE) {
            return fixCompareNode(rootNode, actions);
        }
        if (factCardRecord.getAnswerShape() == AnswerShape.SEQUENCE
                || factCardRecord.getCardType() == FactCardType.FACT_SEQUENCE) {
            return fixSequenceNode(rootNode, actions);
        }
        if (factCardRecord.getAnswerShape() == AnswerShape.STATUS
                || factCardRecord.getCardType() == FactCardType.FACT_STATUS) {
            return fixStatusNode(rootNode, actions);
        }
        if (factCardRecord.getAnswerShape() == AnswerShape.POLICY
                || factCardRecord.getCardType() == FactCardType.FACT_POLICY) {
            return fixPolicyNode(rootNode, actions);
        }
        return rootNode;
    }

    /**
     * 修复枚举卡结构。
     *
     * @param rootNode JSON 根节点
     * @param actions 修复动作
     * @return 修复后的 JSON
     */
    private JsonNode fixEnumNode(JsonNode rootNode, List<String> actions) {
        ObjectNode fixedNode = objectRoot(rootNode);
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        for (JsonNode itemNode : readArrayNodes(rootNode, "items", "rows")) {
            JsonNode fixedItemNode = trimTextNode(itemNode);
            if (!isEmptyNode(fixedItemNode)) {
                itemsNode.add(fixedItemNode);
            }
        }
        if (itemsNode.size() != readArrayNodes(rootNode, "items", "rows").size()) {
            actions.add("移除空枚举条目");
        }
        fixedNode.set("items", itemsNode);
        return fixedNode;
    }

    /**
     * 修复对照卡结构。
     *
     * @param rootNode JSON 根节点
     * @param actions 修复动作
     * @return 修复后的 JSON
     */
    private JsonNode fixCompareNode(JsonNode rootNode, List<String> actions) {
        ObjectNode fixedNode = objectRoot(rootNode);
        ArrayNode rowsNode = OBJECT_MAPPER.createArrayNode();
        for (JsonNode rowNode : readArrayNodes(rootNode, "rows", "items")) {
            rowsNode.add(trimTextNode(rowNode));
        }
        fixedNode.set("rows", rowsNode);
        if (!rowsNode.equals(rootNode.get("rows"))) {
            actions.add("归一对照行字段空白");
        }
        return fixedNode;
    }

    /**
     * 修复顺序卡结构。
     *
     * @param rootNode JSON 根节点
     * @param actions 修复动作
     * @return 修复后的 JSON
     */
    private JsonNode fixSequenceNode(JsonNode rootNode, List<String> actions) {
        ObjectNode fixedNode = objectRoot(rootNode);
        List<JsonNode> stepNodes = new ArrayList<JsonNode>(readArrayNodes(rootNode, "steps", "items"));
        stepNodes.sort(Comparator.comparingInt(this::sequencePositionOrMax));
        ArrayNode stepsNode = OBJECT_MAPPER.createArrayNode();
        for (JsonNode stepNode : stepNodes) {
            stepsNode.add(trimTextNode(stepNode));
        }
        fixedNode.set("steps", stepsNode);
        if (!sameNodeOrder(stepNodes, readArrayNodes(rootNode, "steps", "items"))) {
            actions.add("按 position 重排顺序步骤");
        }
        return fixedNode;
    }

    /**
     * 修复状态卡结构。
     *
     * @param rootNode JSON 根节点
     * @param actions 修复动作
     * @return 修复后的 JSON
     */
    private JsonNode fixStatusNode(JsonNode rootNode, List<String> actions) {
        ObjectNode fixedNode = objectRoot(rootNode);
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        List<JsonNode> itemNodes = readArrayNodes(rootNode, "items", "rows");
        for (JsonNode itemNode : itemNodes) {
            itemsNode.add(trimTextNode(itemNode));
        }
        fixedNode.set("items", itemsNode);
        ArrayNode conflictSubjectsNode = OBJECT_MAPPER.createArrayNode();
        for (String conflictSubject : findConflictSubjects(itemNodes)) {
            conflictSubjectsNode.add(conflictSubject);
        }
        fixedNode.set("conflictSubjects", conflictSubjectsNode);
        if (conflictSubjectsNode.size() > 0) {
            actions.add("补充状态冲突主语标记");
        }
        return fixedNode;
    }

    /**
     * 修复规则卡结构。
     *
     * @param rootNode JSON 根节点
     * @param actions 修复动作
     * @return 修复后的 JSON
     */
    private JsonNode fixPolicyNode(JsonNode rootNode, List<String> actions) {
        ObjectNode fixedNode = objectRoot(rootNode);
        fixedNode.set("constraints", trimArray(readArrayNodes(rootNode, "constraints", "rules", "requirements", "items")));
        fixedNode.set("scopes", trimArray(readArrayNodes(rootNode, "scopes", "scope", "ranges")));
        actions.add("归一规则约束与适用范围字段");
        return fixedNode;
    }

    /**
     * 重建事实证据卡。
     *
     * @param originalRecord 原始记录
     * @param fixedNode 修复后的 JSON 节点
     * @return 修复后的记录
     */
    private FactCardRecord rebuildRecord(FactCardRecord originalRecord, JsonNode fixedNode) {
        return new FactCardRecord(
                originalRecord.getId(),
                originalRecord.getCardId(),
                originalRecord.getSourceId(),
                originalRecord.getSourceFileId(),
                originalRecord.getCardType(),
                originalRecord.getAnswerShape(),
                originalRecord.getTitle(),
                originalRecord.getClaim(),
                writeJson(fixedNode),
                originalRecord.getEvidenceText(),
                originalRecord.getSourceChunkIds(),
                originalRecord.getArticleIds(),
                originalRecord.getConfidence(),
                resolveReviewStatus(originalRecord),
                originalRecord.getContentHash(),
                originalRecord.getCreatedAt(),
                originalRecord.getUpdatedAt()
        );
    }

    /**
     * 解析修复前的审查状态。
     *
     * @param originalRecord 原始记录
     * @return 审查状态
     */
    private FactCardReviewStatus resolveReviewStatus(FactCardRecord originalRecord) {
        return originalRecord.getReviewStatus() == null
                ? FactCardReviewStatus.NEEDS_HUMAN_REVIEW
                : originalRecord.getReviewStatus();
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
     * 写出 JSON 文本。
     *
     * @param node JSON 节点
     * @return JSON 文本
     */
    private String writeJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node == null ? OBJECT_MAPPER.createObjectNode() : node);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to write fixed fact card json", exception);
        }
    }

    /**
     * 获取对象根节点副本。
     *
     * @param rootNode JSON 根节点
     * @return 对象节点
     */
    private ObjectNode objectRoot(JsonNode rootNode) {
        if (rootNode != null && rootNode.isObject()) {
            return rootNode.deepCopy();
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    /**
     * 读取多个候选字段中的数组节点。
     *
     * @param rootNode JSON 根节点
     * @param fieldNames 字段名
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
     * 转换数组节点为列表。
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
     * 修剪数组内文本字段并移除空对象。
     *
     * @param nodes 节点列表
     * @return 数组节点
     */
    private ArrayNode trimArray(List<JsonNode> nodes) {
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        for (JsonNode node : nodes) {
            JsonNode trimmedNode = trimTextNode(node);
            if (!isEmptyNode(trimmedNode)) {
                arrayNode.add(trimmedNode);
            }
        }
        return arrayNode;
    }

    /**
     * 修剪 JSON 节点中的文本空白。
     *
     * @param node JSON 节点
     * @return 修剪后的节点
     */
    private JsonNode trimTextNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return OBJECT_MAPPER.nullNode();
        }
        if (node.isTextual()) {
            return OBJECT_MAPPER.getNodeFactory().textNode(node.asText("").trim());
        }
        if (node.isArray()) {
            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            for (JsonNode itemNode : node) {
                arrayNode.add(trimTextNode(itemNode));
            }
            return arrayNode;
        }
        if (node.isObject()) {
            ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
            node.fields().forEachRemaining(entry -> objectNode.set(entry.getKey(), trimTextNode(entry.getValue())));
            return objectNode;
        }
        return node.deepCopy();
    }

    /**
     * 判断节点是否为空。
     *
     * @param node JSON 节点
     * @return 为空返回 true
     */
    private boolean isEmptyNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return true;
        }
        if (node.isTextual()) {
            return node.asText("").trim().isBlank();
        }
        if (node.isObject()) {
            return collectTextValues(node).isEmpty();
        }
        return node.isArray() && node.isEmpty();
    }

    /**
     * 读取顺序位置，缺失时排到最后。
     *
     * @param node JSON 节点
     * @return 顺序位置
     */
    private int sequencePositionOrMax(JsonNode node) {
        JsonNode positionNode = node == null ? null : node.get("position");
        if (positionNode == null || !positionNode.canConvertToInt()) {
            return Integer.MAX_VALUE;
        }
        return positionNode.intValue();
    }

    /**
     * 判断两个节点列表顺序是否一致。
     *
     * @param leftNodes 左侧节点
     * @param rightNodes 右侧节点
     * @return 一致返回 true
     */
    private boolean sameNodeOrder(List<JsonNode> leftNodes, List<JsonNode> rightNodes) {
        if (leftNodes.size() != rightNodes.size()) {
            return false;
        }
        for (int index = 0; index < leftNodes.size(); index++) {
            if (!leftNodes.get(index).equals(rightNodes.get(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查找状态冲突主语。
     *
     * @param itemNodes 状态条目
     * @return 冲突主语
     */
    private List<String> findConflictSubjects(List<JsonNode> itemNodes) {
        Map<String, Set<String>> groupsBySubject = new LinkedHashMap<String, Set<String>>();
        Map<String, String> displaySubjectByKey = new LinkedHashMap<String, String>();
        for (JsonNode itemNode : itemNodes) {
            String subject = readFirstText(itemNode, "subject", "name", "label", "key", "item");
            String status = readFirstText(itemNode, "status", "state", "value", "result");
            String statusGroup = readFirstText(itemNode, "statusGroup", "group");
            if (subject.isBlank() || status.isBlank()) {
                continue;
            }
            String subjectKey = normalizeKey(subject);
            String group = statusGroup.isBlank() ? normalizeKey(status) : statusGroup;
            displaySubjectByKey.putIfAbsent(subjectKey, subject);
            groupsBySubject.computeIfAbsent(subjectKey, ignored -> new LinkedHashSet<String>()).add(group);
        }
        List<String> conflictSubjects = new ArrayList<String>();
        for (Map.Entry<String, Set<String>> entry : groupsBySubject.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflictSubjects.add(displaySubjectByKey.get(entry.getKey()));
            }
        }
        return conflictSubjects;
    }

    /**
     * 从候选字段读取第一个文本。
     *
     * @param node JSON 节点
     * @param fieldNames 字段名
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
     * 收集 JSON 节点中的文本值。
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
     * 递归收集文本值。
     *
     * @param node JSON 节点
     * @param values 文本值
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
     * 归一化状态主语 key。
     *
     * @param value 文本
     * @return key
     */
    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s+", "")
                .replaceAll("[：:，,；;\\-—>]", "")
                .toLowerCase(Locale.ROOT);
    }
}
