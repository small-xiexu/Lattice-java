package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.governance.LintFixResult;
import com.xbk.lattice.governance.LintFixService;
import com.xbk.lattice.governance.LintIssue;
import com.xbk.lattice.governance.LintReport;
import com.xbk.lattice.governance.LintService;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LatticeMcpTools lint-fix 测试
 *
 * 职责：验证 MCP lint fix 工具会按 targetIds 触发自动修复
 *
 * @author xiexu
 */
class LatticeMcpToolsLintFixTests {

    /**
     * 验证 lattice_lint_fix 支持只修指定 targetIds。
     */
    @Test
    void lintFixShouldFixSpecifiedTargetIds() {
        RecordingLintFixService lintFixService = new RecordingLintFixService(new LintFixResult(1, 1, List.of("refund-manual-review")));
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                new FixedLintService(new LintReport(
                        List.of("gaps"),
                        List.of(
                                new LintIssue("gaps", "payment-timeout", "缺少 summary", true, "补齐摘要"),
                                new LintIssue("gaps", "refund-manual-review", "缺少 summary", true, "补齐摘要")
                        )
                )),
                null,
                null
        );
        tools.setLintFixService(lintFixService);

        String result = tools.lintFix("payment-timeout");

        assertThat(result).contains("\"fixed\":1");
        assertThat(result).contains("\"skipped\":1");
        assertThat(result).contains("\"errors\":[\"refund-manual-review\"]");
        assertThat(lintFixService.getReceivedTargetIds()).containsExactly("payment-timeout");
    }

    private static class FixedLintService extends LintService {

        private final LintReport report;

        private FixedLintService(LintReport report) {
            super(null);
            this.report = report;
        }

        @Override
        public LintReport lint() {
            return report;
        }
    }

    private static class RecordingLintFixService extends LintFixService {

        private final LintFixResult result;

        private List<String> receivedTargetIds = List.of();

        private RecordingLintFixService(LintFixResult result) {
            super(null, null, null);
            this.result = result;
        }

        @Override
        public LintFixResult fix(LintReport report, List<String> issueTargetIds) {
            this.receivedTargetIds = issueTargetIds == null ? List.of() : issueTargetIds;
            return result;
        }

        private List<String> getReceivedTargetIds() {
            return receivedTargetIds;
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
