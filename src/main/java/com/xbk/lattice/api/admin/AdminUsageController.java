package com.xbk.lattice.api.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧 usage 控制器
 *
 * 职责：暴露 LLM usage 汇总接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/usage")
public class AdminUsageController {

    private final AdminUsageQueryService adminUsageQueryService;

    /**
     * 创建管理侧 usage 控制器。
     *
     * @param adminUsageQueryService 管理侧 usage 查询服务
     */
    public AdminUsageController(AdminUsageQueryService adminUsageQueryService) {
        this.adminUsageQueryService = adminUsageQueryService;
    }

    /**
     * 返回 usage 汇总信息。
     *
     * @return usage 汇总
     */
    @GetMapping
    public AdminUsageResponse summary() {
        return adminUsageQueryService.summarize();
    }
}
