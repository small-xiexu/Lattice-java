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

    private StubOpenAiChatServer openAiStubServer;

    private StubAnthropicChatServer anthropicStubServer;

    @AfterEach
    void tearDown() {
        if (openAiStubServer != null) {
            openAiStubServer.stop();
        }
        if (anthropicStubServer != null) {
            anthropicStubServer.stop();
        }
    }

    @Test
    void shouldReturnInvocationEnvelopeForOpenAiRoute() throws IOException {
        openAiStubServer = new StubOpenAiChatServer("executor-ok");
        openAiStubServer.start();
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
                openAiStubServer.getBaseUrl(),
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
        assertThat(openAiStubServer.getRequestCount()).isEqualTo(1);
        assertThat(openAiStubServer.getCapturedModels()).containsExactly("gpt-5.4");
        assertThat(openAiStubServer.getCapturedTransferEncodings()).containsExactly((String) null);
        assertThat(openAiStubServer.getCapturedContentLengths().get(0)).isNotBlank();
    }

    /**
     * 验证 raw 调用遇到瞬时 5xx 后会自动重试并成功返回。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldRetryTransientOpenAiFailuresForInvocationExecutor() throws IOException {
        openAiStubServer = new StubOpenAiChatServer("executor-retry-ok", 1);
        openAiStubServer.start();
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
                "query-retry-1",
                "query",
                "answer",
                Long.valueOf(91L),
                Long.valueOf(92L),
                Integer.valueOf(5),
                "query.answer.openai.retry",
                "openai",
                openAiStubServer.getBaseUrl(),
                "test-key",
                "gpt-5.4",
                new BigDecimal("0.2"),
                Integer.valueOf(96),
                Integer.valueOf(30),
                "{}",
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                true
        );

        LlmInvocationEnvelope envelope = llmInvocationExecutor.execute(
                routeResolution,
                new LlmInvocationContext(
                        "query",
                        "query-answer-retry",
                        "query-retry-1",
                        "answer",
                        "query.answer.openai.retry"
                ),
                "你是查询助手",
                "请解释为什么会触发自动重试",
                "llm:cache:retry:test"
        );

        assertThat(envelope.getContent()).isEqualTo("executor-retry-ok");
        assertThat(openAiStubServer.getRequestCount()).isEqualTo(2);
        assertThat(openAiStubServer.getCapturedModels()).containsExactly("gpt-5.4");
    }

    @Test
    void shouldReturnInvocationEnvelopeForAnthropicRoute() throws IOException {
        anthropicStubServer = new StubAnthropicChatServer("anthropic-executor-ok");
        anthropicStubServer.start();
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
                "query-review-1",
                "query",
                "reviewer",
                Long.valueOf(51L),
                Long.valueOf(62L),
                Integer.valueOf(7),
                "query.review.anthropic",
                "anthropic",
                anthropicStubServer.getBaseUrl(),
                "anthropic-key",
                "claude-sonnet-4-6",
                new BigDecimal("0.1"),
                Integer.valueOf(256),
                Integer.valueOf(30),
                "{\"top_p\":0.8,\"top_k\":12}",
                new BigDecimal("0.003"),
                new BigDecimal("0.015"),
                true
        );

        LlmInvocationEnvelope envelope = llmInvocationExecutor.execute(
                routeResolution,
                new LlmInvocationContext(
                        "query",
                        "query-review",
                        "query-review-1",
                        "reviewer",
                        "query.review.anthropic"
                ),
                "你是查询审查助手",
                "请审查当前答案是否存在幻觉",
                "llm:cache:anthropic:test"
        );

        assertThat(envelope.getContent()).isEqualTo("anthropic-executor-ok");
        assertThat(envelope.getPurpose()).isEqualTo("query-review");
        assertThat(envelope.getCacheKey()).isEqualTo("llm:cache:anthropic:test");
        assertThat(envelope.getRouteResolution()).isSameAs(routeResolution);
        assertThat(envelope.getInputTokens()).isEqualTo(11);
        assertThat(envelope.getOutputTokens()).isEqualTo(7);
        assertThat(envelope.getLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(anthropicStubServer.getRequestCount()).isEqualTo(1);
        assertThat(anthropicStubServer.getCapturedModels()).containsExactly("claude-sonnet-4-6");
        assertThat(anthropicStubServer.getCapturedTopPs()).containsExactly(0.8D);
        assertThat(anthropicStubServer.getCapturedTopKs()).containsExactly(12);
    }
}
