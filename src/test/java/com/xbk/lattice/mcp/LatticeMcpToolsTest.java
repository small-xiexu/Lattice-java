package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import com.xbk.lattice.query.service.QueryFacadeService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LatticeMcpTools 单元测试
 *
 * 职责：验证 4 个 MCP 工具的正路与异常路行为
 *
 * @author xiexu
 */
class LatticeMcpToolsTest {

    /**
     * 验证 lattice_query 正路返回包含 answer、queryId、reviewStatus 与 sourceCount 的 JSON。
     */
    @Test
    void queryShouldReturnJsonWithAnswerAndQueryId() {
        QuerySourceResponse source = new QuerySourceResponse("concept-1", "Payment Timeout", List.of("payment/analyze.json"));
        QueryResponse response = new QueryResponse(
                "retry=3",
                List.of(source),
                List.of(new QueryArticleResponse("concept-1", "Payment Timeout")),
                "query-id-001",
                "PASSED"
        );
        LatticeMcpTools tools = new LatticeMcpTools(
                new FixedQueryFacadeService(response),
                new UnsupportedPendingQueryManager()
        );

        String result = tools.query("payment timeout retry=3");

        assertThat(result).contains("\"answer\":\"retry=3\"");
        assertThat(result).contains("\"queryId\":\"query-id-001\"");
        assertThat(result).contains("\"reviewStatus\":\"PASSED\"");
        assertThat(result).contains("\"sourceCount\":1");
    }

    /**
     * 验证 lattice_query_pending 正路按 queryId 返回问题与答案的 JSON。
     */
    @Test
    void queryPendingShouldReturnCurrentAnswerForGivenQueryId() {
        PendingQueryRecord record = buildRecord("query-id-001", "payment timeout retry=3", "retry=3", "PASSED");
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new FixedFindPendingQueryManager(record)
        );

        String result = tools.queryPending("query-id-001");

