package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;

import java.util.List;

/**
 * 快照报告
 *
 * 职责：封装最近文章快照摘要结果
 *
 * @author xiexu
 */
public class SnapshotReport {

    private final List<ArticleSnapshotRecord> items;

    /**
     * 创建快照报告。
     *
     * @param items 快照项列表
     */
    public SnapshotReport(List<ArticleSnapshotRecord> items) {
        this.items = items;
    }

    /**
     * 获取快照项列表。
     *
     * @return 快照项列表
     */
    public List<ArticleSnapshotRecord> getItems() {
        return items;
    }

    /**
     * 获取快照总数。
     *
     * @return 快照总数
     */
    public int getTotalSnapshots() {
        return items == null ? 0 : items.size();
    }
}
