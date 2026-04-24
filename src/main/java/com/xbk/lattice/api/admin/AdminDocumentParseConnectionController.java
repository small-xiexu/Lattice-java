package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.service.DocumentParseConnectionAdminService;
import com.xbk.lattice.documentparse.service.DocumentParseProviderDescriptorService;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 管理侧文档解析连接控制器
 *
 * 职责：暴露文档解析连接的查询、创建、更新与删除接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/document-parse/connections")
public class AdminDocumentParseConnectionController {

    private final DocumentParseConnectionAdminService documentParseConnectionAdminService;

    private final DocumentParseProviderDescriptorService documentParseProviderDescriptorService;

    private final LlmSecretCryptoService llmSecretCryptoService;

    private final ObjectMapper objectMapper;

    /**
     * 创建管理侧文档解析连接控制器。
     *
     * @param documentParseConnectionAdminService 连接后台服务
     * @param documentParseProviderDescriptorService Provider Descriptor 服务
     * @param llmSecretCryptoService 密钥加解密服务
     * @param objectMapper Jackson 对象映射器
     */
    public AdminDocumentParseConnectionController(
            DocumentParseConnectionAdminService documentParseConnectionAdminService,
            DocumentParseProviderDescriptorService documentParseProviderDescriptorService,
            LlmSecretCryptoService llmSecretCryptoService,
            ObjectMapper objectMapper
    ) {
        this.documentParseConnectionAdminService = documentParseConnectionAdminService;
        this.documentParseProviderDescriptorService = documentParseProviderDescriptorService;
        this.llmSecretCryptoService = llmSecretCryptoService;
        this.objectMapper = objectMapper;
    }

    /**
     * 返回全部文档解析连接。
     *
     * @return 连接列表
     */
    @GetMapping
    public AdminDocumentParseConnectionListResponse listConnections() {
        List<AdminDocumentParseConnectionResponse> items = new ArrayList<AdminDocumentParseConnectionResponse>();
        for (ProviderConnection connection : documentParseConnectionAdminService.listConnections()) {
            items.add(toConnectionResponse(connection));
        }
        return new AdminDocumentParseConnectionListResponse(items.size(), items);
    }

    /**
     * 新增文档解析连接。
     *
     * @param request 请求体
     * @return 连接响应
     */
    @PostMapping
    public AdminDocumentParseConnectionResponse createConnection(
            @RequestBody AdminDocumentParseConnectionRequest request
    ) {
        validateRequest(request);
        ProviderConnection connection = toConnection(null, request, Optional.empty());
        return toConnectionResponse(documentParseConnectionAdminService.saveConnection(connection));
    }

    /**
     * 更新文档解析连接。
     *
     * @param id 主键
     * @param request 请求体
     * @return 连接响应
     */
    @PutMapping("/{id}")
    public AdminDocumentParseConnectionResponse updateConnection(
            @PathVariable Long id,
            @RequestBody AdminDocumentParseConnectionRequest request
    ) {
        validateRequest(request);
        Optional<ProviderConnection> existingConnection = documentParseConnectionAdminService.findConnection(id);
        if (existingConnection.isEmpty()) {
            throw new IllegalArgumentException("connection不存在: " + id);
        }
        ProviderConnection connection = toConnection(id, request, existingConnection);
        return toConnectionResponse(documentParseConnectionAdminService.saveConnection(connection));
    }

    /**
     * 删除文档解析连接。
     *
     * @param id 主键
     * @return 删除响应
     */
    @DeleteMapping("/{id}")
    public AdminMutationResponse deleteConnection(@PathVariable Long id) {
        documentParseConnectionAdminService.deleteConnection(id);
        return new AdminMutationResponse(id, "deleted");
    }

    /**
     * 把请求映射为连接模型。
     *
     * @param id 主键
     * @param request 请求体
     * @param existingConnection 已存在连接
     * @return 连接模型
     */
    private ProviderConnection toConnection(
            Long id,
            AdminDocumentParseConnectionRequest request,
            Optional<ProviderConnection> existingConnection
    ) {
        String providerType = documentParseProviderDescriptorService.requireProviderType(request.getProviderType());
        String connectionCode = request.getConnectionCode().trim();
        String baseUrl = normalizeBaseUrl(request.getBaseUrl());
        String configJson = resolveConfigJson(request.getConfigJson(), existingConnection);
        String credentialCiphertext = resolveCredentialCiphertext(request.getCredentialJson(), existingConnection);
        String credentialMask = resolveCredentialMask(request.getCredentialJson(), existingConnection);
        boolean enabled = request.getEnabled() == null || request.getEnabled().booleanValue();
        String operator = resolveOperator(request.getOperator());
        return new ProviderConnection(
                id,
                connectionCode,
                providerType,
                baseUrl,
                credentialCiphertext,
                credentialMask,
                configJson,
                enabled,
                existingConnection.map(ProviderConnection::getCreatedBy).orElse(operator),
                operator,
                existingConnection.map(ProviderConnection::getCreatedAt).orElse(null),
                existingConnection.map(ProviderConnection::getUpdatedAt).orElse(null)
        );
    }

