package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.query.service.EmbeddingClientFactory;
import com.xbk.lattice.query.service.EmbeddingRouteResolution;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * LLM 模型探测服务
 *
 * 职责：根据后台模型配置执行最小化模型调用，验证模型名称、类型与连接是否可用
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LlmModelProbeService {

    private static final int DEFAULT_CHAT_MAX_TOKENS = 64;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final RestClient.Builder restClientBuilder;

    private final ObjectMapper objectMapper;

    private final LlmConfigAdminService llmConfigAdminService;

    private final LlmSecretCryptoService llmSecretCryptoService;

    private final AnthropicConnectionProperties anthropicConnectionProperties;

    private final EmbeddingClientFactory embeddingClientFactory;

    /**
     * 创建 LLM 模型探测服务。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param llmConfigAdminService LLM 配置后台服务
     * @param llmSecretCryptoService 密钥解密服务
     * @param anthropicConnectionProperties Anthropic 连接配置
     * @param embeddingClientFactory Embedding 客户端工厂
     */
    public LlmModelProbeService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            LlmConfigAdminService llmConfigAdminService,
            LlmSecretCryptoService llmSecretCryptoService,
            AnthropicConnectionProperties anthropicConnectionProperties,
            EmbeddingClientFactory embeddingClientFactory
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.llmConfigAdminService = llmConfigAdminService;
        this.llmSecretCryptoService = llmSecretCryptoService;
        this.anthropicConnectionProperties = anthropicConnectionProperties;
        this.embeddingClientFactory = embeddingClientFactory;
    }

    /**
     * 探测模型是否可用。
     *
     * @param modelId 已保存模型主键
     * @param connectionId 连接主键
     * @param modelName 模型名称
     * @param modelKind 模型类型
     * @param expectedDimensions 期望维度
     * @return 探测结果
     */
    public ProbeResult probe(
            Long modelId,
            Long connectionId,
            String modelName,
            String modelKind,
            Integer expectedDimensions
    ) {
        String effectiveProviderType = "openai";
        String effectiveModelKind = normalizeModelKind(modelKind);
        String effectiveModelName = StringUtils.hasText(modelName) ? modelName.trim() : "";
        try {
            ResolvedModelConfig resolvedConfig = resolveModel(
                    modelId,
                    connectionId,
                    modelName,
                    modelKind,
                    expectedDimensions
            );
            effectiveProviderType = resolvedConfig.providerType;
            effectiveModelKind = resolvedConfig.modelKind;
            effectiveModelName = resolvedConfig.modelName;
            long startedAt = System.nanoTime();
            ProbeSuccessDetails successDetails = probeModel(resolvedConfig);
            Long latencyMs = Long.valueOf(Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L));
            return new ProbeResult(
                    true,
                    effectiveProviderType,
                    effectiveModelKind,
                    latencyMs,
                    successDetails.message + "，耗时 " + latencyMs + " ms"
            );
        }
        catch (RestClientResponseException exception) {
            return new ProbeResult(
                    false,
                    effectiveProviderType,
                    effectiveModelKind,
                    null,
                    buildFailureMessage(effectiveProviderType, effectiveModelName, exception)
            );
        }
        catch (RuntimeException exception) {
            return new ProbeResult(
                    false,
                    effectiveProviderType,
                    effectiveModelKind,
                    null,
                    buildFailureMessage(effectiveProviderType, effectiveModelName, exception)
            );
        }
    }

    /**
     * 合并页面输入与已保存模型，得到最终探测参数。
     *
     * @param modelId 已保存模型主键
     * @param connectionId 连接主键
     * @param modelName 模型名称
     * @param modelKind 模型类型
     * @param expectedDimensions 期望维度
     * @return 最终探测配置
     */
    private ResolvedModelConfig resolveModel(
            Long modelId,
            Long connectionId,
            String modelName,
            String modelKind,
            Integer expectedDimensions
    ) {
        Optional<LlmModelProfile> existingModel = modelId == null
                ? Optional.empty()
                : llmConfigAdminService.findModelProfile(modelId);
        Long resolvedConnectionId = connectionId != null
                ? connectionId
                : existingModel.map(LlmModelProfile::getConnectionId).orElse(null);
        String resolvedModelName = StringUtils.hasText(modelName)
                ? modelName.trim()
                : existingModel.map(LlmModelProfile::getModelName).orElse("");
        String resolvedModelKind = StringUtils.hasText(modelKind)
                ? normalizeModelKind(modelKind)
                : existingModel.map(LlmModelProfile::getModelKind).map(this::normalizeModelKind).orElse(
                        LlmModelProfile.MODEL_KIND_CHAT
                );
        Integer resolvedExpectedDimensions = expectedDimensions != null
                ? expectedDimensions
                : existingModel.map(LlmModelProfile::getExpectedDimensions).orElse(null);
        if (resolvedConnectionId == null) {
            throw new IllegalArgumentException("请先选择所属连接");
        }
        if (!StringUtils.hasText(resolvedModelName)) {
            throw new IllegalArgumentException("请先填写模型名称");
        }
        if (LlmModelProfile.MODEL_KIND_EMBEDDING.equals(resolvedModelKind)
                && (resolvedExpectedDimensions == null || resolvedExpectedDimensions.intValue() <= 0)) {
            throw new IllegalArgumentException("向量模型测试前请先填写正整数维度");
        }
        Optional<LlmProviderConnection> connection = llmConfigAdminService.findConnection(resolvedConnectionId);
        if (connection.isEmpty()) {
            throw new IllegalArgumentException("所属连接不存在: " + resolvedConnectionId);
        }
        LlmProviderConnection providerConnection = connection.orElseThrow();
        return new ResolvedModelConfig(
                modelId,
                resolvedConnectionId,
                normalizeProviderType(providerConnection.getProviderType()),
                providerConnection.getBaseUrl(),
                llmSecretCryptoService.decrypt(providerConnection.getApiKeyCiphertext()),
                resolvedModelName,
                resolvedModelKind,
                resolvedExpectedDimensions,
                existingModel.map(LlmModelProfile::getTemperature).orElse(null),
                existingModel.map(LlmModelProfile::getMaxTokens).orElse(null),
                existingModel.map(LlmModelProfile::getTimeoutSeconds).orElse(null),
                existingModel.map(LlmModelProfile::getExtraOptionsJson).orElse(null)
        );
    }

    /**
     * 按模型类型执行探测。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeModel(ResolvedModelConfig resolvedConfig) {
        if (LlmModelProfile.MODEL_KIND_EMBEDDING.equals(resolvedConfig.modelKind)) {
            return probeEmbeddingModel(resolvedConfig);
        }
        return probeChatModel(resolvedConfig);
    }

    /**
     * 探测对话模型。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeChatModel(ResolvedModelConfig resolvedConfig) {
        if ("ollama".equals(resolvedConfig.providerType)) {
            throw new IllegalArgumentException("当前项目暂不支持 Ollama 对话模型，请改用 OpenAI 或 Claude");
        }
        LlmCallResult result;
        if ("anthropic".equals(resolvedConfig.providerType)) {
            result = new AnthropicMessageApiLlmClient(
                    restClientBuilder,
                    objectMapper,
                    resolvedConfig.baseUrl,
                    resolvedConfig.apiKey,
                    anthropicConnectionProperties.getVersion(),
                    anthropicConnectionProperties.getBetaVersion(),
                    resolvedConfig.modelName,
                    Integer.valueOf(resolveChatMaxTokens(resolvedConfig.maxTokens)),
                    toDouble(resolvedConfig.temperature),
                    null,
                    null,
                    Integer.valueOf(resolveTimeout(resolvedConfig.timeoutSeconds))
            ).call("你是模型测试助手，请只回答 OK。", "请只回答 OK");
        }
        else {
            result = new OpenAiCompatibleLlmClient(
                    restClientBuilder,
                    objectMapper,
                    resolvedConfig.baseUrl,
                    resolvedConfig.apiKey,
                    resolvedConfig.modelName,
                    toDouble(resolvedConfig.temperature),
                    Integer.valueOf(resolveChatMaxTokens(resolvedConfig.maxTokens)),
                    Integer.valueOf(resolveTimeout(resolvedConfig.timeoutSeconds)),
                    resolvedConfig.extraOptionsJson
            ).call("你是模型测试助手，请只回答 OK。", "请只回答 OK");
        }
        if (result == null || !StringUtils.hasText(result.getContent())) {
            throw new IllegalStateException("模型没有返回可用内容");
        }
        return new ProbeSuccessDetails("模型 " + resolvedConfig.modelName + " 测试成功，已返回对话结果");
    }

    /**
     * 探测向量模型。
     *
     * @param resolvedConfig 最终探测配置
     * @return 成功结果
     */
    private ProbeSuccessDetails probeEmbeddingModel(ResolvedModelConfig resolvedConfig) {
        EmbeddingRouteResolution routeResolution = new EmbeddingRouteResolution(
                resolvedConfig.modelId == null ? Long.valueOf(0L) : resolvedConfig.modelId,
                resolvedConfig.providerType,
                resolvedConfig.baseUrl,
                resolvedConfig.apiKey,
                resolvedConfig.modelName,
                resolvedConfig.expectedDimensions
        );
        EmbeddingModel embeddingModel = embeddingClientFactory.getOrCreate(routeResolution);
        EmbeddingResponse response = embeddingModel.call(buildEmbeddingRequest("模型测试", routeResolution));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型没有返回向量结果");
        }
        int actualDimensions = response.getResult().getOutput().length;
        if (actualDimensions <= 0) {
            throw new IllegalStateException("模型返回的向量维度无效");
        }
        if (resolvedConfig.expectedDimensions != null
                && resolvedConfig.expectedDimensions.intValue() > 0
                && actualDimensions != resolvedConfig.expectedDimensions.intValue()) {
            throw new IllegalStateException(
                    "模型返回向量维度为 " + actualDimensions + "，与配置的 "
                            + resolvedConfig.expectedDimensions + " 不一致"
            );
        }
        return new ProbeSuccessDetails(
                "模型 " + resolvedConfig.modelName + " 测试成功，已返回 " + actualDimensions + " 维向量"
        );
    }

    /**
     * 构建 embedding 请求。
     *
     * @param text 探测文本
     * @param routeResolution 路由配置
     * @return embedding 请求
     */
    private EmbeddingRequest buildEmbeddingRequest(String text, EmbeddingRouteResolution routeResolution) {
        if ("ollama".equals(normalizeProviderType(routeResolution.getProviderType()))) {
            OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
                    .model(routeResolution.getModelName())
                    .build();
            return new EmbeddingRequest(List.of(text), options);
        }
        OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder()
                .model(routeResolution.getModelName());
        if (routeResolution.getExpectedDimensions() != null && routeResolution.getExpectedDimensions().intValue() > 0) {
            builder.dimensions(routeResolution.getExpectedDimensions());
        }
        return new EmbeddingRequest(List.of(text), builder.build());
    }

    /**
     * 构建用户可读的失败信息。
     *
     * @param providerType Provider 类型
     * @param modelName 模型名称
     * @param exception 异常
     * @return 错误信息
     */
    private String buildFailureMessage(String providerType, String modelName, Exception exception) {
        String providerLabel = resolveProviderLabel(providerType);
        String modelLabel = StringUtils.hasText(modelName) ? modelName : "当前模型";
        if (exception instanceof RestClientResponseException) {
            RestClientResponseException responseException = (RestClientResponseException) exception;
            String statusText = responseException.getStatusCode().value() + " " + responseException.getStatusText();
            String responseBody = sanitizeMessage(responseException.getResponseBodyAsString());
            return StringUtils.hasText(responseBody)
                    ? providerLabel + " 模型 " + modelLabel + " 测试失败：" + statusText + "，" + responseBody
                    : providerLabel + " 模型 " + modelLabel + " 测试失败：" + statusText;
        }
        String message = sanitizeMessage(exception.getMessage());
        return StringUtils.hasText(message)
                ? providerLabel + " 模型 " + modelLabel + " 测试失败：" + message
                : providerLabel + " 模型 " + modelLabel + " 测试失败";
    }

    /**
     * 规范化 Provider 类型。
     *
     * @param providerType 原始 Provider 类型
     * @return 规范化后的 Provider 类型
     */
    private String normalizeProviderType(String providerType) {
        if (!StringUtils.hasText(providerType)) {
            return "openai";
        }
        String normalized = providerType.trim().toLowerCase(Locale.ROOT);
        if ("openai_compatible".equals(normalized)) {
            return "openai";
        }
        return normalized;
    }

    /**
     * 规范化模型类型。
     *
     * @param modelKind 原始模型类型
     * @return 规范化后的模型类型
     */
    private String normalizeModelKind(String modelKind) {
        if (!StringUtils.hasText(modelKind)) {
            return LlmModelProfile.MODEL_KIND_CHAT;
        }
        return modelKind.trim().toUpperCase(Locale.ROOT);
    }

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
     * 解析对话模型最大输出 token。
     *
     * @param maxTokens 已配置值
     * @return 最终 token 数
     */
    private int resolveChatMaxTokens(Integer maxTokens) {
        if (maxTokens == null || maxTokens.intValue() <= 0) {
            return DEFAULT_CHAT_MAX_TOKENS;
        }
        return Math.min(maxTokens.intValue(), DEFAULT_CHAT_MAX_TOKENS);
    }

    /**
     * 解析超时秒数。
     *
     * @param timeoutSeconds 已配置值
     * @return 最终超时秒数
     */
    private int resolveTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds.intValue() <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return timeoutSeconds.intValue();
    }

    /**
     * 将 BigDecimal 转为 Double。
     *
     * @param value 数值
     * @return Double 值
     */
    private Double toDouble(BigDecimal value) {
        return value == null ? null : Double.valueOf(value.doubleValue());
    }

    /**
     * 清洗异常信息。
     *
     * @param rawMessage 原始文本
     * @return 清洗后的文本
     */
    private String sanitizeMessage(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return "";
        }
        String normalized = rawMessage.replaceAll("\\s+", " ").trim();
        try {
            JsonNode rootNode = objectMapper.readTree(normalized);
            if (rootNode.hasNonNull("error")) {
                JsonNode errorNode = rootNode.path("error");
                if (errorNode.isTextual()) {
                    return errorNode.asText();
                }
                if (errorNode.hasNonNull("message")) {
                    return errorNode.path("message").asText();
                }
            }
        }
        catch (Exception exception) {
            // ignore parse failure and keep normalized text
        }
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }

    /**
     * 模型探测结果
     *
     * 职责：承载模型探测后的页面展示结果
     *
     * @author xiexu
     */
    public static final class ProbeResult {

        private final boolean success;

        private final String providerType;

        private final String modelKind;

        private final Long latencyMs;

        private final String message;

        /**
         * 创建模型探测结果。
         *
         * @param success 是否成功
         * @param providerType Provider 类型
         * @param modelKind 模型类型
         * @param latencyMs 耗时毫秒
         * @param message 提示文案
         */
        public ProbeResult(
                boolean success,
                String providerType,
                String modelKind,
                Long latencyMs,
                String message
        ) {
            this.success = success;
            this.providerType = providerType;
            this.modelKind = modelKind;
            this.latencyMs = latencyMs;
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
         * 返回模型类型。
         *
         * @return 模型类型
         */
        public String getModelKind() {
            return modelKind;
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
         * 返回提示文案。
         *
         * @return 提示文案
         */
        public String getMessage() {
            return message;
        }
    }

    /**
     * 已解析模型配置
     *
     * 职责：保存页面输入与已保存模型合并后的最终探测参数
     *
     * @author xiexu
     */
    private static final class ResolvedModelConfig {

        private final Long modelId;

        private final Long connectionId;

        private final String providerType;

        private final String baseUrl;

        private final String apiKey;

        private final String modelName;

        private final String modelKind;

        private final Integer expectedDimensions;

        private final BigDecimal temperature;

        private final Integer maxTokens;

        private final Integer timeoutSeconds;

        private final String extraOptionsJson;

        private ResolvedModelConfig(
                Long modelId,
                Long connectionId,
                String providerType,
                String baseUrl,
                String apiKey,
                String modelName,
                String modelKind,
                Integer expectedDimensions,
                BigDecimal temperature,
                Integer maxTokens,
                Integer timeoutSeconds,
                String extraOptionsJson
        ) {
            this.modelId = modelId;
            this.connectionId = connectionId;
            this.providerType = providerType;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.modelName = modelName;
            this.modelKind = modelKind;
            this.expectedDimensions = expectedDimensions;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.timeoutSeconds = timeoutSeconds;
            this.extraOptionsJson = extraOptionsJson;
        }
    }

    /**
     * 成功探测详情
     *
     * 职责：承载模型探测成功后的提示文案
     *
     * @author xiexu
     */
    private static final class ProbeSuccessDetails {

        private final String message;

        private ProbeSuccessDetails(String message) {
            this.message = message;
        }
    }
}
