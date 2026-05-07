package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.service.DocumentParseRoutePolicyAdminService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧文档解析路由策略控制器
 *
 * 职责：暴露默认路由策略的查询与保存接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/document-parse/policies")
public class AdminDocumentParsePolicyController {

    private final DocumentParseRoutePolicyAdminService documentParseRoutePolicyAdminService;

    private final ObjectMapper objectMapper;

    /**
     * 创建管理侧文档解析路由策略控制器。
     *
     * @param documentParseRoutePolicyAdminService 路由策略后台服务
     * @param objectMapper Jackson 对象映射器
     */
    public AdminDocumentParsePolicyController(
            DocumentParseRoutePolicyAdminService documentParseRoutePolicyAdminService,
            ObjectMapper objectMapper
    ) {
        this.documentParseRoutePolicyAdminService = documentParseRoutePolicyAdminService;
        this.objectMapper = objectMapper;
    }

    /**
     * 返回默认路由策略。
     *
     * @return 默认路由策略
     */
    @GetMapping("/default")
    public AdminDocumentParsePolicyResponse getDefaultPolicy() {
        return toResponse(documentParseRoutePolicyAdminService.getDefaultPolicy());
    }

    /**
     * 保存默认路由策略。
     *
     * @param request 请求体
     * @return 保存后的路由策略
     */
    @PutMapping("/default")
    public AdminDocumentParsePolicyResponse updateDefaultPolicy(
            @RequestBody AdminDocumentParsePolicyRequest request
    ) {
        validateRequest(request);
        ParseRoutePolicy existingPolicy = documentParseRoutePolicyAdminService.getDefaultPolicy();
        ParseRoutePolicy savedPolicy = documentParseRoutePolicyAdminService.saveDefaultPolicy(new ParseRoutePolicy(
                existingPolicy.getId(),
                ParseRoutePolicy.DEFAULT_SCOPE,
                request.getImageConnectionId(),
                request.getScannedPdfConnectionId(),
                request.getCleanupEnabled() != null && request.getCleanupEnabled().booleanValue(),
                request.getCleanupModelProfileId(),
                normalizeJsonObject(request.getFallbackPolicyJson(), "fallbackPolicyJson"),
                existingPolicy.getCreatedBy(),
                resolveOperator(request.getOperator()),
                existingPolicy.getCreatedAt(),
                existingPolicy.getUpdatedAt()
        ));
        return toResponse(savedPolicy);
    }

    /**
     * 校验策略请求。
     *
     * @param request 请求体
     */
    private void validateRequest(AdminDocumentParsePolicyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
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
            return "{}";
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
     * 解析操作人。
     *
     * @param operator 操作人
     * @return 操作人
     */
    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : "admin";
    }

    /**
     * 把策略模型映射为响应。
     *
     * @param policy 路由策略
     * @return 响应
     */
    private AdminDocumentParsePolicyResponse toResponse(ParseRoutePolicy policy) {
        return new AdminDocumentParsePolicyResponse(
                policy.getId(),
                policy.getPolicyScope(),
                policy.getImageConnectionId(),
                policy.getScannedPdfConnectionId(),
                policy.isCleanupEnabled(),
                policy.getCleanupModelProfileId(),
                normalizeJsonObject(policy.getFallbackPolicyJson(), "fallbackPolicyJson"),
                policy.getCreatedBy(),
                policy.getUpdatedBy(),
                policy.getCreatedAt() == null ? null : policy.getCreatedAt().toString(),
                policy.getUpdatedAt() == null ? null : policy.getUpdatedAt().toString()
        );
    }

    /**
     * 文档解析路由策略请求。
     *
     * 职责：承载管理侧默认路由策略保存参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParsePolicyRequest {

        private Long imageConnectionId;

        private Long scannedPdfConnectionId;

        private Boolean cleanupEnabled;

        private Long cleanupModelProfileId;

        private String fallbackPolicyJson;

        private String operator;
    }

    /**
     * 文档解析路由策略响应。
     *
     * 职责：返回默认路由策略展示信息
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParsePolicyResponse {

        private Long id;

        private String policyScope;

        private Long imageConnectionId;

        private Long scannedPdfConnectionId;

        private boolean cleanupEnabled;

        private Long cleanupModelProfileId;

        private String fallbackPolicyJson;

        private String createdBy;

        private String updatedBy;

        private String createdAt;

        private String updatedAt;
    }
}
