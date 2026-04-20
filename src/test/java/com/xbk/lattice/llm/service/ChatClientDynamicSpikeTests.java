package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatClient 动态构造 Spike 测试
 *
 * 职责：验证运行时动态创建 OpenAI ChatClient、最小 Advisor 上下文透传与多路由隔离可行性
 *
 * @author xiexu
 */
class ChatClientDynamicSpikeTests {

    private final List<StubOpenAiServer> stubServers = new ArrayList<StubOpenAiServer>();

    /**
     * 关闭测试中启动的本地 OpenAI stub 服务。
     */
    @AfterEach
    void tearDown() {
        for (StubOpenAiServer stubServer : stubServers) {
            stubServer.stop();
        }
        stubServers.clear();
    }

    /**
     * 验证同一个动态 ChatClient 可在每次调用中读取不同的 per-request 上下文。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldBuildDynamicChatClientAndPassPerRequestContext() throws IOException {
        StubOpenAiServer stubServer = startStubServer("dynamic-route-ok");
        DynamicOpenAiChatClientRegistry registry = new DynamicOpenAiChatClientRegistry();
        SpikeClientHandle clientHandle = registry.getOrCreate(
                stubServer.getBaseUrl(),
                "test-openai-key",
                "gpt-5.4",
                Double.valueOf(0.2D),
                Integer.valueOf(96)
        );

        ChatClientResponse firstResponse = clientHandle.getChatClient()
                .prompt()
                .advisors(spec -> spec
                        .param("scene", "query")
                        .param("purpose", "ask-why")
                        .param("scopeId", "source:orders"))
                .system("你是测试助手")
                .user("为什么订单服务不直接调用库存服务")
                .call()
                .chatClientResponse();

        ChatClientResponse secondResponse = clientHandle.getChatClient()
                .prompt()
                .advisors(spec -> spec
                        .param("scene", "compile")
                        .param("purpose", "review-pass")
                        .param("scopeId", "compile-job:1"))
                .system("你是复核助手")
                .user("请检查当前知识条目是否忠于原始资料")
                .call()
                .chatClientResponse();

        List<LlmInvocationContext> invocationContexts = clientHandle.getRecordingAdvisor().getInvocationContexts();
        assertThat(extractContent(firstResponse)).isEqualTo("dynamic-route-ok");
        assertThat(extractContent(secondResponse)).isEqualTo("dynamic-route-ok");
        assertThat(invocationContexts).hasSize(2);
        assertThat(invocationContexts.get(0).getScene()).isEqualTo("query");
        assertThat(invocationContexts.get(0).getPurpose()).isEqualTo("ask-why");
        assertThat(invocationContexts.get(0).getScopeId()).isEqualTo("source:orders");
        assertThat(invocationContexts.get(1).getScene()).isEqualTo("compile");
        assertThat(invocationContexts.get(1).getPurpose()).isEqualTo("review-pass");
        assertThat(invocationContexts.get(1).getScopeId()).isEqualTo("compile-job:1");
        assertThat(firstResponse.context().get("capturedScene")).isEqualTo("query");
        assertThat(secondResponse.context().get("capturedScopeId")).isEqualTo("compile-job:1");
        assertThat(stubServer.getRequestCount()).isEqualTo(2);
        assertThat(stubServer.getCapturedModels()).containsExactly("gpt-5.4", "gpt-5.4");
    }

    /**
     * 验证不同运行时路由参数会生成并命中各自隔离的 ChatClient。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldIsolateClientsAcrossDifferentRuntimeRoutes() throws IOException {
        StubOpenAiServer routeA = startStubServer("route-a-ok");
        StubOpenAiServer routeB = startStubServer("route-b-ok");
        DynamicOpenAiChatClientRegistry registry = new DynamicOpenAiChatClientRegistry();

        SpikeClientHandle firstRouteA = registry.getOrCreate(
                routeA.getBaseUrl(),
                "key-a",
                "gpt-5.4",
                Double.valueOf(0.1D),
                Integer.valueOf(64)
        );
        SpikeClientHandle secondRouteA = registry.getOrCreate(
                routeA.getBaseUrl(),
                "key-a",
                "gpt-5.4",
                Double.valueOf(0.1D),
                Integer.valueOf(64)
        );
        SpikeClientHandle routeBHandle = registry.getOrCreate(
                routeB.getBaseUrl(),
                "key-b",
                "gpt-4.1-mini",
                Double.valueOf(0.3D),
                Integer.valueOf(128)
        );

        String routeAResult = firstRouteA.getChatClient()
                .prompt()
                .advisors(spec -> spec
                        .param("scene", "query")
                        .param("purpose", "route-a")
                        .param("scopeId", "scope-a"))
                .system("route-a-system")
                .user("route-a-user")
                .call()
                .content();
        String routeBResult = routeBHandle.getChatClient()
                .prompt()
                .advisors(spec -> spec
                        .param("scene", "query")
                        .param("purpose", "route-b")
                        .param("scopeId", "scope-b"))
                .system("route-b-system")
                .user("route-b-user")
                .call()
                .content();

        assertThat(secondRouteA).isSameAs(firstRouteA);
        assertThat(routeBHandle).isNotSameAs(firstRouteA);
        assertThat(routeAResult).isEqualTo("route-a-ok");
        assertThat(routeBResult).isEqualTo("route-b-ok");
        assertThat(routeA.getRequestCount()).isEqualTo(1);
        assertThat(routeB.getRequestCount()).isEqualTo(1);
        assertThat(routeA.getCapturedModels()).containsExactly("gpt-5.4");
        assertThat(routeB.getCapturedModels()).containsExactly("gpt-4.1-mini");
        assertThat(registry.getClientCount()).isEqualTo(2);
    }

    /**
     * 启动本地 OpenAI Chat Completions stub 服务。
     *
     * @param answerText stub 返回文本
     * @return stub 服务
     * @throws IOException IO 异常
     */
    private StubOpenAiServer startStubServer(String answerText) throws IOException {
        StubOpenAiServer stubServer = new StubOpenAiServer(answerText);
        stubServer.start();
        stubServers.add(stubServer);
        return stubServer;
    }

