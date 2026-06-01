package com.xbk.lattice.api.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理侧 Compile 审查配置请求。
 *
 * <p>承载 compile review 自动修复阈值与人工复核触发条件的后台保存参数，
 * 由 Spring MVC 从 JSON 请求体绑定。修改任一枚举值会立即影响下一次编译的审查行为。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminCompileReviewConfigRequest {

    /**
     * 自动修复总开关。
     *
     * <p>{@code true} 时 LLM 对审查问题自动尝试修复（最多 {@code maxFixRounds} 轮），
     * 每轮修复后重新审查。{@code false} 时所有问题直接进入人工复核队列，
     * review queue 可能快速积压。为 {@code null} 时行为由服务端决定。</p>
     */
    private Boolean autoFixEnabled;

    /**
     * 自动修复最大轮次。
     *
     * <p>每轮修复后重新审查，超过此次数仍未通过则标记 {@code needs_human_review}。
     * 过小（如 1）修复不充分，大量文章落入人工复核；过大（如 10+）可能修复死循环，LLM 成本激增。
     * 为 {@code null} 时使用服务端默认值。</p>
     */
    private Integer maxFixRounds;

    /**
     * 是否允许"需人工复核"状态的文章落库。
     *
     * <p>{@code false} 时阻止所有 {@code needs_human_review} 文章写入，
     * 编译实际产出可能为零，仅有 {@code accepted} 文章落库。
     * {@code true} 时未经人工确认的文章也会写入，降低编译阻塞风险。
     * 为 {@code null} 时行为由服务端决定。</p>
     */
    private Boolean allowPersistNeedsHumanReview;

    /**
     * 人工复核严重度阈值。
     *
     * <p>审查问题严重度 {@code >=} 此阈值时触发人工复核。
     * 设置为最低级别时几乎所有问题都需人工处理，review queue 积压严重。
     * 必须非空非 blank，服务端应做格式校验。</p>
     */
    private String humanReviewSeverityThreshold;

    /**
     * 配置操作人标识。
     *
     * <p>用于审计日志追踪配置变更操作者。服务端应校验非空。</p>
     */
    private String operator;
}
