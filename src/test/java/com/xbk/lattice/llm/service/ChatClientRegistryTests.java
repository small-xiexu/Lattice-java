package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatClientRegistry 测试
 *
 * 职责：验证动态 ChatClient 会按运行时路由隔离缓存，并透传 per-request 调用上下文
 *
 * @author xiexu
 */
class ChatClientRegistryTests {

    private final List<StubOpenAiChatServer> openAiStubServers = new ArrayList<StubOpenAiChatServer>();

    private final List<StubAnthropicChatServer> anthropicStubServers = new ArrayList<StubAnthropicChatServer>();

    @AfterEach
    void tearDown() {
        for (StubOpenAiChatServer stubServer : openAiStubServers) {
            stubServer.stop();
        }
        openAiStubServers.clear();
        for (StubAnthropicChatServer stubServer : anthropicStubServers) {
            stubServer.stop();
        }
        anthropicStubServers.clear();
    }

    @Test
    void shouldCacheAndIsolateDynamicChatClientsAcrossRoutes() throws IOException {
        StubOpenAiChatServer routeA = startOpenAiStubServer("route-a-ok");
        StubOpenAiChatServer routeB = startOpenAiStubServer("route-b-ok");
        ChatClientRegistry registry = new ChatClientRegistry(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                new AdvisorChainFactory()
        );

        ChatClientRegistry.ChatClientHandle firstRouteA = registry.getOrCreate(createRouteResolution(
                routeA.getBaseUrl(),
                "key-a",
                "gpt-5.4",
                new BigDecimal("0.1"),
                Integer.valueOf(64),
                "query.answer.openai"
        ));
        ChatClientRegistry.ChatClientHandle secondRouteA = registry.getOrCreate(createRouteResolution(
                routeA.getBaseUrl(),
                "key-a",
                "gpt-5.4",
                new BigDecimal("0.1"),
                Integer.valueOf(64),
                "query.answer.openai"
        ));
        ChatClientRegistry.ChatClientHandle routeBHandle = registry.getOrCreate(createRouteResolution(
                routeB.getBaseUrl(),
                "key-b",
                "gpt-4.1-mini",
                new BigDecimal("0.3"),
                Integer.valueOf(128),
                "query.answer.openai.route-b"
        ));

        ChatClientResponse firstResponse = firstRouteA.getChatClient()
                .prompt()
                .advisors(spec -> spec.params(new LlmInvocationContext(
                        "query",
                        "route-a",
                        "scope-a",
                        "answer",
                        "query.answer.openai"
                ).toAdvisorParams()))
                .system("route-a-system")
                .user("route-a-user")
                .call()
                .chatClientResponse();
        ChatClientResponse secondResponse = routeBHandle.getChatClient()
                .prompt()
                .advisors(spec -> spec.params(new LlmInvocationContext(
                        "query",
                        "route-b",
                        "scope-b",
                        "answer",
                        "query.answer.openai.route-b"
                ).toAdvisorParams()))
                .system("route-b-system")
                .user("route-b-user")
                .call()
                .chatClientResponse();

        assertThat(secondRouteA).isSameAs(firstRouteA);
        assertThat(routeBHandle).isNotSameAs(firstRouteA);
        assertThat(firstResponse.chatResponse().getResult().getOutput().getText()).isEqualTo("route-a-ok");
        assertThat(secondResponse.chatResponse().getResult().getOutput().getText()).isEqualTo("route-b-ok");
        assertThat(firstResponse.context().get("capturedScene")).isEqualTo("query");
        assertThat(firstResponse.context().get("capturedPurpose")).isEqualTo("route-a");
        assertThat(firstResponse.context().get("capturedScopeId")).isEqualTo("scope-a");
        assertThat(secondResponse.context().get("capturedRouteLabel")).isEqualTo("query.answer.openai.route-b");
        assertThat(routeA.getRequestCount()).isEqualTo(1);
        assertThat(routeB.getRequestCount()).isEqualTo(1);
        assertThat(routeA.getCapturedModels()).containsExactly("gpt-5.4");
        assertThat(routeB.getCapturedModels()).containsExactly("gpt-4.1-mini");
        assertThat(routeA.getCapturedAcceptHeaders()).allSatisfy(acceptHeader ->
                assertThat(String.valueOf(acceptHeader)).contains("application/json"));
        assertThat(routeB.getCapturedAcceptHeaders()).allSatisfy(acceptHeader ->
                assertThat(String.valueOf(acceptHeader)).contains("application/json"));
        assertThat(registry.getClientCount()).isEqualTo(2);
    }

