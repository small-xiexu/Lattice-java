package com.xbk.lattice.query.domain;

/**
 * 模型执行状态
 *
 * 职责：标识当前答案对应的模型执行是否成功、失败或被跳过
 *
 * @author xiexu
 */
public enum ModelExecutionStatus {

    SUCCESS,

    FAILED,

    SKIPPED
}
