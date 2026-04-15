package com.xbk.lattice.api.admin;

/**
 * 管理侧生命周期请求
 *
 * 职责：承载文章生命周期切换时的原因与操作者
 *
 * @author xiexu
 */
public class AdminLifecycleRequest {

    private String reason;

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
