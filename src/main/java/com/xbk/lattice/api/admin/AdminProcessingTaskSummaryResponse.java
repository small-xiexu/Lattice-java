package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 当前处理任务汇总响应。
 *
 * <p>承载工作台当前处理任务概览卡片需要的汇总数量与展示卡片，
 * 由 {@code AdminProcessingTaskController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminProcessingTaskSummaryResponse {

    /** 运行中的任务数。 */
    private final int runningCount;

    /** 待确认的任务数（需要人工介入）。 */
    private final int waitingCount;

    /** 疑似卡住的任务数（超过预期时间无进展）。 */
    private final int stalledCount;

    /** 已成功完成的任务数。 */
    private final int succeededCount;

    /** 失败的任务数。 */
    private final int failedCount;

    /** 展示卡片列表。 */
    private final List<AdminProcessingTaskSummaryCardResponse> cards;

    /**
     * 帮助卡状态。
     *
     * <p>承载"现在该怎么做"帮助卡的展示内容与可执行动作。
     * 为 {@code null} 时前端不展示帮助卡。</p>
     */
    private final AdminKnowledgeHelpStateResponse helpState;

    /**
     * 创建当前处理任务汇总响应。
     *
     * @param runningCount 运行中数量
     * @param waitingCount 待确认数量
     * @param stalledCount 疑似卡住数量
     * @param succeededCount 已完成数量
     * @param failedCount 失败数量
     * @param cards 展示卡片
     * @param helpState 帮助卡状态
     */
    public AdminProcessingTaskSummaryResponse(
            int runningCount,
            int waitingCount,
            int stalledCount,
            int succeededCount,
            int failedCount,
            List<AdminProcessingTaskSummaryCardResponse> cards,
            AdminKnowledgeHelpStateResponse helpState
    ) {
        this.runningCount = runningCount;
        this.waitingCount = waitingCount;
        this.stalledCount = stalledCount;
        this.succeededCount = succeededCount;
        this.failedCount = failedCount;
        this.cards = cards;
        this.helpState = helpState;
    }
}
