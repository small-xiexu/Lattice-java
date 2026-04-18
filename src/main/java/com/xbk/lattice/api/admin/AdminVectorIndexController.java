package com.xbk.lattice.api.admin;

import com.xbk.lattice.query.service.QueryVectorConfigService;
import com.xbk.lattice.query.service.QueryVectorConfigState;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/**
 * 管理侧向量索引控制器
 *
 * 职责：暴露向量配置、向量索引状态查询与全量重建接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/vector")
public class AdminVectorIndexController {

    private final AdminVectorIndexMaintenanceService adminVectorIndexMaintenanceService;

    private final QueryVectorConfigService queryVectorConfigService;

    /**
     * 创建管理侧向量索引控制器。
     *
     * @param adminVectorIndexMaintenanceService 向量索引维护服务
     * @param queryVectorConfigService Query 向量配置服务
     */
    public AdminVectorIndexController(
            AdminVectorIndexMaintenanceService adminVectorIndexMaintenanceService,
            QueryVectorConfigService queryVectorConfigService
    ) {
        this.adminVectorIndexMaintenanceService = adminVectorIndexMaintenanceService;
        this.queryVectorConfigService = queryVectorConfigService;
    }

    /**
     * 返回当前有效向量配置。
     *
     * @return 向量配置
     */
    @GetMapping("/config")
    public AdminVectorConfigResponse getConfig() {
        return toConfigResponse(queryVectorConfigService.getCurrentState());
    }

    /**
     * 保存向量配置。
     *
     * @param request 配置请求
     * @return 向量配置
     */
    @PutMapping("/config")
    public AdminVectorConfigResponse updateConfig(@RequestBody AdminVectorConfigRequest request) {
        validateConfigRequest(request);
        QueryVectorConfigState state = queryVectorConfigService.save(
                request.getVectorEnabled().booleanValue(),
                request.getEmbeddingModelProfileId(),
                request.getOperator()
        );
        return toConfigResponse(state);
    }

    /**
     * 返回向量索引状态。
     *
     * @return 向量索引状态
     */
    @GetMapping("/status")
    public AdminVectorIndexStatusResponse getStatus() {
        return adminVectorIndexMaintenanceService.getStatus();
    }

    /**
     * 执行向量索引全量重建。
     *
     * @param request 重建请求
     * @return 重建结果
     */
    @PostMapping("/rebuild")
    public AdminVectorIndexRebuildResponse rebuild(@RequestBody(required = false) AdminVectorIndexRebuildRequest request) {
        return adminVectorIndexMaintenanceService.rebuild(request);
    }

    /**
     * 把运行时配置状态映射为响应。
     *
     * @param state 运行时配置状态
     * @return 管理侧响应
     */
    private AdminVectorConfigResponse toConfigResponse(QueryVectorConfigState state) {
        return new AdminVectorConfigResponse(
                state.isVectorEnabled(),
                state.getEmbeddingModelProfileId(),
                state.getProviderType(),
                state.getModelName(),
                state.getProfileDimensions(),
                state.getConfigSource(),
                state.isRebuildRecommended(),
                state.getRebuildReason(),
                state.getCreatedBy(),
                state.getUpdatedBy(),
                state.getCreatedAt() == null ? null : state.getCreatedAt().toString(),
                state.getUpdatedAt() == null ? null : state.getUpdatedAt().toString()
        );
    }

    /**
     * 校验向量配置请求。
     *
     * @param request 配置请求
     */
    private void validateConfigRequest(AdminVectorConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        if (request.getVectorEnabled() == null) {
            throw new IllegalArgumentException("vectorEnabled不能为空");
        }
        if (request.getEmbeddingModelProfileId() == null || request.getEmbeddingModelProfileId().longValue() <= 0L) {
            throw new IllegalArgumentException("embeddingModelProfileId必须大于0");
        }
    }
}
