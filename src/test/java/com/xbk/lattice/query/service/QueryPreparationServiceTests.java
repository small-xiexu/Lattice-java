package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.QueryRewriteAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryRewriteRuleJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryRewriteRuleRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query Preparation 服务测试
 *
 * 职责：验证查询改写、意图策略解析与检索入口上下文传递
 *
 * @author xiexu
 */
class QueryPreparationServiceTests {

    /**
     * 验证规则改写会追加扩写文本并写入审计引用。
     */
    @Test
    void shouldRewriteQuestionByRulesAndReturnAuditRef() {
        QueryRewriteService queryRewriteService = new QueryRewriteService(
                new FixedQueryRewriteRuleJdbcRepository(List.of(new QueryRewriteRuleRecord(
                        1L,
                        "payment-timeout-alias",
                        "PT",
                        "payment timeout retry policy",
                        "global",
                        100
                ))),
                new FixedQueryRewriteAuditJdbcRepository()
        );

        QueryRewriteResult queryRewriteResult = queryRewriteService.rewrite("query-1", "PT 怎么处理");

        assertThat(queryRewriteResult.isRewriteApplied()).isTrue();
        assertThat(queryRewriteResult.getRewrittenQuestion()).contains("payment timeout retry policy");
        assertThat(queryRewriteResult.getMatchedRuleCodes()).containsExactly("payment-timeout-alias");
        assertThat(queryRewriteResult.getAuditRef()).isEqualTo("query_rewrite_audits:query-1");
    }

