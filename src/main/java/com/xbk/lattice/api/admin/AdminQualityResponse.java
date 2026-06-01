package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.QualityMetricsTrend;
import lombok.Getter;

/**
 * 管理侧质量响应。
 *
 * <p>承载当前质量指标报告与指定时间窗趋势，用于管理侧质量 Dashboard 展示。
 *
 * <p><b>已知分层问题：</b>{@code report} 和 {@code trend} 直接暴露
 * {@link QualityMetricsReport} 和 {@link QualityMetricsTrend}（{@code governance} 层领域对象），
 * 未经 DTO 包装。本轮不做修复，仅标注。
 *
 * @author xiexu
 */
@Getter
public class AdminQualityResponse {

    /**
     * 当前质量指标报告。
     *
     * <p>含各类质量计数、比率和状态指标。直接暴露 {@link QualityMetricsReport} 领域对象——
     * 后续治理应引入专用的 Quality DTO。</p>
     */
    private final QualityMetricsReport report;

    /**
     * 指定时间窗质量趋势。
     *
     * <p>含多日指标序列，用于管理侧展示质量变化趋势。直接暴露
     * {@link QualityMetricsTrend} 领域对象——后续治理应引入专用的 Trend DTO。</p>
     */
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
}
