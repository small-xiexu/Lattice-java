package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 动态 ChatClient 注册表
 *
 * 职责：按运行时路由参数构造并缓存 OpenAI / Anthropic ChatClient 句柄
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ChatClientRegistry {

    private final RestClient.Builder restClientBuilder;

    private final WebClient.Builder webClientBuilder;

    private final ObjectMapper objectMapper;

    private final AdvisorChainFactory advisorChainFactory;

    private final AnthropicConnectionProperties anthropicConnectionProperties;

    private final AnthropicChatProperties anthropicChatProperties;

    private final ConcurrentMap<String, ChatClientHandle> clientCache = new ConcurrentHashMap<String, ChatClientHandle>();

    /**
     * 创建动态 ChatClient 注册表。
     *
     * @param restClientBuilder RestClient 构建器
     * @param webClientBuilder WebClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param advisorChainFactory Advisor 链工厂
     */
    @Autowired
    public ChatClientRegistry(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AdvisorChainFactory advisorChainFactory,
            AnthropicConnectionProperties anthropicConnectionProperties,
            AnthropicChatProperties anthropicChatProperties
    ) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.advisorChainFactory = advisorChainFactory;
        this.anthropicConnectionProperties = anthropicConnectionProperties;
        this.anthropicChatProperties = anthropicChatProperties;
    }

    /**
     * 创建动态 ChatClient 注册表。
     *
     * @param restClientBuilder RestClient 构建器
     * @param webClientBuilder WebClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param advisorChainFactory Advisor 链工厂
     */
    public ChatClientRegistry(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AdvisorChainFactory advisorChainFactory
    ) {
        this(
                restClientBuilder,
                webClientBuilder,
                objectMapper,
                advisorChainFactory,
                null,
                null
        );
    }

    /**
     * 获取或创建动态 ChatClient 句柄。
     *
     * @param routeResolution 路由解析结果
     * @return ChatClient 句柄
     */
    public ChatClientHandle getOrCreate(LlmRouteResolution routeResolution) {
        validateSupportedProvider(routeResolution);
        String cacheKey = buildCacheKey(routeResolution);
        return clientCache.computeIfAbsent(cacheKey, key -> createHandle(routeResolution));
    }

    /**
     * 返回当前缓存的动态 ChatClient 数量。
     *
     * @return 缓存数量
     */
    int getClientCount() {
        return clientCache.size();
    }

    private ChatClientHandle createHandle(LlmRouteResolution routeResolution) {
        String providerType = normalizeProviderType(routeResolution == null ? null : routeResolution.getProviderType());
        if ("anthropic".equals(providerType)) {
            return createAnthropicHandle(routeResolution);
        }
        return createOpenAiHandle(routeResolution);
    }

    private ChatClientHandle createOpenAiHandle(LlmRouteResolution routeResolution) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(routeResolution.getBaseUrl())
                .apiKey(routeResolution.getApiKey())
                .completionsPath(resolveCompletionPath(routeResolution.getBaseUrl()))
                .restClientBuilder(createRestClientBuilder(routeResolution.getTimeoutSeconds()))
                .webClientBuilder(webClientBuilder.clone())
                .build();
        OpenAiChatOptions defaultOptions = buildDefaultOptions(routeResolution);
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .toolCallingManager(DefaultToolCallingManager.builder()
                        .observationRegistry(ObservationRegistry.NOOP)
                        .build())
                .retryTemplate(createNoRetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisorChainFactory.createDefaultAdvisors())
                .build();
        return new ChatClientHandle(chatClient);
    }

    private ChatClientHandle createAnthropicHandle(LlmRouteResolution routeResolution) {
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .baseUrl(routeResolution.getBaseUrl())
                .apiKey(routeResolution.getApiKey())
                .completionsPath(resolveAnthropicCompletionPath(routeResolution.getBaseUrl()))
                .anthropicVersion(resolveAnthropicVersion())
                .anthropicBetaFeatures(resolveAnthropicBetaFeatures())
                .restClientBuilder(createRestClientBuilder(routeResolution.getTimeoutSeconds()))
                .webClientBuilder(webClientBuilder.clone())
                .build();
        AnthropicChatOptions defaultOptions = buildAnthropicOptions(routeResolution);
        AnthropicChatModel chatModel = new AnthropicChatModel(
                anthropicApi,
                defaultOptions,
                DefaultToolCallingManager.builder()
                        .observationRegistry(ObservationRegistry.NOOP)
                        .build(),
                createNoRetryTemplate(),
                ObservationRegistry.NOOP
        );
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisorChainFactory.createDefaultAdvisors())
                .build();
        return new ChatClientHandle(chatClient);
    }

    private RestClient.Builder createRestClientBuilder(Integer timeoutSeconds) {
        int resolvedTimeoutSeconds = resolveTimeout(timeoutSeconds);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(resolvedTimeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(resolvedTimeoutSeconds));
        return restClientBuilder.clone().requestFactory(new BufferingClientHttpRequestFactory(requestFactory));
    }

    /**
     * 创建单次请求模板，避免与上层调用执行器形成双层重试。
     *
     * @return 单次请求模板
     */
    private RetryTemplate createNoRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(1)
                .build();
    }

    private OpenAiChatOptions buildDefaultOptions(LlmRouteResolution routeResolution) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(routeResolution.getModelName());
        BigDecimal temperature = routeResolution.getTemperature();
        if (temperature != null) {
            builder.temperature(temperature.doubleValue());
        }
        if (routeResolution.getMaxTokens() != null) {
            builder.maxTokens(routeResolution.getMaxTokens());
        }
        Map<String, Object> extraBody = parseExtraBody(routeResolution.getExtraOptionsJson());
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }
        return builder.build();
    }

    private AnthropicChatOptions buildAnthropicOptions(LlmRouteResolution routeResolution) {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
                .model(routeResolution.getModelName());
        BigDecimal temperature = routeResolution.getTemperature();
        if (temperature != null) {
            builder.temperature(temperature.doubleValue());
        }
        if (routeResolution.getMaxTokens() != null) {
            builder.maxTokens(routeResolution.getMaxTokens());
        }
        Double topP = readDouble(routeResolution.getExtraOptionsJson(), "top_p", "topP");
        if (topP != null) {
            builder.topP(topP);
        }
        Integer topK = readInteger(routeResolution.getExtraOptionsJson(), "top_k", "topK");
        if (topK != null) {
            builder.topK(topK);
        }
        return builder.build();
    }

    private Map<String, Object> parseExtraBody(String extraOptionsJson) {
        if (!StringUtils.hasText(extraOptionsJson)) {
            return Map.of();
        }
        try {
            JsonNode rootNode = objectMapper.readTree(extraOptionsJson);
            if (!rootNode.isObject()) {
                return Map.of();
            }
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            rootNode.fields().forEachRemaining(entry -> values.put(entry.getKey(), convertNode(entry.getValue())));
            return values;
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse OpenAI chat extra options", exception);
        }
    }

    private Object convertNode(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        if (jsonNode.isBoolean()) {
            return Boolean.valueOf(jsonNode.booleanValue());
        }
        if (jsonNode.isInt() || jsonNode.isLong()) {
            return Long.valueOf(jsonNode.longValue());
        }
        if (jsonNode.isFloat() || jsonNode.isDouble() || jsonNode.isBigDecimal()) {
            return jsonNode.decimalValue();
        }
        if (jsonNode.isTextual()) {
            return jsonNode.textValue();
        }
        if (jsonNode.isArray()) {
            List<Object> values = new ArrayList<Object>();
            for (JsonNode itemNode : jsonNode) {
                values.add(convertNode(itemNode));
            }
            return values;
        }
        if (jsonNode.isObject()) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            jsonNode.fields().forEachRemaining(entry -> values.put(entry.getKey(), convertNode(entry.getValue())));
            return values;
        }
        return jsonNode.toString();
    }

    private String buildCacheKey(LlmRouteResolution routeResolution) {
        StringBuilder builder = new StringBuilder();
        builder.append(normalizeProviderType(routeResolution.getProviderType()));
        builder.append("|");
        builder.append(safeValue(routeResolution.getBaseUrl()));
        builder.append("|");
        builder.append(safeValue(routeResolution.getModelName()));
        builder.append("|");
        builder.append(routeResolution.getTemperature() == null ? "" : routeResolution.getTemperature().toPlainString());
        builder.append("|");
        builder.append(routeResolution.getMaxTokens() == null ? "" : routeResolution.getMaxTokens());
        builder.append("|");
        builder.append(routeResolution.getTimeoutSeconds() == null ? "" : routeResolution.getTimeoutSeconds());
        builder.append("|");
        builder.append(safeValue(routeResolution.getExtraOptionsJson()));
        builder.append("|");
        builder.append(hash(routeResolution.getApiKey()));
        return builder.toString();
    }

    private void validateSupportedProvider(LlmRouteResolution routeResolution) {
        String providerType = normalizeProviderType(routeResolution == null ? null : routeResolution.getProviderType());
        if (!"openai".equals(providerType) && !"openai_compatible".equals(providerType) && !"anthropic".equals(providerType)) {
            throw new IllegalArgumentException("Unsupported chat client provider type: " + providerType);
        }
    }

    private String normalizeProviderType(String providerType) {
        if (!StringUtils.hasText(providerType)) {
            return "openai";
        }
        return providerType.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveAnthropicCompletionPath(String baseUrl) {
        if (baseUrl != null && baseUrl.endsWith("/v1")) {
            return "/messages";
        }
        return "/v1/messages";
    }

    private String resolveCompletionPath(String baseUrl) {
        if (baseUrl != null && baseUrl.endsWith("/v1")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    private Double readDouble(String extraOptionsJson, String... candidateKeys) {
        JsonNode valueNode = readValue(extraOptionsJson, candidateKeys);
        if (valueNode == null || !valueNode.isNumber()) {
            if (anthropicChatProperties == null || anthropicChatProperties.getOptions() == null) {
                return null;
            }
            return anthropicChatProperties.getOptions().getTopP();
        }
        return valueNode.doubleValue();
    }

    private Integer readInteger(String extraOptionsJson, String... candidateKeys) {
        JsonNode valueNode = readValue(extraOptionsJson, candidateKeys);
        if (valueNode == null || !valueNode.isNumber()) {
            if (anthropicChatProperties == null || anthropicChatProperties.getOptions() == null) {
                return null;
            }
            return anthropicChatProperties.getOptions().getTopK();
        }
        return Integer.valueOf(valueNode.intValue());
    }

    private JsonNode readValue(String extraOptionsJson, String... candidateKeys) {
        if (!StringUtils.hasText(extraOptionsJson)) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(extraOptionsJson);
            if (!rootNode.isObject()) {
                return null;
            }
            for (String candidateKey : candidateKeys) {
                if (rootNode.has(candidateKey)) {
                    return rootNode.get(candidateKey);
                }
            }
            return null;
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse chat client extra options", exception);
        }
    }

    private int resolveTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds.intValue() <= 0) {
            return 300;
        }
        return timeoutSeconds.intValue();
    }

    private String resolveAnthropicVersion() {
        if (anthropicConnectionProperties != null && StringUtils.hasText(anthropicConnectionProperties.getVersion())) {
            return anthropicConnectionProperties.getVersion();
        }
        return AnthropicApi.DEFAULT_ANTHROPIC_VERSION;
    }

    private String resolveAnthropicBetaFeatures() {
        if (anthropicConnectionProperties != null && StringUtils.hasText(anthropicConnectionProperties.getBetaVersion())) {
            return anthropicConnectionProperties.getBetaVersion();
        }
        return AnthropicApi.DEFAULT_ANTHROPIC_BETA_VERSION;
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String hash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    /**
     * 动态 ChatClient 句柄。
     *
     * 职责：暴露已缓存的 ChatClient
     *
     * @author xiexu
     */
    static final class ChatClientHandle {

        private final ChatClient chatClient;

        private ChatClientHandle(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        ChatClient getChatClient() {
            return chatClient;
        }
    }
}
