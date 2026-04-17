package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 lint fix 请求
 *
 * 职责：承载批量或定向 lint 自动修复请求
 *
 * @author xiexu
 */
public class AdminLintFixRequest {

    private List<String> targetIds;

    /**
     * 获取定向修复目标。
     *
     * @return 定向修复目标
     */
    public List<String> getTargetIds() {
        return targetIds;
    }

    /**
     * 设置定向修复目标。
     *
     * @param targetIds 定向修复目标
     */
    public void setTargetIds(List<String> targetIds) {
        this.targetIds = targetIds;
    }
}
