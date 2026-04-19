package com.xbk.lattice.api.admin;

import com.xbk.lattice.llm.service.LlmConnectionProbeService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧连接测试控制器
 *
 * 职责：提供 AI 接入页的即时连接测试接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/llm/connections")
public class AdminLlmConnectionTestController {

    private final LlmConnectionProbeService llmConnectionProbeService;

    /**
     * 创建管理侧连接测试控制器。
     *
     * @param llmConnectionProbeService 连接探测服务
     */
    public AdminLlmConnectionTestController(LlmConnectionProbeService llmConnectionProbeService) {
        this.llmConnectionProbeService = llmConnectionProbeService;
    }

    /**
     * 测试当前连接是否可用。
     *
     * @param request 请求体
     * @return 测试结果
     */
    @PostMapping("/test")
    public AdminLlmConnectionTestResponse testConnection(@RequestBody AdminLlmConnectionTestRequest request) {
        LlmConnectionProbeService.ProbeResult result = llmConnectionProbeService.probe(
                request.getConnectionId(),
                request.getProviderType(),
                request.getBaseUrl(),
                request.getApiKey()
        );
        return new AdminLlmConnectionTestResponse(
                result.isSuccess(),
                result.getProviderType(),
                result.getLatencyMs(),
                result.getEndpoint(),
                result.getMessage()
        );
    }

    /**
     * 连接测试请求
     *
     * 职责：承载 AI 接入页的临时连接探测参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmConnectionTestRequest {

        private Long connectionId;

        private String providerType;

        private String baseUrl;

        private String apiKey;
    }

    /**
     * 连接测试响应
     *
     * 职责：返回连接探测结果给 AI 接入页
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmConnectionTestResponse {

        private boolean success;

        private String providerType;

        private Long latencyMs;

        private String endpoint;

        private String message;
    }
}
