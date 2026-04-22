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

    private final List<StubOpenAiChatServer> stubServers = new ArrayList<StubOpenAiChatServer>();

    @AfterEach
    void tearDown() {
        for (StubOpenAiChatServer stubServer : stubServers) {
            stubServer.stop();
        }
        stubServers.clear();
    }

    @Test
    void shouldCacheAndIsolateDynamicChatClientsAcrossRoutes() throws IOException {
        StubOpenAiChatServer routeA = startStubServer("route-a-ok");
        StubOpenAiChatServer routeB = startStubServer("route-b-ok");
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
        assertThat(registry.getClientCount()).isEqualTo(2);
    }

    private StubOpenAiChatServer startStubServer(String answerText) throws IOException {
        StubOpenAiChatServer stubServer = new StubOpenAiChatServer(answerText);
        stubServer.start();
        stubServers.add(stubServer);
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
}
