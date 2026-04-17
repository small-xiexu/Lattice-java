package com.xbk.lattice.compiler.graph;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.xbk.lattice.compiler.config.CompileGraphProperties;
import com.xbk.lattice.infra.persistence.CompileJobStepJdbcRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CompileGraphLifecycleListener 测试
 *
 * 职责：验证步骤日志失败时的 warn/fail 策略
 *
 * @author xiexu
 */
class CompileGraphLifecycleListenerTests {

    /**
     * 验证 warn 模式会吞掉步骤日志异常。
     */
    @Test
    void shouldSwallowStepLogFailureWhenFailureModeIsWarn() {
        CompileGraphProperties compileGraphProperties = new CompileGraphProperties();
        compileGraphProperties.setPersistStepLog(true);
        compileGraphProperties.setStepLogFailureMode("warn");
        CompileGraphLifecycleListener listener = new CompileGraphLifecycleListener(
                new CompileGraphStateMapper(),
                new FailingGraphStepLogger(),
                compileGraphProperties
        );

        assertThatCode(() -> listener.before(
                "review_articles",
                buildState("job-warn"),
                RunnableConfig.builder().build(),
                100L
        )).doesNotThrowAnyException();
    }

    /**
     * 验证 fail 模式会把步骤日志异常继续向上抛出。
     */
    @Test
    void shouldPropagateStepLogFailureWhenFailureModeIsFail() {
        CompileGraphProperties compileGraphProperties = new CompileGraphProperties();
        compileGraphProperties.setPersistStepLog(true);
        compileGraphProperties.setStepLogFailureMode("fail");
        CompileGraphLifecycleListener listener = new CompileGraphLifecycleListener(
                new CompileGraphStateMapper(),
                new FailingGraphStepLogger(),
                compileGraphProperties
        );

        assertThatThrownBy(() -> listener.before(
                "review_articles",
                buildState("job-fail"),
                RunnableConfig.builder().build(),
                100L
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("step log failed");
    }

    private Map<String, Object> buildState(String jobId) {
        CompileGraphStateMapper compileGraphStateMapper = new CompileGraphStateMapper();
        CompileGraphState state = new CompileGraphState();
        state.setJobId(jobId);
        state.setReviewRoute("rule-based");
        Map<String, Object> stateMap = compileGraphStateMapper.toMap(state);
        stateMap.put(GraphLifecycleListener.EXECUTION_ID_KEY, "graph-execution-" + jobId);
        return stateMap;
    }

    /**
     * 固定抛错的步骤日志器。
     *
     * @author xiexu
     */
    private static class FailingGraphStepLogger extends GraphStepLogger {

        private FailingGraphStepLogger() {
            super(new NoopCompileJobStepJdbcRepository());
        }

        @Override
        public StepExecutionHandle beforeStep(String nodeId, CompileGraphState state, Long curTime) {
            throw new IllegalStateException("step log failed");
        }
    }

    /**
     * 空操作步骤仓储。
     *
     * @author xiexu
     */
    private static class NoopCompileJobStepJdbcRepository extends CompileJobStepJdbcRepository {

        private NoopCompileJobStepJdbcRepository() {
            super(null);
        }
    }
}
