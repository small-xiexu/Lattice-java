package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;

import java.util.List;

/**
 * 历史报告
 *
 * 职责：封装单个概念的文章快照历史结果
 *
 * @author xiexu
 */
public class HistoryReport {

    private final String conceptId;

    private final List<ArticleSnapshotRecord> items;

    /**
     * 创建历史报告。
     *
     * @param conceptId 概念标识
     * @param items 历史项列表
     */
    public HistoryReport(String conceptId, List<ArticleSnapshotRecord> items) {
        this.conceptId = conceptId;
        this.items = items;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取历史项列表。
     *
     * @return 历史项列表
     */
    public List<ArticleSnapshotRecord> getItems() {
        return items;
    }

    /**
     * 获取历史项总数。
     *
     * @return 历史项总数
     */
    public int getTotalEntries() {
        return items == null ? 0 : items.size();
    }
}
