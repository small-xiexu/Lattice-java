package com.xbk.lattice.compiler.error;

import com.xbk.lattice.shared.error.LatticeBusinessException;

/**
 * 编译结构契约异常
 *
 * 职责：表示编译输入或中间产物不满足 schema 契约
 *
 * @author xiexu
 */
public class CompileSchemaInvalidException extends LatticeBusinessException {

    private static final String ERROR_CODE = "COMPILE_SCHEMA_INVALID";

    /**
     * 创建编译结构契约异常。
     *
     * @param message 异常消息
     */
    public CompileSchemaInvalidException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 创建带根因的编译结构契约异常。
     *
     * @param message 异常消息
     * @param cause 根因
     */
    public CompileSchemaInvalidException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
