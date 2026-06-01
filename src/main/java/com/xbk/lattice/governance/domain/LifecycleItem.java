package com.xbk.lattice.governance.domain;

import lombok.Getter;

/**
 * 生命周期条目。
 *
 * <p>描述单篇知识文章当前的生命周期状态与审计留痕信息——用于治理列表展示和筛选。
 *
 * @author xiexu
 */
@Getter
public class LifecycleItem {

    /** 资料源主键。为 null 表示多源或无固定 source。 */
    private final Long sourceId;
    /** 文章唯一键。7 参数构造器中回退为 conceptId。 */
    private final String articleKey;
    /** 概念标识。 */
    private final String conceptId;
    /** 文章标题。 */
    private final String title;
    /**
     * 生命周期状态。
     *
     * <p>可选值：{@code active} / {@code deprecated} / {@code archived}。
     * 驱动前端展示状态标签和治理筛选条件。</p>
     */
    private final String lifecycle;
    /** 审查状态。 */
    private final String reviewStatus;
    /** 生命周期变更原因。可为空，表示当前状态没有额外治理说明。 */
    private final String reason;
    /** 最后更新人。审计追踪字段。 */
    private final String updatedBy;
    /** 最后更新时间。审计追踪字段。 */
    private final String updatedAt;

    public LifecycleItem(
            Long sourceId, String articleKey, String conceptId, String title,
            String lifecycle, String reviewStatus, String reason, String updatedBy, String updatedAt
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.lifecycle = lifecycle;
        this.reviewStatus = reviewStatus;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /** 7 参数构造器——articleKey 回退为 conceptId。 */
    public LifecycleItem(
            String conceptId, String title, String lifecycle, String reviewStatus,
            String reason, String updatedBy, String updatedAt
    ) {
        this(null, conceptId, conceptId, title, lifecycle, reviewStatus, reason, updatedBy, updatedAt);
    }
}
