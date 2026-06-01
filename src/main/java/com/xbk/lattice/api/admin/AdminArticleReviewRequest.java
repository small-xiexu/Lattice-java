package com.xbk.lattice.api.admin;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧文章人工复核请求。
 *
 * <p>承载人工复核动作的入参——含操作人、意见、乐观锁状态和修正建议，
 * 由 Spring MVC 从 JSON 请求体绑定。
 * 含审计字段（{@code reviewedBy}、{@code comment}），禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminArticleReviewRequest {

    /**
     * 资料源主键。
     *
     * <p>可选，用于限定文章范围。为 {@code null} 时不限制 source。</p>
     */
    private Long sourceId;

    /**
     * 复核人标识。
     *
     * <p>用于审计追踪记录操作者身份。禁止参与 {@code toString()}。</p>
     */
    private String reviewedBy;

    /**
     * 复核意见。
     *
     * <p>{@code approve} 时可选，{@code request-changes} 时应填写具体修改要求。
     * 含人工主观评价，禁止参与 {@code toString()}。</p>
     */
    private String comment;

    /**
     * 乐观锁期望状态。
     *
     * <p>与当前文章审查状态不一致时操作被拒绝，防止并发覆盖。
     * 错误值导致审批失败，需调用方重新获取最新状态后重试。</p>
     */
    private String expectedReviewStatus;

    /**
     * 修正建议摘要。
     *
     * <p>{@code request-changes} 时说明需修正的内容。可能被持久化到审计记录。</p>
     */
    private String correctionSummary;
}
