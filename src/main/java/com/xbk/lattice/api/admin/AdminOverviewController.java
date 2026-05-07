package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.QualityMetricsService;
import com.xbk.lattice.governance.StatusService;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理侧总览控制器
 *
 * 职责：暴露 B8 最小只读管理总览接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOverviewController {

    private final StatusService statusService;

    private final QualityMetricsService qualityMetricsService;

    private final PendingQueryManager pendingQueryManager;

    /**
     * 创建管理侧总览控制器。
     *
     * @param statusService 状态服务
     * @param qualityMetricsService 质量指标服务
     * @param pendingQueryManager pending 查询管理器
     */
    public AdminOverviewController(
            StatusService statusService,
            QualityMetricsService qualityMetricsService,
            PendingQueryManager pendingQueryManager
    ) {
        this.statusService = statusService;
        this.qualityMetricsService = qualityMetricsService;
        this.pendingQueryManager = pendingQueryManager;
    }

    /**
     * 返回管理侧总览信息。
     *
     * @return 总览响应
     */
    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        List<PendingQueryRecord> pendingRecords = pendingQueryManager.listPendingQueries();
        List<AdminOverviewPendingItemResponse> items = new ArrayList<AdminOverviewPendingItemResponse>();
        for (PendingQueryRecord pendingRecord : pendingRecords) {
            items.add(new AdminOverviewPendingItemResponse(
                    pendingRecord.getQueryId(),
                    pendingRecord.getQuestion(),
                    pendingRecord.getReviewStatus()
            ));
        }
        return new AdminOverviewResponse(
                statusService.snapshot(),
                qualityMetricsService.measure(),
                new AdminOverviewPendingResponse(pendingRecords.size(), items)
        );
    }
}
