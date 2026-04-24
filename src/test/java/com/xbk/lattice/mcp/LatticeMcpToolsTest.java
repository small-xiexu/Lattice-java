package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import com.xbk.lattice.query.service.QueryFacadeService;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.KnowledgeSourcePage;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.service.SourceService;
import com.xbk.lattice.source.service.SourceSyncWorkflowService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * LatticeMcpTools 单元测试
 *
 * 职责：验证查询与反馈 MCP 工具的正路与异常路行为
 *
 * @author xiexu
 */
class LatticeMcpToolsTest {

    /**
     * 验证 B7 首批 MCP 工具面已经补齐 search/get/status/lint/quality/compile。
     */
    @Test
    void shouldExposeB7CoreMcpTools() {
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("search", String.class, int.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("get", String.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("status"))
                .doesNotThrowAnyException();
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("lint"))
                .doesNotThrowAnyException();
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("quality"))
                .doesNotThrowAnyException();
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("compile", String.class, boolean.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("sourceList", int.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> LatticeMcpTools.class.getDeclaredMethod("sourceSync", long.class))
                .doesNotThrowAnyException();
    }

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
     * 验证 lattice_query_pending 会列出全部 pending 查询。
     */
    @Test
    void queryPendingShouldReturnAllPendingQueries() {
        PendingQueryRecord firstRecord = buildRecord("query-id-001", "payment timeout retry=3", "retry=3", "PASSED");
        PendingQueryRecord secondRecord = buildRecord("query-id-002", "refund manual review", "# 修订答案", "TIMEOUT_FALLBACK");
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new FixedListPendingQueryManager(List.of(firstRecord, secondRecord))
        );

        String result = tools.queryPending();

        assertThat(result).contains("\"count\":2");
        assertThat(result).contains("\"queryId\":\"query-id-001\"");
        assertThat(result).contains("\"queryId\":\"query-id-002\"");
        assertThat(result).contains("\"reviewStatus\":\"TIMEOUT_FALLBACK\"");
    }

    /**
     * 验证 lattice_query_pending 在没有 pending 数据时返回空列表。
     */
    @Test
    void queryPendingShouldReturnEmptyListWhenNoPendingRecordsExist() {
        LatticeMcpTools tools = new LatticeMcpTools(null, new FixedListPendingQueryManager(List.of()));

        String result = tools.queryPending();

        assertThat(result).contains("\"count\":0");
        assertThat(result).contains("\"items\":[]");
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
     * 验证 lattice_query_discard 返回 discarded 状态 JSON。
     */
    @Test
    void discardShouldReturnDiscardedStatus() {
        LatticeMcpTools tools = new LatticeMcpTools(null, new UnsupportedPendingQueryManager());

        String result = tools.discard("query-id-001");

        assertThat(result).contains("\"queryId\":\"query-id-001\"");
        assertThat(result).contains("\"status\":\"discarded\"");
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

    /**
     * 验证 lattice_source_list 返回资料源摘要。
     */
    @Test
    void sourceListShouldReturnSourceSummaries() {
        KnowledgeSource source = new KnowledgeSource(
                11L,
                "payments-docs",
                "Payments Docs",
                "GIT",
                "DOCUMENT",
                "ACTIVE",
                "NORMAL",
                "AUTO",
                "{}",
                "{}",
                null,
                null,
                "SUCCEEDED",
                OffsetDateTime.parse("2026-04-19T18:00:00+08:00"),
                null,
                null
        );
        LatticeMcpTools tools = new LatticeMcpTools(null, new UnsupportedPendingQueryManager());
        tools.setSourceService(new FixedSourceService(new KnowledgeSourcePage(1, 10, 1L, List.of(source))));

        String result = tools.sourceList(10);

        assertThat(result).contains("\"count\":1");
        assertThat(result).contains("\"sourceCode\":\"payments-docs\"");
        assertThat(result).contains("\"sourceType\":\"GIT\"");
        assertThat(result).contains("\"lastSyncStatus\":\"SUCCEEDED\"");
    }

    /**
     * 验证 lattice_source_sync 返回同步运行详情。
     *
     * @throws Exception 测试异常
     */
    @Test
    void sourceSyncShouldReturnRunDetailJson() throws Exception {
        SourceSyncRunDetail detail = new SourceSyncRunDetail(
                21L,
                11L,
                "Payments Docs",
                "GIT",
                "COMPILE_QUEUED",
                "MANUAL",
                "EXISTING_SOURCE_UPDATE",
                "UPDATE",
                11L,
                "job-1",
                "QUEUED",
                "QUEUED",
                null,
                0,
                0,
                "等待执行",
                null,
                null,
                null,
                "hash-1",
                "ok",
                null,
                List.of("README.md"),
                "{}",
                "2026-04-19T18:10:00+08:00",
                "2026-04-19T18:10:01+08:00",
                null,
                null
        );
        LatticeMcpTools tools = new LatticeMcpTools(null, new UnsupportedPendingQueryManager());
        tools.setSourceSyncWorkflowService(new FixedSourceSyncWorkflowService(detail));

        String result = tools.sourceSync(11L);

        assertThat(result).contains("\"runId\":21");
        assertThat(result).contains("\"sourceId\":11");
        assertThat(result).contains("\"status\":\"COMPILE_QUEUED\"");
        assertThat(result).contains("\"compileJobStatus\":\"QUEUED\"");
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
            super(null, null, null, null, null);
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
     * 固定返回预置列表的 pending 查询替身。
     *
     * @author xiexu
     */
    private static class FixedListPendingQueryManager extends UnsupportedPendingQueryManager {

        private final List<PendingQueryRecord> records;

        /**
         * 创建固定返回值的 pending 列表替身。
         *
         * @param records 预置记录列表
         */
        private FixedListPendingQueryManager(List<PendingQueryRecord> records) {
            this.records = records;
        }

        /**
         * 返回预置列表。
         *
         * @return 预置记录列表
         */
        @Override
        public List<PendingQueryRecord> listPendingQueries() {
            return records;
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
            // 无操作：lattice_query_discard 只需验证返回值格式
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
     * 固定返回资料源分页结果的替身。
     *
     * @author xiexu
     */
    private static class FixedSourceService extends SourceService {

        private final KnowledgeSourcePage knowledgeSourcePage;

        /**
         * 创建固定返回值的资料源服务替身。
         *
         * @param knowledgeSourcePage 预置分页结果
         */
        private FixedSourceService(KnowledgeSourcePage knowledgeSourcePage) {
            super(null, null);
            this.knowledgeSourcePage = knowledgeSourcePage;
        }

        /**
         * 返回预置的分页结果。
         *
         * @param keyword 关键词
         * @param status 状态
         * @param sourceType 类型
         * @param page 页码
         * @param size 大小
         * @return 预置分页结果
         */
        @Override
        public KnowledgeSourcePage listSources(String keyword, String status, String sourceType, int page, int size) {
            return knowledgeSourcePage;
        }
    }

    /**
     * 固定返回同步运行详情的替身。
     *
     * @author xiexu
     */
    private static class FixedSourceSyncWorkflowService extends SourceSyncWorkflowService {

        private final SourceSyncRunDetail detail;

        /**
         * 创建固定返回值的同步工作流替身。
         *
         * @param detail 预置同步结果
         */
        private FixedSourceSyncWorkflowService(SourceSyncRunDetail detail) {
            super(null, null, null);
            this.detail = detail;
        }

        /**
         * 返回预置同步运行详情。
         *
         * @param sourceId 资料源主键
         * @return 预置同步运行详情
         */
        @Override
        public SourceSyncRunDetail syncSource(Long sourceId) {
            return detail;
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
