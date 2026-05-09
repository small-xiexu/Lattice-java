package com.xbk.lattice.query.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.QueryAnswerAuditPersistenceService;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.AnswerShapeClassifier;
import com.xbk.lattice.query.service.ArticleChunkFtsSearchService;
import com.xbk.lattice.query.service.ChunkVectorSearchService;
import com.xbk.lattice.query.service.ContributionSearchService;
import com.xbk.lattice.query.service.FactCardFtsSearchService;
import com.xbk.lattice.query.service.FactCardVectorSearchService;
import com.xbk.lattice.query.service.FtsSearchService;
import com.xbk.lattice.query.service.GraphSearchService;
import com.xbk.lattice.query.service.QueryIntent;
import com.xbk.lattice.query.service.QueryIntentClassifier;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QueryEvidenceType;
import com.xbk.lattice.query.service.QueryEvidenceRelevanceSupport;
import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import com.xbk.lattice.query.service.QuerySearchProperties;
import com.xbk.lattice.query.service.QueryRewriteResult;
import com.xbk.lattice.query.service.QueryRewriteService;
import com.xbk.lattice.query.service.RefKeySearchService;
import com.xbk.lattice.query.service.RetrievalAuditService;
import com.xbk.lattice.query.service.RetrievalChannelRun;
import com.xbk.lattice.query.service.RetrievalDispatchPlan;
import com.xbk.lattice.query.service.RetrievalDispatchResult;
import com.xbk.lattice.query.service.RetrievalDispatcher;
import com.xbk.lattice.query.service.RetrievalExecutionContext;
import com.xbk.lattice.query.service.RetrievalQueryContext;
import com.xbk.lattice.query.service.RetrievalStrategy;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import com.xbk.lattice.query.service.ReviewerAgent;
import com.xbk.lattice.query.service.RrfFusionService;
import com.xbk.lattice.query.service.SourceChunkFtsSearchService;
import com.xbk.lattice.query.service.SourceSearchService;
import com.xbk.lattice.query.service.SupplierRetrievalChannel;
import com.xbk.lattice.query.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Query Graph 检索节点支持。
 *
 * 职责：执行检索分发、候选融合与精确查值上下文补强。
 *
 * @author xiexu
 */
@Slf4j
abstract class QueryGraphRetrievalSupport extends QueryGraphDefinitionBaseSupport {

    /**
     * 创建 Query Graph 检索节点支持。
     */
    protected QueryGraphRetrievalSupport(
            FtsSearchService ftsSearchService,
            ArticleChunkFtsSearchService articleChunkFtsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            SourceChunkFtsSearchService sourceChunkFtsSearchService,
            FactCardFtsSearchService factCardFtsSearchService,
            FactCardVectorSearchService factCardVectorSearchService,
            ContributionSearchService contributionSearchService,
            GraphSearchService graphSearchService,
            VectorSearchService vectorSearchService,
            ChunkVectorSearchService chunkVectorSearchService,
            RrfFusionService rrfFusionService,
            QueryRetrievalSettingsService queryRetrievalSettingsService,
            QuerySearchProperties querySearchProperties,
            QueryRewriteService queryRewriteService,
            QueryIntentClassifier queryIntentClassifier,
            AnswerShapeClassifier answerShapeClassifier,
            RetrievalStrategyResolver retrievalStrategyResolver,
            RetrievalAuditService retrievalAuditService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            QueryWorkingSetStore queryWorkingSetStore,
            CitationCheckService citationCheckService,
            QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService,
            QueryGraphStateMapper queryGraphStateMapper,
            QueryGraphConditions queryGraphConditions,
            QueryAnswerProjectionBuilder queryAnswerProjectionBuilder
    ) {
        super(
                ftsSearchService,
                articleChunkFtsSearchService,
                refKeySearchService,
                sourceSearchService,
                sourceChunkFtsSearchService,
                factCardFtsSearchService,
                factCardVectorSearchService,
                contributionSearchService,
                graphSearchService,
                vectorSearchService,
                chunkVectorSearchService,
                rrfFusionService,
                queryRetrievalSettingsService,
                querySearchProperties,
                queryRewriteService,
                queryIntentClassifier,
                answerShapeClassifier,
                retrievalStrategyResolver,
                retrievalAuditService,
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                queryWorkingSetStore,
                citationCheckService,
                queryAnswerAuditPersistenceService,
                queryGraphStateMapper,
                queryGraphConditions,
                queryAnswerProjectionBuilder
        );
    }

