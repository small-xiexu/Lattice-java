package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 pending 列表响应。
 *
 * <p>承载管理侧 pending 管理的完整列表（非截断），
 * 与 Dashboard 中的 {@link AdminOverviewPendingResponse}（截断摘要）不同。
 *
 * @author xiexu
 */
@Getter
public class AdminPendingResponse {

    /**
     * 当前返回的 pending 记录数。
     *
     * <p>等于 {@code items.size()}，受分页参数限制。</p>
     */
    private final int count;

    /**
     * pending 条目列表。
     *
     * <p>按创建时间倒序排列。</p>
     */
    private final List<AdminPendingItemResponse> items;

    /**
     * 创建管理侧 pending 列表响应。
     *
     * @param count 数量
     * @param items 条目
     */
    public AdminPendingResponse(int count, List<AdminPendingItemResponse> items) {
        this.count = count;
        this.items = items;
    }
}
