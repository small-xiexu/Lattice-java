package com.xbk.lattice.compiler.error;

import com.xbk.lattice.shared.error.LatticeIntegrationException;

/**
 * 编译图中断异常
 *
 * 职责：表示 StateGraph 编译链路未产出有效终态或执行被底层编排中断
 *
 * @author xiexu
 */
public class CompileGraphAbortException extends LatticeIntegrationException {

    private static final String ERROR_CODE = "COMPILE_GRAPH_ABORTED";

    /**
     * 创建编译图中断异常。
     *
     * @param message 异常信息
     */
    public CompileGraphAbortException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 创建带根因的编译图中断异常。
     *
     * @param message 异常信息
     * @param cause 根因
     */
    public CompileGraphAbortException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
