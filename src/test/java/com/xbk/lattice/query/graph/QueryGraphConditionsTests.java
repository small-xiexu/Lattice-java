package com.xbk.lattice.query.graph;

import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.service.QueryReviewProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query Graph 条件路由测试
 *
 * 职责：验证审查、citation 检查后的图路由选择
 *
 * @author xiexu
 */
class QueryGraphConditionsTests {

    /**
     * 验证高覆盖且已有可用引用时，不因少量 demoted citation 触发整题修复。
     */
    @Test
    void shouldPersistHighCoverageAnswerWhenOnlyMinorCitationDemotionExists() {
        QueryGraphConditions queryGraphConditions = new QueryGraphConditions(new QueryReviewProperties());
        QueryGraphState queryGraphState = new QueryGraphState();
        queryGraphState.setCitationRepairAttemptCount(0);
        CitationCheckReport citationCheckReport = new CitationCheckReport(
                "answer",
                List.of(),
                List.of(),
                4,
                1,
                0,
                false,
                0.8D,
                0,
                0,
                0,
                0
        );

        String route = queryGraphConditions.routeAfterCitationCheck(queryGraphState, citationCheckReport);

        assertThat(route).isEqualTo("persist_response");
    }

    /**
     * 验证低覆盖或完全没有可用引用时，仍进入 citation repair。
     */
    @Test
    void shouldRepairLowCoverageCitationReport() {
        QueryGraphConditions queryGraphConditions = new QueryGraphConditions(new QueryReviewProperties());
        QueryGraphState queryGraphState = new QueryGraphState();
        queryGraphState.setCitationRepairAttemptCount(0);
        CitationCheckReport citationCheckReport = new CitationCheckReport(
                "answer",
                List.of(),
                List.of(),
                0,
                1,
                0,
                false,
                0.0D,
                1,
                1,
                0,
                0
        );

        String route = queryGraphConditions.routeAfterCitationCheck(queryGraphState, citationCheckReport);

        assertThat(route).isEqualTo("citation_repair");
    }

    /**
     * 验证首次无引用时先进入修复节点，而不是直接持久化或立即降级。
     */
    @Test
    void shouldRepairNoCitationReportBeforePersisting() {
        QueryGraphConditions queryGraphConditions = new QueryGraphConditions(new QueryReviewProperties());
        QueryGraphState queryGraphState = new QueryGraphState();
        queryGraphState.setCitationRepairAttemptCount(0);
        CitationCheckReport citationCheckReport = new CitationCheckReport(
                "answer",
                List.of(),
                List.of(),
                0,
                0,
                0,
                true,
                0.0D,
                1,
                0,
                0,
                0
        );

        String route = queryGraphConditions.routeAfterCitationCheck(queryGraphState, citationCheckReport);

        assertThat(route).isEqualTo("citation_repair");
    }
}
