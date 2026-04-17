package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentModelRouter 快照测试
 *
 * 职责：验证 Agent 路由优先读取运行时快照，并在审查关闭时回退为 rule-based
 *
 * @author xiexu
 */
class AgentModelRouterSnapshotTests {

    /**
     * 验证存在快照时优先返回快照路由标签。
     */
    @Test
    void shouldPreferSnapshotRouteWhenSnapshotExists() {
        StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setReviewEnabled(true);
        snapshotService.route = new LlmRouteResolution(
                "compile_job",
                "job-1",
                "compile",
                "writer",
                Long.valueOf(1L),
                Long.valueOf(2L),
                Integer.valueOf(1),
                "compile.writer.codex",
                "openai",
                "http://localhost",
                "sk-test",
                "gpt-5.4",
                new BigDecimal("0.2"),
                Integer.valueOf(4096),
                Integer.valueOf(300),
                "{}",
                new BigDecimal("0.002500"),
                new BigDecimal("0.010000"),
                true
        );
        AgentModelRouter agentModelRouter = new AgentModelRouter(snapshotService, llmProperties);

        String route = agentModelRouter.routeFor("job-1", "compile", "writer");

        assertThat(route).isEqualTo("compile.writer.codex");
    }

    /**
     * 验证审查关闭时直接回退为 rule-based。
     */
    @Test
    void shouldReturnRuleBasedWhenReviewIsDisabled() {
        StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setReviewEnabled(false);
        AgentModelRouter agentModelRouter = new AgentModelRouter(snapshotService, llmProperties);

        String route = agentModelRouter.routeFor("job-1", "compile", "reviewer");

        assertThat(route).isEqualTo("rule-based");
    }

    /**
     * 运行时快照服务替身。
     *
     * 职责：为路由器测试返回固定快照路由
     *
     * @author xiexu
     */
    private static class StubExecutionLlmSnapshotService extends ExecutionLlmSnapshotService {

        private LlmRouteResolution route;

        private StubExecutionLlmSnapshotService() {
            super(
                    properties(),
                    null,
                    null,
                    null,
                    null,
                    new com.xbk.lattice.llm.service.LlmSecretCryptoService(properties())
            );
        }

        @Override
        public Optional<LlmRouteResolution> resolveRoute(
                String scopeType,
                String scopeId,
                String scene,
                String agentRole
        ) {
            return Optional.ofNullable(route);
        }

        private static LlmProperties properties() {
            LlmProperties llmProperties = new LlmProperties();
            llmProperties.setSecretEncryptionKey("test-phase8-key-0123456789abcdef");
            return llmProperties;
        }
    }
}
