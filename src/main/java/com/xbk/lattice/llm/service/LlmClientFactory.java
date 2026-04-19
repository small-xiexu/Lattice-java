package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * LLM 客户端工厂
 *
 * 职责：按快照路由动态创建并缓存底层 Provider 客户端
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LlmClientFactory {

    private final RestClient.Builder restClientBuilder;

    private final ObjectMapper objectMapper;

    private final AnthropicConnectionProperties anthropicConnectionProperties;

    private final AnthropicChatProperties anthropicChatProperties;

    private final ConcurrentMap<String, LlmClient> clientCache = new ConcurrentHashMap<String, LlmClient>();

    /**
     * 创建 LLM 客户端工厂。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param anthropicConnectionProperties Anthropic 连接配置
     * @param anthropicChatProperties Anthropic Chat 配置
     */
    public LlmClientFactory(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AnthropicConnectionProperties anthropicConnectionProperties,
            AnthropicChatProperties anthropicChatProperties
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.anthropicConnectionProperties = anthropicConnectionProperties;
        this.anthropicChatProperties = anthropicChatProperties;
    }

    /**
     * 按路由获取底层客户端。
     *
     * @param routeResolution 路由解析结果
     * @return LLM 客户端
     */
    public LlmClient getClient(LlmRouteResolution routeResolution) {
        String cacheKey = buildClientCacheKey(routeResolution);
        return clientCache.computeIfAbsent(cacheKey, key -> createClient(routeResolution));
    }

    private LlmClient createClient(LlmRouteResolution routeResolution) {
        String providerType = normalizeProviderType(routeResolution.getProviderType());
        if ("anthropic".equals(providerType)) {
            return createAnthropicClient(routeResolution);
        }
        if ("openai".equals(providerType) || "openai_compatible".equals(providerType)) {
            return createOpenAiClient(routeResolution);
        }
        throw new IllegalArgumentException("Unsupported llm provider type: " + routeResolution.getProviderType());
    }

    private LlmClient createAnthropicClient(LlmRouteResolution routeResolution) {
        Double topP = readDouble(routeResolution.getExtraOptionsJson(), "top_p", "topP");
        Integer topK = readInteger(routeResolution.getExtraOptionsJson(), "top_k", "topK");
        return new AnthropicMessageApiLlmClient(
                restClientBuilder,
                objectMapper,
                routeResolution.getBaseUrl(),
                routeResolution.getApiKey(),
                anthropicConnectionProperties.getVersion(),
                anthropicConnectionProperties.getBetaVersion(),
                routeResolution.getModelName(),
                routeResolution.getMaxTokens(),
                routeResolution.getTemperature() == null ? null : routeResolution.getTemperature().doubleValue(),
                topP,
                topK,
                routeResolution.getTimeoutSeconds()
        );
    }

    private LlmClient createOpenAiClient(LlmRouteResolution routeResolution) {
        return new OpenAiCompatibleLlmClient(
                restClientBuilder,
                objectMapper,
                routeResolution.getBaseUrl(),
                routeResolution.getApiKey(),
                routeResolution.getModelName(),
                routeResolution.getTemperature() == null ? null : routeResolution.getTemperature().doubleValue(),
                routeResolution.getMaxTokens(),
                routeResolution.getTimeoutSeconds(),
                routeResolution.getExtraOptionsJson()
        );
    }

    private Double readDouble(String extraOptionsJson, String... candidateKeys) {
        JsonNode valueNode = readValue(extraOptionsJson, candidateKeys);
        if (valueNode == null || !valueNode.isNumber()) {
            return anthropicChatProperties.getOptions().getTopP();
        }
        return valueNode.doubleValue();
    }

    private Integer readInteger(String extraOptionsJson, String... candidateKeys) {
        JsonNode valueNode = readValue(extraOptionsJson, candidateKeys);
        if (valueNode == null || !valueNode.isNumber()) {
            return anthropicChatProperties.getOptions().getTopK();
        }
        return Integer.valueOf(valueNode.intValue());
    }

    private JsonNode readValue(String extraOptionsJson, String... candidateKeys) {
        if (extraOptionsJson == null || extraOptionsJson.isBlank()) {
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
            throw new IllegalStateException("Failed to parse llm extra options", exception);
        }
    }

    private String buildClientCacheKey(LlmRouteResolution routeResolution) {
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

    private String normalizeProviderType(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            return "openai";
        }
        return providerType.trim().toLowerCase(Locale.ROOT);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String hash(String value) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(value.getBytes());
            return toHex(bytes);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte item : bytes) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