    /**
     * 提取 ChatClient 响应文本。
     *
     * @param response ChatClient 响应
     * @return 返回文本
     */
    private String extractContent(ChatClientResponse response) {
        return response.chatResponse().getResult().getOutput().getText();
    }

    /**
     * 动态 OpenAI ChatClient 注册表。
     *
     * 职责：按运行时路由参数构造并缓存 ChatClient + Advisor 句柄
     *
     * @author xiexu
     */
    private static class DynamicOpenAiChatClientRegistry {

        private final ConcurrentMap<String, SpikeClientHandle> clientCache = new ConcurrentHashMap<String, SpikeClientHandle>();

        /**
         * 获取或创建动态 ChatClient 句柄。
         *
         * @param baseUrl 运行时 Base URL
         * @param apiKey 运行时 API Key
         * @param modelName 运行时模型名称
         * @param temperature 运行时温度
         * @param maxTokens 运行时最大输出 Token
         * @return ChatClient 句柄
         */
        private SpikeClientHandle getOrCreate(
                String baseUrl,
                String apiKey,
                String modelName,
                Double temperature,
                Integer maxTokens
        ) {
            String cacheKey = buildCacheKey(baseUrl, apiKey, modelName, temperature, maxTokens);
            return clientCache.computeIfAbsent(cacheKey, key -> createHandle(baseUrl, apiKey, modelName, temperature, maxTokens));
        }

        /**
         * 返回当前已缓存的动态客户端数量。
         *
         * @return 缓存数量
         */
        private int getClientCount() {
            return clientCache.size();
        }

