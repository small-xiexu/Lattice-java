package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.QualityMetricsTrend;

/**
 * 管理侧质量响应
 *
 * 职责：承载当前质量指标与指定时间窗趋势
 *
 * @author xiexu
 */
public class AdminQualityResponse {

    private final QualityMetricsReport report;

    private final QualityMetricsTrend trend;

    /**
     * 创建管理侧质量响应。
     *
     * @param report 当前质量报告
     * @param trend 趋势摘要
     */
    public AdminQualityResponse(QualityMetricsReport report, QualityMetricsTrend trend) {
        this.report = report;
        this.trend = trend;
    }

    public QualityMetricsReport getReport() {
        return report;
    }

    public QualityMetricsTrend getTrend() {
        return trend;
    }
}
