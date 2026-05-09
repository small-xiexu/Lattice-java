package com.xbk.lattice.query.deepresearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.ResearchTaskHit;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceRelevanceSupport;
import com.xbk.lattice.query.service.QueryEvidenceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deep Research 证据组装支持。
 *
 * 职责：解析结构化证据 JSON，调用答案生成并装配 EvidenceCard findings。
 *
 * @author xiexu
 */
@Slf4j
abstract class DeepResearchEvidenceAssemblySupport extends DeepResearchResearcherBaseSupport {

    protected void appendStructuredEvidenceFromJson(
            EvidenceCard evidenceCard,
            String queryId,
            ResearchTask task,
            List<QueryArticleHit> hits,
            String answerSummary,
            DeepResearchExecutionContext executionContext
    ) {
        StructuredEvidenceBundle structuredEvidenceBundle = parseStructuredEvidence(answerSummary);
        if (!structuredEvidenceBundle.isValid()) {
            evidenceCard.getFollowUps().add("schema_repair_attempted");
            String repairedSummary = buildRepairSummary(
                    queryId,
                    task,
                    hits,
                    answerSummary,
                    evidenceCard,
                    executionContext
            );
            structuredEvidenceBundle = parseStructuredEvidence(repairedSummary);
        }
        if (structuredEvidenceBundle.isValid()) {
            evidenceCard.getEvidenceAnchors().addAll(structuredEvidenceBundle.getEvidenceAnchors());
            evidenceCard.getFactFindings().addAll(structuredEvidenceBundle.getFactFindings());
            return;
        }
        appendRecoveredEvidenceFromHits(
                evidenceCard,
                task,
                hits,
                executionContext,
                "structured_fact_schema_invalid"
        );
    }

    /**
     * 从检索命中恢复最小可投影证据。
     *
     * @param evidenceCard 证据卡
     * @param task 研究任务
     * @param hits 检索命中
     * @param executionContext 执行上下文
     * @param gap 需要记录的缺口类型
     */
    protected void appendRecoveredEvidenceFromHits(
            EvidenceCard evidenceCard,
            ResearchTask task,
            List<QueryArticleHit> hits,
            DeepResearchExecutionContext executionContext,
            String gap
    ) {
        int originalFindingCount = evidenceCard.getFactFindings().size();
        String fallbackSummary = fallbackSummaryFromHits(hits);
        appendFindings(evidenceCard, task, hits, fallbackSummary, executionContext);
        if (evidenceCard.getFactFindings().size() > originalFindingCount) {
            evidenceCard.getFollowUps().add("fallback_to_retrieved_evidence");
            return;
        }
        if (gap != null && !gap.isBlank()) {
            evidenceCard.getGaps().add(gap);
        }
        evidenceCard.getFollowUps().add("retry_structured_fact_extraction");
        appendAnchorOnlyEvidence(evidenceCard, hits, executionContext);
    }

