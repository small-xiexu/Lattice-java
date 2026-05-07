package com.xbk.lattice.api.admin;

import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧 Query 检索配置控制器
 *
 * 职责：暴露 query_retrieval_settings 的查看与保存接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/query/retrieval")
public class AdminQueryRetrievalConfigController {

    private final QueryRetrievalSettingsService queryRetrievalSettingsService;

    /**
     * 创建管理侧 Query 检索配置控制器。
     *
     * @param queryRetrievalSettingsService Query 检索配置服务
     */
    public AdminQueryRetrievalConfigController(QueryRetrievalSettingsService queryRetrievalSettingsService) {
        this.queryRetrievalSettingsService = queryRetrievalSettingsService;
    }

    /**
     * 返回当前 Query 检索配置。
     *
     * @return Query 检索配置
     */
    @GetMapping("/config")
    public AdminQueryRetrievalConfigResponse getConfig() {
        return toResponse(queryRetrievalSettingsService.getCurrentState());
    }

    /**
     * 保存 Query 检索配置。
     *
     * @param request 配置请求
     * @return 保存后的配置
     */
    @PutMapping("/config")
    public AdminQueryRetrievalConfigResponse updateConfig(@RequestBody AdminQueryRetrievalConfigRequest request) {
        validateRequest(request);
        QueryRetrievalSettingsState saved = queryRetrievalSettingsService.save(
                request.getParallelEnabled().booleanValue(),
                request.getRewriteEnabled().booleanValue(),
                request.getIntentAwareVectorEnabled().booleanValue(),
                request.getFtsWeight().doubleValue(),
                request.getRefkeyWeight().doubleValue(),
                request.getArticleChunkWeight().doubleValue(),
                request.getSourceWeight().doubleValue(),
                request.getSourceChunkWeight().doubleValue(),
                request.getFactCardWeight().doubleValue(),
                request.getContributionWeight().doubleValue(),
                request.getGraphWeight().doubleValue(),
                request.getArticleVectorWeight().doubleValue(),
                request.getChunkVectorWeight().doubleValue(),
                request.getRrfK().intValue()
        );
        return toResponse(saved);
    }

    /**
     * 把运行时状态映射为管理侧响应。
     *
     * @param state 运行时状态
     * @return 管理侧响应
     */
    private AdminQueryRetrievalConfigResponse toResponse(QueryRetrievalSettingsState state) {
        return new AdminQueryRetrievalConfigResponse(
                state.isParallelEnabled(),
                state.isRewriteEnabled(),
                state.isIntentAwareVectorEnabled(),
                state.getFtsWeight(),
                state.getRefkeyWeight(),
                state.getArticleChunkWeight(),
                state.getSourceWeight(),
                state.getSourceChunkWeight(),
                state.getFactCardWeight(),
                state.getContributionWeight(),
                state.getGraphWeight(),
                state.getArticleVectorWeight(),
                state.getChunkVectorWeight(),
                state.getRrfK()
        );
    }

    /**
     * 校验 Query 检索配置请求。
     *
     * @param request 配置请求
     */
    private void validateRequest(AdminQueryRetrievalConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        if (request.getParallelEnabled() == null) {
            throw new IllegalArgumentException("parallelEnabled不能为空");
        }
        if (request.getRewriteEnabled() == null) {
            throw new IllegalArgumentException("rewriteEnabled不能为空");
        }
        if (request.getIntentAwareVectorEnabled() == null) {
            throw new IllegalArgumentException("intentAwareVectorEnabled不能为空");
        }
        validateWeight(request.getFtsWeight(), "ftsWeight");
        validateWeight(request.getRefkeyWeight(), "refkeyWeight");
        validateWeight(request.getArticleChunkWeight(), "articleChunkWeight");
        validateWeight(request.getSourceWeight(), "sourceWeight");
        validateWeight(request.getSourceChunkWeight(), "sourceChunkWeight");
        validateWeight(request.getFactCardWeight(), "factCardWeight");
        validateWeight(request.getContributionWeight(), "contributionWeight");
        validateWeight(request.getGraphWeight(), "graphWeight");
        validateWeight(request.getArticleVectorWeight(), "articleVectorWeight");
        validateWeight(request.getChunkVectorWeight(), "chunkVectorWeight");
        if (request.getRrfK() == null || request.getRrfK().intValue() <= 0) {
            throw new IllegalArgumentException("rrfK必须大于0");
        }
    }

    /**
     * 校验权重字段。
     *
     * @param value 权重
     * @param field 字段名
     */
    private void validateWeight(Double value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (value.doubleValue() < 0D) {
            throw new IllegalArgumentException(field + "不能小于0");
        }
    }
}
