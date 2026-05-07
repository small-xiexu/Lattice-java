package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 Fact Card 列表响应
 *
 * 职责：承载结构化证据卡列表
 *
 * @author xiexu
 */
public class AdminFactCardListResponse {

    private final int count;

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

    /**
     * 获取数量。
     *
     * @return 数量
     */
    public int getCount() {
        return count;
    }

    /**
     * 获取条目。
     *
     * @return 条目
     */
    public List<AdminFactCardItemResponse> getItems() {
        return items;
    }
}
