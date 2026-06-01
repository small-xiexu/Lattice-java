package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 当前处理任务列表响应。
 *
 * <p>承载工作台"当前处理任务"的汇总与明细，
 * 由 {@code AdminProcessingTaskController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminProcessingTaskListResponse {

    /** 任务汇总信息（含分类计数和帮助卡）。 */
    private final AdminProcessingTaskSummaryResponse summary;

    /** 任务明细列表。 */
    private final List<AdminProcessingTaskItemResponse> items;

    /**
     * 创建当前处理任务列表响应。
     *
     * @param summary 汇总信息
     * @param items 任务明细
     */
    public AdminProcessingTaskListResponse(
            AdminProcessingTaskSummaryResponse summary,
            List<AdminProcessingTaskItemResponse> items
    ) {
        this.summary = summary;
        this.items = items;
    }
}
