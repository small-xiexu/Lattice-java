package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.StatusSnapshot;
import lombok.Getter;

/**
 * 管理侧总览响应。
 *
 * <p>聚合系统状态、质量指标与 pending 汇总，供管理侧 Dashboard 顶部使用。
 *
 * <p><b>已知分层问题：</b>{@code status} 和 {@code quality} 直接暴露
 * {@link StatusSnapshot} 和 {@link QualityMetricsReport}（{@code governance} 层领域对象），
 * 未经 DTO 包装。本轮不做修复，仅标注。
 *
 * @author xiexu
 */
@Getter
public class AdminOverviewResponse {

    /**
     * 系统状态快照。
     *
     * <p>含服务健康、数据完整性等 Dashboard 顶部状态指示。
     * 直接暴露 {@link StatusSnapshot} 领域对象——后续应引入专用 Status DTO。</p>
     */
    private final StatusSnapshot status;

    /**
     * 当前质量指标报告。
     *
     * <p>含知识库整体质量评分与分类指标。
     * 直接暴露 {@link QualityMetricsReport} 领域对象——后续应引入专用 Quality DTO。</p>
     */
    private final QualityMetricsReport quality;

    /**
     * 待确认查询汇总。
     *
     * <p>{@code count=0} 时前端不展示 pending 区块或展示"全部已确认"提示。</p>
     */
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
}
