package com.xbk.lattice.api.admin;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 管理侧答案反馈创建请求。
 *
 * <p>承载问答页提交的结果反馈上下文，由 Spring MVC 从 JSON 请求体绑定。
 * 含用户数据（{@code question}、{@code answerSummary}、{@code comment}、{@code reportedBy}），
 * 禁止引入 {@code @Data} 以防止用户内容泄露到日志。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminQueryFeedbackCreateRequest {

    /**
     * 关联的查询会话标识。
     *
     * <p>用于回溯原始问答上下文，定位产生该反馈的查询。</p>
     */
    private String queryId;

    /**
     * 用户原始问题文本。
     *
     * <p>可能含 PII 或敏感查询内容，禁止参与 {@code toString()}。</p>
     */
    private String question;

    /**
     * 系统给出的答案摘要文本。
     *
     * <p>可能与完整答案不同，仅用于管理侧快速了解被反馈的答案内容。禁止参与 {@code toString()}。</p>
     */
    private String answerSummary;

    /**
     * 反馈类型。
     *
     * <p>可选值：{@code positive} / {@code negative} / {@code correction}。
     * 驱动反馈分类、处理优先级和前端展示样式。</p>
     */
    private String feedbackType;

    /**
     * 用户提交的反馈说明文本。
     *
     * <p>可能含主观评价或具体纠错内容，为不可控的用户输入。禁止参与 {@code toString()}。</p>
     */
    private String comment;

    /**
     * 反馈关联的文章唯一键列表。
     *
     * <p>用于快速定位问题文章，帮助处理人定位反馈来源。</p>
     */
    private List<String> articleKeys;

    /**
     * 反馈关联的来源文件路径列表。
     */
    private List<String> sourcePaths;

    /**
     * 反馈提交人标识。
     *
     * <p>用于审计追踪记录反馈来源。禁止参与 {@code toString()}。</p>
     */
    private String reportedBy;
}
