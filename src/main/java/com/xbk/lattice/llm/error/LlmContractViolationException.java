package com.xbk.lattice.llm.error;

import com.xbk.lattice.shared.error.LatticeIntegrationException;

/**
 * LLM 契约违例异常
 *
 * 职责：表示模型响应或结构化输出不满足调用契约
 *
 * @author xiexu
 */
public class LlmContractViolationException extends LatticeIntegrationException {

    private static final String ERROR_CODE = "LLM_CONTRACT_VIOLATION";

    /**
     * 创建 LLM 契约违例异常。
     *
     * @param message 异常消息
     */
    public LlmContractViolationException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 创建带根因的 LLM 契约违例异常。
     *
     * @param message 异常消息
     * @param cause 根因
     */
    public LlmContractViolationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
