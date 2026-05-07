package com.xbk.lattice.api.admin;

/**
 * 管理侧 Query 检索通道运行响应
 *
 * 职责：承载单个召回通道的运行状态、耗时、命中数量与失败摘要
 *
 * @author xiexu
 */
public class AdminQueryRetrievalChannelRunResponse {

    private final String channelName;

    private final String status;

    private final long durationMillis;

    private final int hitCount;

    private final String skippedReason;

    private final String errorSummary;

    private final boolean timeout;

    private final boolean zeroHit;

    /**
     * 创建管理侧 Query 检索通道运行响应。
     *
     * @param channelName 通道名称
     * @param status 运行状态
     * @param durationMillis 耗时毫秒
     * @param hitCount 命中数量
     * @param skippedReason 跳过原因
     * @param errorSummary 错误摘要
     * @param timeout 是否超时
     * @param zeroHit 是否零命中
     */
    public AdminQueryRetrievalChannelRunResponse(
            String channelName,
            String status,
            long durationMillis,
            int hitCount,
            String skippedReason,
            String errorSummary,
            boolean timeout,
            boolean zeroHit
    ) {
        this.channelName = channelName;
        this.status = status;
        this.durationMillis = durationMillis;
        this.hitCount = hitCount;
        this.skippedReason = skippedReason;
        this.errorSummary = errorSummary;
        this.timeout = timeout;
        this.zeroHit = zeroHit;
    }

    /**
     * 获取通道名称。
     *
     * @return 通道名称
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * 获取运行状态。
     *
     * @return 运行状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取耗时毫秒。
     *
     * @return 耗时毫秒
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * 获取命中数量。
     *
     * @return 命中数量
     */
    public int getHitCount() {
        return hitCount;
    }

    /**
     * 获取跳过原因。
     *
     * @return 跳过原因
     */
    public String getSkippedReason() {
        return skippedReason;
    }

    /**
     * 获取错误摘要。
     *
     * @return 错误摘要
     */
    public String getErrorSummary() {
        return errorSummary;
    }

    /**
     * 获取是否超时。
     *
     * @return 是否超时
     */
    public boolean isTimeout() {
        return timeout;
    }

    /**
     * 获取是否零命中。
     *
     * @return 是否零命中
     */
    public boolean isZeroHit() {
        return zeroHit;
    }
}
