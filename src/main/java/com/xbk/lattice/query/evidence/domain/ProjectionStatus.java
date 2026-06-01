package com.xbk.lattice.query.evidence.domain;

/**
 * 投影记录状态。
 *
 * <p>标识投影是否仍为最终出站白名单中的有效记录——驱动前端展示和 citation 过滤。
 *
 * @author xiexu
 */
public enum ProjectionStatus {

    /** 活跃（当前有效，可出站）。 */
    ACTIVE,

    /** 已被替换（新版本已替代此记录）。 */
    REPLACED,

    /** 已被移除（从白名单中删除）。 */
    REMOVED
}
