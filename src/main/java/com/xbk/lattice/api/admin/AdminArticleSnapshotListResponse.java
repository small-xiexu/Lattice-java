package com.xbk.lattice.api.admin;

import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import lombok.Getter;

import java.util.List;

/**
 * 管理侧文章快照列表响应。
 *
 * <p>承载文章级快照浏览结果，由 {@code AdminArticleController} 组装返回。
 *
 * <p><b>已知分层问题：</b>{@code items} 直接暴露 {@link ArticleSnapshotRecord}
 * （{@code infra/persistence} 层类型），未经 DTO 包装。本轮不做修复，仅标注。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleSnapshotListResponse {

    /** 概念标识。 */
    private final String conceptId;

    /** 快照数量。 */
    private final int count;

    /**
     * 快照条目列表。
     *
     * <p><b>已知分层问题：</b>元素类型为 {@link ArticleSnapshotRecord}（持久层记录类型），
     * 未经 DTO 包装直接暴露给 API 响应。后续治理应引入专用的 Snapshot DTO。</p>
     */
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
}
