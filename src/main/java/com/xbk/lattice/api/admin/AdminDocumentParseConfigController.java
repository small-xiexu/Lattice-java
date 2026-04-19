package com.xbk.lattice.api.admin;

import com.xbk.lattice.documentparse.domain.DocumentParseProviderConnection;
import com.xbk.lattice.documentparse.domain.DocumentParseSettings;
import com.xbk.lattice.documentparse.service.DocumentParseAdminService;
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
import java.util.Locale;
import java.util.Optional;

/**
 * 管理侧文档解析配置控制器
 *
 * 职责：暴露文档解析连接与全局设置的后台管理接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/document-parse")
public class AdminDocumentParseConfigController {

    private final DocumentParseAdminService documentParseAdminService;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建管理侧文档解析配置控制器。
     *
     * @param documentParseAdminService 文档解析后台服务
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public AdminDocumentParseConfigController(
            DocumentParseAdminService documentParseAdminService,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.documentParseAdminService = documentParseAdminService;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 返回全部文档解析连接。
     *
     * @return 连接列表
     */
    @GetMapping("/connections")
    public AdminDocumentParseConnectionListResponse listConnections() {
        List<AdminDocumentParseConnectionResponse> items = new ArrayList<AdminDocumentParseConnectionResponse>();
        for (DocumentParseProviderConnection connection : documentParseAdminService.listConnections()) {
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
    @PostMapping("/connections")
    public AdminDocumentParseConnectionResponse createConnection(
            @RequestBody AdminDocumentParseConnectionRequest request
    ) {
        validateConnectionRequest(request);
        requireCredential(request.getCredential());
        return toConnectionResponse(documentParseAdminService.saveConnection(
                toConnection(null, request, Optional.empty())
        ));
    }

    /**
     * 更新文档解析连接。
     *
     * @param id 主键
     * @param request 请求体
     * @return 连接响应
     */
    @PutMapping("/connections/{id}")
    public AdminDocumentParseConnectionResponse updateConnection(
            @PathVariable Long id,
            @RequestBody AdminDocumentParseConnectionRequest request
    ) {
        validateConnectionRequest(request);
        Optional<DocumentParseProviderConnection> existing = documentParseAdminService.findConnection(id);
        return toConnectionResponse(documentParseAdminService.saveConnection(toConnection(id, request, existing)));
    }

    /**
     * 删除文档解析连接。
     *
     * @param id 主键
     * @return 删除响应
     */
    @DeleteMapping("/connections/{id}")
    public AdminMutationResponse deleteConnection(@PathVariable Long id) {
        documentParseAdminService.deleteConnection(id);
        return new AdminMutationResponse(id, "deleted");
    }

    /**
     * 返回默认文档解析设置。
     *
     * @return 设置响应
     */
    @GetMapping("/settings")
    public AdminDocumentParseSettingsResponse getSettings() {
        return toSettingsResponse(documentParseAdminService.getSettings());
    }

    /**
     * 更新默认文档解析设置。
     *
     * @param request 请求体
     * @return 设置响应
     */
    @PutMapping("/settings")
    public AdminDocumentParseSettingsResponse updateSettings(
            @RequestBody AdminDocumentParseSettingsRequest request
    ) {
        return toSettingsResponse(documentParseAdminService.saveSettings(toSettings(request)));
    }

    private DocumentParseProviderConnection toConnection(
            Long id,
            AdminDocumentParseConnectionRequest request,
            Optional<DocumentParseProviderConnection> existing
    ) {
        String credentialCiphertext = existing.map(DocumentParseProviderConnection::getCredentialCiphertext).orElse("");
        String credentialMask = existing.map(DocumentParseProviderConnection::getCredentialMask).orElse("");
        if (StringUtils.hasText(request.getCredential())) {
            credentialCiphertext = llmSecretCryptoService.encrypt(request.getCredential().trim());
            credentialMask = buildCredentialMask(request.getCredential());
        }
        if (!StringUtils.hasText(credentialCiphertext)) {
            requireCredential(request.getCredential());
            credentialCiphertext = llmSecretCryptoService.encrypt(request.getCredential().trim());
            credentialMask = buildCredentialMask(request.getCredential());
        }
        String providerType = normalizeProviderType(request.getProviderType());
        String endpointPath = resolveEndpointPath(
                request.getEndpointPath(),
                providerType,
                existing.map(DocumentParseProviderConnection::getEndpointPath).orElse("")
        );
        String operator = resolveOperator(request.getOperator());
        return new DocumentParseProviderConnection(
                id,
                request.getConnectionCode().trim(),
                providerType,
                normalizeBaseUrl(request.getBaseUrl()),
                endpointPath,
                credentialCiphertext,
                credentialMask,
                "{}",
                request.getEnabled() == null || request.getEnabled().booleanValue(),
                existing.map(DocumentParseProviderConnection::getCreatedBy).orElse(operator),
                operator,
                existing.map(DocumentParseProviderConnection::getCreatedAt).orElse(null),
                existing.map(DocumentParseProviderConnection::getUpdatedAt).orElse(null)
        );
    }

    private DocumentParseSettings toSettings(AdminDocumentParseSettingsRequest request) {
        DocumentParseSettings existing = documentParseAdminService.getSettings();
        String operator = resolveOperator(request.getOperator());
        return new DocumentParseSettings(
                existing.getId(),
                DocumentParseSettings.DEFAULT_SCOPE,
                request.getDefaultConnectionId(),
                request.getImageOcrEnabled() != null && request.getImageOcrEnabled().booleanValue(),
                request.getScannedPdfOcrEnabled() != null && request.getScannedPdfOcrEnabled().booleanValue(),
                request.getCleanupEnabled() != null && request.getCleanupEnabled().booleanValue(),
                request.getCleanupModelProfileId(),
                existing.getCreatedBy(),
                operator,
                existing.getCreatedAt(),
                existing.getUpdatedAt()
        );
    }

    private AdminDocumentParseConnectionResponse toConnectionResponse(DocumentParseProviderConnection connection) {
        return new AdminDocumentParseConnectionResponse(
                connection.getId(),
                connection.getConnectionCode(),
                connection.getProviderType(),
                connection.getBaseUrl(),
                connection.getEndpointPath(),
                connection.getCredentialMask(),
                connection.isEnabled(),
                connection.getCreatedBy(),
                connection.getUpdatedBy(),
                connection.getCreatedAt() == null ? null : connection.getCreatedAt().toString(),
                connection.getUpdatedAt() == null ? null : connection.getUpdatedAt().toString()
        );
    }

    private AdminDocumentParseSettingsResponse toSettingsResponse(DocumentParseSettings settings) {
        return new AdminDocumentParseSettingsResponse(
                settings.getId(),
                settings.getConfigScope(),
                settings.getDefaultConnectionId(),
                settings.isImageOcrEnabled(),
                settings.isScannedPdfOcrEnabled(),
                settings.isCleanupEnabled(),
                settings.getCleanupModelProfileId(),
                settings.getCreatedBy(),
                settings.getUpdatedBy(),
                settings.getCreatedAt() == null ? null : settings.getCreatedAt().toString(),
                settings.getUpdatedAt() == null ? null : settings.getUpdatedAt().toString()
        );
    }

    private void validateConnectionRequest(AdminDocumentParseConnectionRequest request) {
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

    private void requireCredential(String credential) {
        if (!StringUtils.hasText(credential)) {
            throw new IllegalArgumentException("credential不能为空");
        }
    }

    private String buildCredentialMask(String credential) {
        if (!StringUtils.hasText(credential)) {
            return "";
        }
        String normalized = credential.trim();
        if (normalized.startsWith("{")) {
            return "已配置 JSON 凭证";
        }
        return llmSecretCryptoService.mask(normalized);
    }

    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : "admin";
    }

    private String normalizeProviderType(String providerType) {
        String normalized = providerType.trim().toLowerCase(Locale.ROOT);
        if (DocumentParseProviderConnection.PROVIDER_TENCENT_OCR.equals(normalized)
                || DocumentParseProviderConnection.PROVIDER_ALIYUN_OCR.equals(normalized)
                || DocumentParseProviderConnection.PROVIDER_GOOGLE_DOCUMENT_AI.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的providerType: " + providerType);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String resolveEndpointPath(String endpointPath, String providerType, String existingEndpointPath) {
        if (StringUtils.hasText(endpointPath)) {
            return normalizeEndpointPath(endpointPath.trim());
        }
        if (StringUtils.hasText(existingEndpointPath)) {
            return normalizeEndpointPath(existingEndpointPath);
        }
        if (DocumentParseProviderConnection.PROVIDER_ALIYUN_OCR.equals(providerType)) {
            return "/ocr/v1/general";
        }
        if (DocumentParseProviderConnection.PROVIDER_GOOGLE_DOCUMENT_AI.equals(providerType)) {
            return "/v1/documents:process";
        }
        return "/ocr/v1/general-basic";
    }

    private String normalizeEndpointPath(String endpointPath) {
        return endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
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

        private String endpointPath;

        private String credential;

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

        private String endpointPath;

        private String credentialMask;

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
     * 文档解析设置请求。
     *
     * 职责：承载管理侧文档解析全局设置参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseSettingsRequest {

        private Long defaultConnectionId;

        private Boolean imageOcrEnabled;

        private Boolean scannedPdfOcrEnabled;

        private Boolean cleanupEnabled;

        private Long cleanupModelProfileId;

        private String operator;
    }

    /**
     * 文档解析设置响应。
     *
     * 职责：返回管理侧文档解析全局设置展示信息
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseSettingsResponse {

        private Long id;

        private String configScope;

        private Long defaultConnectionId;

        private boolean imageOcrEnabled;

        private boolean scannedPdfOcrEnabled;

        private boolean cleanupEnabled;

        private Long cleanupModelProfileId;

        private String createdBy;

        private String updatedBy;

        private String createdAt;

        private String updatedAt;
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
