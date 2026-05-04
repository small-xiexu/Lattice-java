package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 当前处理任务汇总响应。
 *
 * 职责：承载工作台当前处理任务概览卡片需要的汇总数量与展示卡片
 *
 * @author xiexu
 */
public class AdminProcessingTaskSummaryResponse {

    private final int runningCount;

    private final int waitingCount;

    private final int stalledCount;

    private final int succeededCount;

    private final int failedCount;

    private final List<AdminProcessingTaskSummaryCardResponse> cards;

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

    public int getRunningCount() {
        return runningCount;
    }

    public int getWaitingCount() {
        return waitingCount;
    }

    public int getStalledCount() {
        return stalledCount;
    }

    public int getSucceededCount() {
        return succeededCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public List<AdminProcessingTaskSummaryCardResponse> getCards() {
        return cards;
    }

    public AdminKnowledgeHelpStateResponse getHelpState() {
        return helpState;
    }
}