        assertThat(result).contains("\"queryId\":\"query-id-001\"");
        assertThat(result).contains("\"question\":\"payment timeout retry=3\"");
        assertThat(result).contains("\"answer\":\"retry=3\"");
        assertThat(result).contains("\"reviewStatus\":\"PASSED\"");
    }

    /**
     * 验证 lattice_query_pending 当 queryId 不存在时抛出异常。
     */
    @Test
    void queryPendingShouldPropagateExceptionWhenQueryIdNotFound() {
        LatticeMcpTools tools = new LatticeMcpTools(null, new ThrowingFindPendingQueryManager("nonexistent"));

        assertThatThrownBy(() -> tools.queryPending("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    /**
     * 验证 lattice_query_correct 返回修订后答案并保持 PENDING 状态。
     */
    @Test
    void correctShouldReturnRevisedAnswerWithPendingStatus() {
        String revisedAnswer = "retry=3\n\n用户纠正：interval=30s";
        PendingQueryRecord updated = buildRecord("query-id-001", "payment timeout retry=3", revisedAnswer, "PASSED");
        LatticeMcpTools tools = new LatticeMcpTools(null, new FixedCorrectPendingQueryManager(updated));

        String result = tools.correct("query-id-001", "interval=30s");

        assertThat(result).contains("\"queryId\":\"query-id-001\"");
        assertThat(result).contains("用户纠正：interval=30s");
        assertThat(result).contains("\"status\":\"PENDING\"");
    }

    /**
     * 验证 lattice_query_confirm 返回 confirmed 状态 JSON。
     */
    @Test
    void confirmShouldReturnConfirmedStatus() {
        LatticeMcpTools tools = new LatticeMcpTools(null, new UnsupportedPendingQueryManager());

        String result = tools.confirm("query-id-001");

        assertThat(result).contains("\"queryId\":\"query-id-001\"");
        assertThat(result).contains("\"status\":\"confirmed\"");
    }

    /**
     * 验证 lattice_query 当答案包含双引号时能正确转义为合法 JSON。
     */
    @Test
    void queryShouldEscapeSpecialCharsInAnswer() {
        QueryResponse response = new QueryResponse(
                "配置为 \"retry=3\"",
                List.of(),
                List.of(),
                "query-id-002",
                "PASSED"
        );
        LatticeMcpTools tools = new LatticeMcpTools(
                new FixedQueryFacadeService(response),
                new UnsupportedPendingQueryManager()
        );

        String result = tools.query("test");

        assertThat(result).contains("\\\"retry=3\\\"");
    }

    // -----------------------------------------------------------------------
    // 测试替身
    // -----------------------------------------------------------------------

    /**
     * 固定返回预置响应的查询门面替身。
     *
     * @author xiexu
     */
    private static class FixedQueryFacadeService extends QueryFacadeService {

        private final QueryResponse response;

        /**
         * 创建固定返回值的查询门面替身。
         *
         * @param response 预置响应
         */
        private FixedQueryFacadeService(QueryResponse response) {
            super(null, null, null, null, null, null, null);
            this.response = response;
        }

        /**
         * 返回预置查询响应，忽略 question 参数。
         *
         * @param question 查询问题
         * @return 预置响应
         */
        @Override
        public QueryResponse query(String question) {
            return response;
        }
    }

    /**
     * 固定返回预置记录的 findPendingQuery 替身。
     *
     * @author xiexu
     */
    private static class FixedFindPendingQueryManager extends UnsupportedPendingQueryManager {

        private final PendingQueryRecord record;

        /**
         * 创建固定返回值的 findPendingQuery 替身。
         *
         * @param record 预置记录
         */
        private FixedFindPendingQueryManager(PendingQueryRecord record) {
            this.record = record;
        }

        /**
         * 返回预置记录。
         *
         * @param queryId 查询标识
         * @return 预置记录
         */
        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            return record;
        }
    }

    /**
     * findPendingQuery 抛出异常的替身，模拟 queryId 不存在场景。
     *
     * @author xiexu
     */
    private static class ThrowingFindPendingQueryManager extends UnsupportedPendingQueryManager {

        private final String missingQueryId;

        /**
         * 创建抛出异常的 findPendingQuery 替身。
         *
         * @param missingQueryId 不存在的 queryId
         */
        private ThrowingFindPendingQueryManager(String missingQueryId) {
            this.missingQueryId = missingQueryId;
        }

        /**
         * 抛出异常，模拟 queryId 不存在。
         *
         * @param queryId 查询标识
         * @return 不会返回
         */
        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            throw new IllegalArgumentException("pending query 不存在: " + missingQueryId);
        }
    }

    /**
     * 固定返回预置更新记录的 correct 替身。
     *
     * @author xiexu
     */
    private static class FixedCorrectPendingQueryManager extends UnsupportedPendingQueryManager {

        private final PendingQueryRecord updated;

        /**
         * 创建固定返回更新记录的 correct 替身。
         *
         * @param updated 预置更新记录
         */
        private FixedCorrectPendingQueryManager(PendingQueryRecord updated) {
            this.updated = updated;
        }

        /**
         * 返回预置更新记录。
         *
         * @param queryId 查询标识
         * @param correction 纠正内容
         * @return 预置更新记录
         */
        @Override
        public PendingQueryRecord correct(String queryId, String correction) {
            return updated;
        }
    }

    /**
     * 所有方法均抛出 UnsupportedOperationException 的基础替身，供子类选择性覆盖。
     *
     * @author xiexu
     */
    private static class UnsupportedPendingQueryManager implements PendingQueryManager {

        /**
         * 不支持创建待确认查询。
         *
         * @param question 问题
         * @param queryResponse 查询结果
         * @return 待确认查询记录
         */
        @Override
        public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
            throw new UnsupportedOperationException();
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
         * confirm 默认为无操作，供 lattice_query_confirm 测试使用。
         *
         * @param queryId 查询标识
         */
        @Override
        public void confirm(String queryId) {
            // 无操作：lattice_query_confirm 只需验证返回值格式
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
    }

    /**
     * 构造测试用的待确认查询记录。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param answer 答案
     * @param reviewStatus 审查状态
     * @return 待确认查询记录
     */
    private PendingQueryRecord buildRecord(String queryId, String question, String answer, String reviewStatus) {
        return new PendingQueryRecord(
                queryId,
                question,
                answer,
                List.of("concept-1"),
                List.of("payment/analyze.json"),
                "[]",
                reviewStatus,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(7)
        );
    }
}
