package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.service.DocumentParseRoutePolicyAdminService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
     * <p>承载管理侧默认路由策略保存参数，由 Spring MVC 从 JSON 请求体绑定。
     *
     * @author xiexu
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParsePolicyRequest {

        /** 图片类型文档的解析连接主键。为 {@code null} 时不对图片做解析路由。 */
        private Long imageConnectionId;

        /** 扫描 PDF 类型文档的解析连接主键。为 {@code null} 时不对扫描 PDF 做解析路由。 */
        private Long scannedPdfConnectionId;

        /** 是否启用文档清理（cleanup）步骤。为 {@code null} 时按 {@code false} 处理。 */
        private Boolean cleanupEnabled;

        /** cleanup 步骤使用的 LLM 模型配置主键。仅 cleanupEnabled=true 时生效。 */
        private Long cleanupModelProfileId;

        /**
         * 降级路由策略 JSON。
         *
         * <p>定义无法匹配到明确连接时的兜底行为。可为空，默认 {@code "{}"}。服务端做 JSON 对象格式校验。</p>
         */
        private String fallbackPolicyJson;

        /** 操作人标识。为空时默认 {@code "admin"}。 */
        private String operator;
    }

    /**
     * 文档解析路由策略响应。
     *
     * <p>返回默认路由策略展示信息，由 {@code toResponse()} 组装。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParsePolicyResponse {

        /** 策略主键。 */
        private Long id;

        /** 策略范围标识（固定为 {@code "default"}）。 */
        private String policyScope;

        /** 图片文档解析连接主键。 */
        private Long imageConnectionId;

        /** 扫描 PDF 文档解析连接主键。 */
        private Long scannedPdfConnectionId;

        /** 是否启用文档清理。 */
        private boolean cleanupEnabled;

        /** cleanup 模型配置主键。 */
        private Long cleanupModelProfileId;

        /** 降级路由策略 JSON。 */
        private String fallbackPolicyJson;

        /** 创建人。 */
        private String createdBy;

        /** 最后更新人。 */
        private String updatedBy;

        /** 创建时间（ISO-8601 字符串）。 */
        private String createdAt;

        /** 最后更新时间（ISO-8601 字符串）。 */
        private String updatedAt;
    }
}
