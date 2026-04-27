package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 当前处理任务列表响应。
 *
 * 职责：承载工作台“当前处理任务”的汇总与明细
 *
 * @author xiexu
 */
public class AdminProcessingTaskListResponse {

    private final AdminProcessingTaskSummaryResponse summary;

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

    public AdminProcessingTaskSummaryResponse getSummary() {
        return summary;
    }

    public List<AdminProcessingTaskItemResponse> getItems() {
        return items;
    }
}
