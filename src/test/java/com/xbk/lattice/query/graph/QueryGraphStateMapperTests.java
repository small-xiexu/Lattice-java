package com.xbk.lattice.query.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryGraphStateMapper 测试
 *
 * 职责：验证 Query Graph 的 queryId 主键契约与旧 key 兼容读取
 *
 * @author xiexu
 */
class QueryGraphStateMapperTests {

    private final QueryGraphStateMapper queryGraphStateMapper = new QueryGraphStateMapper();

    /**
     * 验证 mapper 仍可读取旧的 requestId key。
     */
    @Test
    void shouldReadLegacyRequestIdAsQueryId() {
        QueryGraphState queryGraphState = queryGraphStateMapper.fromMap(Map.of(
                QueryGraphStateKeys.LEGACY_REQUEST_ID, "legacy-request-id",
                QueryGraphStateKeys.QUESTION, "payment timeout"
        ));

        assertThat(queryGraphState.getQueryId()).isEqualTo("legacy-request-id");
    }

    /**
     * 验证 mapper 输出只写入统一后的 queryId key。
     */
    @Test
    void shouldWriteUnifiedQueryIdKey() {
        QueryGraphState queryGraphState = new QueryGraphState();
        queryGraphState.setQueryId("query-id-001");
        queryGraphState.setQuestion("payment timeout");
        queryGraphState.setLlmScopeType("query_request");
        queryGraphState.setLlmScopeId("query-id-001");
        queryGraphState.setLlmBindingSnapshotRef("query_request:query-id-001:query");
        queryGraphState.setAnswerRoute("query.answer.gpt54");
        queryGraphState.setReviewRoute("query.reviewer.claude");
        queryGraphState.setRewriteRoute("query.rewrite.gpt54");

        Map<String, Object> stateMap = queryGraphStateMapper.toMap(queryGraphState);

        assertThat(stateMap).containsEntry(QueryGraphStateKeys.QUERY_ID, "query-id-001");
        assertThat(stateMap).containsEntry(QueryGraphStateKeys.LLM_SCOPE_TYPE, "query_request");
        assertThat(stateMap).containsEntry(QueryGraphStateKeys.LLM_SCOPE_ID, "query-id-001");
        assertThat(stateMap).containsEntry(QueryGraphStateKeys.LLM_BINDING_SNAPSHOT_REF, "query_request:query-id-001:query");
        assertThat(stateMap).containsEntry(QueryGraphStateKeys.ANSWER_ROUTE, "query.answer.gpt54");
        assertThat(stateMap).containsEntry(QueryGraphStateKeys.REVIEW_ROUTE, "query.reviewer.claude");
        assertThat(stateMap).containsEntry(QueryGraphStateKeys.REWRITE_ROUTE, "query.rewrite.gpt54");
        assertThat(stateMap).doesNotContainKey(QueryGraphStateKeys.LEGACY_REQUEST_ID);
    }
}
