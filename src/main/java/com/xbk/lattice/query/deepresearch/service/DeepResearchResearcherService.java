package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceFinding;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep Research 研究员服务
 *
 * 职责：执行单个研究任务，产出 EvidenceCard
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DeepResearchResearcherService {

    private final KnowledgeSearchService knowledgeSearchService;

    private final AnswerGenerationService answerGenerationService;

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
        evidenceCard.setTaskId(task.getTaskId());
        evidenceCard.setScope(task.getQuestion());
        if (executionContext.isTimedOut()) {
            evidenceCard.getGaps().add("overall_timeout");
            return evidenceCard;
        }
        List<QueryArticleHit> hits = knowledgeSearchService.search(task.getQuestion(), 5);
        for (QueryArticleHit hit : hits) {
            String articleKey = hit.getArticleKey() == null || hit.getArticleKey().isBlank()
                    ? hit.getConceptId()
                    : hit.getArticleKey();
            evidenceCard.getSelectedArticleKeys().add(articleKey);
        }
        String answerSummary = buildAnswerSummary(queryId, task, hits, executionContext);
        if (answerSummary.isBlank()) {
            evidenceCard.getGaps().add("insufficient_grounding");
        }
        appendFindings(evidenceCard, hits, answerSummary);
        if (previousLayerSummary != null && previousLayerSummary.getSummaryMarkdown() != null) {
            evidenceCard.getRelatedLeads().add("previous-layer:" + previousLayerSummary.getLayerIndex());
        }
        for (EvidenceCard preferredCard : preferredCards) {
            evidenceCard.getRelatedLeads().add(preferredCard.getEvidenceId());
        }
        return evidenceCard;
    }

    private String buildAnswerSummary(
            String queryId,
            ResearchTask task,
            List<QueryArticleHit> hits,
            DeepResearchExecutionContext executionContext
    ) {
        if (!executionContext.tryAcquireLlmCall()) {
            if (hits.isEmpty()) {
                return "";
            }
            return hits.get(0).getTitle() + "：" + extractSnippet(hits.get(0).getContent());
        }
        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                queryId,
                ExecutionLlmSnapshotService.QUERY_SCENE,
                "deep_researcher",
                task.getQuestion(),
                hits
        );
        return answerPayload == null || answerPayload.getAnswerMarkdown() == null
                ? ""
                : answerPayload.getAnswerMarkdown();
    }

    private void appendFindings(EvidenceCard evidenceCard, List<QueryArticleHit> hits, String answerSummary) {
        int findingCount = Math.min(hits.size(), 3);
        for (int index = 0; index < findingCount; index++) {
            QueryArticleHit hit = hits.get(index);
            EvidenceFinding finding = new EvidenceFinding();
            finding.setClaim(resolveClaim(answerSummary, hit));
            finding.setQuote(extractSnippet(hit.getContent()));
            finding.setSourceType(hit.getEvidenceType().name());
            finding.setSourceId(resolveSourceId(hit));
            finding.setChunkId(null);
            finding.setConfidence(normalizeConfidence(hit.getScore()));
            evidenceCard.getFindings().add(finding);
        }
    }

    private String resolveClaim(String answerSummary, QueryArticleHit hit) {
        if (answerSummary != null && !answerSummary.isBlank()) {
            String[] lines = answerSummary.split("\\R");
            for (String line : lines) {
                String normalizedLine = line == null ? "" : line.trim();
                if (!normalizedLine.isBlank() && !normalizedLine.startsWith("#")) {
                    return normalizedLine;
                }
            }
        }
        return hit.getTitle() + "：" + extractSnippet(hit.getContent());
    }

    private String resolveSourceId(QueryArticleHit hit) {
        if (hit.getArticleKey() != null && !hit.getArticleKey().isBlank()) {
            return hit.getArticleKey();
        }
        if (!hit.getSourcePaths().isEmpty()) {
            return hit.getSourcePaths().get(0);
        }
        return hit.getConceptId();
    }

    private String extractSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180);
    }

    private double normalizeConfidence(double score) {
        if (score <= 0.0D) {
            return 0.2D;
        }
        if (score >= 1.0D) {
            return Math.min(score / 10.0D, 1.0D);
        }
        return score;
    }
}
