package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧编译作业列表响应。
 *
 * <p>承载 compile job 浏览列表，由 {@code AdminCompileController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminCompileJobListResponse {

    /**
     * 当前返回的作业数。
     *
     * <p>等于 {@code items.size()}，受分页参数限制，不等于数据库总数。</p>
     */
    private final int count;

    /**
     * 编译作业列表。
     *
     * <p>按提交时间倒序排列。</p>
     */
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
}
