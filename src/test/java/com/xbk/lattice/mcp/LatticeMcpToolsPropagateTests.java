package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.governance.PropagateExecutionService;
import com.xbk.lattice.governance.PropagationExecutionResult;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LatticeMcpTools lattice_propagate 测试
 *
 * 职责：验证 MCP 传播工具会返回真正的执行统计
 *
 * @author xiexu
 */
class LatticeMcpToolsPropagateTests {

    /**
     * 验证 lattice_propagate 会返回 processed/updated/skipped 摘要。
     */
    @Test
    void propagateShouldReturnExecutionJson() {
        LatticeMcpTools tools = new LatticeMcpTools(null, new UnsupportedPendingQueryManager());
        tools.setPropagateExecutionService(new FixedPropagateExecutionService(
                new PropagationExecutionResult(2, 1, 1)
        ));

        String result = tools.propagate("payment-config");

        assertThat(result).contains("\"rootConceptId\":\"payment-config\"");
        assertThat(result).contains("\"processed\":2");
        assertThat(result).contains("\"updated\":1");
        assertThat(result).contains("\"skipped\":1");
    }

    private static class FixedPropagateExecutionService extends PropagateExecutionService {

        private final PropagationExecutionResult result;

        private FixedPropagateExecutionService(PropagationExecutionResult result) {
            super(null, null, null);
            this.result = result;
        }

        @Override
        public PropagationExecutionResult executePropagation(String rootConceptId) {
            return result;
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
