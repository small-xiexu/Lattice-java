package com.xbk.lattice.governance.domain;

import lombok.Getter;

/**
 * 生命周期切换结果。
 *
 * <p>返回单篇文章生命周期变更后的最小结果——含变更后的生命周期和审计信息。
 * 这是变更操作的结果对象，非列表状态条目。
 *
 * @author xiexu
 */
@Getter
public class LifecycleTransitionResult {

    /** 资料源主键。为 null 表示多源。 */
    private final Long sourceId;
    /** 文章唯一键。6 参数构造器中回退为 conceptId。 */
    private final String articleKey;
    /** 概念标识。 */
    private final String conceptId;
    /** 文章标题。 */
    private final String title;
    /** 变更后的目标生命周期。 */
    private final String lifecycle;
    /** 变更原因。可为空，表示本次变更没有额外原因说明。 */
    private final String reason;
    /** 变更操作人。用于审计本次变更。 */
    private final String updatedBy;
    /** 变更时间。用于审计本次变更。 */
    private final String updatedAt;

    public LifecycleTransitionResult(
            Long sourceId, String articleKey, String conceptId, String title,
            String lifecycle, String reason, String updatedBy, String updatedAt
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.lifecycle = lifecycle;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /** 6 参数构造器——articleKey 回退为 conceptId。 */
    public LifecycleTransitionResult(
            String conceptId, String title, String lifecycle, String reason, String updatedBy, String updatedAt
    ) {
        this(null, conceptId, conceptId, title, lifecycle, reason, updatedBy, updatedAt);
    }
}
