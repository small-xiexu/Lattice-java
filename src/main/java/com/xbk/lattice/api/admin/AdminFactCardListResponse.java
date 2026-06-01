package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 Fact Card 列表响应。
 *
 * <p>承载结构化证据卡浏览列表，由 {@code AdminFactCardController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminFactCardListResponse {

    /**
     * 当前返回的 Fact Card 数量。
     *
     * <p>等于 {@code items.size()}，受分页参数限制。</p>
     */
    private final int count;

    /**
     * Fact Card 条目列表。
     *
     * <p>按创建时间倒序排列。</p>
     */
    private final List<AdminFactCardItemResponse> items;

    /**
     * 创建管理侧 Fact Card 列表响应。
     *
     * @param count 数量
     * @param items 条目
     */
    public AdminFactCardListResponse(int count, List<AdminFactCardItemResponse> items) {
        this.count = count;
        this.items = items;
    }
}
