package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧 Query 检索通道运行响应。
 *
 * <p>承载单个召回通道的运行状态、耗时、命中数量与失败摘要，
 * 用于检索性能诊断和瓶颈定位。
 * 禁止引入 {@code @Data}：{@code errorSummary} 可能含后端错误详情。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryRetrievalChannelRunResponse {

    /**
     * 通道名称。
     *
     * <p>如 {@code fts} / {@code article_vector} / {@code chunk_vector} /
     * {@code fact_card} / {@code graph} 等。</p>
     */
    private final String channelName;

    /**
     * 运行状态。
     *
     * <p>可选值：{@code SUCCESS} / {@code TIMEOUT} / {@code SKIPPED} / {@code ERROR}。
     * 驱动前端通道诊断标签颜色和状态图标。</p>
     */
    private final String status;

    /**
     * 通道耗时（毫秒）。
     *
     * <p>用于性能诊断和瓶颈定位——耗时异常高的通道可能需要优化或限流。</p>
     */
    private final long durationMillis;

    /**
     * 通道命中数。
     *
     * <p>0 表示该通道未贡献结果，可能是数据缺失或索引问题。</p>
     */
    private final int hitCount;

    /**
     * 通道跳过原因。
     *
     * <p>非空时表示通道被策略跳过而未执行（如权重为 0 或条件不满足）。</p>
     */
    private final String skippedReason;

    /**
     * 错误摘要。
     *
     * <p>非空时表示通道执行异常——可能含异常栈或后端错误信息。
     * 仅用于管理侧排查，禁止参与 {@code toString()}。</p>
     */
    private final String errorSummary;

    /**
     * 是否超时（计算字段，非持久化）。
     *
     * <p>由 controller 根据 {@code status == TIMEOUT} 推导，非数据库字段。</p>
     */
    private final boolean timeout;

    /**
     * 是否零命中（计算字段，非持久化）。
     *
     * <p>由 controller 根据 {@code status == SUCCESS && hitCount == 0} 推导，非数据库字段。</p>
     */
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
}
