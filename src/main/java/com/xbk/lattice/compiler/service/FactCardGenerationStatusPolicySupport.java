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
 * 事实卡状态与规则生成支持
 *
 * 职责：承载 FactCardGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
abstract class FactCardGenerationStatusPolicySupport extends FactCardGenerationListSupport {

/**
     * 从通用状态行生成状态事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    List<FactCardRecord> generateStatusCards(FactCardSourceChunkView chunk, List<String> lines) {
        List<StatusItem> items = findStatusItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        for (StatusItem item : items) {
            ObjectNode itemNode = OBJECT_MAPPER.createObjectNode();
            itemNode.put("subject", item.getSubject());
            itemNode.put("status", item.getStatus());
            itemNode.put("statusGroup", item.getStatusGroup());
            itemNode.put("raw", item.getRaw());
            itemsNode.add(itemNode);
        }
        rootNode.put("structure", "status_group");
        rootNode.set("items", itemsNode);
        ArrayNode conflictSubjectsNode = OBJECT_MAPPER.createArrayNode();
        for (String subject : findConflictSubjects(items)) {
            conflictSubjectsNode.add(subject);
        }
        rootNode.set("conflictSubjects", conflictSubjectsNode);
        boolean conflict = conflictSubjectsNode.size() > 0;
        String evidenceText = joinStatusEvidence(items);
        FactCardReviewStatus reviewStatus = conflict
                ? FactCardReviewStatus.CONFLICT
                : resolveReviewStatus(chunk, evidenceText, true);
        double confidence = conflict ? 0.45D : 0.81D;
        String claim = conflict
                ? "识别到状态冲突，需人工确认互斥状态。"
                : "识别到 " + items.size() + " 个状态条目。";
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_STATUS,
                AnswerShape.STATUS,
                "结构化状态分组",
                claim,
                writeJson(rootNode),
                evidenceText,
                reviewStatus,
                confidence
        );
        return List.of(record);
    }

    /**
     * 从通用约束行生成规则事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    List<FactCardRecord> generatePolicyCards(FactCardSourceChunkView chunk, List<String> lines) {
        List<PolicyItem> constraints = findPolicyItems(lines);
        if (constraints.isEmpty()) {
            return List.of();
        }
        List<String> scopes = findPolicyScopes(lines);
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode constraintsNode = OBJECT_MAPPER.createArrayNode();
        for (PolicyItem constraint : constraints) {
            ObjectNode constraintNode = OBJECT_MAPPER.createObjectNode();
            constraintNode.put("constraint", constraint.getText());
            constraintNode.put("raw", constraint.getRaw());
            constraintsNode.add(constraintNode);
        }
        ArrayNode scopesNode = OBJECT_MAPPER.createArrayNode();
        for (String scope : scopes) {
            scopesNode.add(scope);
        }
        rootNode.put("structure", "policy_constraints");
        rootNode.set("constraints", constraintsNode);
        rootNode.set("scopes", scopesNode);
        boolean complete = !scopes.isEmpty();
        String evidenceText = joinPolicyEvidence(lines, constraints, scopes);
        String claim = complete
                ? "识别到 " + constraints.size() + " 条规则约束及适用范围。"
                : "识别到 " + constraints.size() + " 条规则约束，但缺少适用范围。";
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_POLICY,
                AnswerShape.POLICY,
                "结构化规则约束",
                claim,
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, complete),
                complete ? 0.80D : 0.56D
        );
        return List.of(record);
    }
}
