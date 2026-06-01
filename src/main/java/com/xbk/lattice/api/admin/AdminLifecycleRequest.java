package com.xbk.lattice.api.admin;

/**
 * 管理侧生命周期请求。
 *
 * <p>承载文章生命周期切换时的审计信息，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class AdminLifecycleRequest {

    /**
     * 生命周期变更原因。
     *
     * <p>用于审计追踪，说明为什么对文章进行生命周期切换（如激活、归档、删除）。
     * 该值应被持久化到审计表中。</p>
     */
    private String reason;

    /**
     * 操作者标识。
     *
     * <p><b>理想应从认证上下文（如 SecurityContextHolder）获取，当前从请求体直接传入，
     * 存在操作者身份被伪造的风险。</b>调用方不应依赖此处取值做授权判断。</p>
     */
    private String updatedBy;

    /**
     * 获取原因。
     *
     * @return 原因
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置原因。
     *
     * @param reason 原因
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * 获取更新人。
     *
     * @return 更新人
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 设置更新人。
     *
     * @param updatedBy 更新人
     */
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
