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

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final List<ArticleSnapshotRecord> items;

    /**
     * 创建历史报告。
     *
     * @param conceptId 概念标识
     * @param items 历史项列表
     */
    public HistoryReport(Long sourceId, String articleKey, String conceptId, List<ArticleSnapshotRecord> items) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.items = items;
    }

    /**
     * 创建兼容旧调用的历史报告。
     *
     * @param conceptId 概念标识
     * @param items 历史项列表
     */
    public HistoryReport(String conceptId, List<ArticleSnapshotRecord> items) {
        this(null, conceptId, conceptId, items);
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
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
