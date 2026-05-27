package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.service.mapper.FactCardGenerationMapper;
import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 事实卡列表与顺序生成支持
 *
 * 职责：承载 FactCardGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
abstract class FactCardGenerationListSupport extends FactCardGenerationTableSupport {

/**
     * 从 bullet 列表生成枚举事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    List<FactCardRecord> generateBulletEnumCards(FactCardSourceChunkView chunk, List<String> lines) {
        List<LineItem> items = findBulletItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        for (LineItem item : items) {
            ObjectNode itemNode = OBJECT_MAPPER.createObjectNode();
            itemNode.put("order", item.getOrder());
            itemNode.put("text", item.getText());
            itemsNode.add(itemNode);
        }
        rootNode.put("structure", "bullet_list");
        rootNode.set("items", itemsNode);
        String evidenceText = joinEvidence(items);
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "结构化列表条目",
                "识别到 " + items.size() + " 个列表条目。",
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, true),
                0.82D
        );
        return List.of(record);
    }

    /**
     * 从重复键值行生成枚举事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    List<FactCardRecord> generateKeyValueEnumCards(FactCardSourceChunkView chunk, List<String> lines) {
        List<KeyValueItem> items = findKeyValueItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        boolean hasStructuredPath = false;
        for (KeyValueItem item : items) {
            ObjectNode itemNode = OBJECT_MAPPER.createObjectNode();
            itemNode.put("key", item.getKey());
            itemNode.put("value", item.getValue());
            itemNode.put("raw", item.getRaw());
            itemNode.put("parentPath", item.getParentPath());
            itemNode.put("keyPath", item.getKeyPath());
            itemNode.put("contextPath", item.getContextPath());
            itemNode.put("displayText", item.getDisplayText());
            itemNode.put("lineIndex", item.getLineIndex());
            ArrayNode pathSegmentsNode = OBJECT_MAPPER.createArrayNode();
            for (String pathSegment : item.getPathSegments()) {
                pathSegmentsNode.add(pathSegment);
            }
            itemNode.set("pathSegments", pathSegmentsNode);
            itemsNode.add(itemNode);
            hasStructuredPath = hasStructuredPath || item.hasStructuredPath();
        }
        rootNode.put("structure", "key_value_list");
        rootNode.put("pathAware", hasStructuredPath);
        rootNode.set("items", itemsNode);
        String evidenceText = joinKeyValueEvidence(items);
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "结构化键值条目",
                "识别到 " + items.size() + " 个键值条目。",
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, true),
                0.80D
        );
        return List.of(record);
    }

    /**
     * 从有序列表生成顺序事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    List<FactCardRecord> generateSequenceCards(FactCardSourceChunkView chunk, List<String> lines) {
        List<LineItem> items = findOrderedItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode stepsNode = OBJECT_MAPPER.createArrayNode();
        int stepIndex = 1;
        for (LineItem item : items) {
            ObjectNode stepNode = OBJECT_MAPPER.createObjectNode();
            stepNode.put("order", item.getOrder());
            stepNode.put("position", stepIndex);
            stepNode.put("text", item.getText());
            stepsNode.add(stepNode);
            stepIndex++;
        }
        rootNode.put("structure", "ordered_sequence");
        rootNode.set("steps", stepsNode);
        String evidenceText = joinEvidence(items);
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_SEQUENCE,
                AnswerShape.SEQUENCE,
                "结构化顺序步骤",
                "识别到 " + items.size() + " 个顺序步骤。",
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, true),
                0.83D
        );
        return List.of(record);
    }
}
