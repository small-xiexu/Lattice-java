package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索调度计划
 *
 * 职责：保存本次检索应按顺序执行的通道列表
 *
 * @author xiexu
 */
public class RetrievalDispatchPlan {

    private final List<RetrievalChannel> channels;

    private final boolean parallelEnabled;

    private final int maxConcurrency;

    private final int maxConcurrencyPerGroup;

    private final long channelTimeoutMillis;

    private final long totalDeadlineMillis;

    /**
     * 创建检索调度计划。
     *
     * @param channels 通道列表
     */
    public RetrievalDispatchPlan(List<RetrievalChannel> channels) {
        this(channels, false, 1, 1, 0L, 0L);
    }

    /**
     * 创建检索调度计划。
     *
     * @param channels 通道列表
     * @param parallelEnabled 是否并发执行
     * @param maxConcurrency 最大并发数
     * @param maxConcurrencyPerGroup 单组最大并发数
     * @param channelTimeoutMillis 单通道超时毫秒
     * @param totalDeadlineMillis 总截止毫秒
     */
    public RetrievalDispatchPlan(
            List<RetrievalChannel> channels,
            boolean parallelEnabled,
            int maxConcurrency,
            int maxConcurrencyPerGroup,
            long channelTimeoutMillis,
            long totalDeadlineMillis
    ) {
        this.channels = channels == null ? List.of() : List.copyOf(channels);
        this.parallelEnabled = parallelEnabled;
        this.maxConcurrency = maxConcurrency <= 0 ? 1 : maxConcurrency;
        this.maxConcurrencyPerGroup = maxConcurrencyPerGroup <= 0 ? 1 : maxConcurrencyPerGroup;
        this.channelTimeoutMillis = Math.max(channelTimeoutMillis, 0L);
        this.totalDeadlineMillis = Math.max(totalDeadlineMillis, 0L);
    }

    /**
     * 返回通道列表。
     *
     * @return 通道列表
     */
    public List<RetrievalChannel> getChannels() {
        return new ArrayList<RetrievalChannel>(channels);
    }

    /**
     * 返回是否并发执行。
     *
     * @return 是否并发执行
     */
    public boolean isParallelEnabled() {
        return parallelEnabled;
    }

    /**
     * 返回最大并发数。
     *
     * @return 最大并发数
     */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /**
     * 返回单组最大并发数。
     *
     * @return 单组最大并发数
     */
    public int getMaxConcurrencyPerGroup() {
        return maxConcurrencyPerGroup;
    }

    /**
     * 返回单通道超时毫秒。
     *
     * @return 单通道超时毫秒
     */
    public long getChannelTimeoutMillis() {
        return channelTimeoutMillis;
    }

    /**
     * 返回总截止毫秒。
     *
     * @return 总截止毫秒
     */
    public long getTotalDeadlineMillis() {
        return totalDeadlineMillis;
    }
}
