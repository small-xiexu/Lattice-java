package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryFacadeService 向量链路测试
 *
 * 职责：验证向量命中能够接入查询主链并生成回答
 *
 * @author xiexu
 */
class QueryFacadeServiceVectorTests {

    /**
     * 验证只有向量命中时，查询主链仍可生成回答并返回文章证据。
     */
    @Test
    void shouldAnswerQuestionFromVectorHits() {
        QueryArticleHit vectorHit = new QueryArticleHit(
                "refund-status",
                "Refund Status",
                "退款状态流转说明",
                "{\"source\":\"vector\"}",
                List.of("refund/status.md"),
                0.95D
        );

        QueryFacadeService queryFacadeService = new QueryFacadeService(
                new FixedFtsSearchService(List.of()),
                new FixedRefKeySearchService(List.of()),
                new FixedSourceSearchService(List.of()),
                new FixedContributionSearchService(List.of()),
                new FixedVectorSearchService(List.of(vectorHit)),
                new RrfFusionService(),
                new FixedAnswerGenerationService("来自向量召回的答案"),
                new InMemoryQueryCacheStore(),
                new ReviewerAgent(new FixedReviewerGateway(), new ReviewResultParser()),
                new FixedPendingQueryManager()
        );

        QueryResponse queryResponse = queryFacadeService.query("退款状态是什么");

        assertThat(queryResponse.getAnswer()).isEqualTo("来自向量召回的答案");
        assertThat(queryResponse.getArticles()).extracting("conceptId").containsExactly("refund-status");
        assertThat(queryResponse.getSources()).extracting("title").contains("Refund Status");
    }

    /**
     * 固定审查网关替身。
     *
     * @author xiexu
     */
    private static class FixedReviewerGateway implements ReviewerGateway {

        /**
         * 返回固定通过的审查结果。
         *
         * @param reviewPrompt 审查提示词
         * @return 审查结果
         */
        @Override
        public String review(String reviewPrompt) {
            return "{\"pass\":true,\"issues\":[]}";
        }
    }

    /**
     * 固定答案生成服务替身。
     *
     * @author xiexu
     */
    private static class FixedAnswerGenerationService extends AnswerGenerationService {

        private final String answer;

        /**
         * 创建固定答案生成服务替身。
         *
         * @param answer 固定答案
         */
        private FixedAnswerGenerationService(String answer) {
            super();
            this.answer = answer;
        }

        /**
         * 返回固定答案。
         *
         * @param question 查询问题
         * @param articleHit 查询命中
         * @return 固定答案
         */
        @Override
        public String generate(String question, QueryArticleHit articleHit) {
            return answer;
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
     * 固定 pending 管理器替身。
     *
     * @author xiexu
     */
    private static class FixedPendingQueryManager implements PendingQueryManager {

        /**
         * 创建待确认查询记录。
         *
         * @param question 问题
         * @param queryResponse 查询结果
         * @return 待确认查询记录
         */
        @Override
        public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
            return new PendingQueryRecord(
                    "query-1",
                    question,
                    queryResponse.getAnswer(),
                    List.of("refund-status"),
                    List.of("refund/status.md"),
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
         * 不支持按标识查询。
         *
         * @param queryId 查询标识
         * @return 待确认查询记录
         */
        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 不支持列出全部待确认查询。
         *
         * @return 待确认查询记录列表
         */
        @Override
        public List<PendingQueryRecord> listPendingQueries() {
            throw new UnsupportedOperationException();
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
         * @param hits 预置命中
         */
        private FixedFtsSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回预置命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 命中列表
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
         * @param hits 预置命中
         */
        private FixedRefKeySearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回预置命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 命中列表
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
         * @param hits 预置命中
         */
        private FixedSourceSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回预置命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 命中列表
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }

    /**
     * 固定 contribution 检索服务替身。
     *
     * @author xiexu
     */
    private static class FixedContributionSearchService extends ContributionSearchService {

        private final List<QueryArticleHit> hits;

        /**
         * 创建固定 contribution 检索服务替身。
         *
         * @param hits 预置命中
         */
        private FixedContributionSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        /**
         * 返回预置命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 命中列表
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
         * @param hits 预置命中
         */
        private FixedVectorSearchService(List<QueryArticleHit> hits) {
            super();
            this.hits = hits;
        }

        /**
         * 返回预置命中。
         *
         * @param question 查询问题
         * @param limit 返回数量
         * @return 命中列表
         */
        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }
}