        /**
         * 根据运行时路由参数创建 ChatClient 句柄。
         *
         * @param baseUrl 运行时 Base URL
         * @param apiKey 运行时 API Key
         * @param modelName 运行时模型名称
         * @param temperature 运行时温度
         * @param maxTokens 运行时最大输出 Token
         * @return ChatClient 句柄
         */
        private SpikeClientHandle createHandle(
                String baseUrl,
                String apiKey,
                String modelName,
                Double temperature,
                Integer maxTokens
        ) {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .completionsPath("/chat/completions")
                    .restClientBuilder(RestClient.builder())
                    .webClientBuilder(WebClient.builder())
                    .build();
            OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                    .model(modelName)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(defaultOptions)
                    .toolCallingManager(DefaultToolCallingManager.builder()
                            .observationRegistry(ObservationRegistry.NOOP)
                            .build())
                    .retryTemplate(RetryTemplate.defaultInstance())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build();
            RecordingAdvisor recordingAdvisor = new RecordingAdvisor();
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultAdvisors(recordingAdvisor)
                    .build();
            return new SpikeClientHandle(chatClient, recordingAdvisor);
        }

        /**
         * 构建运行时客户端缓存键。
         *
         * @param baseUrl 运行时 Base URL
         * @param apiKey 运行时 API Key
         * @param modelName 运行时模型名称
         * @param temperature 运行时温度
         * @param maxTokens 运行时最大输出 Token
         * @return 缓存键
         */
        private String buildCacheKey(
                String baseUrl,
                String apiKey,
                String modelName,
                Double temperature,
                Integer maxTokens
        ) {
            return safeValue(baseUrl)
                    + "|"
                    + safeValue(apiKey)
                    + "|"
                    + safeValue(modelName)
                    + "|"
                    + (temperature == null ? "" : String.valueOf(temperature))
                    + "|"
                    + (maxTokens == null ? "" : String.valueOf(maxTokens));
        }

        /**
         * 规范化空字符串。
         *
         * @param value 原始值
         * @return 非空字符串
         */
        private String safeValue(String value) {
            return value == null ? "" : value;
        }
    }

    /**
     * 动态 ChatClient 句柄。
     *
     * 职责：同时暴露 ChatClient 与用于断言的记录型 Advisor
     *
     * @author xiexu
     */
    private static class SpikeClientHandle {

        private final ChatClient chatClient;

        private final RecordingAdvisor recordingAdvisor;

        private SpikeClientHandle(ChatClient chatClient, RecordingAdvisor recordingAdvisor) {
            this.chatClient = chatClient;
            this.recordingAdvisor = recordingAdvisor;
        }

        /**
         * 返回动态 ChatClient。
         *
         * @return ChatClient
         */
        private ChatClient getChatClient() {
            return chatClient;
        }

        /**
         * 返回记录型 Advisor。
         *
         * @return Advisor
         */
        private RecordingAdvisor getRecordingAdvisor() {
            return recordingAdvisor;
        }
    }

    /**
     * 记录型 Advisor。
     *
     * 职责：读取 ChatClientRequest.context() 中的最小调用上下文，并写回响应上下文
     *
     * @author xiexu
     */
    private static class RecordingAdvisor implements CallAdvisor {

        private final List<LlmInvocationContext> invocationContexts = new CopyOnWriteArrayList<LlmInvocationContext>();

        /**
         * 返回 Advisor 名称。
         *
         * @return 名称
         */
        @Override
        public String getName() {
            return "recording-advisor";
        }

        /**
         * 返回 Advisor 顺序。
         *
         * @return 顺序
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /**
         * 记录调用上下文，并继续执行后续调用链。
         *
         * @param request 当前请求
         * @param chain 调用链
         * @return 调用结果
         */
        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            LlmInvocationContext invocationContext = LlmInvocationContext.from(request.context());
            invocationContexts.add(invocationContext);
            ChatClientResponse response = chain.nextCall(request);
            return response.mutate()
                    .context("capturedScene", invocationContext.getScene())
                    .context("capturedPurpose", invocationContext.getPurpose())
                    .context("capturedScopeId", invocationContext.getScopeId())
                    .build();
        }

