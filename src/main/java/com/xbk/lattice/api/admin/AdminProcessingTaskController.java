package com.xbk.lattice.api.admin;

import com.xbk.lattice.admin.service.AdminProcessingTaskService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台当前处理任务控制器。
 *
 * 职责：暴露统一后的当前处理任务概览与列表接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin")
public class AdminProcessingTaskController {

    private final AdminProcessingTaskService adminProcessingTaskService;

    /**
     * 创建工作台当前处理任务控制器。
     *
     * @param adminProcessingTaskService 当前处理任务服务
     */
    public AdminProcessingTaskController(AdminProcessingTaskService adminProcessingTaskService) {
        this.adminProcessingTaskService = adminProcessingTaskService;
    }

    /**
     * 查询当前处理任务列表。
     *
     * @param limit 返回数量
     * @return 当前处理任务列表
     */
    @GetMapping("/processing-tasks")
    public AdminProcessingTaskListResponse listProcessingTasks(@RequestParam(defaultValue = "10") Integer limit) {
        int resolvedLimit = limit == null ? 10 : Math.max(1, Math.min(limit.intValue(), 50));
        return adminProcessingTaskService.listProcessingTasks(resolvedLimit);
    }
}
