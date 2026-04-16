package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.governance.RollbackResult;
import com.xbk.lattice.governance.SnapshotService;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LatticeMcpTools rollback 测试
 *
 * 职责：验证 MCP rollback 工具会返回恢复结果 JSON
 *
 * @author xiexu
 */
class LatticeMcpToolsRollbackTests {

    /**
     * 验证 lattice_rollback 会返回恢复概念、快照和时间。
     */
    @Test
    void rollbackShouldReturnRollbackJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedSnapshotService(new RollbackResult(
                        "payment-timeout",
                        7L,
                        OffsetDateTime.parse("2026-04-16T10:35:00+08:00")
                )),
                null
        );

        String result = tools.rollback("payment-timeout", 7L);

        assertThat(result).contains("\"conceptId\":\"payment-timeout\"");
        assertThat(result).contains("\"restoredSnapshotId\":7");
        assertThat(result).contains("\"restoredAt\":\"2026-04-16T10:35:00+08:00\"");
    }

    private static class FixedSnapshotService extends SnapshotService {

        private final RollbackResult rollbackResult;

        private FixedSnapshotService(RollbackResult rollbackResult) {
            super(null);
            this.rollbackResult = rollbackResult;
        }

        @Override
        public RollbackResult rollback(String conceptId, long snapshotId) {
            return rollbackResult;
        }
    }

    private static class UnsupportedPendingQueryManager implements PendingQueryManager {

        @Override
        public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PendingQueryRecord correct(String queryId, String correction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void confirm(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void discard(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PendingQueryRecord> listPendingQueries() {
            throw new UnsupportedOperationException();
        }
    }
}