        /**
         * 返回已记录的调用上下文。
         *
         * @return 调用上下文列表
         */
        private List<LlmInvocationContext> getInvocationContexts() {
            return invocationContexts;
        }
    }

    /**
     * 最小调用上下文。
     *
     * 职责：承接 Spike 里需要验证的 scene / purpose / scopeId
     *
     * @author xiexu
     */
    private static class LlmInvocationContext {

        private final String scene;

        private final String purpose;

        private final String scopeId;

        private LlmInvocationContext(String scene, String purpose, String scopeId) {
            this.scene = scene;
            this.purpose = purpose;
            this.scopeId = scopeId;
        }

        /**
         * 从请求上下文中解析最小调用上下文。
         *
         * @param context 请求上下文
         * @return 最小调用上下文
         */
        private static LlmInvocationContext from(Map<String, Object> context) {
            String scene = toStringValue(context, "scene");
            String purpose = toStringValue(context, "purpose");
            String scopeId = toStringValue(context, "scopeId");
            return new LlmInvocationContext(scene, purpose, scopeId);
        }

        /**
         * 返回场景。
         *
         * @return 场景
         */
        private String getScene() {
            return scene;
        }

        /**
         * 返回用途。
         *
         * @return 用途
         */
        private String getPurpose() {
            return purpose;
        }

        /**
         * 返回作用域标识。
         *
         * @return 作用域标识
         */
        private String getScopeId() {
            return scopeId;
        }

        /**
         * 将上下文值转换为字符串。
         *
         * @param context 请求上下文
         * @param key 键名
         * @return 字符串值
         */
        private static String toStringValue(Map<String, Object> context, String key) {
            if (context == null || !context.containsKey(key) || context.get(key) == null) {
                return "";
            }
            return String.valueOf(context.get(key));
        }
    }

    /**
     * 本地 OpenAI stub 服务。
     *
     * 职责：模拟 Chat Completions 接口，并记录收到的运行时模型与请求次数
     *
     * @author xiexu
     */
    private static class StubOpenAiServer {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final HttpServer httpServer;

        private final String answerText;

        private final AtomicInteger requestCount = new AtomicInteger();

        private final List<String> capturedModels = new CopyOnWriteArrayList<String>();

        private StubOpenAiServer(String answerText) throws IOException {
            this.answerText = answerText;
            this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            this.httpServer.createContext("/v1/chat/completions", new ChatCompletionHandler());
        }

        /**
         * 启动 stub 服务。
         */
        private void start() {
            httpServer.start();
        }

        /**
         * 关闭 stub 服务。
         */
        private void stop() {
            httpServer.stop(0);
        }

        /**
         * 返回对外 Base URL。
         *
         * @return Base URL
         */
        private String getBaseUrl() {
            return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/v1";
        }

        /**
         * 返回收到的请求次数。
         *
         * @return 请求次数
         */
        private int getRequestCount() {
            return requestCount.get();
        }

        /**
         * 返回收到的模型名称列表。
         *
         * @return 模型名称列表
         */
        private List<String> getCapturedModels() {
            return capturedModels;
        }

        /**
         * Chat Completions 处理器。
         *
         * 职责：读取请求模型并返回固定 OpenAI 响应
         *
         * @author xiexu
         */
        private class ChatCompletionHandler implements HttpHandler {

            /**
             * 处理本地 stub 请求。
             *
             * @param exchange HTTP 交换对象
             * @throws IOException IO 异常
             */
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requestCount.incrementAndGet();
                capturedModels.add(readModelName(requestBody));
                String responseBody = """
                        {
                          "id": "chatcmpl_spike",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "%s"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 12,
                            "completion_tokens": 4
                          }
                        }
                        """.formatted(answerText);
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            }

            /**
             * 读取请求中的模型名称。
             *
             * @param requestBody 原始请求体
             * @return 模型名称
             * @throws IOException IO 异常
             */
            private String readModelName(String requestBody) throws IOException {
                JsonNode rootNode = OBJECT_MAPPER.readTree(requestBody);
                return rootNode.path("model").asText();
            }
        }
    }
}
