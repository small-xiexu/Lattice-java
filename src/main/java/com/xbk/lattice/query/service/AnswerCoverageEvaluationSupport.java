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
 * 答案覆盖评估支持。
 *
 * 职责：根据覆盖要求评估答案命中、缺失和顺序一致性。
 *
 * @author xiexu
 */
abstract class AnswerCoverageEvaluationSupport extends AnswerCoverageRequirementSupport {

    /**
     * 汇总所有覆盖要求的答案覆盖结果。
     *
     * @param answerShape 答案形态
     * @param requirements 覆盖要求
     * @param answerMarkdown 答案 Markdown
     * @return 覆盖评估摘要
     */
    protected CoverageEvaluationSummary evaluateRequirements(
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
    protected CoverageEvaluation evaluateRequirement(
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
    protected CoverageEvaluation evaluateStatusRequirement(
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
    protected boolean hasConflictingStatus(
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
}
