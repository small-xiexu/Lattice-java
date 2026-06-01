package com.xbk.lattice.api.admin;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧答案反馈处理请求。
 *
 * <p>承载反馈队列处理动作的入参，由 Spring MVC 从 JSON 请求体绑定。
 * 含审计字段（{@code handledBy}、{@code comment}），禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminQueryFeedbackHandleRequest {

    /**
     * 处理人标识。
     *
     * <p>用于审计追踪记录处理操作者。禁止参与 {@code toString()}。</p>
     */
    private String handledBy;

    /**
     * 处理说明。
     *
     * <p>{@code resolve} 时填写处理措施，{@code dismiss} 时填写忽略原因。
     * 含管理员主观评价，禁止参与 {@code toString()}。</p>
     */
    private String comment;
}
