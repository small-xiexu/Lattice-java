package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.domain.ReviewResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryGraphOrchestrator 测试
 *
 * 职责：验证问答图中的重写分支、退出条件与缓存写回策略
 *
 * @author xiexu
 */
class QueryGraphOrchestratorTests {

    /**
     * 验证审查失败后会进入重写分支，并在通过后写入缓存。
     */
    @Test
    void shouldRewriteAnswerWhenReviewFailsThenCachePassedResponse() {
        QueryArticleHit articleHit = new QueryArticleHit(
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "{\"description\":\"Handles payment timeout recovery\"}",
                List.of("payment/analyze.json"),
                10.0D
        );
        TrackingAnswerGenerationService answerGenerationService = new TrackingAnswerGenerationService(
                "TODO",
                "修复后的答案：retry=3 [[payment-timeout]]"
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(true);
        queryReviewProperties.setMaxRewriteRounds(1);
        QueryGraphOrchestrator queryGraphOrchestrator = QueryGraphTestSupport.createQueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(
                        new SequencedReviewerGateway(
                                "{\"approved\":false,\"rewriteRequired\":true,\"riskLevel\":\"HIGH\",\"issues\":[{\"severity\":\"HIGH\",\"category\":\"WEAK_ANSWER\",\"description\":\"答案包含 TODO\"}],\"userFacingRewriteHints\":[\"请去掉 TODO 并补齐明确结论\"],\"cacheWritePolicy\":\"SKIP_WRITE\"}",
                                "{\"approved\":true,\"rewriteRequired\":false,\"riskLevel\":\"LOW\",\"issues\":[],\"userFacingRewriteHints\":[],\"cacheWritePolicy\":\"WRITE\"}"
                        ),
                        new ReviewResultParser()
                ),
                queryReviewProperties,
                List.of(articleHit)
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("payment timeout retry=3");

        assertThat(queryResponse.getAnswer()).isEqualTo("修复后的答案：retry=3 [[payment-timeout]]");
        assertThat(queryResponse.getReviewStatus()).isEqualTo("PASSED");
        assertThat(queryResponse.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(queryResponse.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(queryResponse.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SUCCESS);
        assertThat(answerGenerationService.getGenerateCount()).isEqualTo(1);
        assertThat(answerGenerationService.getReviseCount()).isEqualTo(1);
        assertThat(answerGenerationService.getGeneratedScopeId()).isNotBlank();
        assertThat(answerGenerationService.getGeneratedScopeId()).isEqualTo(answerGenerationService.getRevisedScopeId());
        assertThat(answerGenerationService.getGeneratedScene()).isEqualTo("query");
        assertThat(answerGenerationService.getGeneratedRole()).isEqualTo("answer");
        assertThat(answerGenerationService.getRevisedScene()).isEqualTo("query");
        assertThat(answerGenerationService.getRevisedRole()).isEqualTo("rewrite");
        assertThat(queryCacheStore.getCachedResponse()).isPresent();
        assertThat(queryCacheStore.getCachedResponse().orElseThrow().getAnswer())
                .isEqualTo("修复后的答案：retry=3 [[payment-timeout]]");
        assertThat(queryResponse.getSources()).hasSize(1);
        assertThat(queryResponse.getSources().get(0).getDerivation()).isEqualTo("PROJECTION");
        assertThat(queryResponse.getArticles()).hasSize(1);
        assertThat(queryResponse.getArticles().get(0).getDerivation()).isEqualTo("PROJECTION");
    }

    /**
     * 验证达到最大重写轮次后会直接收口，并且不会写入缓存。
     */
    @Test
    void shouldFinalizeWithoutCachingWhenRewriteLimitIsReached() {
        QueryArticleHit articleHit = new QueryArticleHit(
                "refund-status",
                "Refund Status",
                "退款状态说明",
                "{\"description\":\"Refund lifecycle\"}",
                List.of("refund/status.md"),
                9.0D
        );
        TrackingAnswerGenerationService answerGenerationService = new TrackingAnswerGenerationService(
                "TODO",
                "仍然需要确认"
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(true);
        queryReviewProperties.setMaxRewriteRounds(1);
        QueryGraphOrchestrator queryGraphOrchestrator = QueryGraphTestSupport.createQueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(
                        new SequencedReviewerGateway(
                                "{\"approved\":false,\"rewriteRequired\":true,\"riskLevel\":\"HIGH\",\"issues\":[{\"severity\":\"HIGH\",\"category\":\"WEAK_ANSWER\",\"description\":\"答案包含 TODO\"}],\"userFacingRewriteHints\":[\"请返回可验证的结论\"],\"cacheWritePolicy\":\"SKIP_WRITE\"}",
                                "{\"approved\":false,\"rewriteRequired\":true,\"riskLevel\":\"HIGH\",\"issues\":[{\"severity\":\"HIGH\",\"category\":\"WEAK_ANSWER\",\"description\":\"答案仍缺乏可验证结论\"}],\"userFacingRewriteHints\":[\"请补齐可验证证据\"],\"cacheWritePolicy\":\"SKIP_WRITE\"}"
                        ),
                        new ReviewResultParser()
                ),
                queryReviewProperties,
                List.of(articleHit)
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("refund status");

        assertThat(queryResponse.getAnswer()).startsWith("# 查询回答");
        assertThat(queryResponse.getAnswer()).contains("[→ refund/status.md]");
        assertThat(queryResponse.getReviewStatus()).isEqualTo("ISSUES_FOUND");
        assertThat(queryResponse.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(queryResponse.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerGenerationService.getGenerateCount()).isEqualTo(1);
        assertThat(answerGenerationService.getReviseCount()).isEqualTo(1);
        assertThat(queryCacheStore.getCachedResponse()).isEmpty();
    }

    /**
     * 验证 Query Graph 会把统一的 query scope 透传给审查节点。
     */
    @Test
    void shouldPassScopedReviewerContextToReviewNode() {
        QueryArticleHit articleHit = new QueryArticleHit(
                "gateway-config",
                "Gateway Config",
                "failure-rate-threshold=50",
                "{\"description\":\"Gateway breaker config\"}",
                List.of("payments/gateway-config.yaml"),
                9.0D
        );
        TrackingAnswerGenerationService answerGenerationService = new TrackingAnswerGenerationService(
                "结论：failure-rate-threshold=50 [[gateway-config]]",
                "结论：failure-rate-threshold=50 [[gateway-config]]"
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(false);
        TrackingReviewerAgent reviewerAgent = new TrackingReviewerAgent();
        QueryGraphOrchestrator queryGraphOrchestrator = QueryGraphTestSupport.createQueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                queryReviewProperties,
                List.of(articleHit)
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("gateway breaker threshold");

        assertThat(queryResponse.getReviewStatus()).isEqualTo("PASSED");
        assertThat(reviewerAgent.reviewScopeId).isNotBlank();
        assertThat(reviewerAgent.reviewScopeId).isEqualTo(answerGenerationService.getGeneratedScopeId());
        assertThat(reviewerAgent.reviewScene).isEqualTo("query");
        assertThat(reviewerAgent.reviewRole).isEqualTo("reviewer");
        assertThat(reviewerAgent.currentRouteScopeId).isEqualTo(reviewerAgent.reviewScopeId);
        assertThat(reviewerAgent.currentRouteScene).isEqualTo("query");
        assertThat(reviewerAgent.currentRouteRole).isEqualTo("reviewer");
    }

    /**
     * 验证负向 outcome 不会写入缓存，而不是再依赖文案 marker。
     */
    @Test
    void shouldNotCacheEvidenceInsufficientAnswerEvenWhenReviewPasses() {
        QueryArticleHit articleHit = new QueryArticleHit(
                "inventory-async",
                "Inventory Async",
                "订单服务通过消息队列异步通知库存服务",
                "{\"description\":\"订单与库存通过异步事件解耦\"}",
                List.of("adr/order-inventory-mq.md"),
                9.5D
        );
        TrackingAnswerGenerationService answerGenerationService = new TrackingAnswerGenerationService(
                new QueryAnswerPayload(
                        "现有证据只能说明订单服务通过消息队列异步通知库存服务，但无法证明这是唯一必选方案。",
                        AnswerOutcome.INSUFFICIENT_EVIDENCE,
                        GenerationMode.LLM,
                        ModelExecutionStatus.SUCCESS,
                        false
                ),
                new QueryAnswerPayload(
                        "现有证据只能说明订单服务通过消息队列异步通知库存服务，但无法证明这是唯一必选方案。",
                        AnswerOutcome.INSUFFICIENT_EVIDENCE,
                        GenerationMode.LLM,
                        ModelExecutionStatus.SUCCESS,
                        false
                )
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(false);
        QueryGraphOrchestrator queryGraphOrchestrator = QueryGraphTestSupport.createQueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(
                        new SequencedReviewerGateway("{\"approved\":true,\"rewriteRequired\":false,\"riskLevel\":\"LOW\",\"issues\":[],\"userFacingRewriteHints\":[],\"cacheWritePolicy\":\"WRITE\"}"),
                        new ReviewResultParser()
                ),
                queryReviewProperties,
                List.of(articleHit)
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("为什么订单服务要走消息队列");

        assertThat(queryResponse.getAnswer()).startsWith("# 查询回答");
        assertThat(queryResponse.getAnswer()).contains("[→ adr/order-inventory-mq.md]");
        assertThat(queryResponse.getReviewStatus()).isEqualTo("PASSED");
        assertThat(queryResponse.getAnswerOutcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
        assertThat(queryResponse.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(queryCacheStore.getCachedResponse()).isEmpty();
    }

    /**
     * 验证缓存命中时会为当前请求重新绑定 queryId，而不是复用旧请求的 queryId。
     */
    @Test
    void shouldRebindQueryIdWhenReturningCachedResponse() {
        QueryArticleHit articleHit = new QueryArticleHit(
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "{\"description\":\"Handles payment timeout recovery\"}",
                List.of("payment/analyze.json"),
                10.0D
        );
        TrackingAnswerGenerationService answerGenerationService = new TrackingAnswerGenerationService(
                "结论：retry=3 [[payment-timeout]]",
                "结论：retry=3 [[payment-timeout]]"
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(false);
        QueryGraphOrchestrator queryGraphOrchestrator = QueryGraphTestSupport.createQueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(
                        new SequencedReviewerGateway("{\"approved\":true,\"rewriteRequired\":false,\"riskLevel\":\"LOW\",\"issues\":[],\"userFacingRewriteHints\":[],\"cacheWritePolicy\":\"WRITE\"}"),
                        new ReviewResultParser()
                ),
                queryReviewProperties,
                List.of(articleHit)
        );

        QueryResponse firstResponse = queryGraphOrchestrator.execute("payment timeout retry=3", "query-001");
        QueryResponse secondResponse = queryGraphOrchestrator.execute("payment timeout retry=3", "query-002");

        QueryResponse cachedResponse = queryCacheStore.getCachedResponse().orElseThrow();
        assertThat(firstResponse.getQueryId()).isEqualTo("query-001");
        assertThat(secondResponse.getQueryId()).isEqualTo("query-002");
        assertThat(secondResponse.getQueryId()).isNotEqualTo(firstResponse.getQueryId());
        assertThat(cachedResponse.getQueryId()).isNull();
        assertThat(secondResponse.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(secondResponse.getGenerationMode()).isEqualTo(GenerationMode.LLM);
    }

    /**
     * 验证 reviewer 能看到 answerOutcome，避免把简短但可核验的直接答案机械打回。
     */
    @Test
    void shouldPassAnswerOutcomeToReviewerPrompt() {
        QueryArticleHit articleHit = new QueryArticleHit(
                "payment-timeout",
                "Payment Timeout",
                "retry=5",
                "{\"description\":\"Handles payment timeout recovery\"}",
                List.of("payment/analyze.json"),
                10.0D
        );
        TrackingAnswerGenerationService answerGenerationService = new TrackingAnswerGenerationService(
                new QueryAnswerPayload(
                        "结论：`payment.timeout.retry` 默认值为 `5`。[[payment-timeout]]",
                        AnswerOutcome.SUCCESS,
                        GenerationMode.LLM,
                        ModelExecutionStatus.SUCCESS,
                        true
                ),
                new QueryAnswerPayload(
                        "结论：`payment.timeout.retry` 默认值为 `5`。[[payment-timeout]]",
                        AnswerOutcome.SUCCESS,
                        GenerationMode.LLM,
                        ModelExecutionStatus.SUCCESS,
                        true
                )
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(false);
        PromptAwareReviewerGateway reviewerGateway = new PromptAwareReviewerGateway();
        QueryGraphOrchestrator queryGraphOrchestrator = QueryGraphTestSupport.createQueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(reviewerGateway, new ReviewResultParser()),
                queryReviewProperties,
                List.of(articleHit)
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("payment timeout retry=3 是什么配置");

        assertThat(queryResponse.getReviewStatus()).isEqualTo("PASSED");
        assertThat(reviewerGateway.lastReviewPrompt).contains("answerOutcome=SUCCESS");
    }

    /**
     * 验证简单问答的 citation 必须命中本轮 TOP_K projection 白名单。
     */
    @Test
    void shouldRepairCitationOutsideTopKProjectionWhitelist() {
        QueryArticleHit topKHit = new QueryArticleHit(
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "{\"description\":\"Handles payment timeout recovery\"}",
                List.of("payment/analyze.json"),
                10.0D
        );
        QueryArticleHit outsideHit = new QueryArticleHit(
                "outside-article",
                "Outside Article",
                "retry=3",
                "{\"description\":\"Outside evidence\"}",
                List.of("outside.md"),
                1.0D
        );
        TrackingAnswerGenerationService answerGenerationService = new TrackingAnswerGenerationService(
                "结论：retry=3 [[outside-article]]",
                "结论：retry=3 [[outside-article]]"
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(false);
        QueryGraphOrchestrator queryGraphOrchestrator = QueryGraphTestSupport.createQueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(topKHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(
                        new SequencedReviewerGateway("{\"approved\":true,\"rewriteRequired\":false,\"riskLevel\":\"LOW\",\"issues\":[],\"userFacingRewriteHints\":[],\"cacheWritePolicy\":\"WRITE\"}"),
                        new ReviewResultParser()
                ),
                queryReviewProperties,
                List.of(topKHit, outsideHit)
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("payment timeout retry");

        assertThat(queryResponse.getAnswer()).startsWith("# 查询回答");
        assertThat(queryResponse.getAnswer()).contains("[→ payment/analyze.json]");
        assertThat(queryResponse.getReviewStatus()).isEqualTo("PASSED");
        assertThat(queryResponse.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(queryResponse.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(queryResponse.getCitationCheck()).isNotNull();
        assertThat(queryResponse.getCitationCheck().isNoCitation()).isFalse();
        assertThat(queryResponse.getSources()).hasSize(1);
        assertThat(queryResponse.getSources().get(0).getDerivation()).isEqualTo("PROJECTION");
        assertThat(queryResponse.getArticles()).hasSize(1);
        assertThat(queryResponse.getArticles().get(0).getDerivation()).isEqualTo("PROJECTION");
        assertThat(queryCacheStore.getCachedResponse()).isEmpty();
    }

    /**
     * 追踪生成与重写次数的答案服务替身。
     *
     * @author xiexu
     */
    private static class TrackingAnswerGenerationService extends AnswerGenerationService {

        private final QueryAnswerPayload generatedPayload;

        private final QueryAnswerPayload revisedPayload;

        private int generateCount;

        private int reviseCount;

        private String generatedScopeId;

        private String generatedScene;

        private String generatedRole;

        private String revisedScopeId;

        private String revisedScene;

        private String revisedRole;

        /**
         * 创建答案服务替身。
         *
         * @param generatedAnswer 首轮答案
         * @param revisedAnswer 重写答案
         */
        private TrackingAnswerGenerationService(String generatedAnswer, String revisedAnswer) {
            this(
                    new QueryAnswerPayload(
                            generatedAnswer,
                            AnswerOutcome.SUCCESS,
                            GenerationMode.LLM,
                            ModelExecutionStatus.SUCCESS,
                            true
                    ),
                    new QueryAnswerPayload(
                            revisedAnswer,
                            AnswerOutcome.SUCCESS,
                            GenerationMode.LLM,
                            ModelExecutionStatus.SUCCESS,
                            true
                    )
            );
        }

        /**
         * 创建答案服务替身。
         *
         * @param generatedPayload 首轮答案载荷
         * @param revisedPayload 重写答案载荷
         */
        private TrackingAnswerGenerationService(
                QueryAnswerPayload generatedPayload,
                QueryAnswerPayload revisedPayload
        ) {
            super();
            this.generatedPayload = generatedPayload;
            this.revisedPayload = revisedPayload;
        }

        /**
         * 返回首轮答案并记录调用次数。
         *
         * @param question 查询问题
         * @param queryArticleHits 融合命中
         * @return 首轮答案
         */
        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            generateCount++;
            generatedScopeId = scopeId;
            generatedScene = scene;
            generatedRole = agentRole;
            return generatedPayload;
        }

        /**
         * 返回重写答案并记录调用次数。
         *
         * @param question 查询问题
         * @param currentAnswer 当前答案
         * @param correction 审查反馈
         * @param queryArticleHits 融合命中
         * @return 重写答案
         */
        @Override
        public QueryAnswerPayload rewriteFromReviewPayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                String currentAnswer,
                String correction,
                List<QueryArticleHit> queryArticleHits
        ) {
            reviseCount++;
            revisedScopeId = scopeId;
            revisedScene = scene;
            revisedRole = agentRole;
            return revisedPayload;
        }

        /**
         * 获取生成次数。
         *
         * @return 生成次数
         */
        private int getGenerateCount() {
            return generateCount;
        }

        /**
         * 获取重写次数。
         *
         * @return 重写次数
         */
        private int getReviseCount() {
            return reviseCount;
        }

        private String getGeneratedScopeId() {
            return generatedScopeId;
        }

        private String getGeneratedScene() {
            return generatedScene;
        }

        private String getGeneratedRole() {
            return generatedRole;
        }

        private String getRevisedScopeId() {
            return revisedScopeId;
        }

        private String getRevisedScene() {
            return revisedScene;
        }

        private String getRevisedRole() {
            return revisedRole;
        }
    }

    /**
     * 顺序返回审查结果的网关替身。
     *
     * @author xiexu
     */
    private static class SequencedReviewerGateway implements ReviewerGateway {

        private final Deque<String> rawResults;

        /**
         * 创建审查网关替身。
         *
         * @param rawResults 顺序审查结果
         */
        private SequencedReviewerGateway(String... rawResults) {
            this.rawResults = new ArrayDeque<String>(List.of(rawResults));
        }

        /**
         * 返回下一条审查结果。
         *
         * @param reviewPrompt 审查提示词
         * @return 原始审查输出
         */
        @Override
        public String review(String reviewPrompt) {
            return rawResults.removeFirst();
        }
    }

    /**
     * 根据 prompt 是否携带 answerOutcome 决定审查结果的网关替身。
     *
     * @author xiexu
     */
    private static class PromptAwareReviewerGateway implements ReviewerGateway {

        private String lastReviewPrompt;

        @Override
        public String review(String reviewPrompt) {
            lastReviewPrompt = reviewPrompt;
            if (reviewPrompt != null && reviewPrompt.contains("answerOutcome=SUCCESS")) {
                return """
                        {"approved":true,"rewriteRequired":false,"riskLevel":"LOW","issues":[],"userFacingRewriteHints":[],"cacheWritePolicy":"WRITE"}
                        """;
            }
            return """
                    {"approved":false,"rewriteRequired":true,"riskLevel":"HIGH","issues":[{"severity":"HIGH","category":"MISSING_OUTCOME_CONTEXT","description":"缺少 answerOutcome 上下文"}],"userFacingRewriteHints":["请补充答案语义"],"cacheWritePolicy":"SKIP_WRITE"}
                    """;
        }
    }

    /**
     * 跟踪 scope / route 的审查代理替身。
     *
     * @author xiexu
     */
    private static class TrackingReviewerAgent extends ReviewerAgent {

        private String reviewScopeId;

        private String reviewScene;

        private String reviewRole;

        private String currentRouteScopeId;

        private String currentRouteScene;

        private String currentRouteRole;

        /**
         * 创建审查代理替身。
         */
        private TrackingReviewerAgent() {
            super(
                    new SequencedReviewerGateway(
                            "{\"approved\":true,\"rewriteRequired\":false,\"riskLevel\":\"LOW\",\"issues\":[],\"userFacingRewriteHints\":[],\"cacheWritePolicy\":\"WRITE\"}"
                    ),
                    new ReviewResultParser()
            );
        }

        /**
         * 记录作用域参数并返回通过结果。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @param question 问题
         * @param answer 答案
         * @param sourcePaths 来源路径
         * @return 审查结果
         */
        @Override
        public ReviewResult review(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                String answer,
                AnswerOutcome answerOutcome,
                List<String> sourcePaths
        ) {
            reviewScopeId = scopeId;
            reviewScene = scene;
            reviewRole = agentRole;
            return ReviewResult.passed();
        }

        /**
         * 记录当前路由查询参数。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @return 路由标签
         */
        @Override
        public String currentRoute(String scopeId, String scene, String agentRole) {
            currentRouteScopeId = scopeId;
            currentRouteScene = scene;
            currentRouteRole = agentRole;
            return "query.reviewer.claude";
        }
    }

    /**
     * 内存查询缓存替身。
     *
     * @author xiexu
     */
    private static class InMemoryQueryCacheStore implements QueryCacheStore {

        private QueryResponse cachedResponse;

        /**
         * 读取缓存结果。
         *
         * @param cacheKey 缓存键
         * @return 查询结果
         */
        @Override
        public Optional<QueryResponse> get(String cacheKey) {
            return Optional.ofNullable(cachedResponse);
        }

        /**
         * 写入缓存结果。
         *
         * @param cacheKey 缓存键
         * @param queryResponse 查询结果
         */
        @Override
        public void put(String cacheKey, QueryResponse queryResponse) {
            cachedResponse = queryResponse;
        }

        @Override
        public void evictAll() {
            cachedResponse = null;
        }

        /**
         * 返回当前缓存结果。
         *
         * @return 当前缓存结果
         */
        private Optional<QueryResponse> getCachedResponse() {
            return Optional.ofNullable(cachedResponse);
        }
    }

    /**
     * 固定 FTS 检索服务替身。
     *
     * @author xiexu
     */
    private static class FixedFtsSearchService extends FtsSearchService {

        private final List<QueryArticleHit> hits;

        /**
         * 创建固定 FTS 检索服务替身。
         *
         * @param hits 固定命中
         */
        private FixedFtsSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回固定命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }

    /**
     * 固定引用词检索服务替身。
     *
     * @author xiexu
     */
    private static class FixedRefKeySearchService extends RefKeySearchService {

        private final List<QueryArticleHit> hits;

        /**
         * 创建固定引用词检索服务替身。
         *
         * @param hits 固定命中
         */
        private FixedRefKeySearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回固定命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }

    /**
     * 固定源文件检索服务替身。
     *
     * @author xiexu
     */
    private static class FixedSourceSearchService extends SourceSearchService {

        private final List<QueryArticleHit> hits;

        /**
         * 创建固定源文件检索服务替身。
         *
         * @param hits 固定命中
         */
        private FixedSourceSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回固定命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }

    /**
     * 固定 Contribution 检索服务替身。
     *
     * @author xiexu
     */
    private static class FixedContributionSearchService extends ContributionSearchService {

        private final List<QueryArticleHit> hits;

        /**
         * 创建固定 Contribution 检索服务替身。
         *
         * @param hits 固定命中
         */
        private FixedContributionSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回固定命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }

    /**
     * 固定向量检索服务替身。
     *
     * @author xiexu
     */
    private static class FixedVectorSearchService extends VectorSearchService {

        private final List<QueryArticleHit> hits;

        /**
         * 创建固定向量检索服务替身。
         *
         * @param hits 固定命中
         */
        private FixedVectorSearchService(List<QueryArticleHit> hits) {
            super();
            this.hits = hits;
        }

        /**
         * 返回固定命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }
}
