package com.xbk.lattice.api.admin;

import com.xbk.lattice.compiler.config.CompileReviewConfigService;
import com.xbk.lattice.compiler.config.CompileReviewConfigState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧 Compile 审查配置控制器
 *
 * 职责：暴露 compile review 配置的查询与保存接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/compile/review")
public class AdminCompileReviewConfigController {

    private final CompileReviewConfigService compileReviewConfigService;

    /**
     * 创建管理侧 Compile 审查配置控制器。
     *
     * @param compileReviewConfigService Compile 审查配置服务
     */
    public AdminCompileReviewConfigController(CompileReviewConfigService compileReviewConfigService) {
        this.compileReviewConfigService = compileReviewConfigService;
    }

    /**
     * 返回当前 Compile 审查配置。
     *
     * @return Compile 审查配置
     */
    @GetMapping("/config")
    public AdminCompileReviewConfigResponse getConfig() {
        return toResponse(compileReviewConfigService.getCurrentState());
    }

    /**
     * 保存 Compile 审查配置。
     *
     * @param request 配置请求
     * @return 保存后的配置
     */
    @PutMapping("/config")
    public AdminCompileReviewConfigResponse updateConfig(@RequestBody AdminCompileReviewConfigRequest request) {
        validateRequest(request);
        CompileReviewConfigState saved = compileReviewConfigService.save(
                request.getAutoFixEnabled().booleanValue(),
                request.getMaxFixRounds().intValue(),
                request.getAllowPersistNeedsHumanReview().booleanValue(),
                request.getHumanReviewSeverityThreshold(),
                request.getOperator()
        );
        return toResponse(saved);
    }

    private AdminCompileReviewConfigResponse toResponse(CompileReviewConfigState state) {
        return new AdminCompileReviewConfigResponse(
                state.isAutoFixEnabled(),
                state.getMaxFixRounds(),
                state.isAllowPersistNeedsHumanReview(),
                state.getHumanReviewSeverityThreshold(),
                state.getConfigSource(),
                state.getCreatedBy(),
                state.getUpdatedBy(),
                state.getCreatedAt() == null ? null : state.getCreatedAt().toString(),
                state.getUpdatedAt() == null ? null : state.getUpdatedAt().toString()
        );
    }

    private void validateRequest(AdminCompileReviewConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        if (request.getAutoFixEnabled() == null) {
            throw new IllegalArgumentException("autoFixEnabled不能为空");
        }
        if (request.getMaxFixRounds() == null) {
            throw new IllegalArgumentException("maxFixRounds不能为空");
        }
        if (request.getAllowPersistNeedsHumanReview() == null) {
            throw new IllegalArgumentException("allowPersistNeedsHumanReview不能为空");
        }
        if (request.getHumanReviewSeverityThreshold() == null
                || request.getHumanReviewSeverityThreshold().isBlank()) {
            throw new IllegalArgumentException("humanReviewSeverityThreshold不能为空");
        }
    }
}
