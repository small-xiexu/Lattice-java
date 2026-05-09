package com.xbk.lattice.compiler.error;

import com.xbk.lattice.shared.error.LatticeBusinessException;

/**
 * 编译作业状态异常
 *
 * 职责：表示编译作业当前状态不满足请求操作的业务前置条件
 *
 * @author xiexu
 */
public class CompileJobStateException extends LatticeBusinessException {

    private static final String ERROR_CODE = "COMPILE_JOB_STATE_INVALID";

    /**
     * 创建编译作业状态异常。
     *
     * @param message 异常信息
     */
    public CompileJobStateException(String message) {
        super(ERROR_CODE, message);
    }
}
