package com.xbk.lattice.api.admin;

import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;

import java.util.List;

/**
 * 管理侧文章快照列表响应
 *
 * 职责：承载文章级快照浏览结果
 *
 * @author xiexu
 */
public class AdminArticleSnapshotListResponse {

    private final String conceptId;

    private final int count;

    private final List<ArticleSnapshotRecord> items;

    /**
     * 创建管理侧文章快照列表响应。
     *
     * @param conceptId 概念标识
     * @param count 数量
     * @param items 快照条目
     */
    public AdminArticleSnapshotListResponse(String conceptId, int count, List<ArticleSnapshotRecord> items) {
        this.conceptId = conceptId;
        this.count = count;
        this.items = items;
    }

    public String getConceptId() {
        return conceptId;
    }

    public int getCount() {
        return count;
    }

    public List<ArticleSnapshotRecord> getItems() {
        return items;
    }
}
