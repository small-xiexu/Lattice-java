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
 * Deep Research 命中选择支持。
 *
 * 职责：执行任务检索、相关性过滤、结构化实体偏好和任务命中记录。
 *
 * @author xiexu
 */
@Slf4j
abstract class DeepResearchHitSelectionSupport extends DeepResearchEvidenceAssemblySupport {

    /**
     * 安全执行任务级检索，避免单通道异常拖垮整轮 Deep Research。
     *
     * @param task 研究任务
     * @param evidenceCard 证据卡
     * @return 检索命中
     */
    protected List<QueryArticleHit> searchSafely(ResearchTask task, EvidenceCard evidenceCard) {
        if (knowledgeSearchService == null) {
            evidenceCard.getGaps().add("retrieval_unavailable");
            evidenceCard.getFollowUps().add("retry_with_available_retrieval_channels");
            return List.of();
        }
        try {
            return knowledgeSearchService.search(resolveTaskQuestion(task), 5);
        }
        catch (RuntimeException exception) {
            log.warn("Deep Research task retrieval failed. taskId: {}", resolveTaskId(task), exception);
            evidenceCard.getGaps().add("retrieval_failed");
            evidenceCard.getFollowUps().add("retry_with_available_retrieval_channels");
            return List.of();
        }
    }
    /**
     * 按任务问题过滤低相关命中，避免无关资料污染单任务研究结论。
     *
     * @param task 研究任务
     * @param hits 原始检索命中
     * @return 过滤后的命中
     */
    protected List<QueryArticleHit> filterRelevantHits(ResearchTask task, List<QueryArticleHit> hits) {
        if (task == null || task.getQuestion() == null || task.getQuestion().isBlank()) {
            return hits == null ? List.of() : hits;
        }
        List<QueryArticleHit> relevantHits = new ArrayList<QueryArticleHit>(
                QueryEvidenceRelevanceSupport.filterRelevantHits(task.getQuestion(), hits)
        );
        relevantHits = preferStructuredEntityHits(task.getQuestion(), relevantHits);
        if (hits != null) {
            for (QueryArticleHit hit : hits) {
                if (hit == null || hit.getEvidenceType() != QueryEvidenceType.GRAPH || relevantHits.contains(hit)) {
                    continue;
                }
                relevantHits.add(hit);
            }
        }
        if (!relevantHits.isEmpty()) {
            return relevantHits;
        }
        return List.of();
    }
    /**
     * 在任务级优先保留结构化字段命中的实体证据，避免正文顺带提及的笔记污染 task hits。
     *
     * @param question 任务问题
     * @param relevantHits 已通过通用相关性过滤的命中
     * @return 收敛后的命中列表
     */
    protected List<QueryArticleHit> preferStructuredEntityHits(String question, List<QueryArticleHit> relevantHits) {
        if (question == null || question.isBlank() || relevantHits == null || relevantHits.size() <= 1) {
            return relevantHits == null ? List.of() : relevantHits;
        }
        List<String> structuredEntityTokens = extractStructuredEntityTokens(question);
        if (structuredEntityTokens.isEmpty()) {
            return relevantHits;
        }
        List<QueryArticleHit> structuredHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit relevantHit : relevantHits) {
            if (hasStructuredEntityMatch(relevantHit, structuredEntityTokens)) {
                structuredHits.add(relevantHit);
            }
        }
        if (!structuredHits.isEmpty()) {
            return structuredHits;
        }
        return relevantHits;
    }
    /**
     * 提取更适合作为任务级结构化过滤依据的实体 token。
     *
     * @param question 任务问题
     * @return 实体 token 列表
     */
    protected List<String> extractStructuredEntityTokens(String question) {
        Set<String> structuredEntityTokens = new LinkedHashSet<String>();
        for (String highSignalToken : QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)) {
            if (isStructuredEntityToken(highSignalToken)) {
                structuredEntityTokens.add(normalizeFactToken(highSignalToken));
            }
        }
        return new ArrayList<String>(structuredEntityTokens);
    }
    /**
     * 判断 token 是否更适合做结构化字段匹配。
     *
     * @param token 候选 token
     * @return 适合返回 true
     */
    protected boolean isStructuredEntityToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return token.contains("_") || token.contains("-") || token.contains("/") || isAsciiToken(token);
    }
    /**
     * 判断命中是否在 articleKey/conceptId/title/path 这类结构化字段中匹配到实体 token。
     *
     * @param hit 检索命中
     * @param structuredEntityTokens 实体 token 列表
     * @return 匹配返回 true
     */
    protected boolean hasStructuredEntityMatch(QueryArticleHit hit, List<String> structuredEntityTokens) {
        if (hit == null || structuredEntityTokens == null || structuredEntityTokens.isEmpty()) {
            return false;
        }
        for (String structuredEntityToken : structuredEntityTokens) {
            if (containsStructuredToken(hit.getArticleKey(), structuredEntityToken)
                    || containsStructuredToken(hit.getConceptId(), structuredEntityToken)
                    || containsStructuredToken(hit.getTitle(), structuredEntityToken)) {
                return true;
            }
            if (hit.getSourcePaths() == null) {
                continue;
            }
            for (String sourcePath : hit.getSourcePaths()) {
                if (containsStructuredToken(sourcePath, structuredEntityToken)) {
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * 判断结构化字段是否包含目标 token。
     *
     * @param value 字段值
     * @param token 目标 token
     * @return 包含返回 true
     */
    protected boolean containsStructuredToken(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return false;
        }
        return normalizeFactToken(value).contains(normalizeFactToken(token));
    }
    /**
     * 判断 token 是否为 ASCII 实体标识。
     *
     * @param token 候选 token
     * @return ASCII token 返回 true
     */
    protected boolean isAsciiToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if ((value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')) {
                continue;
            }
            return false;
        }
        return true;
    }
    protected void appendSynthesisPlaceholder(EvidenceCard evidenceCard, List<EvidenceCard> preferredCards) {
        for (EvidenceCard preferredCard : preferredCards) {
            if (preferredCard == null || preferredCard.getSelectedArticleKeys() == null) {
                continue;
            }
            for (String articleKey : preferredCard.getSelectedArticleKeys()) {
                if (articleKey != null
                        && !articleKey.isBlank()
                        && !evidenceCard.getSelectedArticleKeys().contains(articleKey)) {
                    evidenceCard.getSelectedArticleKeys().add(articleKey);
                }
            }
        }
        evidenceCard.getFollowUps().add("synthesis_reused_upstream_findings");
    }
    protected void appendTaskHits(EvidenceCard evidenceCard, List<QueryArticleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        for (int index = 0; index < hits.size(); index++) {
            QueryArticleHit hit = hits.get(index);
            if (hit == null) {
                continue;
            }
            ResearchTaskHit taskHit = new ResearchTaskHit();
            taskHit.setHitOrdinal(index + 1);
            taskHit.setChannel("knowledge_search");
            taskHit.setEvidenceType(hit.getEvidenceType() == null ? null : hit.getEvidenceType().name());
            taskHit.setSourceId(hit.getSourceId() == null ? null : String.valueOf(hit.getSourceId()));
            taskHit.setArticleKey(hit.getArticleKey());
            taskHit.setConceptId(hit.getConceptId());
            taskHit.setTitle(hit.getTitle());
            taskHit.setPath(firstSourcePath(hit));
            taskHit.setOriginalScore(Double.valueOf(hit.getScore()));
            taskHit.setFusedScore(Double.valueOf(hit.getScore()));
            taskHit.setContentExcerpt(extractSnippet(hit.getContent()));
            evidenceCard.getTaskHits().add(taskHit);
        }
    }
}
