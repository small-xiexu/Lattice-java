package com.xbk.lattice.query.domain;

/**
 * 模型执行状态。
 *
 * <p>标识当前答案对应的模型执行是否成功、降级、失败或被跳过——影响答案质量评估和缓存决策。
 *
 * @author xiexu
 */
public enum ModelExecutionStatus {

    /** 模型执行成功。 */
    SUCCESS,

    /** 模型执行降级（如非结构化回退后仍有可用结果）。 */
    DEGRADED,

    /** 模型执行失败（超时、限流、异常）。 */
    FAILED,

    /** 模型执行被跳过（如规则路径无需 LLM）。 */
    SKIPPED
}
