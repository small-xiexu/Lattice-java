package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryResponse;
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
                "修复后的答案：retry=3"
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(true);
        queryReviewProperties.setMaxRewriteRounds(1);
        QueryGraphOrchestrator queryGraphOrchestrator = new QueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                new RrfFusionService(),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(
                        new SequencedReviewerGateway(
                                "{\"pass\":false,\"issues\":[{\"severity\":\"HIGH\",\"category\":\"WEAK_ANSWER\",\"description\":\"答案包含 TODO\"}]}",
                                "{\"pass\":true,\"issues\":[]}"
                        ),
                        new ReviewResultParser()
                ),
                queryReviewProperties
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("payment timeout retry=3");

        assertThat(queryResponse.getAnswer()).isEqualTo("修复后的答案：retry=3");
        assertThat(queryResponse.getReviewStatus()).isEqualTo("PASSED");
        assertThat(answerGenerationService.getGenerateCount()).isEqualTo(1);
        assertThat(answerGenerationService.getReviseCount()).isEqualTo(1);
        assertThat(answerGenerationService.getGeneratedScopeId()).isNotBlank();
        assertThat(answerGenerationService.getGeneratedScopeId()).isEqualTo(answerGenerationService.getRevisedScopeId());
        assertThat(answerGenerationService.getGeneratedScene()).isEqualTo("query");
        assertThat(answerGenerationService.getGeneratedRole()).isEqualTo("answer");
        assertThat(answerGenerationService.getRevisedScene()).isEqualTo("query");
        assertThat(answerGenerationService.getRevisedRole()).isEqualTo("rewrite");
        assertThat(queryCacheStore.getCachedResponse()).isPresent();
        assertThat(queryCacheStore.getCachedResponse().orElseThrow().getAnswer()).isEqualTo("修复后的答案：retry=3");
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
        QueryGraphOrchestrator queryGraphOrchestrator = new QueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                new RrfFusionService(),
                answerGenerationService,
                queryCacheStore,
                new ReviewerAgent(
                        new SequencedReviewerGateway(
                                "{\"pass\":false,\"issues\":[{\"severity\":\"HIGH\",\"category\":\"WEAK_ANSWER\",\"description\":\"答案包含 TODO\"}]}",
                                "{\"pass\":false,\"issues\":[{\"severity\":\"HIGH\",\"category\":\"WEAK_ANSWER\",\"description\":\"答案仍缺乏可验证结论\"}]}"
                        ),
                        new ReviewResultParser()
                ),
                queryReviewProperties
        );

        QueryResponse queryResponse = queryGraphOrchestrator.execute("refund status");

        assertThat(queryResponse.getAnswer()).isEqualTo("仍然需要确认");
        assertThat(queryResponse.getReviewStatus()).isEqualTo("ISSUES_FOUND");
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
                "结论：failure-rate-threshold=50",
                "结论：failure-rate-threshold=50"
        );
        InMemoryQueryCacheStore queryCacheStore = new InMemoryQueryCacheStore();
        QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
        queryReviewProperties.setRewriteEnabled(false);
        TrackingReviewerAgent reviewerAgent = new TrackingReviewerAgent();
        QueryGraphOrchestrator queryGraphOrchestrator = new QueryGraphOrchestrator(
                new FixedFtsSearchService(List.of(articleHit)),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of()),
                new RrfFusionService(),
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                queryReviewProperties
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
     * 追踪生成与重写次数的答案服务替身。
     *
     * @author xiexu
     */
    private static class TrackingAnswerGenerationService extends AnswerGenerationService {

        private final String generatedAnswer;

        private final String revisedAnswer;

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
            super();
            this.generatedAnswer = generatedAnswer;
            this.revisedAnswer = revisedAnswer;
        }

        /**
         * 返回首轮答案并记录调用次数。
         *
         * @param question 查询问题
         * @param queryArticleHits 融合命中
         * @return 首轮答案
         */
        @Override
        public String generate(
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
            return generatedAnswer;
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
        public String rewriteFromReviewFeedback(
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
            return revisedAnswer;
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
            super(new SequencedReviewerGateway("{\"pass\":true,\"issues\":[]}"), new ReviewResultParser());
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
