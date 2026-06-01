package com.xbk.lattice.api.admin;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧文章回滚请求。
 *
 * <p>承载文章级回滚所需的概念标识与快照标识，由 Spring MVC 从 JSON 请求体绑定。
 * {@code articleId} 字段的 getter 含 fallback 逻辑（为空时返回 {@code conceptId}），
 * Lombok 已排除此字段，手写 {@code getArticleId()} 保留。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminArticleRollbackRequest {

    /**
     * 文章唯一键或概念标识。
     *
     * <p>{@code getArticleId()} 含 fallback 逻辑：{@code articleId} 非空时返回自身，
     * 为空时返回 {@code conceptId}。Lombok getter 已排除此字段。</p>
     */
    @Getter(AccessLevel.NONE)
    private String articleId;

    /**
     * 概念标识。
     *
     * <p>{@code articleId} 为空时作为回滚目标标识（由 {@code getArticleId()} fallback 使用）。</p>
     */
    private String conceptId;

    /**
     * 资料源主键。
     *
     * <p>可选，为 {@code null} 时不限制 source。</p>
     */
    private Long sourceId;

    /**
     * 目标快照主键。
     *
     * <p>回滚会将文章恢复到该快照版本。为 0 时行为由服务端决定。
     * 服务端应校验该快照存在且属于目标文章——错误快照 ID 导致文章回滚到错误版本，不可逆。</p>
     */
    private long snapshotId;

    /**
     * 返回回滚目标文章标识（含 fallback 逻辑）。
     *
     * <p>{@code articleId} 非空且非 blank 时返回 {@code articleId}，
     * 否则返回 {@code conceptId}。</p>
     *
     * @return 回滚目标文章标识
     */
    public String getArticleId() {
        if (articleId != null && !articleId.isBlank()) {
            return articleId;
        }
        return conceptId;
    }
}
