package com.xbk.lattice.source.error;

import com.xbk.lattice.shared.error.LatticeBusinessException;

/**
 * 资料源解析异常
 *
 * 职责：表示资料源输入内容无法被当前导入链路解析
 *
 * @author xiexu
 */
public class SourceParseException extends LatticeBusinessException {

    private static final String ERROR_CODE = "SOURCE_PARSE_FAILED";

    /**
     * 创建资料源解析异常。
     *
     * @param message 异常消息
     */
    public SourceParseException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 创建带根因的资料源解析异常。
     *
     * @param message 异常消息
     * @param cause 根因
     */
    public SourceParseException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
