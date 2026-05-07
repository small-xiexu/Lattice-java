package com.xbk.lattice.api.admin;

import com.xbk.lattice.llm.service.LlmModelProbeService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧模型测试控制器
 *
 * 职责：提供 AI 接入页的即时模型测试接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/llm/models")
public class AdminLlmModelTestController {

    private final LlmModelProbeService llmModelProbeService;

    /**
     * 创建管理侧模型测试控制器。
     *
     * @param llmModelProbeService 模型探测服务
     */
    public AdminLlmModelTestController(LlmModelProbeService llmModelProbeService) {
        this.llmModelProbeService = llmModelProbeService;
    }

    /**
     * 测试当前模型是否可用。
     *
     * @param request 请求体
     * @return 测试结果
     */
    @PostMapping("/test")
    public AdminLlmModelTestResponse testModel(@RequestBody AdminLlmModelTestRequest request) {
        LlmModelProbeService.ProbeResult result = llmModelProbeService.probe(
                request.getModelId(),
                request.getConnectionId(),
                request.getModelName(),
                request.getModelKind(),
                request.getExpectedDimensions()
        );
        return new AdminLlmModelTestResponse(
                result.isSuccess(),
                result.getProviderType(),
                result.getModelKind(),
                result.getLatencyMs(),
                result.getMessage()
        );
    }

    /**
     * 模型测试请求
     *
     * 职责：承载模型测试所需的表单参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmModelTestRequest {

        private Long modelId;

        private Long connectionId;

        private String modelName;

        private String modelKind;

        private Integer expectedDimensions;
    }

    /**
     * 模型测试响应
     *
     * 职责：返回模型探测结果给 AI 接入页
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmModelTestResponse {

        private boolean success;

        private String providerType;

        private String modelKind;

        private Long latencyMs;

        private String message;
    }
}
