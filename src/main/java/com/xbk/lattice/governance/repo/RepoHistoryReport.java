package com.xbk.lattice.governance.repo;

import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;

import java.util.List;

/**
 * 整库历史报告
 *
 * 职责：封装整库快照历史列表
 *
 * @author xiexu
 */
public class RepoHistoryReport {

    private final List<RepoSnapshotRecord> items;

    /**
     * 创建整库历史报告。
     *
     * @param items 条目列表
     */
    public RepoHistoryReport(List<RepoSnapshotRecord> items) {
        this.items = items;
    }

    public List<RepoSnapshotRecord> getItems() {
        return items;
    }

    public int getCount() {
        return items == null ? 0 : items.size();
    }
}
