package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.QualityMetricsService;
import com.xbk.lattice.governance.QualityMetricsTrend;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧质量控制器
 *
 * 职责：暴露当前质量指标与趋势摘要
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/quality")
public class AdminQualityController {

    private final QualityMetricsService qualityMetricsService;

    /**
     * 创建管理侧质量控制器。
     *
     * @param qualityMetricsService 质量服务
     */
    public AdminQualityController(QualityMetricsService qualityMetricsService) {
        this.qualityMetricsService = qualityMetricsService;
    }

    /**
     * 返回质量摘要与趋势。
     *
     * @param days 趋势天数
     * @return 质量响应
     */
    @GetMapping
    public AdminQualityResponse quality(@RequestParam(defaultValue = "7") int days) {
        QualityMetricsReport report = qualityMetricsService.measure();
        QualityMetricsTrend trend = qualityMetricsService.trend(days);
        return new AdminQualityResponse(report, trend);
    }
}
