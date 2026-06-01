package com.xbk.lattice.api.query;

import lombok.Getter;

/**
 * 待确认查询状态响应。
 *
 * <p>返回 pending query 的 confirm/discard 操作的执行状态。
 * 调用方通过这个结构确认操作是否成功。
 *
 * @author xiexu
 */
@Getter
public class PendingQueryStatusResponse {

    /**
     * 执行状态。
     *
     * <p>表示 confirm 或 discard 操作的结果，例如 confirmed（确认成功）、
     * discarded（丢弃成功）、failed（操作失败）等。</p>
     */
    private final String status;

    /**
     * 创建待确认查询状态响应。
     *
     * @param status 状态
     */
    public PendingQueryStatusResponse(String status) {
        this.status = status;
    }
}
