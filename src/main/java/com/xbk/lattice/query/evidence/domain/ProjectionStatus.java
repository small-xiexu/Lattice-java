package com.xbk.lattice.query.evidence.domain;

/**
 * 投影记录状态
 *
 * 职责：标识投影是否仍为最终出站白名单中的有效记录
 *
 * @author xiexu
 */
public enum ProjectionStatus {

    ACTIVE,

    REPLACED,

    REMOVED
}
