package com.xbk.lattice.documentparse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.documentparse.domain.DocumentParseProviderConnection;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
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
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * 文档解析连接探测服务
 *
 * 职责：根据后台连接信息或页面临时输入，探测 OCR / Document AI 接口是否可达
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseConnectionProbeService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    private final RestClient.Builder restClientBuilder;

    private final ObjectMapper objectMapper;

    private final DocumentParseAdminService documentParseAdminService;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建文档解析连接探测服务。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param documentParseAdminService 文档解析后台服务
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public DocumentParseConnectionProbeService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            DocumentParseAdminService documentParseAdminService,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.documentParseAdminService = documentParseAdminService;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 探测连接是否可用。
     *
     * @param connectionId 已保存连接主键
     * @param providerType 页面输入的供应商类型
     * @param baseUrl 页面输入的基础地址
     * @param endpointPath 页面输入的接口路径
     * @param credential 页面输入的访问凭证
     * @return 探测结果
     */
    public ProbeResult probe(
            Long connectionId,
            String providerType,
            String baseUrl,
            String endpointPath,
            String credential
    ) {
        String effectiveProviderType = StringUtils.hasText(providerType)
                ? normalizeProviderType(providerType)
                : "";
        try {
            ResolvedConnectionConfig resolvedConfig = resolveConnection(
                    connectionId,
                    providerType,
                    baseUrl,
                    endpointPath,
                    credential
            );
            effectiveProviderType = resolvedConfig.providerType;
            long startedAt = System.nanoTime();
            probeEndpoint(resolvedConfig);
            Long latencyMs = Long.valueOf(Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L));
            return new ProbeResult(
                    true,
                    resolvedConfig.providerType,
                    latencyMs,
                    resolvedConfig.baseUrl + resolvedConfig.endpointPath,
                    "文档解析连接可用，耗时 " + latencyMs + " ms"
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

    private ResolvedConnectionConfig resolveConnection(
            Long connectionId,
            String providerType,
            String baseUrl,
            String endpointPath,
            String credential
    ) {
        Optional<DocumentParseProviderConnection> existingConnection = connectionId == null
                ? Optional.empty()
                : documentParseAdminService.findConnection(connectionId);
        String resolvedProviderType = StringUtils.hasText(providerType)
                ? normalizeProviderType(providerType)
                : existingConnection.map(DocumentParseProviderConnection::getProviderType)
                .map(this::normalizeProviderType)
                .orElse("");
        String resolvedBaseUrl = StringUtils.hasText(baseUrl)
                ? normalizeBaseUrl(baseUrl)
                : existingConnection.map(DocumentParseProviderConnection::getBaseUrl)
                .map(this::normalizeBaseUrl)
                .orElse("");
        String resolvedEndpointPath = StringUtils.hasText(endpointPath)
                ? normalizeEndpointPath(endpointPath)
                : existingConnection.map(DocumentParseProviderConnection::getEndpointPath)
                .map(this::normalizeEndpointPath)
                .orElseGet(() -> defaultEndpointPath(resolvedProviderType));
        String resolvedCredential = StringUtils.hasText(credential)
                ? credential.trim()
                : existingConnection.map(DocumentParseProviderConnection::getCredentialCiphertext)
                .filter(StringUtils::hasText)
                .map(llmSecretCryptoService::decrypt)
                .orElse("");
        if (!StringUtils.hasText(resolvedProviderType)) {
            throw new IllegalArgumentException("请先选择文档解析供应商");
        }
        if (!StringUtils.hasText(resolvedBaseUrl)) {
            throw new IllegalArgumentException("请先填写接口地址");
        }
        if (!StringUtils.hasText(resolvedEndpointPath)) {
            throw new IllegalArgumentException("请先填写接口路径");
        }
        return new ResolvedConnectionConfig(
                resolvedProviderType,
                resolvedBaseUrl,
                resolvedEndpointPath,
                resolvedCredential
        );
    }

    private void probeEndpoint(ResolvedConnectionConfig resolvedConfig) {
        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(createRequestFactory())
                .baseUrl(resolvedConfig.baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        applyCredentialHeaders(builder, resolvedConfig);
        RestClient client = builder.build();
        client.post()
                .uri(resolvedConfig.endpointPath)
                .body("""
                        {"probe":true}
                        """)
                .retrieve()
                .toBodilessEntity();
    }

    private void applyCredentialHeaders(
            RestClient.Builder builder,
            ResolvedConnectionConfig resolvedConfig
    ) {
        if (!StringUtils.hasText(resolvedConfig.credential)) {
            return;
        }
        String credential = resolvedConfig.credential.trim();
        JsonNode credentialNode = parseCredential(credential);
        if (credentialNode == null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credential);
            return;
        }
        addHeaderIfPresent(builder, HttpHeaders.AUTHORIZATION, "Bearer " + readCredentialValue(
                credentialNode,
                "apiKey",
                "token",
                "bearerToken"
        ));
        addHeaderIfPresent(builder, "x-api-key", readCredentialValue(credentialNode, "xApiKey", "apiKey"));
        addHeaderIfPresent(builder, "x-secret-id", readCredentialValue(credentialNode, "secretId"));
        addHeaderIfPresent(builder, "x-secret-key", readCredentialValue(credentialNode, "secretKey"));
        addHeaderIfPresent(builder, "x-access-key-id", readCredentialValue(credentialNode, "accessKeyId"));
        addHeaderIfPresent(builder, "x-access-key-secret", readCredentialValue(credentialNode, "accessKeySecret"));
        addHeaderIfPresent(builder, "x-project-id", readCredentialValue(credentialNode, "projectId"));
    }

    private void addHeaderIfPresent(RestClient.Builder builder, String headerName, String headerValue) {
        if (StringUtils.hasText(headerValue)) {
            builder.defaultHeader(headerName, headerValue);
        }
    }

    private String readCredentialValue(JsonNode credentialNode, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = credentialNode.path(fieldName);
            if (!valueNode.isMissingNode() && !valueNode.isNull() && StringUtils.hasText(valueNode.asText())) {
                return valueNode.asText().trim();
            }
        }
        return "";
    }

    private JsonNode parseCredential(String credential) {
        if (!credential.startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readTree(credential);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("credential 不是合法 JSON");
        }
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS).toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        requestFactory.setProxy(Proxy.NO_PROXY);
        return requestFactory;
    }

    private String normalizeProviderType(String providerType) {
        String normalized = StringUtils.hasText(providerType)
                ? providerType.trim().toLowerCase(Locale.ROOT)
                : "";
        if (DocumentParseProviderConnection.PROVIDER_TENCENT_OCR.equals(normalized)
                || DocumentParseProviderConnection.PROVIDER_ALIYUN_OCR.equals(normalized)
                || DocumentParseProviderConnection.PROVIDER_GOOGLE_DOCUMENT_AI.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的文档解析供应商: " + providerType);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeEndpointPath(String endpointPath) {
        String normalized = endpointPath == null ? "" : endpointPath.trim();
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String defaultEndpointPath(String providerType) {
        if (DocumentParseProviderConnection.PROVIDER_ALIYUN_OCR.equals(providerType)) {
            return "/ocr/v1/general";
        }
        if (DocumentParseProviderConnection.PROVIDER_GOOGLE_DOCUMENT_AI.equals(providerType)) {
            return "/v1/documents:process";
        }
        return "/ocr/v1/general-basic";
    }

    private String buildFailureMessage(String providerType, Exception exception) {
        String providerLabel = StringUtils.hasText(providerType) ? providerType : "document-parse";
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        return providerLabel + " 连接失败: " + message;
    }

    /**
     * 探测结果
     *
     * 职责：承载文档解析连接测试结果
     *
     * @author xiexu
     */
    public static class ProbeResult {

        private final boolean success;

        private final String providerType;

        private final Long latencyMs;

        private final String endpoint;

        private final String message;

        /**
         * 创建探测结果。
         *
         * @param success 是否成功
         * @param providerType 供应商类型
         * @param latencyMs 耗时
         * @param endpoint 探测地址
         * @param message 提示信息
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
         * 返回供应商类型。
         *
         * @return 供应商类型
         */
        public String getProviderType() {
            return providerType;
        }

        /**
         * 返回耗时。
         *
         * @return 耗时
         */
        public Long getLatencyMs() {
            return latencyMs;
        }

        /**
         * 返回探测地址。
         *
         * @return 探测地址
         */
        public String getEndpoint() {
            return endpoint;
        }

        /**
         * 返回提示信息。
         *
         * @return 提示信息
         */
        public String getMessage() {
            return message;
        }
    }

    private static class ResolvedConnectionConfig {

        private final String providerType;

        private final String baseUrl;

        private final String endpointPath;

        private final String credential;

        private ResolvedConnectionConfig(
                String providerType,
                String baseUrl,
                String endpointPath,
                String credential
        ) {
            this.providerType = providerType;
            this.baseUrl = baseUrl;
            this.endpointPath = endpointPath;
            this.credential = credential;
        }
    }
}