    /**
     * 解析配置 JSON。
     *
     * @param configJson 请求中的配置 JSON
     * @param existingConnection 已存在连接
     * @return 规范化后的配置 JSON
     */
    private String resolveConfigJson(
            String configJson,
            Optional<ProviderConnection> existingConnection
    ) {
        if (StringUtils.hasText(configJson)) {
            return normalizeJsonObject(configJson, "configJson");
        }
        return existingConnection.map(ProviderConnection::getConfigJson).orElse("{}");
    }

    /**
     * 解析凭证密文。
     *
     * @param credentialJson 请求中的凭证 JSON
     * @param existingConnection 已存在连接
     * @return 凭证密文
     */
    private String resolveCredentialCiphertext(
            String credentialJson,
            Optional<ProviderConnection> existingConnection
    ) {
        if (StringUtils.hasText(credentialJson)) {
            String normalizedCredentialJson = normalizeJsonObject(credentialJson, "credentialJson");
            return llmSecretCryptoService.encrypt(normalizedCredentialJson);
        }
        return existingConnection.map(ProviderConnection::getCredentialCiphertext)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalArgumentException("credentialJson不能为空"));
    }

    /**
     * 解析凭证脱敏值。
     *
     * @param credentialJson 请求中的凭证 JSON
     * @param existingConnection 已存在连接
     * @return 凭证脱敏值
     */
    private String resolveCredentialMask(
            String credentialJson,
            Optional<ProviderConnection> existingConnection
    ) {
        if (StringUtils.hasText(credentialJson)) {
            normalizeJsonObject(credentialJson, "credentialJson");
            return "已配置 JSON 凭证";
        }
        return existingConnection.map(ProviderConnection::getCredentialMask)
                .filter(StringUtils::hasText)
                .orElse("已配置 JSON 凭证");
    }

    /**
     * 规范化 JSON 对象字符串。
     *
     * @param jsonValue JSON 字符串
     * @param fieldName 字段名
     * @return 规范化后的 JSON 字符串
     */
    private String normalizeJsonObject(String jsonValue, String fieldName) {
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
     * 校验连接请求。
     *
     * @param request 请求体
     */
    private void validateRequest(AdminDocumentParseConnectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        if (!StringUtils.hasText(request.getConnectionCode())) {
            throw new IllegalArgumentException("connectionCode不能为空");
        }
        if (!StringUtils.hasText(request.getProviderType())) {
            throw new IllegalArgumentException("providerType不能为空");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new IllegalArgumentException("baseUrl不能为空");
        }
    }

    /**
     * 构造成管理侧连接响应。
     *
     * @param connection 连接模型
     * @return 管理侧连接响应
     */
    private AdminDocumentParseConnectionResponse toConnectionResponse(ProviderConnection connection) {
        return new AdminDocumentParseConnectionResponse(
                connection.getId(),
                connection.getConnectionCode(),
                connection.getProviderType(),
                connection.getBaseUrl(),
                connection.getCredentialMask(),
                StringUtils.hasText(connection.getCredentialCiphertext()),
                normalizeJsonObject(connection.getConfigJson(), "configJson"),
                connection.isEnabled(),
                connection.getCreatedBy(),
                connection.getUpdatedBy(),
                connection.getCreatedAt() == null ? null : connection.getCreatedAt().toString(),
                connection.getUpdatedAt() == null ? null : connection.getUpdatedAt().toString()
        );
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
     * 解析操作人。
     *
     * @param operator 操作人
     * @return 操作人
     */
    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : "admin";
    }

    /**
     * 文档解析连接请求。
     *
     * 职责：承载管理侧文档解析连接新增与更新参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseConnectionRequest {

        private String connectionCode;

        private String providerType;

        private String baseUrl;

        private String credentialJson;

        private String configJson;

        private Boolean enabled;

        private String operator;
    }

    /**
     * 文档解析连接响应。
     *
     * 职责：返回管理侧文档解析连接展示信息
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseConnectionResponse {

        private Long id;

        private String connectionCode;

        private String providerType;

        private String baseUrl;

        private String credentialMask;

        private boolean credentialConfigured;

        private String configJson;

        private boolean enabled;

        private String createdBy;

        private String updatedBy;

        private String createdAt;

        private String updatedAt;
    }

    /**
     * 文档解析连接列表响应。
     *
     * 职责：返回管理侧文档解析连接列表
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseConnectionListResponse {

        private int count;

        private List<AdminDocumentParseConnectionResponse> items;
    }

    /**
     * 通用变更响应。
     *
     * 职责：返回管理侧删除操作结果
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminMutationResponse {

        private Long id;

        private String status;
    }
}
