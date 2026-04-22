package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryFacadeService 缓存测试
 *
 * 职责：验证相同问题命中缓存后不会重复执行查询主链路
 *
 * @author xiexu
 */
class QueryFacadeServiceCacheTests {

    /**
     * 验证相同问题第二次查询时会复用第一次的结果。
     */
    @Test
    void shouldReuseCachedQueryResponseForSameQuestion() {
        QueryArticleHit articleHit = new QueryArticleHit(
                "payment-timeout",
                "Payment Timeout",
                "retry=3\ninterval=30s",
                "{\"description\":\"Handles payment timeout recovery\"}",
                List.of("payment/analyze.json"),
                10.0
        );
        CountingFtsSearchService ftsSearchService = new CountingFtsSearchService(List.of(articleHit));
        CountingRefKeySearchService refKeySearchService = new CountingRefKeySearchService(List.of());
        SequencedAnswerGenerationService answerGenerationService = new SequencedAnswerGenerationService();
        QueryFacadeService queryFacadeService = new QueryFacadeService(
                ftsSearchService,
                refKeySearchService,
                new RrfFusionService(),
                answerGenerationService,
                new FakeQueryCacheStore(),
                new ReviewerAgent(new StaticReviewerGateway(), new ReviewResultParser()),
                new CountingPendingQueryManager()
        );

        QueryResponse firstResponse = queryFacadeService.query("payment timeout retry=3");
        QueryResponse secondResponse = queryFacadeService.query("payment timeout retry=3");

        assertThat(firstResponse.getAnswer()).isEqualTo("answer-1");
        assertThat(secondResponse.getAnswer()).isEqualTo("answer-1");
        assertThat(ftsSearchService.getInvocationCount()).isEqualTo(1);
        assertThat(refKeySearchService.getInvocationCount()).isEqualTo(1);
        assertThat(answerGenerationService.getInvocationCount()).isEqualTo(1);
    }

    /**
     * 固定返回通过结果的审查网关替身。
     *
     * @author xiexu
     */
    private static class StaticReviewerGateway implements ReviewerGateway {

        /**
         * 执行审查。
         *
         * @param reviewPrompt 审查提示词
         * @return 审查原始输出
         */
        @Override
        public String review(String reviewPrompt) {
            return "{\"pass\":true,\"issues\":[]}";
        }
    }

    /**
     * 仅供测试使用的查询缓存替身。
     *
     * 职责：在单测中模拟缓存命中与回填
     *
     * @author xiexu
     */
    private static class FakeQueryCacheStore implements QueryCacheStore {

        private QueryResponse cachedResponse;

        /**
         * 读取缓存结果。
         *
         * @param cacheKey 缓存键
         * @return 查询结果
         */
        @Override
        public java.util.Optional<QueryResponse> get(String cacheKey) {
            return java.util.Optional.ofNullable(cachedResponse);
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
    }

    /**
     * 仅返回固定 queryId 的 PendingQuery 管理器替身。
     *
     * @author xiexu
     */
    private static class CountingPendingQueryManager implements PendingQueryManager {

        private int index;

        /**
         * 创建待确认查询。
         *
         * @param question 问题
         * @param queryResponse 查询结果
         * @return 待确认查询记录
         */
        @Override
        public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
            index++;
            return new PendingQueryRecord(
                    "query-" + index,
                    question,
                    queryResponse.getAnswer(),
                    List.of("payment-timeout"),
                    List.of("payment/analyze.json"),
                    "[]",
                    "PASSED",
                    OffsetDateTime.now(),
                    OffsetDateTime.now().plusDays(7)
            );
        }

        /**
         * 不支持纠错。
         *
         * @param queryId 查询标识
         * @param correction 纠正内容
         * @return 待确认查询记录
         */
        @Override
        public PendingQueryRecord correct(String queryId, String correction) {
            throw new UnsupportedOperationException();
        }

        /**
         * 不支持确认。
         *
         * @param queryId 查询标识
         */
        @Override
        public void confirm(String queryId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 不支持丢弃。
         *
         * @param queryId 查询标识
         */
        @Override
        public void discard(String queryId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 不支持按 queryId 查询。
         *
         * @param queryId 查询标识
         * @return 待确认查询记录
         */
        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 不支持列出全部 pending。
         *
         * @return 待确认查询记录列表
         */
        @Override
        public List<PendingQueryRecord> listPendingQueries() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 统计 FTS 调用次数的测试替身。
     *
     * @author xiexu
     */
    private static class CountingFtsSearchService extends FtsSearchService {

        private final List<QueryArticleHit> hits;

        private int invocationCount;

        /**
         * 创建 FTS 测试替身。
         *
         * @param hits 预置命中
         */
        private CountingFtsSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回预置命中，并统计调用次数。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 预置命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            invocationCount++;
            return hits;
        }

        /**
         * 获取调用次数。
         *
         * @return 调用次数
         */
        private int getInvocationCount() {
            return invocationCount;
        }
    }

    /**
     * 统计引用词检索调用次数的测试替身。
     *
     * @author xiexu
     */
    private static class CountingRefKeySearchService extends RefKeySearchService {

        private final List<QueryArticleHit> hits;

        private int invocationCount;

        /**
         * 创建引用词检索测试替身。
         *
         * @param hits 预置命中
         */
        private CountingRefKeySearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回预置命中，并统计调用次数。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 预置命中
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            invocationCount++;
            return hits;
        }

        /**
         * 获取调用次数。
         *
         * @return 调用次数
         */
        private int getInvocationCount() {
            return invocationCount;
        }
    }

    /**
     * 用递增答案标识重复生成的测试替身。
     *
     * @author xiexu
     */
    private static class SequencedAnswerGenerationService extends AnswerGenerationService {

        private int invocationCount;

        /**
         * 生成递增答案，并统计调用次数。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @param question 查询问题
         * @param queryArticleHits 融合命中
         * @return 递增答案载荷
         */
        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            invocationCount++;
            return new QueryAnswerPayload(
                    "answer-" + invocationCount,
                    AnswerOutcome.SUCCESS,
                    GenerationMode.LLM,
                    ModelExecutionStatus.SUCCESS,
                    true
            );
        }

        /**
         * 获取调用次数。
         *
         * @return 调用次数
         */
        private int getInvocationCount() {
            return invocationCount;
        }
    }
}
