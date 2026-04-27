package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitView;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunView;

import java.util.List;

/**
 * 检索审计快照
 *
 * 职责：承载按 queryId 读取的一次检索审计详情与历史摘要
 *
 * @author xiexu
 */
public class RetrievalAuditSnapshot {

    private final String queryId;

    private final QueryRetrievalRunView latestRun;

    private final List<QueryRetrievalRunView> runHistory;

    private final List<QueryRetrievalChannelHitView> channelHits;

    /**
     * 创建检索审计快照。
     *
     * @param queryId 查询标识
     * @param latestRun 最新 run
     * @param runHistory 历史 runs
     * @param channelHits 通道命中
     */
    public RetrievalAuditSnapshot(
            String queryId,
            QueryRetrievalRunView latestRun,
            List<QueryRetrievalRunView> runHistory,
            List<QueryRetrievalChannelHitView> channelHits
    ) {
        this.queryId = queryId;
        this.latestRun = latestRun;
        this.runHistory = runHistory;
        this.channelHits = channelHits;
    }

    /**
     * 创建空快照。
     *
     * @param queryId 查询标识
     * @return 空快照
     */
    public static RetrievalAuditSnapshot empty(String queryId) {
        return new RetrievalAuditSnapshot(queryId, null, List.of(), List.of());
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
     * 获取最新 run。
     *
     * @return 最新 run
     */
    public QueryRetrievalRunView getLatestRun() {
        return latestRun;
    }

    /**
     * 获取历史 runs。
     *
     * @return 历史 runs
     */
    public List<QueryRetrievalRunView> getRunHistory() {
        return runHistory;
    }

    /**
     * 获取通道命中。
     *
     * @return 通道命中
     */
    public List<QueryRetrievalChannelHitView> getChannelHits() {
        return channelHits;
    }

    /**
     * 判断当前是否存在审计数据。
     *
     * @return 是否存在审计数据
     */
    public boolean isFound() {
        return latestRun != null;
    }
}
