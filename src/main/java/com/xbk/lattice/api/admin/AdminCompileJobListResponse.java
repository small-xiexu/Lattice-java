package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧编译作业列表响应
 *
 * 职责：承载 compile job 浏览列表
 *
 * @author xiexu
 */
public class AdminCompileJobListResponse {

    private final int count;

    private final List<AdminCompileJobResponse> items;

    /**
     * 创建管理侧编译作业列表响应。
     *
     * @param count 数量
     * @param items 条目
     */
    public AdminCompileJobListResponse(int count, List<AdminCompileJobResponse> items) {
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
    public List<AdminCompileJobResponse> getItems() {
        return items;
    }
}
