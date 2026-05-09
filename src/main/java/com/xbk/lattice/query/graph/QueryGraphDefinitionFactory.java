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
 * 问答图定义工厂
 *
 * 职责：集中声明 Query Graph 的节点、顺序边与条件边
 *
 * @author xiexu
 */
@Slf4j
@Component
public class QueryGraphDefinitionFactory extends QueryGraphAnswerSupport {

    /**
     * 创建问答图定义工厂。
     */
    public QueryGraphDefinitionFactory(
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

    /**
     * 构建问答图定义。
     *
     * @return StateGraph 定义
     * @throws Exception 构建异常
     */
    public StateGraph build() throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("normalize_question", AsyncNodeAction.node_async(this::normalizeQuestion));
        stateGraph.addNode("rewrite_query", AsyncNodeAction.node_async(this::rewriteQuery));
        stateGraph.addNode("classify_intent", AsyncNodeAction.node_async(this::classifyIntent));
        stateGraph.addNode("resolve_retrieval_strategy", AsyncNodeAction.node_async(this::resolveRetrievalStrategy));
        stateGraph.addNode("check_cache", AsyncNodeAction.node_async(this::checkCache));
        stateGraph.addNode("dispatch_retrieval", AsyncNodeAction.node_async(this::dispatchRetrieval));
        stateGraph.addNode("fuse_candidates", AsyncNodeAction.node_async(this::fuseCandidates));
        stateGraph.addNode("answer_question", AsyncNodeAction.node_async(this::answerQuestion));
        stateGraph.addNode("review_answer", AsyncNodeAction.node_async(this::reviewAnswer));
        stateGraph.addNode("rewrite_answer", AsyncNodeAction.node_async(this::rewriteAnswer));
        stateGraph.addNode("claim_segment", AsyncNodeAction.node_async(queryFinalizationGraphFragment::claimSegment));
        stateGraph.addNode("citation_check", AsyncNodeAction.node_async(queryFinalizationGraphFragment::citationCheck));
        stateGraph.addNode("citation_repair", AsyncNodeAction.node_async(queryFinalizationGraphFragment::citationRepair));
        stateGraph.addNode("persist_response", AsyncNodeAction.node_async(queryFinalizationGraphFragment::persistResponse));
        stateGraph.addNode("finalize_response", AsyncNodeAction.node_async(queryFinalizationGraphFragment::finalizeResponse));

        stateGraph.addEdge(StateGraph.START, "normalize_question");
        stateGraph.addEdge("normalize_question", "rewrite_query");
        stateGraph.addEdge("rewrite_query", "classify_intent");
        stateGraph.addEdge("classify_intent", "resolve_retrieval_strategy");
        stateGraph.addEdge("resolve_retrieval_strategy", "check_cache");
        stateGraph.addConditionalEdges(
                "check_cache",
                AsyncEdgeAction.edge_async(state -> queryGraphConditions.routeAfterCacheCheck(
                        queryGraphStateMapper.fromMap(state.data())
                )),
                Map.of(
                        "dispatch_retrieval", "dispatch_retrieval",
                        "finalize_response", "finalize_response"
                )
        );
        stateGraph.addEdge("dispatch_retrieval", "fuse_candidates");
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
                        "rewrite_answer", "rewrite_answer",
                        "claim_segment", "claim_segment"
                )
        );
        stateGraph.addEdge("rewrite_answer", "review_answer");
        stateGraph.addEdge("claim_segment", "citation_check");
        stateGraph.addConditionalEdges(
                "citation_check",
                AsyncEdgeAction.edge_async(state -> routeAfterCitationCheck(queryGraphStateMapper.fromMap(state.data()))),
                Map.of(
                        "citation_repair", "citation_repair",
                        "persist_response", "persist_response"
                )
        );
        stateGraph.addEdge("citation_repair", "citation_check");
        stateGraph.addEdge("persist_response", "finalize_response");
        stateGraph.addEdge("finalize_response", StateGraph.END);
        return stateGraph;
    }
}
