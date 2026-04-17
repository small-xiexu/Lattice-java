package com.xbk.lattice.query.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.ContributionSearchService;
import com.xbk.lattice.query.service.FtsSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QueryEvidenceType;
import com.xbk.lattice.query.service.RefKeySearchService;
import com.xbk.lattice.query.service.ReviewIssue;
import com.xbk.lattice.query.service.ReviewResult;
import com.xbk.lattice.query.service.ReviewerAgent;
import com.xbk.lattice.query.service.RrfFusionService;
import com.xbk.lattice.query.service.SourceSearchService;
import com.xbk.lattice.query.service.VectorSearchService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 问答图定义工厂
 *
 * 职责：集中声明 Query Graph 的节点、顺序边与条件边
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class QueryGraphDefinitionFactory {

    private static final int TOP_K = 5;

    private final FtsSearchService ftsSearchService;

    private final RefKeySearchService refKeySearchService;

    private final SourceSearchService sourceSearchService;

    private final ContributionSearchService contributionSearchService;

    private final VectorSearchService vectorSearchService;

    private final RrfFusionService rrfFusionService;

    private final AnswerGenerationService answerGenerationService;

    private final QueryCacheStore queryCacheStore;

    private final ReviewerAgent reviewerAgent;

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final QueryGraphStateMapper queryGraphStateMapper;

    private final QueryGraphConditions queryGraphConditions;

    /**
     * 创建问答图定义工厂。
     *
     * @param ftsSearchService FTS 检索服务
     * @param refKeySearchService 引用词检索服务
     * @param sourceSearchService 源文件检索服务
     * @param contributionSearchService Contribution 检索服务
     * @param vectorSearchService 向量检索服务
     * @param rrfFusionService RRF 融合服务
     * @param answerGenerationService 答案生成服务
     * @param queryCacheStore 查询缓存存储
     * @param reviewerAgent 审查代理
     * @param queryWorkingSetStore 问答图工作集存储
     * @param queryGraphStateMapper 问答图状态映射器
     * @param queryGraphConditions 问答图条件路由
     */
    public QueryGraphDefinitionFactory(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            VectorSearchService vectorSearchService,
            RrfFusionService rrfFusionService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            QueryWorkingSetStore queryWorkingSetStore,
            QueryGraphStateMapper queryGraphStateMapper,
            QueryGraphConditions queryGraphConditions
    ) {
        this.ftsSearchService = ftsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.contributionSearchService = contributionSearchService;
        this.vectorSearchService = vectorSearchService;
        this.rrfFusionService = rrfFusionService;
        this.answerGenerationService = answerGenerationService;
        this.queryCacheStore = queryCacheStore;
        this.reviewerAgent = reviewerAgent;
        this.queryWorkingSetStore = queryWorkingSetStore;
        this.queryGraphStateMapper = queryGraphStateMapper;
        this.queryGraphConditions = queryGraphConditions;
    }

    /**
     * 构建问答图定义。
     *
     * @return StateGraph 定义
     * @throws Exception 构建异常
     */
    public StateGraph build() throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("normalize_question", AsyncNodeAction.node_async(this::normalizeQuestion));
        stateGraph.addNode("check_cache", AsyncNodeAction.node_async(this::checkCache));
        stateGraph.addNode("retrieve_candidates", AsyncNodeAction.node_async(this::retrieveCandidates));
        stateGraph.addNode("fuse_candidates", AsyncNodeAction.node_async(this::fuseCandidates));
        stateGraph.addNode("answer_question", AsyncNodeAction.node_async(this::answerQuestion));
        stateGraph.addNode("review_answer", AsyncNodeAction.node_async(this::reviewAnswer));
        stateGraph.addNode("rewrite_answer", AsyncNodeAction.node_async(this::rewriteAnswer));
        stateGraph.addNode("cache_response", AsyncNodeAction.node_async(this::cacheResponse));
        stateGraph.addNode("finalize_response", AsyncNodeAction.node_async(this::finalizeResponse));

        stateGraph.addEdge(StateGraph.START, "normalize_question");
        stateGraph.addEdge("normalize_question", "check_cache");
        stateGraph.addConditionalEdges(
                "check_cache",
                AsyncEdgeAction.edge_async(state -> queryGraphConditions.routeAfterCacheCheck(
                        queryGraphStateMapper.fromMap(state.data())
                )),
                Map.of(
                        "retrieve_candidates", "retrieve_candidates",
                        "finalize_response", "finalize_response"
                )
        );
        stateGraph.addEdge("retrieve_candidates", "fuse_candidates");
        stateGraph.addConditionalEdges(
                "fuse_candidates",
                AsyncEdgeAction.edge_async(state -> queryGraphConditions.routeAfterFuseCandidates(
                        queryGraphStateMapper.fromMap(state.data())
                )),
                Map.of(
                        "answer_question", "answer_question",
                        "finalize_response", "finalize_response"
                )
        );
        stateGraph.addEdge("answer_question", "review_answer");
        stateGraph.addConditionalEdges(
                "review_answer",
                AsyncEdgeAction.edge_async(state -> routeAfterReview(queryGraphStateMapper.fromMap(state.data()))),
                Map.of(
                        "cache_response", "cache_response",
                        "rewrite_answer", "rewrite_answer",
                        "finalize_response", "finalize_response"
                )
        );
        stateGraph.addEdge("rewrite_answer", "review_answer");
        stateGraph.addEdge("cache_response", "finalize_response");
        stateGraph.addEdge("finalize_response", StateGraph.END);
        return stateGraph;
    }

    /**
     * 规范化查询问题。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> normalizeQuestion(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        String question = state.getQuestion() == null ? "" : state.getQuestion();
        state.setNormalizedQuestion(question.trim());
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 执行缓存检查。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> checkCache(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryResponse cachedResponse = queryCacheStore.get(state.getNormalizedQuestion()).orElse(null);
        if (cachedResponse != null) {
            state.setCacheHit(true);
            state.setCachedResponseRef(queryWorkingSetStore.saveResponse(state.getQueryId(), cachedResponse));
            state.setReviewStatus(cachedResponse.getReviewStatus());
        }
        else {
            state.setCacheHit(false);
        }
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 执行多路检索。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> retrieveCandidates(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<List<QueryArticleHit>> hitGroups = List.of(
                ftsSearchService.search(state.getQuestion(), TOP_K),
                refKeySearchService.search(state.getQuestion(), TOP_K),
                sourceSearchService.search(state.getQuestion(), TOP_K),
                contributionSearchService.search(state.getQuestion(), TOP_K),
                vectorSearchService.search(state.getQuestion(), TOP_K)
        );
        state.setRetrievedHitGroupsRef(queryWorkingSetStore.saveRetrievedHitGroups(state.getQueryId(), hitGroups));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 融合多路检索命中。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> fuseCandidates(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                queryWorkingSetStore.loadRetrievedHitGroups(state.getRetrievedHitGroupsRef()),
                TOP_K
        );
        state.setHasFusedHits(!fusedHits.isEmpty());
        if (!fusedHits.isEmpty()) {
            state.setFusedHitsRef(queryWorkingSetStore.saveFusedHits(state.getQueryId(), fusedHits));
        }
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 生成答案草稿。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> answerQuestion(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String answer = answerGenerationService.generate(state.getQuestion(), fusedHits);
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), answer));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 审查当前答案草稿。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> reviewAnswer(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        ReviewResult reviewResult = reviewerAgent.review(
                state.getQuestion(),
                answer,
                collectSourcePaths(fusedHits)
        );
        state.setReviewResultRef(queryWorkingSetStore.saveReviewResult(state.getQueryId(), reviewResult));
        state.setReviewStatus(reviewResult.getStatus().name());
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 基于审查问题重写答案。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> rewriteAnswer(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String currentAnswer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        ReviewResult reviewResult = queryWorkingSetStore.loadReviewResult(state.getReviewResultRef());
        String rewrittenAnswer = answerGenerationService.revise(
                state.getQuestion(),
                currentAnswer,
                buildRewriteGuidance(reviewResult),
                fusedHits
        );
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), rewrittenAnswer));
        state.setRewriteAttemptCount(state.getRewriteAttemptCount() + 1);
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 写入可缓存响应。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> cacheResponse(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryResponse queryResponse = buildSuccessResponse(state);
        queryCacheStore.put(state.getNormalizedQuestion(), queryResponse);
        String responseRef = queryWorkingSetStore.saveResponse(state.getQueryId(), queryResponse);
        state.setCachedResponseRef(responseRef);
        state.setFinalResponseRef(responseRef);
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 收口最终响应。
     *
     * @param overAllState 当前状态
     * @return 状态增量
     */
    private Map<String, Object> finalizeResponse(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        if (state.getFinalResponseRef() != null) {
            return queryGraphStateMapper.toDeltaMap(state);
        }
        if (state.isCacheHit()) {
            state.setFinalResponseRef(state.getCachedResponseRef());
            return queryGraphStateMapper.toDeltaMap(state);
        }
        QueryResponse queryResponse;
        if (!state.isHasFusedHits()) {
            queryResponse = new QueryResponse("未找到相关知识", List.of(), List.of(), null, null);
        }
        else {
            queryResponse = buildSuccessResponse(state);
        }
        state.setFinalResponseRef(queryWorkingSetStore.saveResponse(state.getQueryId(), queryResponse));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 决定审查后的后续节点。
     *
     * @param state 当前图状态
     * @return 路由键
     */
    private String routeAfterReview(QueryGraphState state) {
        ReviewResult reviewResult = queryWorkingSetStore.loadReviewResult(state.getReviewResultRef());
        return queryGraphConditions.routeAfterReview(state, reviewResult);
    }

    /**
     * 构建成功响应。
     *
     * @param state 当前图状态
     * @return 查询响应
     */
    private QueryResponse buildSuccessResponse(QueryGraphState state) {
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        return new QueryResponse(
                answer,
                toSourceResponses(fusedHits),
                toArticleResponses(fusedHits),
                null,
                state.getReviewStatus()
        );
    }

    /**
     * 汇总重写指导语。
     *
     * @param reviewResult 审查结果
     * @return 重写指导语
     */
    private String buildRewriteGuidance(ReviewResult reviewResult) {
        if (reviewResult == null || reviewResult.getIssues().isEmpty()) {
            return "审查未通过，请基于证据增强答案的可验证性。";
        }
        List<String> issueDescriptions = new ArrayList<String>();
        for (ReviewIssue reviewIssue : reviewResult.getIssues()) {
            issueDescriptions.add(reviewIssue.getCategory() + ":" + reviewIssue.getDescription());
        }
        return String.join("; ", issueDescriptions);
    }

    /**
     * 转换来源响应列表。
     *
     * @param fusedHits 融合命中
     * @return 来源响应列表
     */
    private List<QuerySourceResponse> toSourceResponses(List<QueryArticleHit> fusedHits) {
        List<QuerySourceResponse> sourceResponses = new ArrayList<QuerySourceResponse>();
        Set<String> responseKeys = new LinkedHashSet<String>();
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.ARTICLE);
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.SOURCE);
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.CONTRIBUTION);
        return sourceResponses;
    }

    /**
     * 追加指定证据类型的来源响应。
     *
     * @param sourceResponses 来源响应列表
     * @param responseKeys 已输出键集合
     * @param fusedHits 融合命中
     * @param queryEvidenceType 证据类型
     */
    private void appendSourceResponses(
            List<QuerySourceResponse> sourceResponses,
            Set<String> responseKeys,
            List<QueryArticleHit> fusedHits,
            QueryEvidenceType queryEvidenceType
    ) {
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit.getEvidenceType() != queryEvidenceType) {
                continue;
            }
            String responseKey = fusedHit.getEvidenceType().name() + ":" + fusedHit.getConceptId();
            if (!responseKeys.add(responseKey)) {
                continue;
            }
            sourceResponses.add(new QuerySourceResponse(
                    fusedHit.getConceptId(),
                    fusedHit.getTitle(),
                    fusedHit.getSourcePaths()
            ));
        }
    }

    /**
     * 转换文章响应列表。
     *
     * @param fusedHits 融合命中
     * @return 文章响应列表
     */
    private List<QueryArticleResponse> toArticleResponses(List<QueryArticleHit> fusedHits) {
        List<QueryArticleResponse> articleResponses = new ArrayList<QueryArticleResponse>();
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
                continue;
            }
            articleResponses.add(new QueryArticleResponse(
                    fusedHit.getConceptId(),
                    fusedHit.getTitle()
            ));
        }
        return articleResponses;
    }

    /**
     * 收集审查所需的来源路径。
     *
     * @param fusedHits 融合命中
     * @return 去重来源路径
     */
    private List<String> collectSourcePaths(List<QueryArticleHit> fusedHits) {
        Set<String> sourcePaths = new LinkedHashSet<String>();
        for (QueryArticleHit fusedHit : fusedHits) {
            sourcePaths.addAll(fusedHit.getSourcePaths());
        }
        return new ArrayList<String>(sourcePaths);
    }
}