    protected Map<String, Object> dispatchRetrieval(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        state.setRetrievalMode(retrievalStrategy.isParallelEnabled() ? "parallel" : "serial");
        state.setRetrievalStartedAtEpochMs(System.currentTimeMillis());
        log.info(
                "[VECTOR][RETRIEVE][START] queryId={}, mode={}, elapsedMs=0, success=true",
                state.getQueryId(),
                state.getRetrievalMode()
        );
        Map<String, Object> delta = queryGraphStateMapper.toDeltaMap(state);
        delta.putAll(saveAllRetrievalHits(state));
        return delta;
    }
    protected Map<String, Object> fuseCandidates(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        Map<String, List<QueryArticleHit>> channelHits = loadChannelHits(state);
        Map<String, RetrievalChannelRun> channelRuns =
                queryWorkingSetStore.loadRetrievalChannelRuns(state.getRetrievalChannelRunsRef());
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(channelHits, retrievalStrategy, TOP_K);
        fusedHits = enrichExactLookupSupportHits(state.getQuestion(), fusedHits, channelHits);
        fusedHits = filterFusedHits(state.getQuestion(), fusedHits);
        state.setHasFusedHits(!fusedHits.isEmpty());
        state.setFusedHitsRef(queryWorkingSetStore.saveFusedHits(state.getQueryId(), fusedHits));
        if (retrievalAuditService != null) {
            state.setRetrievalAuditRef(retrievalAuditService.persist(
                    state.getQueryId(),
                    state.getQuestion(),
                    state.getNormalizedQuestion(),
                    retrievalStrategy,
                    state.getRetrievalMode(),
                    isRewriteApplied(state),
                    state.getRewriteAuditRef(),
                    state.getRetrievalStrategyRef(),
                    channelHits,
                    fusedHits,
                    channelRuns
            ));
        }
        long startedAt = state.getRetrievalStartedAtEpochMs();
        long elapsedMs = startedAt <= 0L ? 0L : System.currentTimeMillis() - startedAt;
        log.info(
                "[VECTOR][RETRIEVE][END] queryId={}, mode={}, elapsedMs={}, success=true",
                state.getQueryId(),
                state.getRetrievalMode(),
                elapsedMs
        );
        return queryGraphStateMapper.toDeltaMap(state);
    }
    /**
     * 对融合后的 TOP-K 再做问题相关性过滤，避免低相关文章进入生成上下文。
     *
     * @param question 用户问题
     * @param fusedHits 融合命中
     * @return 过滤后的命中；若过滤为空则保留原始命中
     */
    protected List<QueryArticleHit> filterFusedHits(String question, List<QueryArticleHit> fusedHits) {
        List<QueryArticleHit> relevantHits = QueryEvidenceRelevanceSupport.filterRelevantHits(question, fusedHits);
        if (relevantHits.isEmpty()) {
            if (hasStrictExactToken(question)) {
                return relevantHits;
            }
            return fusedHits;
        }
        return relevantHits;
    }
    /**
     * 精确查值题额外补入更直接的 source/refkey 证据，避免 fused 结果只剩概述性 article。
     *
     * @param question 用户问题
     * @param fusedHits 当前融合命中
     * @param channelHits 各通道命中
     * @return 补齐后的融合命中
     */
    protected List<QueryArticleHit> enrichExactLookupSupportHits(
            String question,
            List<QueryArticleHit> fusedHits,
            Map<String, List<QueryArticleHit>> channelHits
    ) {
        if (!looksLikeExactLookupQuestion(question) || channelHits == null || channelHits.isEmpty()) {
            return fusedHits;
        }
        List<QueryArticleHit> supportCandidates = collectExactLookupSupportCandidates(question, channelHits);
        if (supportCandidates.isEmpty()) {
            return fusedHits;
        }
        List<QueryArticleHit> enrichedHits = fusedHits == null
                ? new ArrayList<QueryArticleHit>()
                : new ArrayList<QueryArticleHit>(fusedHits);
        for (QueryArticleHit supportCandidate : supportCandidates) {
            if (containsEquivalentHit(enrichedHits, supportCandidate)) {
                continue;
            }
            enrichedHits.add(supportCandidate);
            if (enrichedHits.size() >= EXACT_LOOKUP_CONTEXT_LIMIT) {
                break;
            }
        }
        return enrichedHits;
    }
    /**
     * 收集精确查值题更值得补进 fused hits 的 support 证据。
     *
     * @param question 用户问题
     * @param channelHits 各通道命中
     * @return 候选证据
     */
    protected List<QueryArticleHit> collectExactLookupSupportCandidates(
            String question,
            Map<String, List<QueryArticleHit>> channelHits
    ) {
        List<QueryArticleHit> supportHits = new ArrayList<QueryArticleHit>();
        addExactLookupSupportHits(
                supportHits,
                question,
                channelHits.get(CHANNEL_SOURCE_CHUNK_FTS)
        );
        addExactLookupSupportHits(
                supportHits,
                question,
                channelHits.get(CHANNEL_SOURCE)
        );
        addExactLookupSupportHits(
                supportHits,
                question,
                channelHits.get(CHANNEL_REFKEY)
        );
        supportHits.sort((leftHit, rightHit) -> Integer.compare(
                scoreExactLookupSupportHit(question, rightHit),
                scoreExactLookupSupportHit(question, leftHit)
        ));
        if (supportHits.size() <= EXACT_LOOKUP_SUPPORT_LIMIT) {
            return supportHits;
        }
        return new ArrayList<QueryArticleHit>(supportHits.subList(0, EXACT_LOOKUP_SUPPORT_LIMIT));
    }
    /**
     * 从单个通道里挑出适合精确查值题的补充证据。
     *
     * @param supportHits 目标列表
     * @param question 用户问题
     * @param channelHits 通道命中
     */
    protected void addExactLookupSupportHits(
            List<QueryArticleHit> supportHits,
            String question,
            List<QueryArticleHit> channelHits
    ) {
        if (channelHits == null || channelHits.isEmpty()) {
            return;
        }
        for (QueryArticleHit channelHit : channelHits) {
            if (channelHit == null || !QueryEvidenceRelevanceSupport.isRelevant(question, channelHit)) {
                continue;
            }
            int supportScore = scoreExactLookupSupportHit(question, channelHit);
            if (supportScore <= 0) {
                continue;
            }
            supportHits.add(channelHit);
            if (supportHits.size() >= EXACT_LOOKUP_CONTEXT_LIMIT) {
                return;
            }
        }
    }
    /**
     * 为精确查值题评估单条 support 证据的价值。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 支持分值
     */
    protected int scoreExactLookupSupportHit(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return Integer.MIN_VALUE;
        }
        String normalizedQuestion = lowerCase(question);
        String haystack = lowerCase(queryArticleHit.getTitle())
                + " "
                + lowerCase(queryArticleHit.getContent())
                + " "
                + lowerCase(queryArticleHit.getMetadataJson());
        int score = QueryEvidenceRelevanceSupport.score(question, queryArticleHit);
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            score += 16;
        }
        if (normalizedQuestion.contains("命中数") && haystack.matches("(?s).*\\d.*")) {
            score += 18;
        }
        if ((normalizedQuestion.contains("路径") || normalizedQuestion.contains("接口")) && haystack.contains("/")) {
            score += 18;
        }
        if ((normalizedQuestion.contains("结论") || normalizedQuestion.contains("状态"))
                && (haystack.contains("修正为")
                || haystack.contains("确认")
                || haystack.contains("生效")
                || haystack.contains("启用")
                || haystack.contains("禁用"))) {
            score += 20;
        }
        if ((normalizedQuestion.contains("差异") || normalizedQuestion.contains("不同") || normalizedQuestion.contains("是否一致"))
                && (haystack.contains("不同") || haystack.contains("不一致") || haystack.contains("差异"))) {
            score += 18;
        }
        if ((normalizedQuestion.contains("批") || normalizedQuestion.contains("场景"))
                && haystack.contains("第")
                && haystack.contains("批")) {
            score += 16;
        }
        return score;
    }
    /**
     * 判断融合命中中是否已有同一条证据。
     *
     * @param fusedHits 融合命中
     * @param candidate 候选命中
     * @return 已存在返回 true
     */
    protected boolean containsEquivalentHit(List<QueryArticleHit> fusedHits, QueryArticleHit candidate) {
        if (fusedHits == null || fusedHits.isEmpty() || candidate == null) {
            return false;
        }
        String candidateKey = hitIdentity(candidate);
        for (QueryArticleHit fusedHit : fusedHits) {
            if (candidateKey.equals(hitIdentity(fusedHit))) {
                return true;
            }
        }
        return false;
    }
    /**
     * 生成查询命中的稳定身份键。
     *
     * @param queryArticleHit 查询命中
     * @return 身份键
     */
    protected String hitIdentity(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        String sourcePathText = queryArticleHit.getSourcePaths() == null
                ? ""
                : String.join("|", queryArticleHit.getSourcePaths());
        return queryArticleHit.getEvidenceType()
                + "|"
                + nullToEmpty(queryArticleHit.getArticleKey())
                + "|"
                + nullToEmpty(queryArticleHit.getConceptId())
                + "|"
                + sourcePathText;
    }
}
