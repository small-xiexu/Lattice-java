package com.xbk.lattice.query.evidence.domain;

/**
 * 事实支撑级别。
 *
 * <p>标识 FactFinding 是直接证据还是推导证据——影响证据的可信度权重和展示标签。
 *
 * @author xiexu
 */
public enum FindingSupportLevel {

    /** 直接证据（从源材料中直接提取）。 */
    DIRECT,

    /** 推导证据（通过推理或关联间接得出）。 */
    INFERRED
}
