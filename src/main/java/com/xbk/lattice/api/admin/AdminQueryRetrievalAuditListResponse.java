package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 Query 检索审计列表响应
 *
 * 职责：承载 recent retrieval audit runs 列表
 *
 * @author xiexu
 */
public class AdminQueryRetrievalAuditListResponse {

    private final int count;

    private final List<AdminQueryRetrievalAuditRunResponse> items;

    /**
     * 创建管理侧 Query 检索审计列表响应。
     *
     * @param count 数量
     * @param items 条目
     */
    public AdminQueryRetrievalAuditListResponse(int count, List<AdminQueryRetrievalAuditRunResponse> items) {
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
    public List<AdminQueryRetrievalAuditRunResponse> getItems() {
        return items;
    }
}
