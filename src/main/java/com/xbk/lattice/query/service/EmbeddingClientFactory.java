package com.xbk.lattice.query.service;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Embedding 客户端工厂
 *
 * 职责：按 embedding 路由动态创建并缓存底层 EmbeddingModel 客户端
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class EmbeddingClientFactory {

    private final RestClient.Builder restClientBuilder;

    private final WebClient.Builder webClientBuilder;

    private final ConcurrentMap<String, EmbeddingModel> clientCache = new ConcurrentHashMap<String, EmbeddingModel>();

    /**
     * 创建 Embedding 客户端工厂。
     *
     * @param restClientBuilder RestClient 构建器
     * @param webClientBuilder WebClient 构建器
     */
    public EmbeddingClientFactory(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * 按路由返回 EmbeddingModel。
     *
     * @param routeResolution 路由解析结果
     * @return EmbeddingModel
     */
    public EmbeddingModel getOrCreate(EmbeddingRouteResolution routeResolution) {
        String cacheKey = buildCacheKey(routeResolution);
        return clientCache.computeIfAbsent(cacheKey, key -> createClient(routeResolution));
    }

    /**
     * 监听 embedding profile 切换，清理本地缓存。
     *
     * @param event profile 变更事件
     */
    @EventListener
    public void onProfileChanged(EmbeddingProfileChangedEvent event) {
        clientCache.clear();
    }

    private EmbeddingModel createClient(EmbeddingRouteResolution routeResolution) {
        String providerType = normalizeProviderType(routeResolution.getProviderType());
        if ("ollama".equals(providerType)) {
            return createOllamaClient(routeResolution);
        }
        if ("openai".equals(providerType)
                || "openai_compatible".equals(providerType)
                || "anthropic".equals(providerType)) {
            return createOpenAiCompatibleClient(routeResolution);
        }
        throw new IllegalArgumentException("Unsupported embedding provider type: " + routeResolution.getProviderType());
    }

    private EmbeddingModel createOpenAiCompatibleClient(EmbeddingRouteResolution routeResolution) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(routeResolution.getBaseUrl())
                .apiKey(routeResolution.getApiKey())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(routeResolution.getModelName())
                .dimensions(routeResolution.getExpectedDimensions())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }

    private EmbeddingModel createOllamaClient(EmbeddingRouteResolution routeResolution) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(routeResolution.getBaseUrl())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
                .model(routeResolution.getModelName())
                .build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }

    private String buildCacheKey(EmbeddingRouteResolution routeResolution) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(routeResolution.getProfileId());
        stringBuilder.append("|");
        stringBuilder.append(normalizeProviderType(routeResolution.getProviderType()));
        stringBuilder.append("|");
        stringBuilder.append(safeValue(routeResolution.getBaseUrl()));
        stringBuilder.append("|");
        stringBuilder.append(safeValue(routeResolution.getModelName()));
        stringBuilder.append("|");
        stringBuilder.append(routeResolution.getExpectedDimensions() == null ? "" : routeResolution.getExpectedDimensions());
        stringBuilder.append("|");
        stringBuilder.append(hash(routeResolution.getApiKey()));
        return stringBuilder.toString();
    }

    private String normalizeProviderType(String providerType) {
        if (!StringUtils.hasText(providerType)) {
            return "openai";
        }
        return providerType.trim().toLowerCase(Locale.ROOT);
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
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha-256 is not available", exception);
        }
    }
}
