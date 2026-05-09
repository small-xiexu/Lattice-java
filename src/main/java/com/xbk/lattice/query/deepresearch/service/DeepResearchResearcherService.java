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
 * Deep Research 研究员服务
 *
 * 职责：执行单个研究任务，产出 EvidenceCard
 *
 * @author xiexu
 */
@Slf4j
@Service
public class DeepResearchResearcherService extends DeepResearchHitSelectionSupport {

    /**
     * 创建 Deep Research 研究员服务。
     *
     * @param knowledgeSearchService 知识检索服务
     * @param answerGenerationService 答案生成服务
     */
    public DeepResearchResearcherService(
            KnowledgeSearchService knowledgeSearchService,
            AnswerGenerationService answerGenerationService
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.answerGenerationService = answerGenerationService;
    }

    /**
     * 执行单个研究任务。
     *
     * @param queryId 查询标识
     * @param task 研究任务
     * @param layerIndex 层序号
     * @param previousLayerSummary 上一层摘要
     * @param preferredCards 上一层优选证据卡
     * @param executionContext 执行上下文
     * @return 证据卡
     */
    public EvidenceCard research(
            String queryId,
            ResearchTask task,
            int layerIndex,
            LayerSummary previousLayerSummary,
            List<EvidenceCard> preferredCards,
            DeepResearchExecutionContext executionContext
    ) {
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId(executionContext.nextEvidenceId());
        evidenceCard.setLayerIndex(layerIndex);
        evidenceCard.setTaskId(resolveTaskId(task));
        evidenceCard.setScope(resolveTaskQuestion(task));
        List<EvidenceCard> effectivePreferredCards = preferredCards == null ? List.of() : preferredCards;
        if (executionContext.isTimedOut()) {
            evidenceCard.getGaps().add("overall_timeout");
            return evidenceCard;
        }
        if (task != null
                && task.getTaskType() == com.xbk.lattice.query.deepresearch.domain.ResearchTaskType.SYNTHESIS
                && !effectivePreferredCards.isEmpty()) {
            appendSynthesisPlaceholder(evidenceCard, effectivePreferredCards);
            return evidenceCard;
        }
        List<QueryArticleHit> searchHits = searchSafely(task, evidenceCard);
        if (searchHits.isEmpty() && evidenceCard.getGaps().contains("retrieval_failed")) {
            return evidenceCard;
        }
        List<QueryArticleHit> hits = filterRelevantHits(task, searchHits);
        if (hits.isEmpty()) {
            evidenceCard.getGaps().add("no_relevant_hits");
            evidenceCard.getFollowUps().add("broaden_query_or_refine_task");
            return evidenceCard;
        }
        appendTaskHits(evidenceCard, hits);
        for (QueryArticleHit hit : hits) {
            String articleKey = hit.getArticleKey() == null || hit.getArticleKey().isBlank()
                    ? hit.getConceptId()
                    : hit.getArticleKey();
            evidenceCard.getSelectedArticleKeys().add(articleKey);
        }
        String answerSummary = buildAnswerSummary(queryId, task, hits, executionContext, evidenceCard);
        if (answerSummary.isBlank()) {
            appendRecoveredEvidenceFromHits(
                    evidenceCard,
                    task,
                    hits,
                    executionContext,
                    "insufficient_grounding"
            );
        }
        else if (looksLikeStructuredEvidenceJson(answerSummary)) {
            appendStructuredEvidenceFromJson(
                    evidenceCard,
                    queryId,
                    task,
                    hits,
                    answerSummary,
                    executionContext
            );
        }
        else {
            appendFindings(evidenceCard, task, hits, answerSummary, executionContext);
        }
        if (previousLayerSummary != null && previousLayerSummary.getSummaryMarkdown() != null) {
            evidenceCard.getRelatedLeads().add("previous-layer:" + previousLayerSummary.getLayerIndex());
        }
        for (EvidenceCard preferredCard : effectivePreferredCards) {
            evidenceCard.getRelatedLeads().add(preferredCard.getEvidenceId());
        }
        return evidenceCard;
    }

}
