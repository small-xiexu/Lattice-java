package com.xbk.lattice.compiler.service;

/**
 * LLM 预算超限异常
 *
 * 职责：在累计估算成本超出预算时中断调用
 *
 * @author xiexu
 */
public class BudgetExceededException extends RuntimeException {

    /**
     * 创建预算超限异常。
     *
     * @param message 异常消息
     */
    public BudgetExceededException(String message) {
        super(message);
    }
}
