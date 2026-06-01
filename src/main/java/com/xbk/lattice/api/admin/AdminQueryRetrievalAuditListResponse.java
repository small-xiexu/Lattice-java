package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 Query 检索审计列表响应。
 *
 * <p>承载最近的 retrieval audit run 列表，由 {@code AdminQueryRetrievalAuditController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryRetrievalAuditListResponse {

    /**
     * 当前返回的 run 数量。
     *
     * <p>等于 {@code items.size()}，受分页参数限制。</p>
     */
    private final int count;

    /**
     * retrieval audit run 列表。
     *
     * <p>按创建时间倒序排列。</p>
     */
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
}
