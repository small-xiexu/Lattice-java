package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.governance.ArticleCorrectionResult;
import com.xbk.lattice.governance.ArticleCorrectionService;
import com.xbk.lattice.governance.PropagationService;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LatticeMcpTools lattice_correct 测试
 *
 * 职责：验证 MCP 纠错工具会返回修正预览，并标记 downstream 待传播文章
 *
 * @author xiexu
 */
class LatticeMcpToolsCorrectTests {

    /**
     * 验证 lattice_correct 会调用真实纠错服务，并返回新的纠错结果 JSON。
     */
    @Test
    void correctKnowledgeShouldReturnCorrectionPreviewJsonAndMarkDownstream() {
        RecordingPropagationService propagationService = new RecordingPropagationService();
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
                new FixedArticleCorrectionService(new ArticleCorrectionResult(
                        "payment-config",
                        "# Payment Config\n\n已按源文件修正为 retry=5。",
                        List.of("payment-timeout", "refund-manual-review"),
                        true
                )),
                propagationService
        );

        String result = tools.correctKnowledge("payment-config", "重试次数应为 5");

        assertThat(result).contains("\"conceptId\":\"payment-config\"");
        assertThat(result).contains("\"revisedContentPreview\":\"# Payment Config\\n\\n已按源文件修正为 retry=5。\"");
        assertThat(result).contains("\"downstreamCount\":2");
        assertThat(result).contains("\"downstreamIds\":[\"payment-timeout\",\"refund-manual-review\"]");
        assertThat(result).contains("\"evidenceSupported\":true");
        assertThat(result).contains("lattice_propagate");
        assertThat(propagationService.getMarkedRootConceptId()).isEqualTo("payment-config");
        assertThat(propagationService.getMarkedCorrectionSummary()).isEqualTo("重试次数应为 5");
        assertThat(propagationService.getMarkedDownstreamIds())
                .containsExactly("payment-timeout", "refund-manual-review");
    }

    /**
     * 固定返回结果的纠错服务替身。
     *
     * 职责：为 MCP 工具测试提供稳定的纠错返回值
     *
     * @author xiexu
     */
    private static class FixedArticleCorrectionService extends ArticleCorrectionService {

        private final ArticleCorrectionResult result;

        private FixedArticleCorrectionService(ArticleCorrectionResult result) {
            super(null, null, null, null, null);
            this.result = result;
        }

        @Override
        public ArticleCorrectionResult correct(String conceptId, String correctionSummary) {
            return result;
        }
    }

    /**
     * 记录标记请求的传播服务替身。
     *
     * 职责：验证 lattice_correct 会把 downstream 标记请求传给传播服务
     *
     * @author xiexu
     */
    private static class RecordingPropagationService extends PropagationService {

        private String markedRootConceptId;

        private String markedCorrectionSummary;

        private List<String> markedDownstreamIds = List.of();

        private RecordingPropagationService() {
            super(null);
        }

        @Override
        public void markDownstream(String rootConceptId, String correctionSummary, List<String> downstreamIds) {
            this.markedRootConceptId = rootConceptId;
            this.markedCorrectionSummary = correctionSummary;
            this.markedDownstreamIds = downstreamIds;
        }

        private String getMarkedRootConceptId() {
            return markedRootConceptId;
        }

        private String getMarkedCorrectionSummary() {
            return markedCorrectionSummary;
        }

        private List<String> getMarkedDownstreamIds() {
            return markedDownstreamIds;
        }
    }

    /**
     * 所有方法均抛出 UnsupportedOperationException 的基础替身。
     *
     * @author xiexu
     */
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
