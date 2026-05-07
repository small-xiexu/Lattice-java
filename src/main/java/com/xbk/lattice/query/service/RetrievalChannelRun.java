package com.xbk.lattice.query.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 检索通道运行摘要
 *
 * 职责：记录单个召回通道的执行状态、耗时、命中数与失败摘要
 *
 * @author xiexu
 */
public class RetrievalChannelRun {

    private final String channelName;

    private final RetrievalChannelRunStatus status;

    private final long durationMillis;

    private final int hitCount;

    private final String skippedReason;

    private final String errorSummary;

    /**
     * 创建检索通道运行摘要。
     *
     * @param channelName 通道名称
     * @param status 运行状态
     * @param durationMillis 耗时毫秒
     * @param hitCount 命中数量
     * @param skippedReason 跳过原因
     * @param errorSummary 错误摘要
     */
    @JsonCreator
    public RetrievalChannelRun(
            @JsonProperty("channelName") String channelName,
            @JsonProperty("status") RetrievalChannelRunStatus status,
            @JsonProperty("durationMillis") long durationMillis,
            @JsonProperty("hitCount") int hitCount,
            @JsonProperty("skippedReason") String skippedReason,
            @JsonProperty("errorSummary") String errorSummary
    ) {
        this.channelName = channelName;
        this.status = status == null ? RetrievalChannelRunStatus.FAILED : status;
        this.durationMillis = Math.max(durationMillis, 0L);
        this.hitCount = Math.max(hitCount, 0);
        this.skippedReason = skippedReason == null ? "" : skippedReason;
        this.errorSummary = errorSummary == null ? "" : errorSummary;
    }

    /**
     * 创建成功摘要。
     *
     * @param channelName 通道名称
     * @param durationMillis 耗时毫秒
     * @param hitCount 命中数量
     * @return 运行摘要
     */
    public static RetrievalChannelRun success(String channelName, long durationMillis, int hitCount) {
        return new RetrievalChannelRun(
                channelName,
                RetrievalChannelRunStatus.SUCCESS,
                durationMillis,
                hitCount,
                "",
                ""
        );
    }

    /**
     * 创建跳过摘要。
     *
     * @param channelName 通道名称
     * @param skippedReason 跳过原因
     * @return 运行摘要
     */
    public static RetrievalChannelRun skipped(String channelName, String skippedReason) {
        return new RetrievalChannelRun(
                channelName,
                RetrievalChannelRunStatus.SKIPPED,
                0L,
                0,
                skippedReason,
                ""
        );
    }

    /**
     * 创建失败摘要。
     *
     * @param channelName 通道名称
     * @param durationMillis 耗时毫秒
     * @param errorSummary 错误摘要
     * @return 运行摘要
     */
    public static RetrievalChannelRun failed(String channelName, long durationMillis, String errorSummary) {
        return new RetrievalChannelRun(
                channelName,
                RetrievalChannelRunStatus.FAILED,
                durationMillis,
                0,
                "",
                errorSummary
        );
    }

    /**
     * 创建超时摘要。
     *
     * @param channelName 通道名称
     * @param durationMillis 耗时毫秒
     * @param timeoutMillis 超时毫秒
     * @return 运行摘要
     */
    public static RetrievalChannelRun timeout(String channelName, long durationMillis, long timeoutMillis) {
        return timeout(channelName, durationMillis, timeoutMillis, "channel_timeout");
    }

    /**
     * 创建超时摘要。
     *
     * @param channelName 通道名称
     * @param durationMillis 耗时毫秒
     * @param timeoutMillis 超时毫秒
     * @param reason 超时原因
     * @return 运行摘要
     */
    public static RetrievalChannelRun timeout(
            String channelName,
            long durationMillis,
            long timeoutMillis,
            String reason
    ) {
        String safeReason = reason == null || reason.isBlank() ? "channel_timeout" : reason.trim();
        return new RetrievalChannelRun(
                channelName,
                RetrievalChannelRunStatus.TIMEOUT,
                durationMillis,
                0,
                "",
                safeReason + "_after_" + Math.max(timeoutMillis, 0L) + "ms"
        );
    }

    /**
     * 返回通道名称。
     *
     * @return 通道名称
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * 返回运行状态。
     *
     * @return 运行状态
     */
    public RetrievalChannelRunStatus getStatus() {
        return status;
    }

    /**
     * 返回耗时毫秒。
     *
     * @return 耗时毫秒
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * 返回命中数量。
     *
     * @return 命中数量
     */
    public int getHitCount() {
        return hitCount;
    }

    /**
     * 返回跳过原因。
     *
     * @return 跳过原因
     */
    public String getSkippedReason() {
        return skippedReason;
    }

    /**
     * 返回错误摘要。
     *
     * @return 错误摘要
     */
    public String getErrorSummary() {
        return errorSummary;
    }
}
