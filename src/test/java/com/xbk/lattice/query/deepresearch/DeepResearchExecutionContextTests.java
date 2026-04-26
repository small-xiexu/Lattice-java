package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.graph.DeepResearchState;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchStateKeys;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchStateMapper;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchExecutionContext 测试
 *
 * 职责：验证预算与超时控制会按预期收口
 *
 * @author xiexu
 */
class DeepResearchExecutionContextTests {

    /**
     * 验证 LLM 调用预算耗尽后不再继续占用。
     */
    @Test
    void shouldStopAcquiringLlmCallsAfterBudgetIsExhausted() {
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(2, System.currentTimeMillis() + 60_000L);

        assertThat(executionContext.tryAcquireLlmCall()).isTrue();
        assertThat(executionContext.tryAcquireLlmCall()).isTrue();
        assertThat(executionContext.tryAcquireLlmCall()).isFalse();
        assertThat(executionContext.remainingLlmCalls()).isZero();
    }

    /**
     * 验证超过截止时间后会进入超时状态。
     */
    @Test
    void shouldReportTimedOutWhenDeadlineHasPassed() {
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(2, System.currentTimeMillis() - 1L);

        assertThat(executionContext.isTimedOut()).isTrue();
        assertThat(executionContext.tryAcquireLlmCall()).isFalse();
    }

    /**
     * 验证 Deep Research 状态只在 Graph Map 中保存轻量工作集引用。
     */
    @Test
    void shouldRoundTripLightweightStateReferences() {
        DeepResearchState state = new DeepResearchState();
        state.setQueryId("dr-q1");
        state.setQuestion("支付路由如何决策");
        state.setPlanRef("dr-q1:plan");
        state.getTaskResultRefs().add("dr-q1:task-results-layer-0-task-1");
        state.setLedgerRef("dr-q1:evidence-ledger");
        state.getLayerSummaryRefs().add("dr-q1:layer-summary-0");
        state.setInternalAnswerDraftRef("dr-q1:answer-1");
        state.setProjectionRef("dr-q1:answer-projection-bundle");
        state.setCitationCheckReportRef("dr-q1:citation-check-report");
        state.setAnswerAuditRef("dr-q1:audit");
        state.setProjectionRetryCount(1);

        DeepResearchStateMapper mapper = new DeepResearchStateMapper();
        Map<String, Object> stateMap = mapper.toMap(state);
        DeepResearchState restoredState = mapper.fromMap(stateMap);

        assertThat(stateMap).containsKeys(
                DeepResearchStateKeys.TASK_RESULT_REFS,
                DeepResearchStateKeys.LEDGER_REF,
                DeepResearchStateKeys.INTERNAL_ANSWER_DRAFT_REF,
                DeepResearchStateKeys.PROJECTION_REF,
                DeepResearchStateKeys.ANSWER_AUDIT_REF
        );
        assertThat(stateMap).doesNotContainKeys("evidenceLedger", "layerSummaries", "answerMarkdown");
        assertThat(restoredState.getTaskResultRefs()).containsExactly("dr-q1:task-results-layer-0-task-1");
        assertThat(restoredState.getLedgerRef()).isEqualTo("dr-q1:evidence-ledger");
        assertThat(restoredState.getProjectionRetryCount()).isEqualTo(1);
    }
}
