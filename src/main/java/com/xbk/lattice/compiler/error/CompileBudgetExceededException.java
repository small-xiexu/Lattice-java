package com.xbk.lattice.compiler.error;

import com.xbk.lattice.shared.error.LatticeBusinessException;

/**
 * 编译预算超限异常
 *
 * 职责：在累计估算成本超出预算时中断编译侧 LLM 调用
 *
 * @author xiexu
 */
public class CompileBudgetExceededException extends LatticeBusinessException {

    private static final String ERROR_CODE = "COMPILE_TOTAL_BUDGET_EXCEEDED";

    /**
     * 创建编译预算超限异常。
     *
     * @param message 异常消息
     */
    public CompileBudgetExceededException(String message) {
        super(ERROR_CODE, message);
    }
}
