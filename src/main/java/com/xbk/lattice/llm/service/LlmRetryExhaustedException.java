package com.xbk.lattice.llm.service;

/**
 * LLM 重试耗尽异常
 *
 * 职责：标识一次可重试 LLM 调用在达到最大尝试次数后仍未成功
 *
 * @author xiexu
 */
public class LlmRetryExhaustedException extends RuntimeException {

    private final int attempts;

    private final Integer statusCode;

    private final String lastErrorCode;

    /**
     * 创建 LLM 重试耗尽异常。
     *
     * @param operationName 操作名称
     * @param attempts 已尝试次数
     * @param statusCode 最后一次 HTTP 状态码
     * @param lastErrorCode 最后一次错误码
     * @param errorSummary 最后一次错误摘要
     * @param cause 最后一次原始异常
     */
    public LlmRetryExhaustedException(
            String operationName,
            int attempts,
            Integer statusCode,
            String lastErrorCode,
            String errorSummary,
            RuntimeException cause
    ) {
        super(operationName + " exhausted after " + attempts + " attempts: " + errorSummary, cause);
        this.attempts = attempts;
        this.statusCode = statusCode;
        this.lastErrorCode = lastErrorCode;
    }

    /**
     * 返回已尝试次数。
     *
     * @return 已尝试次数
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * 返回最后一次 HTTP 状态码。
     *
     * @return 最后一次 HTTP 状态码
     */
    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     * 返回最后一次错误码。
     *
     * @return 最后一次错误码
     */
    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
