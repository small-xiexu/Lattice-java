package com.xbk.lattice.query.service;

import io.micrometer.observation.ObservationRegistry;
import com.xbk.lattice.llm.service.LlmEndpointUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private final LlmEndpointUrlResolver llmEndpointUrlResolver;

    private final ConcurrentMap<String, EmbeddingModel> clientCache = new ConcurrentHashMap<String, EmbeddingModel>();

    /**
     * 创建 Embedding 客户端工厂。
     *
     * @param restClientBuilder RestClient 构建器
     * @param webClientBuilder WebClient 构建器
     */
    public EmbeddingClientFactory(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        this(restClientBuilder, webClientBuilder, new LlmEndpointUrlResolver());
    }

    /**
     * 创建 Embedding 客户端工厂。
     *
     * @param restClientBuilder RestClient 构建器
     * @param webClientBuilder WebClient 构建器
     * @param llmEndpointUrlResolver 端点地址解析器
     */
    @Autowired
    public EmbeddingClientFactory(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            LlmEndpointUrlResolver llmEndpointUrlResolver
    ) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.llmEndpointUrlResolver = llmEndpointUrlResolver;
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
        if (usesOpenAiCompatibleEmbeddingEndpoint(routeResolution.getBaseUrl())) {
            return createOpenAiCompatibleEmbeddingEndpointClient(routeResolution);
        }
        List<String> baseUrlCandidates = llmEndpointUrlResolver.resolveEmbeddingBaseUrlCandidates(routeResolution.getBaseUrl());
        List<RuntimeException> failures = new ArrayList<RuntimeException>();
        for (String baseUrlCandidate : baseUrlCandidates) {
            try {
                return createOpenAiEmbeddingModel(routeResolution, baseUrlCandidate);
            }
            catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        RuntimeException failure = failures.isEmpty()
                ? new IllegalStateException("embedding client创建失败")
                : failures.get(failures.size() - 1);
        throw failure;
    }

    private EmbeddingModel createOpenAiEmbeddingModel(
            EmbeddingRouteResolution routeResolution,
            String baseUrl
    ) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
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

    private EmbeddingModel createOpenAiCompatibleEmbeddingEndpointClient(EmbeddingRouteResolution routeResolution) {
        RestClient restClient = restClientBuilder
                .baseUrl(llmEndpointUrlResolver.resolveEmbeddingBaseUrl(routeResolution.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + routeResolution.getApiKey())
                .build();
        return new OpenAiCompatibleEmbeddingEndpointModel(
                restClient,
                routeResolution.getModelName(),
                llmEndpointUrlResolver.resolveEmbeddingsPath(routeResolution.getBaseUrl())
        );
    }

    private EmbeddingModel createOllamaClient(EmbeddingRouteResolution routeResolution) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(llmEndpointUrlResolver.normalizeBaseUrl(routeResolution.getBaseUrl()))
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
        stringBuilder.append(buildBaseUrlCacheKey(routeResolution.getBaseUrl()));
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

    private boolean usesOpenAiCompatibleEmbeddingEndpoint(String baseUrl) {
        return llmEndpointUrlResolver.usesDedicatedEmbeddingEndpoint(baseUrl);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String buildBaseUrlCacheKey(String baseUrl) {
        List<String> candidates = llmEndpointUrlResolver.resolveEmbeddingBaseUrlCandidates(baseUrl);
        return String.join("|", candidates.stream().filter(Objects::nonNull).toList());
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

    /**
     * OpenAI 兼容 embedding 接口模型。
     *
     * 职责：适配直接暴露 /embeddings 的兼容网关
     *
     * @author xiexu
     */
    private static class OpenAiCompatibleEmbeddingEndpointModel implements EmbeddingModel {

        private final RestClient restClient;

        private final String modelName;

        private final String embeddingsPath;

        private OpenAiCompatibleEmbeddingEndpointModel(RestClient restClient, String modelName, String embeddingsPath) {
            this.restClient = restClient;
            this.modelName = modelName;
            this.embeddingsPath = embeddingsPath;
        }

        /**
         * 执行 embedding 请求。
         *
         * @param request embedding 请求
         * @return embedding 响应
         */
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            if (request == null || CollectionUtils.isEmpty(request.getInstructions())) {
                throw new IllegalArgumentException("embedding request不能为空");
            }
            Integer dimensions = resolveDimensions(request.getOptions());
            Object input = request.getInstructions().size() == 1
                    ? request.getInstructions().get(0)
                    : request.getInstructions();
            Map<String, Object> requestBody = dimensions == null
                    ? Map.of("model", modelName, "input", input)
                    : Map.of("model", modelName, "input", input, "dimensions", dimensions);
            OpenAiCompatibleEmbeddingResponse response = restClient.post()
                    .uri(embeddingsPath)
                    .body(requestBody)
                    .retrieve()
                    .body(OpenAiCompatibleEmbeddingResponse.class);
            if (response == null || CollectionUtils.isEmpty(response.getData())) {
                throw new IllegalStateException("embedding response为空");
            }
            List<Embedding> embeddings = new ArrayList<Embedding>();
            for (OpenAiCompatibleEmbeddingData data : response.getData()) {
                embeddings.add(new Embedding(toFloatArray(data.getEmbedding()), data.getIndex()));
            }
            return new EmbeddingResponse(embeddings);
        }

        /**
         * 对文档执行 embedding。
         *
         * @param document 文档
         * @return embedding 向量
         */
        @Override
        public float[] embed(Document document) {
            if (document == null || document.getText() == null) {
                return new float[0];
            }
            EmbeddingResponse response = call(new EmbeddingRequest(List.of(document.getText()), null));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return new float[0];
            }
            return response.getResult().getOutput();
        }

        private Integer resolveDimensions(EmbeddingOptions embeddingOptions) {
            if (embeddingOptions instanceof OpenAiEmbeddingOptions openAiEmbeddingOptions
                    && openAiEmbeddingOptions.getDimensions() != null
                    && openAiEmbeddingOptions.getDimensions().intValue() > 0) {
                return openAiEmbeddingOptions.getDimensions();
            }
            return null;
        }

        private float[] toFloatArray(List<Double> values) {
            if (CollectionUtils.isEmpty(values)) {
                return new float[0];
            }
            float[] embedding = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                embedding[index] = values.get(index).floatValue();
            }
            return embedding;
        }
    }

    /**
     * OpenAI 兼容 embedding 响应。
     *
     * 职责：承载 embedding 接口响应体
     *
     * @author xiexu
     */
    private static class OpenAiCompatibleEmbeddingResponse {

        private List<OpenAiCompatibleEmbeddingData> data;

        public List<OpenAiCompatibleEmbeddingData> getData() {
            return data;
        }

        public void setData(List<OpenAiCompatibleEmbeddingData> data) {
            this.data = data;
        }
    }

    /**
     * OpenAI 兼容 embedding 数据项。
     *
     * 职责：承载单条 embedding 结果
     *
     * @author xiexu
     */
    private static class OpenAiCompatibleEmbeddingData {

        private List<Double> embedding;

        private int index;

        public List<Double> getEmbedding() {
            return embedding;
        }

        public void setEmbedding(List<Double> embedding) {
            this.embedding = embedding;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }
}
