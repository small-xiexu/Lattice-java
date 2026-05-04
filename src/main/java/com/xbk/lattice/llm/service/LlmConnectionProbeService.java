package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * LLM 连接探测服务
 *
 * 职责：根据后台连接信息或页面临时输入，探测 OpenAI / Claude / Ollama 接口是否可达
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LlmConnectionProbeService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient.Builder restClientBuilder;

    private final ObjectMapper objectMapper;

    private final LlmConfigAdminService llmConfigAdminService;

    private final LlmSecretCryptoService llmSecretCryptoService;

    private final AnthropicConnectionProperties anthropicConnectionProperties;

    private final LlmEndpointUrlResolver llmEndpointUrlResolver;

    /**
     * 创建 LLM 连接探测服务。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param llmConfigAdminService LLM 配置中心后台服务
     * @param llmSecretCryptoService 密钥加解密服务
     * @param anthropicConnectionProperties Anthropic 连接配置
     * @param llmEndpointUrlResolver 端点地址解析器
     */
    public LlmConnectionProbeService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            LlmConfigAdminService llmConfigAdminService,
            LlmSecretCryptoService llmSecretCryptoService,
            AnthropicConnectionProperties anthropicConnectionProperties,
            LlmEndpointUrlResolver llmEndpointUrlResolver
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.llmConfigAdminService = llmConfigAdminService;
        this.llmSecretCryptoService = llmSecretCryptoService;
        this.anthropicConnectionProperties = anthropicConnectionProperties;
        this.llmEndpointUrlResolver = llmEndpointUrlResolver;
    }

    /**
     * 探测连接是否可用。
     *
     * @param connectionId 已保存连接主键
     * @param providerType 页面输入的 Provider 类型
     * @param baseUrl 页面输入的基础地址
     * @param apiKey 页面输入的 API Key
     * @return 探测结果
     */
    public ProbeResult probe(
            Long connectionId,
            String providerType,
            String baseUrl,
            String apiKey
    ) {
        String effectiveProviderType = StringUtils.hasText(providerType)
                ? normalizeProviderType(providerType)
                : "";
        try {
            ResolvedConnectionConfig resolvedConfig = resolveConnection(connectionId, providerType, baseUrl, apiKey);
            effectiveProviderType = resolvedConfig.providerType;
            long startedAt = System.nanoTime();
            ProbeSuccessDetails successDetails = probeByProvider(resolvedConfig);
            Long latencyMs = toLatencyMs(startedAt);
            return new ProbeResult(
                    true,
                    resolvedConfig.providerType,
                    latencyMs,
                    successDetails.endpoint,
                    successDetails.message + "，耗时 " + latencyMs + " ms"
            );
        }
        catch (RestClientResponseException exception) {
            return new ProbeResult(
                    false,
                    effectiveProviderType,
                    null,
                    null,
                    buildFailureMessage(effectiveProviderType, exception)
            );
        }
        catch (RestClientException | IllegalArgumentException | IllegalStateException exception) {
            return new ProbeResult(
                    false,
                    effectiveProviderType,
                    null,
                    null,
                    buildFailureMessage(effectiveProviderType, exception)
            );
        }
    }

    /**
     * 合并页面输入与已保存连接，得到最终探测参数。
     *
     * @param connectionId 已保存连接主键
     * @param providerType 页面输入的 Provider 类型
     * @param baseUrl 页面输入的基础地址
     * @param apiKey 页面输入的 API Key
     * @return 最终探测配置
     */
    private ResolvedConnectionConfig resolveConnection(
            Long connectionId,
            String providerType,
            String baseUrl,
            String apiKey
    ) {
        Optional<LlmProviderConnection> existingConnection = connectionId == null
                ? Optional.empty()
                : llmConfigAdminService.findConnection(connectionId);
        String resolvedProviderType = StringUtils.hasText(providerType)
                ? normalizeProviderType(providerType)
                : existingConnection.map(LlmProviderConnection::getProviderType)
                .map(this::normalizeProviderType)
                .orElse("");
        String resolvedBaseUrl = StringUtils.hasText(baseUrl)
                ? llmEndpointUrlResolver.normalizeBaseUrl(baseUrl)
                : existingConnection.map(LlmProviderConnection::getBaseUrl)
                .map(llmEndpointUrlResolver::normalizeBaseUrl)
                .orElse("");
        String resolvedApiKey = StringUtils.hasText(apiKey)
                ? apiKey.trim()
                : existingConnection.map(LlmProviderConnection::getApiKeyCiphertext)
                .filter(StringUtils::hasText)
                .map(llmSecretCryptoService::decrypt)
                .orElse("");
        if (!StringUtils.hasText(resolvedProviderType)) {
            throw new IllegalArgumentException("请先选择接入类型");
        }
        if (!StringUtils.hasText(resolvedBaseUrl)) {
            throw new IllegalArgumentException("请先填写接口地址");
        }
        return new ResolvedConnectionConfig(connectionId, resolvedProviderType, resolvedBaseUrl, resolvedApiKey);
    }

    /**
     * 按 Provider 类型执行探测。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeByProvider(ResolvedConnectionConfig resolvedConfig) {
        if ("anthropic".equals(resolvedConfig.providerType)) {
            return probeAnthropic(resolvedConfig);
        }
        if ("ollama".equals(resolvedConfig.providerType)) {
            return probeOllama(resolvedConfig);
        }
        if (shouldProbeEmbeddingEndpoint(resolvedConfig)) {
            return probeOpenAiEmbeddingEndpoint(resolvedConfig);
        }
        return probeOpenAi(resolvedConfig);
    }

    /**
     * 探测 OpenAI 风格接口。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeOpenAi(ResolvedConnectionConfig resolvedConfig) {
        String endpoint = resolveOpenAiModelsPath(resolvedConfig.baseUrl);
        RestClient client = buildRestClient(
                llmEndpointUrlResolver.resolveModelsBaseUrl(resolvedConfig.baseUrl),
                resolvedConfig.apiKey,
                HttpHeaders.AUTHORIZATION,
                resolvedConfig.apiKey
        );
        String responseBody = requestJson(client, endpoint);
        int modelCount = extractCollectionSize(responseBody, "data");
        String message = modelCount >= 0
                ? "OpenAI 连接成功，可访问 " + modelCount + " 个模型"
                : "OpenAI 连接成功，接口已正常返回";
        return new ProbeSuccessDetails(endpoint, message);
    }

    /**
     * 探测 OpenAI 兼容 embedding 专用接口。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeOpenAiEmbeddingEndpoint(ResolvedConnectionConfig resolvedConfig) {
        String endpoint = resolveOpenAiEmbeddingsPath(resolvedConfig.baseUrl);
        RestClientResponseException lastResponseException = null;
        RestClientException lastClientException = null;
        for (String baseUrlCandidate : llmEndpointUrlResolver.resolveEmbeddingBaseUrlCandidates(resolvedConfig.baseUrl)) {
            try {
                RestClient client = buildRestClient(
                        baseUrlCandidate,
                        resolvedConfig.apiKey,
                        HttpHeaders.AUTHORIZATION,
                        resolvedConfig.apiKey
                );
                String responseBody = requestJson(
                        client,
                        endpoint,
                        "{\"model\":\"embedding-3\",\"input\":\"probe\"}"
                );
                int vectorCount = extractCollectionSize(responseBody, "data");
                String message = vectorCount >= 0
                        ? "OpenAI embedding 连接成功，接口已返回向量结果"
                        : "OpenAI embedding 连接成功，接口已正常返回";
                return new ProbeSuccessDetails(endpoint, message);
            }
            catch (RestClientResponseException exception) {
                lastResponseException = exception;
            }
            catch (RestClientException exception) {
                lastClientException = exception;
            }
        }
        if (lastResponseException != null) {
            throw lastResponseException;
        }
        if (lastClientException != null) {
            throw lastClientException;
        }
        throw new IllegalStateException("embedding 连接探测失败");
    }

    /**
     * 探测 Claude 风格接口。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeAnthropic(ResolvedConnectionConfig resolvedConfig) {
        String endpoint = resolveAnthropicModelsPath(resolvedConfig.baseUrl);
        SimpleClientHttpRequestFactory requestFactory = createRequestFactory();
        RestClient.Builder clientBuilder = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(llmEndpointUrlResolver.resolveAnthropicBaseUrl(resolvedConfig.baseUrl))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(resolvedConfig.apiKey)) {
            clientBuilder.defaultHeader("x-api-key", resolvedConfig.apiKey);
        }
        String anthropicVersion = StringUtils.hasText(anthropicConnectionProperties.getVersion())
                ? anthropicConnectionProperties.getVersion()
                : DEFAULT_ANTHROPIC_VERSION;
        clientBuilder.defaultHeader("anthropic-version", anthropicVersion);
        if (StringUtils.hasText(anthropicConnectionProperties.getBetaVersion())) {
            clientBuilder.defaultHeader("anthropic-beta", anthropicConnectionProperties.getBetaVersion());
        }
        String responseBody = requestJson(clientBuilder.build(), endpoint);
        int modelCount = extractCollectionSize(responseBody, "data");
        String message = modelCount >= 0
                ? "Claude 连接成功，可访问 " + modelCount + " 个模型"
                : "Claude 连接成功，接口已正常返回";
        return new ProbeSuccessDetails(endpoint, message);
    }

    /**
     * 探测 Ollama 接口。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeOllama(ResolvedConnectionConfig resolvedConfig) {
        String endpoint = resolveOllamaTagsPath(resolvedConfig.baseUrl);
        RestClient client = buildRestClient(
                resolvedConfig.baseUrl,
                resolvedConfig.apiKey,
                HttpHeaders.AUTHORIZATION,
                resolvedConfig.apiKey
        );
        String responseBody = requestJson(client, endpoint);
        int modelCount = extractCollectionSize(responseBody, "models");
        String message = modelCount >= 0
                ? "Ollama 连接成功，当前发现 " + modelCount + " 个本地模型"
                : "Ollama 连接成功，接口已正常返回";
        return new ProbeSuccessDetails(endpoint, message);
    }

    /**
     * 构建通用 RestClient。
     *
     * @param baseUrl 基础地址
     * @param apiKey API Key
     * @param headerName 认证请求头
     * @param headerValue 认证请求头值
     * @return RestClient
     */
    private RestClient buildRestClient(
            String baseUrl,
            String apiKey,
            String headerName,
            String headerValue
    ) {
        SimpleClientHttpRequestFactory requestFactory = createRequestFactory();
        RestClient.Builder clientBuilder = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(apiKey) && StringUtils.hasText(headerName) && StringUtils.hasText(headerValue)) {
            String normalizedHeaderValue = HttpHeaders.AUTHORIZATION.equals(headerName)
                    ? "Bearer " + headerValue
                    : headerValue;
            clientBuilder.defaultHeader(headerName, normalizedHeaderValue);
        }
        return clientBuilder.build();
    }

    /**
     * 创建关闭代理并带超时的请求工厂。
     *
     * @return 请求工厂
     */
    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        requestFactory.setReadTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        return requestFactory;
    }

    /**
     * 发起 GET 请求并返回文本响应。
     *
     * @param client RestClient
     * @param endpoint 探测端点
     * @return 响应文本
     */
    private String requestJson(RestClient client, String endpoint) {
        byte[] responseBytes = client.get()
                .uri(endpoint)
                .retrieve()
                .body(byte[].class);
        if (responseBytes == null || responseBytes.length == 0) {
            return "";
        }
        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    /**
     * 发起 POST JSON 请求并返回文本响应。
     *
     * @param client RestClient
     * @param endpoint 探测端点
     * @param requestBody JSON 请求体
     * @return 响应文本
     */
    private String requestJson(RestClient client, String endpoint, String requestBody) {
        byte[] responseBytes = client.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(byte[].class);
        if (responseBytes == null || responseBytes.length == 0) {
            return "";
        }
        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    /**
     * 从 JSON 响应中读取集合长度。
     *
     * @param responseBody 响应文本
     * @param fieldName 目标字段名
     * @return 集合长度，无法识别时返回 -1
     */
    private int extractCollectionSize(String responseBody, String fieldName) {
        if (!StringUtils.hasText(responseBody)) {
            return -1;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode arrayNode = rootNode.path(fieldName);
            return arrayNode.isArray() ? arrayNode.size() : -1;
        }
        catch (Exception exception) {
            return -1;
        }
    }

    /**
     * 将纳秒耗时转换为毫秒。
     *
     * @param startedAt 起始纳秒时间
     * @return 毫秒耗时
     */
    private Long toLatencyMs(long startedAt) {
        return Long.valueOf(Math.max(1L, Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
    }

    /**
     * 构建用户可读的失败信息。
     *
     * @param providerType Provider 类型
     * @param exception 异常
     * @return 用户可读错误信息
     */
    private String buildFailureMessage(String providerType, Exception exception) {
        String providerLabel = resolveProviderLabel(providerType);
        if (exception instanceof RestClientResponseException) {
            RestClientResponseException responseException = (RestClientResponseException) exception;
            String statusText = responseException.getStatusCode().value() + " " + responseException.getStatusText();
            String responseBody = sanitizeMessage(responseException.getResponseBodyAsString(StandardCharsets.UTF_8));
            return StringUtils.hasText(responseBody)
                    ? providerLabel + " 连接失败：" + statusText + "，" + responseBody
                    : providerLabel + " 连接失败：" + statusText;
        }
        String message = sanitizeMessage(exception.getMessage());
        return StringUtils.hasText(message)
                ? providerLabel + " 连接失败：" + message
                : providerLabel + " 连接失败，请检查接口地址和密钥";
    }

    /**
     * 规范化 Provider 类型。
     *
     * @param providerType 原始 Provider 类型
     * @return 规范化后的 Provider 类型
     */
    private String normalizeProviderType(String providerType) {
        String normalized = StringUtils.hasText(providerType)
                ? providerType.trim().toLowerCase(Locale.ROOT)
                : "openai";
        if ("openai_compatible".equals(normalized)) {
            return "openai";
        }
        return normalized;
    }

    /**
     * 规范化基础地址。
     *
     * @param baseUrl 原始基础地址
     * @return 规范化后的基础地址
     */
    /**
     * 解析 Provider 展示名称。
     *
     * @param providerType Provider 类型
     * @return 展示名称
     */
    private String resolveProviderLabel(String providerType) {
        if ("anthropic".equals(providerType)) {
            return "Claude";
        }
        if ("ollama".equals(providerType)) {
            return "Ollama";
        }
        return "OpenAI";
    }

    /**
     * 解析 OpenAI 风格模型探测端点。
     *
     * @param baseUrl 基础地址
     * @return 端点路径
     */
    private String resolveOpenAiModelsPath(String baseUrl) {
        return llmEndpointUrlResolver.resolveModelsPath(baseUrl);
    }

    /**
     * 解析 OpenAI 风格 embedding 探测端点。
     *
     * @param baseUrl 基础地址
     * @return 端点路径
     */
    private String resolveOpenAiEmbeddingsPath(String baseUrl) {
        return llmEndpointUrlResolver.resolveEmbeddingsPath(baseUrl);
    }

    /**
     * 解析 Claude 模型探测端点。
     *
     * @param baseUrl 基础地址
     * @return 端点路径
     */
    private String resolveAnthropicModelsPath(String baseUrl) {
        return llmEndpointUrlResolver.resolveAnthropicModelsPath(baseUrl);
    }

    /**
     * 解析 Ollama 标签探测端点。
     *
     * @param baseUrl 基础地址
     * @return 端点路径
     */
    private String resolveOllamaTagsPath(String baseUrl) {
        return baseUrl.endsWith("/api") ? "/tags" : "/api/tags";
    }

    /**
     * 清洗异常文本，避免把响应体原样铺满页面。
     *
     * @param rawMessage 原始文本
     * @return 清洗后的文本
     */
    private String sanitizeMessage(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return "";
        }
        String normalized = rawMessage.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }

    /**
     * 判断当前连接是否应按 embedding 专用 endpoint 探测。
     *
     * @param resolvedConfig 最终探测配置
     * @return 是否使用 embedding 探测
     */
    private boolean shouldProbeEmbeddingEndpoint(ResolvedConnectionConfig resolvedConfig) {
        if (resolvedConfig.connectionId == null || resolvedConfig.connectionId.longValue() <= 0L) {
            return usesOpenAiCompatibleEmbeddingEndpoint(resolvedConfig.baseUrl);
        }
        List<LlmModelProfile> modelProfiles = llmConfigAdminService.listModelProfiles();
        boolean hasChatModel = false;
        boolean hasEmbeddingModel = false;
        for (LlmModelProfile modelProfile : modelProfiles) {
            if (modelProfile.getConnectionId() == null
                    || !modelProfile.getConnectionId().equals(resolvedConfig.connectionId)
                    || !modelProfile.isEnabled()) {
                continue;
            }
            if (LlmModelProfile.MODEL_KIND_CHAT.equalsIgnoreCase(modelProfile.getModelKind())) {
                hasChatModel = true;
            }
            if (LlmModelProfile.MODEL_KIND_EMBEDDING.equalsIgnoreCase(modelProfile.getModelKind())) {
                hasEmbeddingModel = true;
            }
        }
        if (hasEmbeddingModel && !hasChatModel) {
            return true;
        }
        return usesOpenAiCompatibleEmbeddingEndpoint(resolvedConfig.baseUrl) && !hasChatModel;
    }

    /**
     * 判断基础地址是否为 embedding 专用 endpoint。
     *
     * @param baseUrl 基础地址
     * @return 是否为 embedding 专用 endpoint
     */
    private boolean usesOpenAiCompatibleEmbeddingEndpoint(String baseUrl) {
        return llmEndpointUrlResolver.usesDedicatedEmbeddingEndpoint(baseUrl);
    }

    /**
     * 连接探测结果
     *
     * 职责：承载连接探测后的页面展示数据
     *
     * @author xiexu
     */
    public static final class ProbeResult {

        private final boolean success;

        private final String providerType;

        private final Long latencyMs;

        private final String endpoint;

        private final String message;

        /**
         * 创建连接探测结果。
         *
         * @param success 是否成功
         * @param providerType Provider 类型
         * @param latencyMs 耗时毫秒
         * @param endpoint 实际探测端点
         * @param message 提示文案
         */
        public ProbeResult(
                boolean success,
                String providerType,
                Long latencyMs,
                String endpoint,
                String message
        ) {
            this.success = success;
            this.providerType = providerType;
            this.latencyMs = latencyMs;
            this.endpoint = endpoint;
            this.message = message;
        }

        /**
         * 返回是否成功。
         *
         * @return 是否成功
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * 返回 Provider 类型。
         *
         * @return Provider 类型
         */
        public String getProviderType() {
            return providerType;
        }

        /**
         * 返回耗时毫秒。
         *
         * @return 耗时毫秒
         */
        public Long getLatencyMs() {
            return latencyMs;
        }

        /**
         * 返回实际探测端点。
         *
         * @return 实际探测端点
         */
        public String getEndpoint() {
            return endpoint;
        }

        /**
         * 返回提示文案。
         *
         * @return 提示文案
         */
        public String getMessage() {
            return message;
        }
    }

    /**
     * 已解析连接参数
     *
     * 职责：保存页面输入与已保存连接合并后的最终参数
     *
     * @author xiexu
     */
    private static final class ResolvedConnectionConfig {

        private final Long connectionId;

        private final String providerType;

        private final String baseUrl;

        private final String apiKey;

        private ResolvedConnectionConfig(Long connectionId, String providerType, String baseUrl, String apiKey) {
            this.connectionId = connectionId;
            this.providerType = providerType;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
        }
    }

    /**
     * 成功探测详情
     *
     * 职责：保存探测成功后的端点与提示文案
     *
     * @author xiexu
     */
    private static final class ProbeSuccessDetails {

        private final String endpoint;

        private final String message;

        private ProbeSuccessDetails(String endpoint, String message) {
            this.endpoint = endpoint;
            this.message = message;
        }
    }
}
