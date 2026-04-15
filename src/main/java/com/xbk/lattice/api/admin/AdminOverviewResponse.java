package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.StatusSnapshot;

/**
 * 管理侧总览响应
 *
 * 职责：聚合状态、质量与 pending 汇总，供 B8 最小管理侧使用
 *
 * @author xiexu
 */
public class AdminOverviewResponse {

    private final StatusSnapshot status;

    private final QualityMetricsReport quality;

    private final AdminOverviewPendingResponse pending;

    /**
     * 创建管理侧总览响应。
     *
     * @param status 状态快照
     * @param quality 质量报告
     * @param pending pending 汇总
     */
    public AdminOverviewResponse(
            StatusSnapshot status,
            QualityMetricsReport quality,
            AdminOverviewPendingResponse pending
    ) {
        this.status = status;
        this.quality = quality;
        this.pending = pending;
    }

    /**
     * 获取状态快照。
     *
     * @return 状态快照
     */
    public StatusSnapshot getStatus() {
        return status;
    }

    /**
     * 获取质量报告。
     *
     * @return 质量报告
     */
    public QualityMetricsReport getQuality() {
        return quality;
    }

    /**
     * 获取 pending 汇总。
     *
     * @return pending 汇总
     */
    public AdminOverviewPendingResponse getPending() {
        return pending;
    }
}