    /**
     * 验证配置类查询会提升结构化通道并关闭意图感知向量通道。
     */
    @Test
    void shouldResolveIntentAwareRetrievalStrategyForConfigurationQuery() {
        QueryRetrievalSettingsState settings = new QueryRetrievalSettingsService().defaultState();
        RetrievalStrategy retrievalStrategy = new RetrievalStrategyResolver().resolve(
                "payment.retry.maxAttempts 配置在哪里",
                QueryIntent.CONFIGURATION,
                settings
        );

        assertThat(retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_REFKEY)).isTrue();
        assertThat(retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS)).isTrue();
        assertThat(retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR)).isFalse();
        assertThat(retrievalStrategy.weightOf(RetrievalStrategyResolver.CHANNEL_REFKEY))
                .isGreaterThan(settings.getRefkeyWeight());
    }

    /**
     * 验证调用链类问题会进一步提升 graph / chunk lexical 通道，并继续关闭意图感知向量通道。
     */
    @Test
    void shouldPromoteGraphAndChunkLexicalForCodeStructureQuery() {
        QueryRetrievalSettingsState settings = new QueryRetrievalSettingsService().defaultState();
        RetrievalStrategy retrievalStrategy = new RetrievalStrategyResolver().resolve(
                "PaymentRetryService 调用链经过哪些类",
                QueryIntent.CODE_STRUCTURE,
                settings
        );

        assertThat(retrievalStrategy.weightOf(RetrievalStrategyResolver.CHANNEL_GRAPH))
                .isGreaterThan(settings.getGraphWeight());
        assertThat(retrievalStrategy.weightOf(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS))
                .isGreaterThan(settings.getSourceChunkWeight());
        assertThat(retrievalStrategy.weightOf(RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS))
                .isGreaterThan(settings.getArticleChunkWeight());
        assertThat(retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR)).isFalse();
        assertThat(retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR)).isFalse();
    }

    /**
     * 验证架构类问题会受控提升 graph / vector 权重，释放默认主链收益。
     */
    @Test
    void shouldPromoteGraphAndVectorForArchitectureQuery() {
        QueryRetrievalSettingsState settings = new QueryRetrievalSettingsService().defaultState();
        RetrievalStrategy retrievalStrategy = new RetrievalStrategyResolver().resolve(
                "为什么要做知识编译而不是只靠关键词检索",
                QueryIntent.ARCHITECTURE,
                settings
        );

        assertThat(retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR)).isTrue();
        assertThat(retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR)).isTrue();
        assertThat(retrievalStrategy.weightOf(RetrievalStrategyResolver.CHANNEL_GRAPH))
                .isGreaterThan(settings.getGraphWeight());
        assertThat(retrievalStrategy.weightOf(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR))
                .isGreaterThan(settings.getArticleVectorWeight());
        assertThat(retrievalStrategy.weightOf(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR))
                .isGreaterThan(settings.getChunkVectorWeight());
    }

    /**
     * 验证 KnowledgeSearchService 会把改写后的问题传给底层检索通道。
     */
    @Test
    void shouldSearchWithRewrittenQuestion() {
        CapturingFtsSearchService ftsSearchService = new CapturingFtsSearchService();
        CapturingRetrievalAuditService retrievalAuditService = new CapturingRetrievalAuditService();
        KnowledgeSearchService knowledgeSearchService = new KnowledgeSearchService(
                ftsSearchService,
                new ArticleChunkFtsSearchService(null),
                new RefKeySearchService(null),
                new SourceSearchService(null),
                new SourceChunkFtsSearchService(null),
                new ContributionSearchService(null),
                new GraphSearchService(),
                new VectorSearchService(),
                new ChunkVectorSearchService(),
                new RrfFusionService(),
                new QueryRetrievalSettingsService(),
                new FixedRewriteService(),
                new QueryIntentClassifier(),
                new RetrievalStrategyResolver(),
                retrievalAuditService
        );

        List<QueryArticleHit> hits = knowledgeSearchService.search("PT 怎么处理", 5);

        assertThat(ftsSearchService.lastQuestion).contains("payment timeout retry policy");
        assertThat(retrievalAuditService.lastRetrievalQuestion).contains("payment timeout retry policy");
        assertThat(retrievalAuditService.lastStrategyTag).contains("rewrite=on");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getConceptId()).isEqualTo("payment-timeout");
    }

    /**
     * 固定改写规则仓储。
     *
     * @author xiexu
     */
    private static class FixedQueryRewriteRuleJdbcRepository extends QueryRewriteRuleJdbcRepository {

        private final List<QueryRewriteRuleRecord> rules;

        /**
         * 创建固定改写规则仓储。
         *
         * @param rules 规则列表
         */
        private FixedQueryRewriteRuleJdbcRepository(List<QueryRewriteRuleRecord> rules) {
            super(null);
            this.rules = rules;
        }

        /**
         * 返回固定规则。
         *
         * @return 固定规则
         */
        @Override
        public List<QueryRewriteRuleRecord> findActiveRules() {
            return rules;
        }
    }

    /**
     * 固定改写审计仓储。
     *
     * @author xiexu
     */
    private static class FixedQueryRewriteAuditJdbcRepository extends QueryRewriteAuditJdbcRepository {

        /**
         * 创建固定改写审计仓储。
         */
        private FixedQueryRewriteAuditJdbcRepository() {
            super(null);
        }

        /**
         * 返回固定审计引用。
         *
         * @param queryId 查询标识
         * @param queryRewriteResult 改写结果
         * @return 固定审计引用
         */
        @Override
        public String save(String queryId, QueryRewriteResult queryRewriteResult) {
            return "query_rewrite_audits:" + queryId;
        }
    }

    /**
     * 固定查询改写服务。
     *
     * @author xiexu
     */
    private static class FixedRewriteService extends QueryRewriteService {

        /**
         * 返回固定改写结果。
         *
         * @param queryId 查询标识
         * @param question 查询问题
         * @return 固定改写结果
         */
        @Override
        public QueryRewriteResult rewrite(String queryId, String question) {
            return new QueryRewriteResult(
                    question,
                    question + " payment timeout retry policy",
                    List.of("payment-timeout-alias"),
                    true,
                    null
            );
        }
    }

    /**
     * 捕获问题的 FTS 检索服务。
     *
     * @author xiexu
     */
    private static class CapturingFtsSearchService extends FtsSearchService {

        private String lastQuestion;

        /**
         * 创建捕获问题的 FTS 检索服务。
         */
        private CapturingFtsSearchService() {
            super(null);
        }

        /**
         * 记录查询问题并返回固定命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            lastQuestion = question;
            if (question == null || !question.contains("payment timeout retry policy")) {
                return List.of();
            }
            return List.of(new QueryArticleHit(
                    "payment-timeout",
                    "Payment Timeout",
                    "retry policy",
                    "{}",
                    List.of("payment-timeout.md"),
                    1.0D
            ));
        }
    }

    /**
     * 捕获检索审计写入的服务。
     *
     * @author xiexu
     */
    private static class CapturingRetrievalAuditService extends RetrievalAuditService {

        private String lastRetrievalQuestion;

        private String lastStrategyTag;

        /**
         * 创建捕获检索审计服务。
         */
        private CapturingRetrievalAuditService() {
            super(null);
        }

        /**
         * 记录最近一次审计写入，并返回固定引用。
         *
         * @param retrievalQueryContext 检索上下文
         * @param channelHits 通道命中
         * @param fusedHits 融合命中
         * @return 固定引用
         */
        @Override
        public String persist(
                RetrievalQueryContext retrievalQueryContext,
                Map<String, List<QueryArticleHit>> channelHits,
                List<QueryArticleHit> fusedHits
        ) {
            lastRetrievalQuestion = retrievalQueryContext == null ? "" : retrievalQueryContext.getRetrievalQuestion();
            RetrievalStrategy retrievalStrategy = retrievalQueryContext == null
                    ? null
                    : retrievalQueryContext.getRetrievalStrategy();
            QueryRewriteResult queryRewriteResult = retrievalQueryContext == null
                    ? null
                    : retrievalQueryContext.getQueryRewriteResult();
            lastStrategyTag = retrievalStrategy == null
                    ? ""
                    : "rewrite=" + (queryRewriteResult != null && queryRewriteResult.isRewriteApplied() ? "on" : "off");
            return "query_retrieval_runs:1";
        }
    }
}
