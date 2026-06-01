package com.xbk.lattice.api.admin;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧编译审查人工确认动作请求。
 *
 * <p>承载人工发布或驳回动作的入参，由 Spring MVC 从 JSON 请求体绑定。
 * 当请求体为空（{@code null}）时 controller 创建空的默认实例。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminCompileReviewQueueActionRequest {

    /**
     * 人工复核人标识。
     *
     * <p>用于审计追踪记录操作者身份。请求为 {@code null} 时 controller 创建空的默认实例，
     * 此字段为空字符串。</p>
     */
    private String reviewedBy;

    /**
     * 人工复核意见文本。
     *
     * <p>可为空。驳回时建议填写原因，便于后续追溯决策依据。
     * 含人工主观评价，不应参与 {@code toString()}。</p>
     */
    private String comment;

    /**
     * 期望的当前队列状态（乐观锁）。
     *
     * <p>用于防并发覆盖：与实际记录状态不匹配时操作被拒绝。
     * 错误值导致审批操作失败，需调用方重新获取最新状态后重试。</p>
     */
    private String expectedReviewStatus;
}
