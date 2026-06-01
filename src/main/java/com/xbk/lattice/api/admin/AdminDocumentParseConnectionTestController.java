package com.xbk.lattice.api.admin;

import com.xbk.lattice.documentparse.service.DocumentParseConnectionProbeService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧文档解析连接测试控制器
 *
 * 职责：提供 AI 接入页的文档解析连接测试接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/document-parse/connections")
public class AdminDocumentParseConnectionTestController {

    private final DocumentParseConnectionProbeService documentParseConnectionProbeService;

    /**
     * 创建管理侧文档解析连接测试控制器。
     *
     * @param documentParseConnectionProbeService 连接探测服务
     */
    public AdminDocumentParseConnectionTestController(
            DocumentParseConnectionProbeService documentParseConnectionProbeService
    ) {
        this.documentParseConnectionProbeService = documentParseConnectionProbeService;
    }

    /**
     * 测试当前文档解析连接是否可用。
     *
     * @param request 请求体
     * @return 测试结果
     */
    @PostMapping("/test")
    public AdminDocumentParseConnectionTestResponse testConnection(
            @RequestBody AdminDocumentParseConnectionTestRequest request
    ) {
        DocumentParseConnectionProbeService.ProbeResult result = documentParseConnectionProbeService.probe(
                request.getConnectionId(),
                request.getProviderType(),
                request.getBaseUrl(),
                request.getCredentialJson(),
                request.getConfigJson()
        );
        return new AdminDocumentParseConnectionTestResponse(
                result.isSuccess(),
                result.getProviderType(),
                result.getLatencyMs(),
                result.getEndpoint(),
                result.getMessage()
        );
    }

    /**
     * 文档解析连接测试请求。
     *
     * <p>承载 AI 接入页的临时文档解析连接测试参数，由 Spring MVC 从 JSON 请求体绑定。
     *
     * @author xiexu
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseConnectionTestRequest {

        /**
         * 已有连接配置主键。
         *
         * <p>非空时优先使用已存储的凭证进行测试，忽略 {@code credentialJson}。</p>
         */
        private Long connectionId;

        /** Provider 类型。用于选择探测适配器。 */
        private String providerType;

        /** 探测目标端点 URL。 */
        private String baseUrl;

        /**
         * 临时探测用凭证 JSON（明文）。
         *
         * <p>仅用于本次连接测试，不持久化，禁止记录到日志。已加 {@code @ToString.Exclude} 防御性排除。</p>
         */
        @ToString.Exclude
        private String credentialJson;

        /** Provider 扩展配置 JSON。可选。 */
        private String configJson;
    }

    /**
     * 文档解析连接测试响应。
     *
     * <p>返回文档解析连接探测结果给 AI 接入页，由 {@code testConnection()} 组装。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseConnectionTestResponse {

        /** 连接测试是否成功。 */
        private boolean success;

        /** 探测到的 Provider 类型。 */
        private String providerType;

        /**
         * 连接延迟（毫秒）。
         *
         * <p>为 {@code null} 表示测试失败未获得延迟数据。</p>
         */
        private Long latencyMs;

        /** 实际探测的端点 URL。 */
        private String endpoint;

        /**
         * 测试结果描述。
         *
         * <p>失败时含错误原因，用于前端展示诊断信息。</p>
         */
        private String message;
    }
}
