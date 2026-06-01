package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 Query 检索审计详情响应。
 *
 * <p>承载按 {@code queryId} 查看的最新 run、历史摘要与通道命中明细，
 * 由 {@code AdminQueryRetrievalAuditController} 组装返回，用于检索诊断。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryRetrievalAuditDetailResponse {

    /** 查询标识。 */
    private final String queryId;

    /**
     * 是否命中审计记录。
     *
     * <p>{@code false} 时表示该 queryId 无检索审计数据，
     * {@code latestRun} 为 {@code null}、{@code runHistory} 为空列表。</p>
     */
    private final boolean found;

    /**
     * 最新一次检索 run 详情。
     *
     * <p>为 {@code null} 表示无 run 记录（{@code found=false}）。</p>
     */
    private final AdminQueryRetrievalAuditRunResponse latestRun;

    /** 历史 run 总数量。 */
    private final int historyCount;

    /**
     * 历史 run 列表。
     *
     * <p>不含 {@code latestRun}，按创建时间倒序。</p>
     */
    private final List<AdminQueryRetrievalAuditRunResponse> runHistory;

    /** 通道命中总数量。 */
    private final int channelHitCount;

    /**
     * 通道命中明细列表。
     *
     * <p>含各通道的 hit rank / fused rank / score 详情，用于排查排序异常。</p>
     */
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
}
