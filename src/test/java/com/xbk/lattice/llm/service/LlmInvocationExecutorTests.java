package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.LlmProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmInvocationExecutor 测试
 *
 * 职责：验证 raw 调用会返回保留路由、prompt cache key 与 token 用量的 invocation envelope
 *
 * @author xiexu
 */
class LlmInvocationExecutorTests {

    private StubOpenAiChatServer stubServer;

    @AfterEach
    void tearDown() {
        if (stubServer != null) {
            stubServer.stop();
        }
    }

    @Test
    void shouldReturnInvocationEnvelopeForOpenAiRoute() throws IOException {
        stubServer = new StubOpenAiChatServer("executor-ok");
        stubServer.start();
        ChatClientRegistry chatClientRegistry = new ChatClientRegistry(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                new AdvisorChainFactory()
        );
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCacheKeyPrefix("llm:cache:");
        LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(chatClientRegistry, llmProperties);
        LlmRouteResolution routeResolution = new LlmRouteResolution(
                "query_request",
                "query-1",
                "query",
                "answer",
                Long.valueOf(11L),
                Long.valueOf(22L),
                Integer.valueOf(3),
                "query.answer.openai",
                "openai",
                stubServer.getBaseUrl(),
                "test-key",
                "gpt-5.4",
                new BigDecimal("0.2"),
                Integer.valueOf(96),
                Integer.valueOf(30),
                "{\"reasoning_effort\":\"medium\"}",
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                true
        );

        LlmInvocationEnvelope envelope = llmInvocationExecutor.execute(
                routeResolution,
                new LlmInvocationContext(
                        "query",
                        "query-answer",
                        "query-1",
                        "answer",
                        "query.answer.openai"
                ),
                "你是查询助手",
                "请解释订单服务为什么没有直接调用库存服务",
                "llm:cache:test"
        );

        assertThat(envelope.getContent()).isEqualTo("executor-ok");
        assertThat(envelope.getPurpose()).isEqualTo("query-answer");
        assertThat(envelope.getCacheKey()).isEqualTo("llm:cache:test");
        assertThat(envelope.getRouteResolution()).isSameAs(routeResolution);
        assertThat(envelope.getInputTokens()).isEqualTo(9);
        assertThat(envelope.getOutputTokens()).isEqualTo(4);
        assertThat(envelope.getLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(stubServer.getRequestCount()).isEqualTo(1);
        assertThat(stubServer.getCapturedModels()).containsExactly("gpt-5.4");
    }
}