    protected String buildRepairSummary(
            String queryId,
            ResearchTask task,
            List<QueryArticleHit> hits,
            String invalidSummary,
            EvidenceCard evidenceCard,
            DeepResearchExecutionContext executionContext
    ) {
        if (!executionContext.tryAcquireLlmCall()) {
            return "";
        }
        String repairPrompt = "请把以下 Deep Research 研究结果修复为 JSON Schema："
                + "{\"evidenceAnchors\":[],\"factFindings\":[]}。原始输出："
                + (invalidSummary == null ? "" : invalidSummary);
        try {
            QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                    queryId,
                    ExecutionLlmSnapshotService.DEEP_RESEARCH_SCENE,
                    ExecutionLlmSnapshotService.ROLE_RESEARCHER,
                    repairPrompt,
                    hits
            );
            return answerPayload == null || answerPayload.getAnswerMarkdown() == null
                    ? ""
                    : answerPayload.getAnswerMarkdown();
        }
        catch (RuntimeException exception) {
            log.warn("Deep Research schema repair failed. taskId: {}", resolveTaskId(task), exception);
            evidenceCard.getGaps().add("schema_repair_failed");
            evidenceCard.getFollowUps().add("retry_structured_fact_extraction");
            return "";
        }
    }
    protected StructuredEvidenceBundle parseStructuredEvidence(String answerSummary) {
        StructuredEvidenceBundle structuredEvidenceBundle = new StructuredEvidenceBundle();
        if (answerSummary == null || answerSummary.isBlank()) {
            return structuredEvidenceBundle;
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(answerSummary);
            for (JsonNode anchorNode : rootNode.withArray("evidenceAnchors")) {
                EvidenceAnchor evidenceAnchor = parseEvidenceAnchor(anchorNode);
                if (evidenceAnchor != null) {
                    structuredEvidenceBundle.getEvidenceAnchors().add(evidenceAnchor);
                }
            }
            for (JsonNode findingNode : rootNode.withArray("factFindings")) {
                FactFinding factFinding = parseFactFinding(findingNode, structuredEvidenceBundle);
                if (factFinding != null) {
                    structuredEvidenceBundle.getFactFindings().add(factFinding);
                }
            }
            return structuredEvidenceBundle;
        }
        catch (Exception exception) {
            return structuredEvidenceBundle;
        }
    }
    protected EvidenceAnchor parseEvidenceAnchor(JsonNode anchorNode) {
        EvidenceAnchorSourceType sourceType = parseSourceType(anchorNode.path("sourceType").asText(""));
        String anchorId = anchorNode.path("anchorId").asText("");
        String sourceId = anchorNode.path("sourceId").asText("");
        String quoteText = anchorNode.path("quoteText").asText("");
        if (anchorId.isBlank() || sourceType == null || sourceId.isBlank() || quoteText.isBlank()) {
            return null;
        }
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(sourceType);
        evidenceAnchor.setSourceId(sourceId);
        evidenceAnchor.setPath(anchorNode.path("path").asText(null));
        evidenceAnchor.setLineStart(nullableInt(anchorNode.path("lineStart")));
        evidenceAnchor.setLineEnd(nullableInt(anchorNode.path("lineEnd")));
        evidenceAnchor.setChunkId(anchorNode.path("chunkId").asText(null));
        evidenceAnchor.setQuoteText(quoteText);
        evidenceAnchor.setRetrievalScore(anchorNode.path("retrievalScore").asDouble(0.8D));
        return evidenceAnchor;
    }
    protected FactFinding parseFactFinding(JsonNode findingNode, StructuredEvidenceBundle structuredEvidenceBundle) {
        String subject = findingNode.path("subject").asText("");
        String predicate = findingNode.path("predicate").asText("");
        String qualifier = findingNode.path("qualifier").asText("");
        String claimText = findingNode.path("claimText").asText("");
        if (subject.isBlank() || predicate.isBlank() || qualifier.isBlank() || claimText.isBlank()) {
            return null;
        }
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(findingNode.path("findingId").asText(resolveDefaultFindingId(findingNode, structuredEvidenceBundle)));
        factFinding.setSubject(subject.trim());
        factFinding.setPredicate(predicate.trim());
        factFinding.setQualifier(qualifier.trim());
        factFinding.setFactKey(findingNode.path("factKey").asText(factFinding.expectedFactKey()));
        factFinding.setValueText(findingNode.path("valueText").asText(claimText));
        factFinding.setValueType(parseFactValueType(findingNode.path("valueType").asText("STRING")));
        factFinding.setUnit(findingNode.path("unit").asText(null));
        factFinding.setClaimText(claimText.trim());
        factFinding.setConfidence(findingNode.path("confidence").asDouble(0.8D));
        factFinding.setSupportLevel(parseSupportLevel(findingNode.path("supportLevel").asText("DIRECT")));
        List<String> anchorIds = parseAnchorIds(findingNode.path("anchorIds"));
        if (anchorIds.isEmpty() && structuredEvidenceBundle.getEvidenceAnchors().size() == 1) {
            anchorIds.add(structuredEvidenceBundle.getEvidenceAnchors().get(0).getAnchorId());
        }
        factFinding.setAnchorIds(anchorIds);
        if (!factFinding.canEnterLedger()) {
            return null;
        }
        return factFinding;
    }
    protected void appendAnchorOnlyEvidence(
            EvidenceCard evidenceCard,
            List<QueryArticleHit> hits,
            DeepResearchExecutionContext executionContext
    ) {
        int anchorCount = Math.min(hits.size(), 3);
        for (int index = 0; index < anchorCount; index++) {
            QueryArticleHit hit = hits.get(index);
            String anchorId = index == 0 ? evidenceCard.getEvidenceId() : executionContext.nextEvidenceId();
            String quoteText = extractSnippet(hit.getContent());
            EvidenceAnchor evidenceAnchor = buildEvidenceAnchor(anchorId, hit, quoteText);
            if (evidenceAnchor != null) {
                evidenceCard.getEvidenceAnchors().add(evidenceAnchor);
            }
        }
    }
    protected String buildAnswerSummary(
            String queryId,
            ResearchTask task,
            List<QueryArticleHit> hits,
            DeepResearchExecutionContext executionContext,
            EvidenceCard evidenceCard
    ) {
        if (!executionContext.tryAcquireLlmCall()) {
            return fallbackSummaryFromHits(hits);
        }
        if (answerGenerationService == null) {
            evidenceCard.getGaps().add("answer_generation_unavailable");
            evidenceCard.getFollowUps().add("fallback_to_retrieved_evidence");
            return fallbackSummaryFromHits(hits);
        }
        try {
            QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                    queryId,
                    ExecutionLlmSnapshotService.DEEP_RESEARCH_SCENE,
                    ExecutionLlmSnapshotService.ROLE_RESEARCHER,
                    resolveTaskQuestion(task),
                    hits
            );
            return answerPayload == null || answerPayload.getAnswerMarkdown() == null
                    ? ""
                    : answerPayload.getAnswerMarkdown();
        }
        catch (RuntimeException exception) {
            log.warn("Deep Research task answer generation failed. taskId: {}", resolveTaskId(task), exception);
            evidenceCard.getGaps().add("answer_generation_failed");
            evidenceCard.getFollowUps().add("fallback_to_retrieved_evidence");
            return fallbackSummaryFromHits(hits);
        }
    }
    /**
     * 从已有检索命中生成最小降级摘要。
     *
     * @param hits 检索命中
     * @return 降级摘要
     */
    protected String fallbackSummaryFromHits(List<QueryArticleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        QueryArticleHit firstHit = hits.get(0);
        return firstHit.getTitle() + "：" + extractEvidenceSnippet(firstHit);
    }
    protected void appendFindings(
            EvidenceCard evidenceCard,
            ResearchTask task,
            List<QueryArticleHit> hits,
            String answerSummary,
            DeepResearchExecutionContext executionContext
    ) {
        int findingCount = Math.min(hits.size(), 3);
        boolean conflictNarrative = answerSummary != null
                && (answerSummary.contains("冲突") || answerSummary.contains("不一致"));
        for (int index = 0; index < findingCount; index++) {
            QueryArticleHit hit = hits.get(index);
            String claimText = conflictNarrative
                    ? resolveConflictClaim(hit, answerSummary)
                    : resolveClaim(answerSummary, hit);
            if (!isHitRelevantToClaim(hit, claimText)) {
                continue;
            }
            String quoteText = extractEvidenceSnippet(hit);
            String anchorId = index == 0 ? evidenceCard.getEvidenceId() : executionContext.nextEvidenceId();
            EvidenceAnchor evidenceAnchor = buildEvidenceAnchor(anchorId, hit, quoteText);
            FactFinding factFinding = buildFactFinding(anchorId, evidenceCard, task, hit, claimText);
            if (evidenceAnchor != null && factFinding != null) {
                evidenceCard.getEvidenceAnchors().add(evidenceAnchor);
                evidenceCard.getFactFindings().add(factFinding);
            }
        }
    }
}
