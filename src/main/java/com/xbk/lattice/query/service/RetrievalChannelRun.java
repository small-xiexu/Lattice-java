package com.xbk.lattice.query.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 检索通道运行摘要。
 *
 * <p>记录单个召回通道的单次执行结果，包括执行状态、耗时、命中数和失败原因。
 * 所有通道的运行摘要汇总后构成一次查询的完整 retrieval audit 信息，
 * 被 {@code RetrievalAuditService} 持久化并用于后台审计展示。
 * 对象不可变，通过 {@code success/skipped/failed/timeout} 静态工厂方法创建。
 *
 * @author xiexu
 */
@Getter
public class RetrievalChannelRun {

    /**
     * 通道名称。
     *
     * <p>例如 fts、refkey、article_chunk_lexical、source、source_chunk_lexical、fact_card_lexical、
     * fact_card_vector、article_vector、chunk_vector、graph 等。
     * 用于在 retrieval audit 中标识每条记录属于哪个召回通道。</p>
     */
    private final String channelName;

    /**
     * 运行状态。
     *
     * <p>SUCCESS 表示通道顺利完成并返回命中；SKIPPED 表示通道被策略跳过（如并行关闭或意图不匹配）；
     * FAILED 表示通道执行异常；TIMEOUT 表示通道执行超时。审计系统根据状态判断各通道的可用性。</p>
     */
    private final RetrievalChannelRunStatus status;

    /**
     * 执行耗时（毫秒）。
     *
     * <p>从通道发起到返回的总耗时，用于 retrieval audit 中的性能分析和慢通道识别。
     * 构造器保证该值不小于 0。</p>
     */
    private final long durationMillis;

    /**
     * 命中数量。
     *
     * <p>通道返回的候选命中数，经过通道级截断后、RRF 融合前的数量。
     * 跳过的通道和失败的通道此值为 0。构造器保证该值不小于 0。</p>
     */
    private final int hitCount;

    /**
     * 跳过原因。
     *
     * <p>仅当 status 为 SKIPPED 时有意义。记录通道为什么没有被执行——例如并行召回未开启、
     * 查询意图不匹配、答案形态不适合该通道等。SUCCESS 和 FAILED 状态下为空字符串。</p>
     */
    private final String skippedReason;

    /**
     * 错误摘要。
     *
     * <p>仅当 status 为 FAILED 或 TIMEOUT 时有意义。记录异常类名、错误消息或超时阈值等信息，
     * 用于 retrieval audit 中的故障排查。SUCCESS 和 SKIPPED 状态下为空字符串。</p>
     */
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
}
