package com.xbk.lattice.documentparse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.documentparse.application.OcrProviderRegistry;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.domain.model.ProviderDescriptor;
import com.xbk.lattice.documentparse.domain.model.ProviderProbeResult;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * 文档解析连接探测服务
 *
 * 职责：根据后台连接信息或页面临时输入，调用对应 Adapter 的 probe 能力验证连接可用性
 *
 * @author xiexu
 */
@Service
public class DocumentParseConnectionProbeService {

    private final ObjectMapper objectMapper;

    private final DocumentParseConnectionAdminService documentParseConnectionAdminService;

    private final DocumentParseProviderDescriptorService documentParseProviderDescriptorService;

    private final OcrProviderRegistry ocrProviderRegistry;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建文档解析连接探测服务。
     *
     * @param objectMapper Jackson 对象映射器
     * @param documentParseConnectionAdminService 连接后台服务
     * @param documentParseProviderDescriptorService Provider Descriptor 服务
     * @param ocrProviderRegistry OCR Provider 注册表
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public DocumentParseConnectionProbeService(
            ObjectMapper objectMapper,
            DocumentParseConnectionAdminService documentParseConnectionAdminService,
            DocumentParseProviderDescriptorService documentParseProviderDescriptorService,
            OcrProviderRegistry ocrProviderRegistry,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.objectMapper = objectMapper;
        this.documentParseConnectionAdminService = documentParseConnectionAdminService;
        this.documentParseProviderDescriptorService = documentParseProviderDescriptorService;
        this.ocrProviderRegistry = ocrProviderRegistry;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 探测连接是否可用。
     *
     * @param connectionId 已保存连接主键
     * @param providerType 页面输入的供应商类型
     * @param baseUrl 页面输入的基础地址
     * @param credentialJson 页面输入的凭证 JSON
     * @param configJson 页面输入的扩展配置 JSON
     * @return 探测结果
     */
    public ProbeResult probe(
            Long connectionId,
            String providerType,
            String baseUrl,
            String credentialJson,
            String configJson
    ) {
        String effectiveProviderType = normalizeProviderType(providerType);
        try {
            ProviderConnection resolvedConnection = resolveConnection(
                    connectionId,
                    providerType,
                    baseUrl,
                    credentialJson,
                    configJson
            );
            effectiveProviderType = resolvedConnection.getProviderType();
            ProviderProbeResult providerProbeResult = ocrProviderRegistry.probe(resolvedConnection);
            return new ProbeResult(
                    providerProbeResult.isSuccess(),
                    providerProbeResult.getProviderType(),
                    providerProbeResult.getLatencyMs(),
                    providerProbeResult.getEndpoint(),
                    providerProbeResult.getMessage()
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
     * 解析最终连接配置。
     *
     * @param connectionId 已保存连接主键
     * @param providerType 页面输入的供应商类型
     * @param baseUrl 页面输入的基础地址
     * @param credentialJson 页面输入的凭证 JSON
     * @param configJson 页面输入的扩展配置 JSON
     * @return 可用于探测的连接配置
     */
    private ProviderConnection resolveConnection(
            Long connectionId,
            String providerType,
            String baseUrl,
            String credentialJson,
            String configJson
    ) {
        Optional<ProviderConnection> existingConnection = connectionId == null
                ? Optional.empty()
                : documentParseConnectionAdminService.findConnection(connectionId);
        String resolvedProviderType = StringUtils.hasText(providerType)
                ? documentParseProviderDescriptorService.requireProviderType(providerType)
                : existingConnection.map(ProviderConnection::getProviderType)
                .map(documentParseProviderDescriptorService::requireProviderType)
                .orElseThrow(() -> new IllegalArgumentException("请先选择文档解析供应商"));
        String resolvedBaseUrl = StringUtils.hasText(baseUrl)
                ? normalizeBaseUrl(baseUrl)
                : existingConnection.map(ProviderConnection::getBaseUrl)
                .map(this::normalizeBaseUrl)
                .orElse("");
        String resolvedCredentialJson = StringUtils.hasText(credentialJson)
                ? normalizeJsonObject(credentialJson, "credentialJson")
                : existingConnection.map(ProviderConnection::getCredentialCiphertext)
                .filter(StringUtils::hasText)
                .map(llmSecretCryptoService::decrypt)
                .map(value -> normalizeJsonObject(value, "credentialJson"))
                .orElse("");
        String resolvedConfigJson = StringUtils.hasText(configJson)
                ? normalizeJsonObject(configJson, "configJson")
                : existingConnection.map(ProviderConnection::getConfigJson)
                .map(value -> normalizeJsonObject(value, "configJson"))
                .orElse("{}");
        if (!StringUtils.hasText(resolvedBaseUrl)) {
            throw new IllegalArgumentException("请先填写接口地址");
        }
        if (!StringUtils.hasText(resolvedCredentialJson)) {
            throw new IllegalArgumentException("请先填写凭证 JSON");
        }
        return new ProviderConnection(
                existingConnection.map(ProviderConnection::getId).orElse(null),
                existingConnection.map(ProviderConnection::getConnectionCode).orElse("probe-only"),
                resolvedProviderType,
                resolvedBaseUrl,
                llmSecretCryptoService.encrypt(resolvedCredentialJson),
                "已配置 JSON 凭证",
                resolvedConfigJson,
                existingConnection.map(ProviderConnection::isEnabled).orElse(Boolean.TRUE).booleanValue(),
                existingConnection.map(ProviderConnection::getCreatedBy).orElse("admin"),
                "admin",
                existingConnection.map(ProviderConnection::getCreatedAt).orElse(null),
                existingConnection.map(ProviderConnection::getUpdatedAt).orElse(null)
        );
    }

    /**
     * 规范化 JSON 对象字符串。
     *
     * @param jsonValue JSON 字符串
     * @param fieldName 字段名
     * @return 规范化后的 JSON 字符串
     */
    private String normalizeJsonObject(String jsonValue, String fieldName) {
        if (!StringUtils.hasText(jsonValue)) {
            return "";
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonValue.trim());
            if (!jsonNode.isObject()) {
                throw new IllegalArgumentException(fieldName + "必须是 JSON 对象");
            }
            return objectMapper.writeValueAsString(jsonNode);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + "不是合法 JSON", exception);
        }
    }

    /**
     * 规范化基础地址。
     *
     * @param baseUrl 基础地址
     * @return 规范化后的基础地址
     */
    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 尝试规范化 Provider 类型。
     *
     * @param providerType Provider 类型
     * @return 规范化后的 Provider 类型
     */
    private String normalizeProviderType(String providerType) {
        if (!StringUtils.hasText(providerType)) {
            return "";
        }
        try {
            return documentParseProviderDescriptorService.requireProviderType(providerType);
        }
        catch (IllegalArgumentException exception) {
            return providerType.trim();
        }
    }

    /**
     * 构造失败提示信息。
     *
     * @param providerType Provider 类型
     * @param exception 异常
     * @return 失败提示信息
     */
    private String buildFailureMessage(String providerType, Exception exception) {
        String providerLabel = documentParseProviderDescriptorService.findDescriptor(providerType)
                .map(ProviderDescriptor::getDisplayName)
                .filter(StringUtils::hasText)
                .orElseGet(() -> StringUtils.hasText(providerType) ? providerType : "文档解析服务");
        String message = resolveFailureDetail(exception);
        if (message.startsWith(providerLabel)) {
            return message;
        }
        return providerLabel + " 连接失败: " + message;
    }

    /**
     * 提取适合管理台直接展示的失败原因。
     *
     * @param exception 异常
     * @return 失败原因
     */
    private String resolveFailureDetail(Exception exception) {
        Throwable current = exception;
        String message = "";
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage().trim();
            }
            Throwable next = current.getCause();
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        if (StringUtils.hasText(message)) {
            return message;
        }
        return exception.getClass().getSimpleName();
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
         * @param latencyMs 耗时毫秒
         * @param endpoint 命中的接口地址
         * @param message 结果说明
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
         * 返回耗时毫秒。
         *
         * @return 耗时毫秒
         */
        public Long getLatencyMs() {
            return latencyMs;
        }

        /**
         * 返回命中的接口地址。
         *
         * @return 命中的接口地址
         */
        public String getEndpoint() {
            return endpoint;
        }

        /**
         * 返回结果说明。
         *
         * @return 结果说明
         */
        public String getMessage() {
            return message;
        }
    }
}
