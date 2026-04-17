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

        Map<String, Object> stateMap = queryGraphStateMapper.toMap(queryGraphState);

        assertThat(stateMap).containsEntry(QueryGraphStateKeys.QUERY_ID, "query-id-001");
        assertThat(stateMap).doesNotContainKey(QueryGraphStateKeys.LEGACY_REQUEST_ID);
    }
}
