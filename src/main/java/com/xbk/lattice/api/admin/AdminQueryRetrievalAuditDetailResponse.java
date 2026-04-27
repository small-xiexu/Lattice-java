package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 Query 检索审计详情响应
 *
 * 职责：承载按 queryId 查看的最新 run、历史摘要与通道命中
 *
 * @author xiexu
 */
public class AdminQueryRetrievalAuditDetailResponse {

    private final String queryId;

    private final boolean found;

    private final AdminQueryRetrievalAuditRunResponse latestRun;

    private final int historyCount;

    private final List<AdminQueryRetrievalAuditRunResponse> runHistory;

    private final int channelHitCount;

    private final List<AdminQueryRetrievalChannelHitResponse> channelHits;

    /**
     * 创建管理侧 Query 检索审计详情响应。
     *
     * @param queryId 查询标识
     * @param found 是否命中
     * @param latestRun 最新 run
     * @param historyCount 历史数量
     * @param runHistory 历史 runs
     * @param channelHitCount 通道命中数量
     * @param channelHits 通道命中
     */
    public AdminQueryRetrievalAuditDetailResponse(
            String queryId,
            boolean found,
            AdminQueryRetrievalAuditRunResponse latestRun,
            int historyCount,
            List<AdminQueryRetrievalAuditRunResponse> runHistory,
            int channelHitCount,
            List<AdminQueryRetrievalChannelHitResponse> channelHits
    ) {
        this.queryId = queryId;
        this.found = found;
        this.latestRun = latestRun;
        this.historyCount = historyCount;
        this.runHistory = runHistory;
        this.channelHitCount = channelHitCount;
        this.channelHits = channelHits;
    }

    /**
     * 获取查询标识。
     *
     * @return 查询标识
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 获取是否命中。
     *
     * @return 是否命中
     */
    public boolean isFound() {
        return found;
    }

    /**
     * 获取最新 run。
     *
     * @return 最新 run
     */
    public AdminQueryRetrievalAuditRunResponse getLatestRun() {
        return latestRun;
    }

    /**
     * 获取历史数量。
     *
     * @return 历史数量
     */
    public int getHistoryCount() {
        return historyCount;
    }

    /**
     * 获取历史 runs。
     *
     * @return 历史 runs
     */
    public List<AdminQueryRetrievalAuditRunResponse> getRunHistory() {
        return runHistory;
    }

    /**
     * 获取通道命中数量。
     *
     * @return 通道命中数量
     */
    public int getChannelHitCount() {
        return channelHitCount;
    }

    /**
     * 获取通道命中。
     *
     * @return 通道命中
     */
    public List<AdminQueryRetrievalChannelHitResponse> getChannelHits() {
        return channelHits;
    }
}
