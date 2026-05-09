package com.xbk.lattice.llm.error;

import com.xbk.lattice.shared.error.LatticeIntegrationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 重试耗尽异常
 *
 * 职责：标识一次可重试 LLM 调用在达到最大尝试次数后仍未成功
 *
 * @author xiexu
 */
public class LlmRetryExhaustedException extends LatticeIntegrationException {

    private static final String ERROR_CODE = "LLM_RETRY_EXHAUSTED";

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
        super(
                ERROR_CODE,
                buildMessage(operationName, attempts, errorSummary),
                cause,
                buildContext(operationName, attempts, statusCode, lastErrorCode)
        );
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

    /**
     * 构建错误消息。
     *
     * @param operationName 操作名称
     * @param attempts 已尝试次数
     * @param errorSummary 最后一次错误摘要
     * @return 错误消息
     */
    private static String buildMessage(String operationName, int attempts, String errorSummary) {
        return operationName + " exhausted after " + attempts + " attempts: " + errorSummary;
    }

    /**
     * 构建错误上下文。
     *
     * @param operationName 操作名称
     * @param attempts 已尝试次数
     * @param statusCode 最后一次 HTTP 状态码
     * @param lastErrorCode 最后一次错误码
     * @return 错误上下文
     */
    private static Map<String, String> buildContext(
            String operationName,
            int attempts,
            Integer statusCode,
            String lastErrorCode
    ) {
        Map<String, String> context = new LinkedHashMap<String, String>();
        context.put("operationName", operationName);
        context.put("attempts", String.valueOf(attempts));
        if (statusCode != null) {
            context.put("statusCode", String.valueOf(statusCode));
        }
        if (lastErrorCode != null && !lastErrorCode.isBlank()) {
            context.put("lastErrorCode", lastErrorCode);
        }
        return context;
    }
}
