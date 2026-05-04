package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryRequest;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.deepresearch.service.DeepResearchRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryFacadeService 深度研究路由测试
 *
 * 职责：验证复杂问题会先看首轮检索证据，再决定是否升级到 Deep Research
 *
 * @author xiexu
 */
class QueryFacadeServiceDeepResearchRoutingTests {

    /**
     * 验证复杂问题若首轮检索证据已经充足，则继续走普通问答主链。
     */
    @Test
    void shouldStayOnQueryGraphWhenPreflightEvidenceIsSufficient() {
        FixedQueryGraphOrchestrator queryGraphOrchestrator = new FixedQueryGraphOrchestrator("simple-answer");
        FixedDeepResearchOrchestrator deepResearchOrchestrator = new FixedDeepResearchOrchestrator("deep-answer");
        FixedKnowledgeSearchService knowledgeSearchService = new FixedKnowledgeSearchService(List.of(
                hit("支付超时", "支付超时重试机制", "支付超时后会触发重试，避免瞬时失败直接丢单"),
                hit("重试策略", "支付重试策略", "支付超时命中重试策略，最多重试三次")
        ));
        QueryFacadeService queryFacadeService = new QueryFacadeService(
                queryGraphOrchestrator,
                deepResearchOrchestrator,
                new DeepResearchRouter(),
                knowledgeSearchService,
                null,
                null,
                null
        );
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("为什么支付超时要重试");

        QueryResponse queryResponse = queryFacadeService.query(queryRequest);

        assertThat(queryResponse.getAnswer()).isEqualTo("simple-answer");
        assertThat(queryGraphOrchestrator.getInvocationCount()).isEqualTo(1);
        assertThat(deepResearchOrchestrator.getInvocationCount()).isEqualTo(0);
        assertThat(knowledgeSearchService.getInvocationCount()).isEqualTo(1);
    }

    /**
     * 验证复杂问题若首轮检索证据不足，则升级到 Deep Research。
     */
    @Test
    void shouldEscalateToDeepResearchWhenPreflightEvidenceIsWeak() {
        FixedQueryGraphOrchestrator queryGraphOrchestrator = new FixedQueryGraphOrchestrator("simple-answer");
        FixedDeepResearchOrchestrator deepResearchOrchestrator = new FixedDeepResearchOrchestrator("deep-answer");
        FixedKnowledgeSearchService knowledgeSearchService = new FixedKnowledgeSearchService(List.of(
                hit("退款流程", "退款流程说明", "这里只介绍退款状态流转与人工审核节点")
        ));
        QueryFacadeService queryFacadeService = new QueryFacadeService(
                queryGraphOrchestrator,
                deepResearchOrchestrator,
                new DeepResearchRouter(),
                knowledgeSearchService,
                null,
                null,
                null
        );
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("为什么支付超时要重试");

        QueryResponse queryResponse = queryFacadeService.query(queryRequest);

        assertThat(queryResponse.getAnswer()).isEqualTo("deep-answer");
        assertThat(queryGraphOrchestrator.getInvocationCount()).isEqualTo(0);
        assertThat(deepResearchOrchestrator.getInvocationCount()).isEqualTo(1);
        assertThat(knowledgeSearchService.getInvocationCount()).isEqualTo(1);
    }

    /**
     * 验证显式 forceDeep 仍然直接走 Deep Research，不受首轮检索命中干扰。
     */
    @Test
    void shouldRespectForceDeepEvenWhenPreflightEvidenceLooksEnough() {
        FixedQueryGraphOrchestrator queryGraphOrchestrator = new FixedQueryGraphOrchestrator("simple-answer");
        FixedDeepResearchOrchestrator deepResearchOrchestrator = new FixedDeepResearchOrchestrator("deep-answer");
        FixedKnowledgeSearchService knowledgeSearchService = new FixedKnowledgeSearchService(List.of(
                hit("支付超时", "支付超时重试机制", "支付超时后会触发重试"),
                hit("重试策略", "支付重试策略", "支付超时命中重试策略")
        ));
        QueryFacadeService queryFacadeService = new QueryFacadeService(
                queryGraphOrchestrator,
                deepResearchOrchestrator,
                new DeepResearchRouter(),
                knowledgeSearchService,
                null,
                null,
                null
        );
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("为什么支付超时要重试");
        queryRequest.setForceDeep(true);

        QueryResponse queryResponse = queryFacadeService.query(queryRequest);

        assertThat(queryResponse.getAnswer()).isEqualTo("deep-answer");
        assertThat(queryGraphOrchestrator.getInvocationCount()).isEqualTo(0);
        assertThat(deepResearchOrchestrator.getInvocationCount()).isEqualTo(1);
        assertThat(knowledgeSearchService.getInvocationCount()).isEqualTo(0);
    }

    private QueryArticleHit hit(String conceptId, String title, String content) {
        return new QueryArticleHit(
                conceptId,
                title,
                content,
                "{\"description\":\"" + title + "\"}",
                List.of("docs/" + conceptId + ".md"),
                10.0D
        );
    }

    /**
     * 固定返回预置答案的普通问答编排器。
     *
     * @author xiexu
     */
    private static class FixedQueryGraphOrchestrator extends QueryGraphOrchestrator {

        private final QueryResponse queryResponse;

        private int invocationCount;

        private FixedQueryGraphOrchestrator(String answer) {
            super(null, null, null, null, new QueryReviewProperties(), null);
            this.queryResponse = new QueryResponse(answer, List.of(), List.of());
        }

        @Override
        public QueryResponse execute(String question, String queryId) {
            invocationCount++;
            return queryResponse;
        }

        private int getInvocationCount() {
            return invocationCount;
        }
    }

    /**
     * 固定返回预置答案的深度研究编排器。
     *
     * @author xiexu
     */
    private static class FixedDeepResearchOrchestrator extends DeepResearchOrchestrator {

        private final QueryResponse queryResponse;

        private int invocationCount;

        private FixedDeepResearchOrchestrator(String answer) {
            super(new DeepResearchRouter(), null, null, null, null, null, null, null, null, null);
            this.queryResponse = new QueryResponse(answer, List.of(), List.of());
        }

        @Override
        public QueryResponse execute(QueryRequest queryRequest, String queryId) {
            invocationCount++;
            return queryResponse;
        }

        private int getInvocationCount() {
            return invocationCount;
        }
    }

    /**
     * 固定返回首轮命中的知识检索服务替身。
     *
     * @author xiexu
     */
    private static class FixedKnowledgeSearchService extends KnowledgeSearchService {

        private final List<QueryArticleHit> hits;

        private int invocationCount;

        private FixedKnowledgeSearchService(List<QueryArticleHit> hits) {
            super(
                    new FtsSearchService(null),
                    new RefKeySearchService(null),
                    new SourceSearchService(null),
                    new ContributionSearchService(null),
                    new RrfFusionService()
            );
            this.hits = hits;
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            invocationCount++;
            return hits;
        }

        private int getInvocationCount() {
            return invocationCount;
        }
    }
}
