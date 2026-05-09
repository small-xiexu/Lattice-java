package com.xbk.lattice.shared.error;

import java.util.Map;

/**
 * Lattice 业务异常
 *
 * 职责：表示调用方可理解、可修正或可重试的业务层错误
 *
 * @author xiexu
 */
public class LatticeBusinessException extends LatticeException {

    /**
     * 创建业务异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public LatticeBusinessException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建带根因的业务异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 根因
     */
    public LatticeBusinessException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 创建带上下文的业务异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 根因
     * @param context 错误上下文
     */
    public LatticeBusinessException(
            String errorCode,
            String message,
            Throwable cause,
            Map<String, String> context
    ) {
        super(errorCode, message, cause, context);
    }
}
