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
public class DeepResearchResearcherService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern NUMERIC_LITERAL_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b");

    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("\\b([a-z][a-z0-9]*(?:_[a-z0-9]+){1,})\\b");

    private static final Pattern JAVA_SYMBOL_PATTERN = Pattern.compile("\\b([A-Z][A-Za-z0-9]{2,})\\b");

    private static final Pattern FRONT_MATTER_SUMMARY_PATTERN = Pattern.compile("(?ms)^---\\s*\\R.*?^summary:\\s*\"([^\"]+)\".*?^---");

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
            evidenceCard.getGaps().add("insufficient_grounding");
            evidenceCard.getFollowUps().add("retry_structured_fact_extraction");
            appendAnchorOnlyEvidence(evidenceCard, hits, executionContext);
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

    /**
     * 返回任务标识，缺失时给出稳定占位值。
     *
     * @param task 研究任务
     * @return 任务标识
     */
    private String resolveTaskId(ResearchTask task) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            return "deep_research_task";
        }
        return task.getTaskId();
    }

    /**
     * 返回任务问题，缺失时给出空字符串。
     *
     * @param task 研究任务
     * @return 任务问题
     */
    private String resolveTaskQuestion(ResearchTask task) {
        if (task == null || task.getQuestion() == null) {
            return "";
        }
        return task.getQuestion();
    }

    /**
     * 安全执行任务级检索，避免单通道异常拖垮整轮 Deep Research。
     *
     * @param task 研究任务
     * @param evidenceCard 证据卡
     * @return 检索命中
     */
    private List<QueryArticleHit> searchSafely(ResearchTask task, EvidenceCard evidenceCard) {
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
    private List<QueryArticleHit> filterRelevantHits(ResearchTask task, List<QueryArticleHit> hits) {
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
    private List<QueryArticleHit> preferStructuredEntityHits(String question, List<QueryArticleHit> relevantHits) {
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
    private List<String> extractStructuredEntityTokens(String question) {
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
    private boolean isStructuredEntityToken(String token) {
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
    private boolean hasStructuredEntityMatch(QueryArticleHit hit, List<String> structuredEntityTokens) {
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
    private boolean containsStructuredToken(String value, String token) {
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
    private boolean isAsciiToken(String token) {
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

    private void appendSynthesisPlaceholder(EvidenceCard evidenceCard, List<EvidenceCard> preferredCards) {
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

    private void appendTaskHits(EvidenceCard evidenceCard, List<QueryArticleHit> hits) {
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

    private void appendStructuredEvidenceFromJson(
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
        evidenceCard.getGaps().add("structured_fact_schema_invalid");
        evidenceCard.getFollowUps().add("retry_structured_fact_extraction");
        appendAnchorOnlyEvidence(evidenceCard, hits, executionContext);
    }

    private String buildRepairSummary(
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

    private StructuredEvidenceBundle parseStructuredEvidence(String answerSummary) {
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

    private EvidenceAnchor parseEvidenceAnchor(JsonNode anchorNode) {
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

    private FactFinding parseFactFinding(JsonNode findingNode, StructuredEvidenceBundle structuredEvidenceBundle) {
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

    private void appendAnchorOnlyEvidence(
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

    private String buildAnswerSummary(
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
    private String fallbackSummaryFromHits(List<QueryArticleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        QueryArticleHit firstHit = hits.get(0);
        return firstHit.getTitle() + "：" + extractEvidenceSnippet(firstHit);
    }

    private void appendFindings(
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

    private EvidenceAnchor buildEvidenceAnchor(String anchorId, QueryArticleHit hit, String quoteText) {
        EvidenceAnchorSourceType sourceType = mapSourceType(hit);
        String sourceId = resolveSourceId(hit);
        if (sourceType == null || sourceId.isBlank() || quoteText == null || quoteText.isBlank()) {
            return null;
        }
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(sourceType);
        evidenceAnchor.setSourceId(sourceId);
        evidenceAnchor.setQuoteText(quoteText);
        evidenceAnchor.setRetrievalScore(normalizeConfidence(hit.getScore()));
        if (sourceType == EvidenceAnchorSourceType.SOURCE_FILE) {
            evidenceAnchor.setPath(sourceId);
        }
        return evidenceAnchor;
    }

    private FactFinding buildFactFinding(
            String anchorId,
            EvidenceCard evidenceCard,
            ResearchTask task,
            QueryArticleHit hit,
            String claimText
    ) {
        if (claimText == null || claimText.isBlank()) {
            return null;
        }
        String subject = normalizeFactToken(task == null ? evidenceCard.getTaskId() : task.getTaskId());
        if (subject.isBlank()) {
            subject = "deep_research_task";
        }
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(anchorId + "-finding");
        factFinding.setSubject(subject);
        factFinding.setPredicate("claim");
        factFinding.setQualifier("deep_research");
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText(claimText.trim());
        factFinding.setValueType(FactValueType.STRING);
        factFinding.setClaimText(claimText.trim());
        factFinding.setConfidence(normalizeConfidence(hit.getScore()));
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of(anchorId));
        return factFinding;
    }

    private String resolveClaim(String answerSummary, QueryArticleHit hit) {
        String fallbackClaim = stripExistingCitationLiteral(hit.getTitle() + "：" + extractEvidenceSnippet(hit));
        if (answerSummary != null && !answerSummary.isBlank()) {
            String[] lines = answerSummary.split("\\R");
            for (String line : lines) {
                String normalizedLine = line == null ? "" : line.trim();
                if (!normalizedLine.isBlank() && !normalizedLine.startsWith("#")) {
                    String claimText = stripExistingCitationLiteral(normalizedLine);
                    if (claimText.contains("冲突") || claimText.contains("不一致")) {
                        String focusedEvidenceSnippet = stripExistingCitationLiteral(
                                extractEvidenceSnippet(hit)
                        );
                        if (!focusedEvidenceSnippet.isBlank()) {
                            return focusedEvidenceSnippet;
                        }
                    }
                    String focusedClaimSnippet = extractFocusedClaimSnippet(hit, claimText);
                    if (!focusedClaimSnippet.isBlank()) {
                        return focusedClaimSnippet;
                    }
                    String focusedEvidenceSnippet = extractEvidenceSnippet(hit);
                    if (looksLikeLowValueEvidenceSnippet(claimText) && !focusedEvidenceSnippet.isBlank()) {
                        return focusedEvidenceSnippet;
                    }
                    return claimText;
                }
            }
        }
        return fallbackClaim;
    }

    private String resolveConflictClaim(QueryArticleHit hit, String answerSummary) {
        String focusedClaimSnippet = extractFocusedClaimSnippet(hit, answerSummary);
        if (!focusedClaimSnippet.isBlank()) {
            return focusedClaimSnippet;
        }
        return stripExistingCitationLiteral(extractEvidenceSnippet(hit));
    }

    private String stripExistingCitationLiteral(String claimText) {
        if (claimText == null || claimText.isBlank()) {
            return "";
        }
        String normalizedClaimText = claimText
                .replaceAll("\\[\\[[^\\]]+]]", "")
                .replaceAll("\\[→\\s*[^\\]]+]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return normalizedClaimText;
    }

    private boolean isHitRelevantToClaim(QueryArticleHit hit, String claimText) {
        if (hit == null || claimText == null || claimText.isBlank()) {
            return false;
        }
        List<String> hardFactTokens = extractHardFactTokens(claimText);
        if (hardFactTokens.isEmpty()) {
            return true;
        }
        String evidenceText = buildEvidenceText(hit).toLowerCase(Locale.ROOT);
        for (String hardFactToken : hardFactTokens) {
            if (evidenceText.contains(hardFactToken)) {
                return true;
            }
        }
        return false;
    }


    private List<String> extractHardFactTokens(String claimText) {
        List<String> preferredTokens = new ArrayList<String>();
        List<String> numericTokens = new ArrayList<String>();
        appendMatches(preferredTokens, SNAKE_CASE_PATTERN.matcher(claimText));
        appendMatches(preferredTokens, JAVA_SYMBOL_PATTERN.matcher(claimText));
        appendMatches(numericTokens, NUMERIC_LITERAL_PATTERN.matcher(claimText));
        if (!preferredTokens.isEmpty()) {
            return preferredTokens;
        }
        return numericTokens;
    }

    private String extractFocusedClaimSnippet(QueryArticleHit hit, String claimText) {
        if (hit == null || claimText == null || claimText.isBlank()) {
            return "";
        }
        List<String> hardFactTokens = extractHardFactTokens(claimText);
        if (hardFactTokens.isEmpty()) {
            return "";
        }
        for (String hardFactToken : hardFactTokens) {
            String body = sanitizeEvidenceBody(hit.getContent());
            String snippetFromBody = extractSentenceContainingToken(body, hardFactToken);
            if (!snippetFromBody.isBlank()) {
                return snippetFromBody;
            }
            String summary = extractArticleSummary(hit.getContent());
            String snippetFromSummary = extractSentenceContainingToken(summary, hardFactToken);
            if (!snippetFromSummary.isBlank()) {
                return snippetFromSummary;
            }
        }
        return "";
    }

    private String buildEvidenceText(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        StringBuilder evidenceTextBuilder = new StringBuilder();
        if (hit.getTitle() != null) {
            evidenceTextBuilder.append(hit.getTitle()).append(' ');
        }
        String summary = extractArticleSummary(hit.getContent());
        if (summary != null && !summary.isBlank()) {
            evidenceTextBuilder.append(summary).append(' ');
        }
        String content = sanitizeEvidenceBody(hit.getContent());
        if (content != null && !content.isBlank()) {
            evidenceTextBuilder.append(content).append(' ');
        }
        if (hit.getConceptId() != null) {
            evidenceTextBuilder.append(hit.getConceptId()).append(' ');
        }
        return evidenceTextBuilder.toString();
    }

    private void appendMatches(List<String> hardFactTokens, Matcher matcher) {
        while (matcher.find()) {
            String literal = matcher.group(1);
            if (literal == null || literal.isBlank()) {
                continue;
            }
            String normalizedLiteral = literal.trim().toLowerCase(Locale.ROOT);
            if (!hardFactTokens.contains(normalizedLiteral)) {
                hardFactTokens.add(normalizedLiteral);
            }
        }
    }

    private String resolveSourceId(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        if (hit.getEvidenceType() == QueryEvidenceType.ARTICLE
                && hit.getConceptId() != null
                && !hit.getConceptId().isBlank()) {
            return hit.getConceptId();
        }
        if (hit.getEvidenceType() == QueryEvidenceType.SOURCE && hit.getSourcePaths() != null && !hit.getSourcePaths().isEmpty()) {
            return hit.getSourcePaths().get(0);
        }
        if (hit.getArticleKey() != null && !hit.getArticleKey().isBlank()) {
            return hit.getArticleKey();
        }
        if (hit.getSourcePaths() != null && !hit.getSourcePaths().isEmpty()) {
            return hit.getSourcePaths().get(0);
        }
        return hit.getConceptId() == null ? "" : hit.getConceptId();
    }

    private String firstSourcePath(QueryArticleHit hit) {
        if (hit == null || hit.getSourcePaths() == null || hit.getSourcePaths().isEmpty()) {
            return null;
        }
        return hit.getSourcePaths().get(0);
    }

    private EvidenceAnchorSourceType mapSourceType(QueryArticleHit hit) {
        if (hit == null || hit.getEvidenceType() == null) {
            return null;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            return EvidenceAnchorSourceType.ARTICLE;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            return EvidenceAnchorSourceType.SOURCE_FILE;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.GRAPH) {
            return EvidenceAnchorSourceType.GRAPH_FACT;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION) {
            return EvidenceAnchorSourceType.CONTRIBUTION;
        }
        return null;
    }

    private EvidenceAnchorSourceType parseSourceType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EvidenceAnchorSourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private FactValueType parseFactValueType(String value) {
        if (value == null || value.isBlank()) {
            return FactValueType.STRING;
        }
        try {
            return FactValueType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            return FactValueType.STRING;
        }
    }

    private FindingSupportLevel parseSupportLevel(String value) {
        if (value == null || value.isBlank()) {
            return FindingSupportLevel.DIRECT;
        }
        try {
            return FindingSupportLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            return FindingSupportLevel.DIRECT;
        }
    }

    private List<String> parseAnchorIds(JsonNode anchorIdsNode) {
        List<String> anchorIds = new ArrayList<String>();
        if (anchorIdsNode == null || !anchorIdsNode.isArray()) {
            return anchorIds;
        }
        for (JsonNode anchorIdNode : anchorIdsNode) {
            String anchorId = anchorIdNode.asText("");
            if (!anchorId.isBlank()) {
                anchorIds.add(anchorId.trim());
            }
        }
        return anchorIds;
    }

    private String resolveDefaultFindingId(JsonNode findingNode, StructuredEvidenceBundle structuredEvidenceBundle) {
        String firstAnchorId = "";
        if (findingNode.path("anchorIds").isArray() && !findingNode.path("anchorIds").isEmpty()) {
            firstAnchorId = findingNode.path("anchorIds").get(0).asText("");
        }
        if (firstAnchorId.isBlank() && structuredEvidenceBundle.getEvidenceAnchors().size() == 1) {
            firstAnchorId = structuredEvidenceBundle.getEvidenceAnchors().get(0).getAnchorId();
        }
        return firstAnchorId.isBlank() ? "finding-unresolved" : firstAnchorId + "-finding";
    }

    private Integer nullableInt(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isMissingNode() || jsonNode.isNull()) {
            return null;
        }
        return Integer.valueOf(jsonNode.asInt());
    }

    private boolean looksLikeStructuredEvidenceJson(String answerSummary) {
        if (answerSummary == null) {
            return false;
        }
        String normalized = answerSummary.trim();
        return normalized.startsWith("{")
                && (normalized.contains("\"factFindings\"") || normalized.contains("\"evidenceAnchors\""));
    }

    private String normalizeFactToken(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized;
    }

    private String extractSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = sanitizeEvidenceBody(content).trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180);
    }

    private String extractSentenceContainingToken(String content, String token) {
        if (content == null || content.isBlank() || token == null || token.isBlank()) {
            return "";
        }
        String normalizedContent = content.replaceAll("\\R+", " ").trim();
        String[] sentences = normalizedContent.split("(?<=[。；!?！？])");
        String loweredToken = token.toLowerCase(Locale.ROOT);
        for (String sentence : sentences) {
            String normalizedSentence = sentence == null ? "" : sentence.trim();
            if (!normalizedSentence.isBlank()
                    && normalizedSentence.toLowerCase(Locale.ROOT).contains(loweredToken)) {
                return stripExistingCitationLiteral(normalizedSentence);
            }
        }
        return "";
    }

    private String stripFrontMatter(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalizedContent = content.trim();
        return normalizedContent.replaceFirst("(?s)^---\\s*\\R.*?\\R---\\s*\\R?", "").trim();
    }

    private String extractArticleSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        Matcher matcher = FRONT_MATTER_SUMMARY_PATTERN.matcher(content.trim());
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    private String extractEvidenceSnippet(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        String focusedSnippet = extractFocusedQuestionSnippet(hit);
        if (!focusedSnippet.isBlank()) {
            return focusedSnippet;
        }
        String summary = extractArticleSummary(hit.getContent());
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        String bodySnippet = extractSnippet(hit.getContent());
        if (!bodySnippet.isBlank()) {
            return bodySnippet;
        }
        return "";
    }

    private String extractFocusedQuestionSnippet(QueryArticleHit hit) {
        List<String> focusTokens = extractHardFactTokens(
                (hit.getTitle() == null ? "" : hit.getTitle()) + " " + buildEvidenceText(hit)
        );
        if (focusTokens.isEmpty()) {
            return "";
        }
        String body = sanitizeEvidenceBody(hit.getContent());
        for (String focusToken : focusTokens) {
            String focusedBodySnippet = extractSentenceContainingToken(body, focusToken);
            if (!focusedBodySnippet.isBlank() && !looksLikeLowValueEvidenceSnippet(focusedBodySnippet)) {
                return focusedBodySnippet;
            }
        }
        String summary = extractArticleSummary(hit.getContent());
        for (String focusToken : focusTokens) {
            String focusedSummarySnippet = extractSentenceContainingToken(summary, focusToken);
            if (!focusedSummarySnippet.isBlank() && !looksLikeLowValueEvidenceSnippet(focusedSummarySnippet)) {
                return focusedSummarySnippet;
            }
        }
        return "";
    }

    private boolean looksLikeLowValueEvidenceSnippet(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return true;
        }
        String normalizedSnippet = snippet.trim();
        String lowerCaseSnippet = normalizedSnippet.toLowerCase(Locale.ROOT);
        return lowerCaseSnippet.contains("目录")
                || lowerCaseSnippet.contains("table of contents")
                || normalizedSnippet.matches("(?s).*\\[[^\\]]+]\\(#[^)]+\\).*");
    }

    private String sanitizeEvidenceBody(String content) {
        String normalizedContent = stripFrontMatter(content);
        if (normalizedContent.isBlank()) {
            return normalizedContent;
        }
        String strippedBody = normalizedContent
                .replaceAll("\\[\\[[^\\]]+]]", "")
                .replaceAll("\\[→\\s*[^\\]]+]", "")
                .replaceAll("\\[[^\\]]*编译[^\\]]*]", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replace('|', ' ');
        StringBuilder bodyBuilder = new StringBuilder();
        for (String rawLine : strippedBody.split("\\R")) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (normalizedLine.isEmpty() || looksLikeTableOfContentsLine(normalizedLine)) {
                continue;
            }
            if (bodyBuilder.length() > 0) {
                bodyBuilder.append(' ');
            }
            bodyBuilder.append(normalizedLine);
        }
        return bodyBuilder.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private boolean looksLikeTableOfContentsLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String compactLine = normalizedLine.trim();
        if ("目录".equals(compactLine) || compactLine.matches("^[-+*]?\\s*目录\\s*$")) {
            return true;
        }
        if (compactLine.matches("^[-+*]\\s*\\[[^\\]]+]\\(#[^)]+\\).*$")) {
            return true;
        }
        return compactLine.matches("^\\d+(?:\\.\\d+)*\\s+.+$")
                && compactLine.contains("#")
                && compactLine.contains("-");
    }

    private double normalizeConfidence(double score) {
        if (score <= 0.0D) {
            return 0.2D;
        }
        // 真实检索返回的是融合排序分，不是校准后的概率值；对 Deep Research top hits
        // 需要映射到可投影的 confidence 区间，否则 ARTICLE/SOURCE_FILE 永远过不了 v2.6 证据门槛。
        if (score < 0.55D) {
            return Math.min(0.95D, 0.55D + score);
        }
        if (score >= 1.0D) {
            return 1.0D;
        }
        return score;
    }

    private static class StructuredEvidenceBundle {

        private final List<EvidenceAnchor> evidenceAnchors = new ArrayList<EvidenceAnchor>();

        private final List<FactFinding> factFindings = new ArrayList<FactFinding>();

        private List<EvidenceAnchor> getEvidenceAnchors() {
            return evidenceAnchors;
        }

        private List<FactFinding> getFactFindings() {
            return factFindings;
        }

        private boolean isValid() {
            return !evidenceAnchors.isEmpty() && !factFindings.isEmpty();
        }
    }
}
