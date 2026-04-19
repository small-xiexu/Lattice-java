package com.xbk.lattice.api.admin;

import com.xbk.lattice.documentparse.service.DocumentParseConnectionProbeService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
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
                request.getEndpointPath(),
                request.getCredential()
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
     * 职责：承载 AI 接入页的临时文档解析连接测试参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseConnectionTestRequest {

        private Long connectionId;

        private String providerType;

        private String baseUrl;

        private String endpointPath;

        private String credential;
    }

    /**
     * 文档解析连接测试响应。
     *
     * 职责：返回文档解析连接探测结果给 AI 接入页
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseConnectionTestResponse {

        private boolean success;

        private String providerType;

        private Long latencyMs;

        private String endpoint;

        private String message;
    }
}