    /**
     * 验证 OpenAI compatible 动态 ChatClient 会显式要求 JSON 响应，避免网关误切到 SSE。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldForceJsonAcceptHeaderForOpenAiCompatibleRoute() throws IOException {
        StubOpenAiChatServer route = startOpenAiStubServer("route-json-only", 0, true);
        ChatClientRegistry registry = new ChatClientRegistry(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                new AdvisorChainFactory()
        );

        ChatClientResponse response = registry.getOrCreate(createRouteResolution(
                route.getBaseUrl(),
                "key-json",
                "gpt-5.4",
                new BigDecimal("0.1"),
                Integer.valueOf(64),
                "query.answer.openai.json-only"
        )).getChatClient()
                .prompt()
                .advisors(spec -> spec.params(new LlmInvocationContext(
                        "query",
                        "json-only",
                        "scope-json",
                        "answer",
                        "query.answer.openai.json-only"
                ).toAdvisorParams()))
                .system("json-only-system")
                .user("json-only-user")
                .call()
                .chatClientResponse();

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("route-json-only");
        assertThat(route.getCapturedAcceptHeaders()).allSatisfy(acceptHeader ->
                assertThat(String.valueOf(acceptHeader)).contains("application/json"));
    }

    /**
     * 验证即使兼容网关错误返回 text/event-stream，同步 ChatClient 仍能按 JSON 解析结果。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldParseEventStreamMarkedJsonForOpenAiCompatibleRoute() throws IOException {
        StubOpenAiChatServer route = startOpenAiStubServer(
                "route-event-stream-json",
                0,
                false,
                "text/event-stream"
        );
        ChatClientRegistry registry = new ChatClientRegistry(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                new AdvisorChainFactory()
        );

        ChatClientResponse response = registry.getOrCreate(createRouteResolution(
                route.getBaseUrl(),
                "key-event-stream",
                "gpt-5.4",
                new BigDecimal("0.1"),
                Integer.valueOf(64),
                "query.answer.openai.event-stream"
        )).getChatClient()
                .prompt()
                .advisors(spec -> spec.params(new LlmInvocationContext(
                        "query",
                        "event-stream-json",
                        "scope-event-stream",
                        "answer",
                        "query.answer.openai.event-stream"
                ).toAdvisorParams()))
                .system("event-stream-system")
                .user("event-stream-user")
                .call()
                .chatClientResponse();

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("route-event-stream-json");
        assertThat(route.getCapturedAcceptHeaders()).allSatisfy(acceptHeader ->
                assertThat(String.valueOf(acceptHeader)).contains("application/json"));
    }

    @Test
    void shouldCacheAndInvokeAnthropicDynamicChatClientsAcrossRoutes() throws IOException {
        StubAnthropicChatServer routeA = startAnthropicStubServer("anthropic-a-ok");
        StubAnthropicChatServer routeB = startAnthropicStubServer("anthropic-b-ok");
        ChatClientRegistry registry = new ChatClientRegistry(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                new AdvisorChainFactory()
        );

        ChatClientRegistry.ChatClientHandle firstRouteA = registry.getOrCreate(createAnthropicRouteResolution(
                routeA.getBaseUrl(),
                "claude-key-a",
                "claude-sonnet-4-6",
                new BigDecimal("0.2"),
                Integer.valueOf(128),
                "query.review.anthropic",
                "{\"top_p\":0.8,\"top_k\":12}"
        ));
        ChatClientRegistry.ChatClientHandle secondRouteA = registry.getOrCreate(createAnthropicRouteResolution(
                routeA.getBaseUrl(),
                "claude-key-a",
                "claude-sonnet-4-6",
                new BigDecimal("0.2"),
                Integer.valueOf(128),
                "query.review.anthropic",
                "{\"top_p\":0.8,\"top_k\":12}"
        ));
        ChatClientRegistry.ChatClientHandle routeBHandle = registry.getOrCreate(createAnthropicRouteResolution(
                routeB.getBaseUrl(),
                "claude-key-b",
                "claude-haiku-3-5",
                new BigDecimal("0.4"),
                Integer.valueOf(64),
                "query.review.anthropic.route-b",
                "{\"top_p\":0.6,\"top_k\":8}"
        ));

        ChatClientResponse firstResponse = firstRouteA.getChatClient()
                .prompt()
                .advisors(spec -> spec.params(new LlmInvocationContext(
                        "query",
                        "query-review",
                        "query-review-a",
                        "reviewer",
                        "query.review.anthropic"
                ).toAdvisorParams()))
                .system("review-system-a")
                .user("review-user-a")
                .call()
                .chatClientResponse();
        ChatClientResponse secondResponse = routeBHandle.getChatClient()
                .prompt()
                .advisors(spec -> spec.params(new LlmInvocationContext(
                        "query",
                        "query-review",
                        "query-review-b",
                        "reviewer",
                        "query.review.anthropic.route-b"
                ).toAdvisorParams()))
                .system("review-system-b")
                .user("review-user-b")
                .call()
                .chatClientResponse();

        assertThat(secondRouteA).isSameAs(firstRouteA);
        assertThat(routeBHandle).isNotSameAs(firstRouteA);
        assertThat(firstResponse.chatResponse().getResult().getOutput().getText()).isEqualTo("anthropic-a-ok");
        assertThat(secondResponse.chatResponse().getResult().getOutput().getText()).isEqualTo("anthropic-b-ok");
        assertThat(firstResponse.context().get("capturedScene")).isEqualTo("query");
        assertThat(firstResponse.context().get("capturedPurpose")).isEqualTo("query-review");
        assertThat(firstResponse.context().get("capturedScopeId")).isEqualTo("query-review-a");
        assertThat(secondResponse.context().get("capturedRouteLabel")).isEqualTo("query.review.anthropic.route-b");
        assertThat(routeA.getRequestCount()).isEqualTo(1);
        assertThat(routeB.getRequestCount()).isEqualTo(1);
        assertThat(routeA.getCapturedModels()).containsExactly("claude-sonnet-4-6");
        assertThat(routeB.getCapturedModels()).containsExactly("claude-haiku-3-5");
        assertThat(routeA.getCapturedTopPs()).containsExactly(0.8D);
        assertThat(routeA.getCapturedTopKs()).containsExactly(12);
        assertThat(routeB.getCapturedTopPs()).containsExactly(0.6D);
        assertThat(routeB.getCapturedTopKs()).containsExactly(8);
        assertThat(registry.getClientCount()).isEqualTo(2);
    }

    private StubOpenAiChatServer startOpenAiStubServer(String answerText) throws IOException {
        StubOpenAiChatServer stubServer = new StubOpenAiChatServer(answerText);
        stubServer.start();
        openAiStubServers.add(stubServer);
        return stubServer;
    }

    private StubOpenAiChatServer startOpenAiStubServer(
            String answerText,
            int transientFailureCount,
            boolean eventStreamUnlessJsonAccept
    ) throws IOException {
        return startOpenAiStubServer(answerText, transientFailureCount, eventStreamUnlessJsonAccept, null);
    }

    private StubOpenAiChatServer startOpenAiStubServer(
            String answerText,
            int transientFailureCount,
            boolean eventStreamUnlessJsonAccept,
            String forcedResponseContentType
    ) throws IOException {
        StubOpenAiChatServer stubServer = new StubOpenAiChatServer(
                answerText,
                transientFailureCount,
                eventStreamUnlessJsonAccept,
                forcedResponseContentType
        );
        stubServer.start();
        openAiStubServers.add(stubServer);
        return stubServer;
    }

    private StubAnthropicChatServer startAnthropicStubServer(String answerText) throws IOException {
        StubAnthropicChatServer stubServer = new StubAnthropicChatServer(answerText);
        stubServer.start();
        anthropicStubServers.add(stubServer);
        return stubServer;
    }

    private LlmRouteResolution createRouteResolution(
            String baseUrl,
            String apiKey,
            String modelName,
            BigDecimal temperature,
            Integer maxTokens,
            String routeLabel
    ) {
        return new LlmRouteResolution(
                "query_request",
                "query-1",
                "query",
                "answer",
                Long.valueOf(11L),
                Long.valueOf(22L),
                Integer.valueOf(3),
                routeLabel,
                "openai",
                baseUrl,
                apiKey,
                modelName,
                temperature,
                maxTokens,
                Integer.valueOf(30),
                "{\"reasoning_effort\":\"medium\"}",
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                true
        );
    }

    private LlmRouteResolution createAnthropicRouteResolution(
            String baseUrl,
            String apiKey,
            String modelName,
            BigDecimal temperature,
            Integer maxTokens,
            String routeLabel,
            String extraOptionsJson
    ) {
        return new LlmRouteResolution(
                "query_request",
                "query-1",
                "query",
                "reviewer",
                Long.valueOf(31L),
                Long.valueOf(42L),
                Integer.valueOf(5),
                routeLabel,
                "anthropic",
                baseUrl,
                apiKey,
                modelName,
                temperature,
                maxTokens,
                Integer.valueOf(30),
                extraOptionsJson,
                new BigDecimal("0.003"),
                new BigDecimal("0.015"),
                true
        );
    }
}
