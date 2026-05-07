package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.observability.StructuredEventLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
     * 验证结构化问答用途会自动下发 OpenAI Structured Outputs schema。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldAttachJsonSchemaResponseFormatForStructuredQueryPurpose() throws IOException {
        openAiStubServer = new StubOpenAiChatServer("structured-route-ok");
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
                "query-structured-1",
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
                "{}",
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                true
        );

        llmInvocationExecutor.execute(
                routeResolution,
                new LlmInvocationContext(
                        "query",
                        "query-answer-structured",
                        "query-structured-1",
                        "answer",
                        "query.answer.openai"
                ),
                "你是查询助手",
                "请输出结构化答案",
                "llm:cache:structured:test"
        );

        assertThat(openAiStubServer.getCapturedResponseFormatTypes()).containsExactly("json_schema");
        JsonNode responseFormat = openAiStubServer.getCapturedResponseFormats().get(0);
        assertThat(responseFormat.path("json_schema").path("name").asText()).isEqualTo("query_answer_payload");
        assertThat(responseFormat.path("json_schema").path("strict").asBoolean(false)).isTrue();
        JsonNode schema = responseFormat.path("json_schema").path("schema");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.path("required")).hasSize(3);
        assertThat(schema.path("required").toString())
                .contains("answerMarkdown", "answerOutcome", "answerCacheable");
        assertThat(schema.path("properties").path("answerOutcome").path("enum").toString())
                .contains("SUCCESS", "INSUFFICIENT_EVIDENCE", "NO_RELEVANT_KNOWLEDGE", "PARTIAL_ANSWER");
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
        CapturingStructuredEventLogger structuredEventLogger = new CapturingStructuredEventLogger();
        LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                chatClientRegistry,
                llmProperties,
                structuredEventLogger
        );
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
        RecordedEvent retryEvent = structuredEventLogger.findWarnEvent("llm_retry_attempt_failed");
        assertThat(retryEvent).isNotNull();
        assertThat(retryEvent.getFields()).containsEntry("queryId", "query-retry-1");
        assertThat(retryEvent.getFields()).containsEntry("routeLabel", "query.answer.openai.retry");
        assertThat(retryEvent.getFields()).containsEntry("providerType", "openai");
        assertThat(retryEvent.getFields()).containsEntry("baseUrl", openAiStubServer.getBaseUrl());
        assertThat(retryEvent.getFields()).containsEntry("modelName", "gpt-5.4");
        assertThat(retryEvent.getFields()).containsEntry("attemptNo", Integer.valueOf(1));
        assertThat(retryEvent.getFields()).containsEntry("maxAttempts", Integer.valueOf(5));
        assertThat(retryEvent.getFields()).containsEntry("willRetry", Boolean.TRUE);
        assertThat(retryEvent.getFields()).containsEntry("statusCode", Integer.valueOf(500));
        assertThat(retryEvent.getFields()).containsEntry("errorCode", "LLM_UPSTREAM_5XX");
        assertThat(String.valueOf(retryEvent.getFields().get("errorSummary"))).contains("temporary upstream failure");
    }

    /**
     * 验证 raw 调用在重试耗尽后会抛出稳定异常，并输出最终 attempt 观测。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldThrowRetryExhaustedExceptionWhenOpenAiFailuresPersist() throws IOException {
        openAiStubServer = new StubOpenAiChatServer("executor-never-ok", 5);
        openAiStubServer.start();
        ChatClientRegistry chatClientRegistry = new ChatClientRegistry(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                new AdvisorChainFactory()
        );
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCacheKeyPrefix("llm:cache:");
        CapturingStructuredEventLogger structuredEventLogger = new CapturingStructuredEventLogger();
        LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                chatClientRegistry,
                llmProperties,
                structuredEventLogger
        );
        LlmRouteResolution routeResolution = new LlmRouteResolution(
                "query_request",
                "query-retry-exhausted-1",
                "query",
                "answer",
                Long.valueOf(91L),
                Long.valueOf(92L),
                Integer.valueOf(5),
                "query.answer.openai.retry.exhausted",
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

        assertThatThrownBy(() -> llmInvocationExecutor.execute(
                routeResolution,
                new LlmInvocationContext(
                        "query",
                        "query-answer-retry",
                        "query-retry-exhausted-1",
                        "answer",
                        "query.answer.openai.retry.exhausted"
                ),
                "你是查询助手",
                "请解释为什么会触发重试耗尽",
                "llm:cache:retry:exhausted"
        ))
                .isInstanceOf(LlmRetryExhaustedException.class)
                .hasMessageContaining("exhausted after 5 attempts");

        assertThat(openAiStubServer.getRequestCount()).isEqualTo(5);
        RecordedEvent retryEvent = structuredEventLogger.findWarnEvent(
                "llm_retry_attempt_failed",
                Integer.valueOf(5)
        );
        assertThat(retryEvent).isNotNull();
        assertThat(retryEvent.getFields()).containsEntry("queryId", "query-retry-exhausted-1");
        assertThat(retryEvent.getFields()).containsEntry("attemptNo", Integer.valueOf(5));
        assertThat(retryEvent.getFields()).containsEntry("maxAttempts", Integer.valueOf(5));
        assertThat(retryEvent.getFields()).containsEntry("willRetry", Boolean.FALSE);
        assertThat(retryEvent.getFields()).containsEntry("statusCode", Integer.valueOf(500));
        assertThat(retryEvent.getFields()).containsEntry("errorCode", "LLM_RETRY_EXHAUSTED");
    }

    /**
     * 验证 compile 作用域重试事件会带出 sourceSyncRunId 与 clientRequestId。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldIncludeCompileCorrelationFieldsInRetryEvent() throws IOException {
        openAiStubServer = new StubOpenAiChatServer("executor-compile-retry-ok", 1);
        openAiStubServer.start();
        ChatClientRegistry chatClientRegistry = new ChatClientRegistry(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                new AdvisorChainFactory()
        );
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCacheKeyPrefix("llm:cache:");
        CapturingStructuredEventLogger structuredEventLogger = new CapturingStructuredEventLogger();
        LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                chatClientRegistry,
                llmProperties,
                structuredEventLogger
        );
        LlmRouteResolution routeResolution = new LlmRouteResolution(
                "compile_job",
                "compile-job-1",
                "compile",
                "reviewer",
                Long.valueOf(91L),
                Long.valueOf(92L),
                Integer.valueOf(5),
                "compile.review.openai.retry",
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

        try {
            MDC.put("sourceSyncRunId", "8801");
            MDC.put("clientRequestId", "compile-trace-8801");
            LlmInvocationEnvelope envelope = llmInvocationExecutor.execute(
                    routeResolution,
                    new LlmInvocationContext(
                            "compile",
                            "compile-review",
                            "compile-job-1",
                            "reviewer",
                            "compile.review.openai.retry"
                    ),
                    "你是编译审查助手",
                    "请继续执行编译审查",
                    "llm:cache:compile:retry:test"
            );

            assertThat(envelope.getContent()).isEqualTo("executor-compile-retry-ok");
        }
        finally {
            MDC.clear();
        }

        RecordedEvent retryEvent = structuredEventLogger.findWarnEvent("llm_retry_attempt_failed");
        assertThat(retryEvent).isNotNull();
        assertThat(retryEvent.getFields()).containsEntry("compileJobId", "compile-job-1");
        assertThat(retryEvent.getFields()).containsEntry("sourceSyncRunId", Long.valueOf(8801L));
        assertThat(retryEvent.getFields()).containsEntry("clientRequestId", "compile-trace-8801");
        assertThat(retryEvent.getFields()).containsEntry("errorCode", "LLM_UPSTREAM_5XX");
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

    /**
     * 结构化事件日志捕获器。
     *
     * 职责：记录测试期间的结构化 WARN 事件
     *
     * @author xiexu
     */
    private static class CapturingStructuredEventLogger extends StructuredEventLogger {

        private final List<RecordedEvent> warnEvents = new ArrayList<RecordedEvent>();

        @Override
        public void warn(String eventName, Map<String, Object> fields, Throwable throwable) {
            warnEvents.add(new RecordedEvent(eventName, new LinkedHashMap<String, Object>(fields), throwable));
        }

        private RecordedEvent findWarnEvent(String eventName) {
            return findWarnEvent(eventName, null);
        }

        private RecordedEvent findWarnEvent(String eventName, Integer attemptNo) {
            for (RecordedEvent warnEvent : warnEvents) {
                if (eventName.equals(warnEvent.getEventName())) {
                    if (attemptNo == null || attemptNo.equals(warnEvent.getFields().get("attemptNo"))) {
                        return warnEvent;
                    }
                }
            }
            return null;
        }
    }

    /**
     * 已记录结构化事件。
     *
     * 职责：承载事件名、字段与异常，便于断言
     *
     * @author xiexu
     */
    private static class RecordedEvent {

        private final String eventName;

        private final Map<String, Object> fields;

        private final Throwable throwable;

        private RecordedEvent(String eventName, Map<String, Object> fields, Throwable throwable) {
            this.eventName = eventName;
            this.fields = fields;
            this.throwable = throwable;
        }

        private String getEventName() {
            return eventName;
        }

        private Map<String, Object> getFields() {
            return fields;
        }

        private Throwable getThrowable() {
            return throwable;
        }
    }
}
