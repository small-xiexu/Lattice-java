package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchExecutionContext 测试
 *
 * 职责：验证预算与超时控制会按预期收口
 */
class DeepResearchExecutionContextTests {

    @Test
    void shouldStopAcquiringLlmCallsAfterBudgetIsExhausted() {
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(2, System.currentTimeMillis() + 60_000L);

        assertThat(executionContext.tryAcquireLlmCall()).isTrue();
        assertThat(executionContext.tryAcquireLlmCall()).isTrue();
        assertThat(executionContext.tryAcquireLlmCall()).isFalse();
        assertThat(executionContext.remainingLlmCalls()).isZero();
    }

    @Test
    void shouldReportTimedOutWhenDeadlineHasPassed() {
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(2, System.currentTimeMillis() - 1L);

        assertThat(executionContext.isTimedOut()).isTrue();
        assertThat(executionContext.tryAcquireLlmCall()).isFalse();
    }
}
