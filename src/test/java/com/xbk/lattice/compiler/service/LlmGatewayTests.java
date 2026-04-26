package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.llm.service.LlmClientFactory;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.LlmInvocationExecutor;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.observability.StructuredEventLogger;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LlmGateway 测试
 *
 * 职责：验证模型路由、缓存与预算守卫
 *
 * @author xiexu
 */
class LlmGatewayTests {

    /**
     * 验证 writer 文本生成会路由到编译模型，并写入缓存。
     */
    @Test
    void shouldRouteWriterTextCallsToCompileClientAndCacheResult() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeLlmClient reviewClient = new FakeLlmClient("review-result", 60, 10);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                reviewClient,
                redisKeyValueStore,
                createProperties()
        );

        String result = llmGateway.generateText(
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_WRITER,
                "compile",
                "system-a",
                "user-a"
        );

        assertThat(result).isEqualTo("compiled-article");
        assertThat(compileClient.getCallCount()).isEqualTo(1);
        assertThat(reviewClient.getCallCount()).isZero();
        assertThat(redisKeyValueStore.values).hasSize(1);
    }

    /**
     * 验证 reviewer 文本生成会路由到审查模型。
     */
    @Test
    void shouldRouteReviewerTextCallsToReviewClient() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeLlmClient reviewClient = new FakeLlmClient("{\"passed\":true,\"issues\":[]}", 80, 20);
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                reviewClient,
                new FakeRedisKeyValueStore(),
                createProperties()
        );

        String result = llmGateway.generateText(
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_REVIEWER,
                "review",
                "system-r",
                "user-r"
        );

        assertThat(result).contains("\"passed\":true");
        assertThat(compileClient.getCallCount()).isZero();
        assertThat(reviewClient.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证缓存命中时不会重复调用模型。
     */
    @Test
    void shouldReuseCachedResponseWithoutCallingModelAgain() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                redisKeyValueStore,
                createProperties()
        );

        String first = llmGateway.generateText(
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_WRITER,
                "compile",
                "system-a",
                "user-a"
        );
        String second = llmGateway.generateText(
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_WRITER,
                "compile",
                "system-a",
                "user-a"
        );

        assertThat(first).isEqualTo("compiled-article");
        assertThat(second).isEqualTo("compiled-article");
        assertThat(compileClient.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证超出预算会阻止后续调用。
     */
    @Test
    void shouldRejectCallsWhenBudgetIsExceeded() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 700000, 700000);
        LlmProperties llmProperties = createProperties();
        llmProperties.setBudgetUsd(0.5D);
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                new FakeRedisKeyValueStore(),
                llmProperties
        );

        assertThatThrownBy(() -> llmGateway.generateText(
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_WRITER,
                "compile",
                "system-a",
                "user-a"
        ))
                .isInstanceOf(BudgetExceededException.class);
    }

    /**
     * 验证 raw facade 在未启用 ChatClient executor 时，仍会通过 legacy client 返回最小调用信封。
     */
    @Test
    void shouldExposeRawInvocationEnvelopeThroughLegacyFacade() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                new FakeRedisKeyValueStore(),
                createProperties()
        );

        LlmInvocationEnvelope envelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );

        assertThat(envelope.getContent()).isEqualTo("compiled-article");
        assertThat(envelope.getPurpose()).isEqualTo("query-answer");
        assertThat(envelope.getCacheKey()).startsWith("llm:cache:");
        assertThat(envelope.getInputTokens()).isEqualTo(120);
        assertThat(envelope.getOutputTokens()).isEqualTo(30);
        assertThat(envelope.getRouteResolution().getScopeId()).isEqualTo("query-1");
        assertThat(envelope.getRouteResolution().getScene()).isEqualTo(ExecutionLlmSnapshotService.QUERY_SCENE);
        assertThat(envelope.getRouteResolution().getAgentRole()).isEqualTo(ExecutionLlmSnapshotService.ROLE_ANSWER);
        assertThat(compileClient.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证 deep_research 场景在缺少快照路由时会 fail-closed，而不是回退到 bootstrap。
     */
    @Test
    void shouldFailClosedWhenDeepResearchRouteIsMissing() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeLlmClient reviewClient = new FakeLlmClient("review-result", 60, 10);
        StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
        LlmProperties llmProperties = createProperties();
        llmProperties.setBootstrapEnabled(true);
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                reviewClient,
                new FakeRedisKeyValueStore(),
                llmProperties,
                null,
                snapshotService,
                null,
                "",
                "",
                "",
                "",
                null
        );

        assertThatThrownBy(() -> llmGateway.invokeRawWithScope(
                "dr-1",
                ExecutionLlmSnapshotService.DEEP_RESEARCH_SCENE,
                ExecutionLlmSnapshotService.ROLE_RESEARCHER,
                "deep-research-research",
                "system-a",
                "user-a"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No llm route configured for deep_research/researcher");
        assertThat(compileClient.getCallCount()).isZero();
        assertThat(reviewClient.getCallCount()).isZero();
    }

    /**
     * 验证 raw path 在写入 prompt cache 后，会直接命中 L1 缓存且不再计费。
     */
    @Test
    void shouldReusePromptCacheForRawInvocationAfterWritePolicyApplied() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                redisKeyValueStore,
                createProperties()
        );

        LlmInvocationEnvelope firstEnvelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );
        llmGateway.applyPromptCacheWritePolicy(firstEnvelope, PromptCacheWritePolicy.WRITE);
        double spentAfterFirstCall = llmGateway.getSpentUsd();

        LlmInvocationEnvelope secondEnvelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );

        assertThat(firstEnvelope.isPromptCacheHit()).isFalse();
        assertThat(secondEnvelope.isPromptCacheHit()).isTrue();
        assertThat(secondEnvelope.getContent()).isEqualTo("compiled-article");
        assertThat(secondEnvelope.getInputTokens()).isZero();
        assertThat(secondEnvelope.getOutputTokens()).isZero();
        assertThat(compileClient.getCallCount()).isEqualTo(1);
        assertThat(llmGateway.getSpentUsd()).isEqualTo(spentAfterFirstCall);
        assertThat(redisKeyValueStore.values).hasSize(1);
    }

    /**
     * 验证 EVICT_AFTER_READ 会驱逐指定 prompt cache 键。
     */
    @Test
    void shouldEvictPromptCacheKeyAfterReadPolicyApplied() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                redisKeyValueStore,
                createProperties()
        );

        LlmInvocationEnvelope firstEnvelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );
        llmGateway.applyPromptCacheWritePolicy(firstEnvelope, PromptCacheWritePolicy.WRITE);
        LlmInvocationEnvelope cachedEnvelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );

        llmGateway.applyPromptCacheWritePolicy(cachedEnvelope, PromptCacheWritePolicy.EVICT_AFTER_READ);
        LlmInvocationEnvelope thirdEnvelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );

        assertThat(cachedEnvelope.isPromptCacheHit()).isTrue();
        assertThat(thirdEnvelope.isPromptCacheHit()).isFalse();
        assertThat(compileClient.getCallCount()).isEqualTo(2);
    }

    /**
     * 验证全量清理会移除所有 prompt cache。
     */
    @Test
    void shouldEvictAllPromptCacheKeys() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                redisKeyValueStore,
                createProperties()
        );

        LlmInvocationEnvelope firstEnvelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );
        llmGateway.applyPromptCacheWritePolicy(firstEnvelope, PromptCacheWritePolicy.WRITE);

        llmGateway.evictPromptCache();
        LlmInvocationEnvelope secondEnvelope = llmGateway.invokeRawWithScope(
                "query-1",
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                "query-answer",
                "system-a",
                "user-a"
        );

        assertThat(redisKeyValueStore.values).isEmpty();
        assertThat(secondEnvelope.isPromptCacheHit()).isFalse();
        assertThat(compileClient.getCallCount()).isEqualTo(2);
    }

    /**
     * 验证 scoped raw facade 会优先命中 query 快照路由，并通过动态 ChatClient 执行 OpenAI/Codex 路径。
     */
    @Test
    void shouldInvokeScopedRawFacadeThroughSnapshotOpenAiCodexRoute() throws IOException {
        StubOpenAiChatServer stubServer = new StubOpenAiChatServer("codex-answer");
        stubServer.start();
        try {
            FakeLlmClient compileClient = new FakeLlmClient("legacy-answer", 120, 30);
            LlmProperties llmProperties = createProperties();
            llmProperties.setCompileModel("claude-sonnet-4");
            StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
            snapshotService.route = new LlmRouteResolution(
                    ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE,
                    "query-1",
                    ExecutionLlmSnapshotService.QUERY_SCENE,
                    ExecutionLlmSnapshotService.ROLE_ANSWER,
                    Long.valueOf(11L),
                    Long.valueOf(22L),
                    Integer.valueOf(3),
                    "query.answer.codex",
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
            LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                    new com.xbk.lattice.llm.service.ChatClientRegistry(
                            RestClient.builder(),
                            WebClient.builder(),
                            new ObjectMapper(),
                            new com.xbk.lattice.llm.service.AdvisorChainFactory()
                    ),
                    llmProperties
            );
            LlmGateway llmGateway = new LlmGateway(
                    compileClient,
                    new FakeLlmClient("review-result", 60, 10),
                    new FakeRedisKeyValueStore(),
                    llmProperties,
                    null,
                    snapshotService,
                    llmInvocationExecutor,
                    "",
                    "",
                    "",
                    "",
                    null
            );

            LlmInvocationEnvelope envelope = llmGateway.invokeRawWithScope(
                    "query-1",
                    ExecutionLlmSnapshotService.QUERY_SCENE,
                    ExecutionLlmSnapshotService.ROLE_ANSWER,
                    "query-answer",
                    "你是查询助手",
                    "请解释订单服务为什么没有直接调用库存服务"
            );

            assertThat(envelope.getContent()).isEqualTo("codex-answer");
            assertThat(envelope.getPurpose()).isEqualTo("query-answer");
            assertThat(envelope.getRouteResolution().getRouteLabel()).isEqualTo("query.answer.codex");
            assertThat(envelope.getRouteResolution().getProviderType()).isEqualTo("openai");
            assertThat(envelope.getRouteResolution().getModelName()).isEqualTo("gpt-5.4");
            assertThat(envelope.getRouteResolution().isSnapshotBacked()).isTrue();
            assertThat(envelope.getRouteResolution().getScopeId()).isEqualTo("query-1");
            assertThat(envelope.getInputTokens()).isEqualTo(9);
            assertThat(envelope.getOutputTokens()).isEqualTo(4);
            assertThat(stubServer.getCapturedModels()).containsExactly("gpt-5.4");
            assertThat(stubServer.getRequestCount()).isEqualTo(1);
            assertThat(compileClient.getCallCount()).isZero();
            assertThat(snapshotService.lastScopeType).isEqualTo(ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE);
            assertThat(snapshotService.lastScopeId).isEqualTo("query-1");
            assertThat(snapshotService.lastScene).isEqualTo(ExecutionLlmSnapshotService.QUERY_SCENE);
            assertThat(snapshotService.lastAgentRole).isEqualTo(ExecutionLlmSnapshotService.ROLE_ANSWER);
        }
        finally {
            stubServer.stop();
        }
    }

    /**
     * 验证 bootstrap Anthropic reviewer 路径会像 OpenAI 一样进入动态 ChatClient。
     */
    @Test
    void shouldInvokeBootstrapReviewerFacadeThroughAnthropicChatClientRoute() throws Exception {
        StubAnthropicChatServer stubServer = new StubAnthropicChatServer("claude-review-answer");
        stubServer.start();
        try {
            FakeLlmClient compileClient = new FakeLlmClient("legacy-compile", 120, 30);
            FakeLlmClient reviewClient = new FakeLlmClient("legacy-review", 60, 10);
            LlmProperties llmProperties = createProperties();
            LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                    new com.xbk.lattice.llm.service.ChatClientRegistry(
                            RestClient.builder(),
                            WebClient.builder(),
                            new ObjectMapper(),
                            new com.xbk.lattice.llm.service.AdvisorChainFactory()
                    ),
                    llmProperties
            );
            LlmGateway llmGateway = new LlmGateway(
                    compileClient,
                    reviewClient,
                    new FakeRedisKeyValueStore(),
                    llmProperties,
                    null,
                    null,
                    llmInvocationExecutor,
                    "",
                    "",
                    stubServer.getBaseUrl(),
                    "anthropic-key",
                    null
            );
            setField(llmGateway, "reviewBootstrapModelName", "claude-sonnet-4-6");

            LlmInvocationEnvelope envelope = llmGateway.invokeRaw(
                    ExecutionLlmSnapshotService.QUERY_SCENE,
                    ExecutionLlmSnapshotService.ROLE_REVIEWER,
                    "query-review",
                    "你是查询审查助手",
                    "请判断当前回答是否需要重写"
            );

            assertThat(envelope.getContent()).isEqualTo("claude-review-answer");
            assertThat(envelope.getPurpose()).isEqualTo("query-review");
            assertThat(envelope.getRouteResolution().getProviderType()).isEqualTo("anthropic");
            assertThat(envelope.getRouteResolution().getModelName()).isEqualTo("claude-sonnet-4-6");
            assertThat(envelope.getRouteResolution().isSnapshotBacked()).isFalse();
            assertThat(envelope.getInputTokens()).isEqualTo(11);
            assertThat(envelope.getOutputTokens()).isEqualTo(7);
            assertThat(stubServer.getRequestCount()).isEqualTo(1);
            assertThat(stubServer.getCapturedModels()).containsExactly("claude-sonnet-4-6");
            assertThat(reviewClient.getCallCount()).isZero();
            assertThat(compileClient.getCallCount()).isZero();
        }
        finally {
            stubServer.stop();
        }
    }

    /**
     * 验证当 Query answer 的 ChatClient 开关关闭时，即使是 OpenAI snapshot 路由也会回退到 legacy client。
     */
    @Test
    void shouldFallbackToLegacyClientWhenQueryAnswerChatClientToggleIsDisabled() throws IOException {
        StubOpenAiChatServer stubServer = new StubOpenAiChatServer("codex-answer");
        stubServer.start();
        try {
            FakeLlmClient compileClient = new FakeLlmClient("legacy-answer", 120, 30);
            LlmProperties llmProperties = createProperties();
            llmProperties.getChatClient().setQueryAnswerEnabled(false);
            StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
            snapshotService.route = new LlmRouteResolution(
                    ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE,
                    "query-1",
                    ExecutionLlmSnapshotService.QUERY_SCENE,
                    ExecutionLlmSnapshotService.ROLE_ANSWER,
                    Long.valueOf(11L),
                    Long.valueOf(22L),
                    Integer.valueOf(3),
                    "query.answer.codex",
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
            LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                    new com.xbk.lattice.llm.service.ChatClientRegistry(
                            RestClient.builder(),
                            WebClient.builder(),
                            new ObjectMapper(),
                            new com.xbk.lattice.llm.service.AdvisorChainFactory()
                    ),
                    llmProperties
            );
            LlmGateway llmGateway = new LlmGateway(
                    compileClient,
                    new FakeLlmClient("review-result", 60, 10),
                    new FakeRedisKeyValueStore(),
                    llmProperties,
                    null,
                    snapshotService,
                    llmInvocationExecutor,
                    "",
                    "",
                    "",
                    "",
                    null
            );

            LlmInvocationEnvelope envelope = llmGateway.invokeRawWithScope(
                    "query-1",
                    ExecutionLlmSnapshotService.QUERY_SCENE,
                    ExecutionLlmSnapshotService.ROLE_ANSWER,
                    "query-answer",
                    "你是查询助手",
                    "请解释订单服务为什么没有直接调用库存服务"
            );

            assertThat(envelope.getContent()).isEqualTo("legacy-answer");
            assertThat(compileClient.getCallCount()).isEqualTo(1);
            assertThat(stubServer.getRequestCount()).isZero();
        }
        finally {
            stubServer.stop();
        }
    }

    /**
     * 验证 snapshot-backed OpenAI legacy 路径在关闭 ChatClient 后，仍由上层统一重试并成功返回。
     */
    @Test
    void shouldRetrySnapshotBackedLegacyOpenAiInvocationWhenChatClientIsDisabled() throws IOException {
        StubOpenAiChatServer stubServer = new StubOpenAiChatServer("legacy-retry-answer", 1);
        stubServer.start();
        try {
            MDC.put("sourceSyncRunId", "701");
            MDC.put("rootTraceId", "trace-701");
            MDC.put("traceId", "trace-701");
            FakeLlmClient compileClient = new FakeLlmClient("legacy-answer", 120, 30);
            LlmProperties llmProperties = createProperties();
            llmProperties.getChatClient().setEnabled(false);
            CapturingStructuredEventLogger structuredEventLogger = new CapturingStructuredEventLogger();
            StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
            snapshotService.route = new LlmRouteResolution(
                    ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE,
                    "query-retry-1",
                    ExecutionLlmSnapshotService.QUERY_SCENE,
                    ExecutionLlmSnapshotService.ROLE_ANSWER,
                    Long.valueOf(11L),
                    Long.valueOf(22L),
                    Integer.valueOf(3),
                    "query.answer.codex.retry",
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
            LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                    new com.xbk.lattice.llm.service.ChatClientRegistry(
                            RestClient.builder(),
                            WebClient.builder(),
                            new ObjectMapper(),
                            new com.xbk.lattice.llm.service.AdvisorChainFactory()
                    ),
                    llmProperties,
                    structuredEventLogger
            );
            LlmClientFactory llmClientFactory = new LlmClientFactory(
                    RestClient.builder(),
                    new ObjectMapper(),
                    new AnthropicConnectionProperties(),
                    new AnthropicChatProperties()
            );
            LlmGateway llmGateway = new LlmGateway(
                    compileClient,
                    new FakeLlmClient("review-result", 60, 10),
                    new FakeRedisKeyValueStore(),
                    llmProperties,
                    llmClientFactory,
                    snapshotService,
                    llmInvocationExecutor,
                    "",
                    "",
                    "",
                    "",
                    structuredEventLogger
            );

            LlmInvocationEnvelope envelope = llmGateway.invokeRawWithScope(
                    "query-retry-1",
                    ExecutionLlmSnapshotService.QUERY_SCENE,
                    ExecutionLlmSnapshotService.ROLE_ANSWER,
                    "query-answer",
                    "你是查询助手",
                    "请解释为什么 legacy 路径仍然会重试"
            );

            assertThat(envelope.getContent()).isEqualTo("legacy-retry-answer");
            assertThat(stubServer.getRequestCount()).isEqualTo(2);
            assertThat(stubServer.getCapturedModels()).containsExactly("gpt-5.4", "gpt-5.4");
            assertThat(compileClient.getCallCount()).isZero();
            RecordedEvent retryEvent = structuredEventLogger.findWarnEvent("llm_retry_attempt_failed");
            assertThat(retryEvent).isNotNull();
            assertThat(retryEvent.getFields()).containsEntry("queryId", "query-retry-1");
            assertThat(retryEvent.getFields()).containsEntry("routeLabel", "query.answer.codex.retry");
            assertThat(retryEvent.getFields()).containsEntry("providerType", "openai");
            assertThat(retryEvent.getFields()).containsEntry("baseUrl", stubServer.getBaseUrl());
            assertThat(retryEvent.getFields()).containsEntry("modelName", "gpt-5.4");
            assertThat(retryEvent.getFields()).containsEntry("attemptNo", Integer.valueOf(1));
            assertThat(retryEvent.getFields()).containsEntry("maxAttempts", Integer.valueOf(5));
            assertThat(retryEvent.getFields()).containsEntry("willRetry", Boolean.TRUE);
            assertThat(retryEvent.getFields()).containsEntry("sourceSyncRunId", Long.valueOf(701L));
            assertThat(retryEvent.getFields()).containsEntry("clientRequestId", "trace-701");
            assertThat(retryEvent.getFields()).containsEntry("statusCode", Integer.valueOf(502));
            assertThat(retryEvent.getFields()).containsEntry("errorCode", "LLM_UPSTREAM_5XX");
            assertThat(String.valueOf(retryEvent.getFields().get("errorSummary"))).contains("temporary upstream failure");
        }
        finally {
            MDC.clear();
            stubServer.stop();
        }
    }

    /**
     * 验证文本生成入口会在 snapshot OpenAI 路径下走动态 ChatClient，并保留 legacy L1 cache 写入语义。
     */
    @Test
    void shouldGenerateTextThroughSnapshotOpenAiRouteAndReusePromptCache() throws IOException {
        StubOpenAiChatServer stubServer = new StubOpenAiChatServer("compiled-by-codex");
        stubServer.start();
        try {
            FakeLlmClient compileClient = new FakeLlmClient("legacy-answer", 120, 30);
            FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
            LlmProperties llmProperties = createProperties();
            llmProperties.setCompileModel("claude-sonnet-4");
            StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
            snapshotService.route = new LlmRouteResolution(
                    ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE,
                    "compile-1",
                    ExecutionLlmSnapshotService.COMPILE_SCENE,
                    ExecutionLlmSnapshotService.ROLE_WRITER,
                    Long.valueOf(11L),
                    Long.valueOf(22L),
                    Integer.valueOf(3),
                    "compile.writer.codex",
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
            LlmInvocationExecutor llmInvocationExecutor = new LlmInvocationExecutor(
                    new com.xbk.lattice.llm.service.ChatClientRegistry(
                            RestClient.builder(),
                            WebClient.builder(),
                            new ObjectMapper(),
                            new com.xbk.lattice.llm.service.AdvisorChainFactory()
                    ),
                    llmProperties
            );
            LlmGateway llmGateway = new LlmGateway(
                    compileClient,
                    new FakeLlmClient("review-result", 60, 10),
                    redisKeyValueStore,
                    llmProperties,
                    null,
                    snapshotService,
                    llmInvocationExecutor,
                    "",
                    "",
                    "",
                    "",
                    null
            );

            String first = llmGateway.generateTextWithScope(
                    "compile-1",
                    ExecutionLlmSnapshotService.COMPILE_SCENE,
                    ExecutionLlmSnapshotService.ROLE_WRITER,
                    "compile-article",
                    "你是编译助手",
                    "请把支付超时规则整理成知识文章"
            );
            String second = llmGateway.generateTextWithScope(
                    "compile-1",
                    ExecutionLlmSnapshotService.COMPILE_SCENE,
                    ExecutionLlmSnapshotService.ROLE_WRITER,
                    "compile-article",
                    "你是编译助手",
                    "请把支付超时规则整理成知识文章"
            );

            assertThat(first).isEqualTo("compiled-by-codex");
            assertThat(second).isEqualTo("compiled-by-codex");
            assertThat(redisKeyValueStore.values).hasSize(1);
            assertThat(stubServer.getRequestCount()).isEqualTo(1);
            assertThat(stubServer.getCapturedModels()).containsExactly("gpt-5.4");
            assertThat(compileClient.getCallCount()).isZero();
            assertThat(snapshotService.lastScopeType).isEqualTo(ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE);
            assertThat(snapshotService.lastScopeId).isEqualTo("compile-1");
            assertThat(snapshotService.lastScene).isEqualTo(ExecutionLlmSnapshotService.COMPILE_SCENE);
            assertThat(snapshotService.lastAgentRole).isEqualTo(ExecutionLlmSnapshotService.ROLE_WRITER);
        }
        finally {
            stubServer.stop();
        }
    }

    /**
     * 验证 bootstrap fallback 路由会优先使用真实 ChatModel 模型名，而不是 provider 占位值。
     */
    @Test
    void shouldUseActualBootstrapChatModelNameForScopedFallbackRoute() throws Exception {
        LlmProperties llmProperties = createProperties();
        StubExecutionLlmSnapshotService snapshotService = new StubExecutionLlmSnapshotService();
        LlmGateway llmGateway = new LlmGateway(
                new FakeLlmClient("legacy-answer", 120, 30),
                new FakeLlmClient("review-result", 60, 10),
                new FakeRedisKeyValueStore(),
                llmProperties,
                null,
                snapshotService,
                null,
                "http://127.0.0.1:18086",
                "test-key",
                "",
                "",
                null
        );
        setField(llmGateway, "compileBootstrapModelName", "gpt-5.4");

        LlmRouteResolution routeResolution = llmGateway.routeResolutionFor(
                "admin-correction:1:ops",
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_WRITER
        );

        assertThat(routeResolution.isSnapshotBacked()).isFalse();
        assertThat(routeResolution.getModelName()).isEqualTo("gpt-5.4");
        assertThat(routeResolution.getBaseUrl()).isEqualTo("http://127.0.0.1:18086");
        assertThat(routeResolution.getTimeoutSeconds()).isEqualTo(Integer.valueOf(90));
        assertThat(snapshotService.lastScopeType).isEqualTo(ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE);
        assertThat(snapshotService.lastScopeId).isEqualTo("admin-correction:1:ops");
        assertThat(snapshotService.lastScene).isEqualTo(ExecutionLlmSnapshotService.COMPILE_SCENE);
        assertThat(snapshotService.lastAgentRole).isEqualTo(ExecutionLlmSnapshotService.ROLE_WRITER);
    }

    /**
     * 创建默认 LLM 配置。
     *
     * @return LLM 配置
     */
    private LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:cache:");
        return llmProperties;
    }

    private void setField(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * LLM 客户端测试替身。
     *
     * 职责：返回固定结果，并统计调用次数
     *
     * @author xiexu
     */
    private static class FakeLlmClient implements LlmClient {

        private final String content;

        private final int inputTokens;

        private final int outputTokens;

        private int callCount;

        private FakeLlmClient(String content, int inputTokens, int outputTokens) {
            this.content = content;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.callCount = 0;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            callCount++;
            return new LlmCallResult(content, inputTokens, outputTokens);
        }

        private int getCallCount() {
            return callCount;
        }
    }

    /**
     * Redis 键值存储测试替身。
     *
     * 职责：记录缓存值与 TTL
     *
     * @author xiexu
     */
    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        private final Map<String, Long> ttlSeconds = new LinkedHashMap<String, Long>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
            ttlSeconds.put(key, ttl.toSeconds());
        }

        @Override
        public Long getExpire(String key) {
            return ttlSeconds.get(key);
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
            ttlSeconds.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }

    /**
     * 运行时快照服务替身。
     *
     * 职责：为网关测试返回固定 query 快照路由，并记录 scoped route 查询参数
     *
     * @author xiexu
     */
    private static class StubExecutionLlmSnapshotService extends ExecutionLlmSnapshotService {

        private LlmRouteResolution route;

        private String lastScopeType;

        private String lastScopeId;

        private String lastScene;

        private String lastAgentRole;

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
            lastScopeType = scopeType;
            lastScopeId = scopeId;
            lastScene = scene;
            lastAgentRole = agentRole;
            return Optional.ofNullable(route);
        }

        private static LlmProperties properties() {
            LlmProperties llmProperties = new LlmProperties();
            llmProperties.setSecretEncryptionKey("test-phase8-key-0123456789abcdef");
            return llmProperties;
        }
    }

    /**
     * OpenAI Chat Completions stub 服务。
     *
     * 职责：为 LlmGateway 的动态 ChatClient 路径提供本地稳定响应
     *
     * @author xiexu
     */
    private static class StubOpenAiChatServer {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final HttpServer httpServer;

        private final String answerText;

        private final int transientFailureCount;

        private final AtomicInteger requestCount = new AtomicInteger();

        private final List<String> capturedModels = new CopyOnWriteArrayList<String>();

        private StubOpenAiChatServer(String answerText) throws IOException {
            this(answerText, 0);
        }

        private StubOpenAiChatServer(String answerText, int transientFailureCount) throws IOException {
            this.answerText = answerText;
            this.transientFailureCount = transientFailureCount;
            this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            HttpHandler handler = new ChatCompletionsHandler();
            httpServer.createContext("/v1/chat/completions", handler);
            httpServer.createContext("/chat/completions", handler);
        }

        private void start() {
            httpServer.start();
        }

        private void stop() {
            httpServer.stop(0);
        }

        private String getBaseUrl() {
            return "http://127.0.0.1:" + httpServer.getAddress().getPort();
        }

        private int getRequestCount() {
            return requestCount.get();
        }

        private List<String> getCapturedModels() {
            return capturedModels;
        }

        private final class ChatCompletionsHandler implements HttpHandler {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                int currentAttempt = requestCount.incrementAndGet();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode rootNode = OBJECT_MAPPER.readTree(requestBody);
                capturedModels.add(rootNode.path("model").asText());
                if (currentAttempt <= transientFailureCount) {
                    byte[] responseBytes = "{\"error\":\"temporary upstream failure\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(502, responseBytes.length);
                    exchange.getResponseBody().write(responseBytes);
                    exchange.close();
                    return;
                }
                String responseBody = """
                        {
                          "id": "chatcmpl_test",
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "%s"
                              }
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 9,
                            "completion_tokens": 4,
                            "total_tokens": 13
                          }
                        }
                        """.formatted(answerText);
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            }
        }
    }

    /**
     * Anthropic Messages stub 服务。
     *
     * 职责：为 LlmGateway 的 Anthropic ChatClient 路径提供本地稳定响应
     *
     * @author xiexu
     */
    private static class StubAnthropicChatServer {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final HttpServer httpServer;

        private final String answerText;

        private final AtomicInteger requestCount = new AtomicInteger();

        private final List<String> capturedModels = new CopyOnWriteArrayList<String>();

        private StubAnthropicChatServer(String answerText) throws IOException {
            this.answerText = answerText;
            this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            HttpHandler handler = new MessagesHandler();
            httpServer.createContext("/v1/messages", handler);
            httpServer.createContext("/messages", handler);
        }

        private void start() {
            httpServer.start();
        }

        private void stop() {
            httpServer.stop(0);
        }

        private String getBaseUrl() {
            return "http://127.0.0.1:" + httpServer.getAddress().getPort();
        }

        private int getRequestCount() {
            return requestCount.get();
        }

        private List<String> getCapturedModels() {
            return capturedModels;
        }

        private final class MessagesHandler implements HttpHandler {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCount.incrementAndGet();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode rootNode = OBJECT_MAPPER.readTree(requestBody);
                capturedModels.add(rootNode.path("model").asText());
                String responseBody = """
                        {
                          "id": "msg_test",
                          "type": "message",
                          "model": "%s",
                          "role": "assistant",
                          "content": [
                            {
                              "type": "text",
                              "text": "%s"
                            }
                          ],
                          "usage": {
                            "input_tokens": 11,
                            "output_tokens": 7
                          }
                        }
                        """.formatted(rootNode.path("model").asText(), answerText);
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            }
        }
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
            for (RecordedEvent warnEvent : warnEvents) {
                if (eventName.equals(warnEvent.getEventName())) {
                    return warnEvent;
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
