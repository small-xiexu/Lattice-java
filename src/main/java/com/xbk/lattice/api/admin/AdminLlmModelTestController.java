package com.xbk.lattice.api.admin;

import com.xbk.lattice.llm.service.LlmModelProbeService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
                request.getExpectedDimensions(),
                request.getTimeoutSeconds()
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
     * 模型测试请求。
     *
     * <p>承载模型测试所需的表单参数，由 Spring MVC 从 JSON 请求体绑定。
     *
     * @author xiexu
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmModelTestRequest {

        /** 已有模型配置主键。非空时使用已存储的配置参数。 */
        private Long modelId;

        /** 关联连接配置主键。用于获取 apiKey 进行探测。 */
        private Long connectionId;

        /** Provider 模型名。 */
        private String modelName;

        /** 模型类别（{@code CHAT} / {@code EMBEDDING}）。 */
        private String modelKind;

        /** 期望向量维度。仅 EMBEDDING 模型使用。 */
        private Integer expectedDimensions;

        /** 探测超时秒数。 */
        private Integer timeoutSeconds;
    }

    /**
     * 模型测试响应。
     *
     * <p>返回模型探测结果给 AI 接入页，由 {@code testModel()} 组装。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmModelTestResponse {

        /** 模型测试是否成功。 */
        private boolean success;

        /** Provider 类型。 */
        private String providerType;

        /** 模型类别。 */
        private String modelKind;

        /**
         * 调用延迟（毫秒）。
         *
         * <p>为 {@code null} 表示测试失败未获得延迟数据。</p>
         */
        private Long latencyMs;

        /**
         * 测试结果描述。
         *
         * <p>失败时含错误原因。</p>
         */
        private String message;
    }
}
