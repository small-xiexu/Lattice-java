package com.xbk.lattice.llm.error;

import com.xbk.lattice.shared.error.LatticeIntegrationException;

/**
 * LLM 连接不可用异常
 *
 * 职责：表示模型中心连接缺失、禁用或无法建立有效路由
 *
 * @author xiexu
 */
public class LlmConnectionUnavailableException extends LatticeIntegrationException {

    private static final String ERROR_CODE = "LLM_CONNECTION_UNAVAILABLE";

    /**
     * 创建 LLM 连接不可用异常。
     *
     * @param message 异常消息
     */
    public LlmConnectionUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 创建带根因的 LLM 连接不可用异常。
     *
     * @param message 异常消息
     * @param cause 根因
     */
    public LlmConnectionUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
