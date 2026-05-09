package com.xbk.lattice.shared.error;

import java.util.Map;

/**
 * Lattice 外部集成异常
 *
 * 职责：表示 LLM、OCR、数据库或第三方依赖导致的集成失败
 *
 * @author xiexu
 */
public class LatticeIntegrationException extends LatticeException {

    /**
     * 创建外部集成异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public LatticeIntegrationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建带根因的外部集成异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 根因
     */
    public LatticeIntegrationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 创建带上下文的外部集成异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 根因
     * @param context 错误上下文
     */
    public LatticeIntegrationException(
            String errorCode,
            String message,
            Throwable cause,
            Map<String, String> context
    ) {
        super(errorCode, message, cause, context);
    }
}
