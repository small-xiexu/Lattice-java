package com.xbk.lattice.api.admin;

/**
 * 管理侧链接增强请求
 *
 * 职责：承载 link enhance 的 persist 开关
 *
 * @author xiexu
 */
public class AdminLinkEnhanceRequest {

    private boolean persist;

    public boolean isPersist() {
        return persist;
    }

    public void setPersist(boolean persist) {
        this.persist = persist;
    }
}
