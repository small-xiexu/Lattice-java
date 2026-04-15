package com.xbk.lattice.query.service;

/**
 * 审查超时异常
 *
 * 职责：表示 ReviewerAgent 执行超时
 *
 * @author xiexu
 */
public class ReviewTimeoutException extends RuntimeException {

    /**
     * 创建审查超时异常。
     *
     * @param message 异常信息
     */
    public ReviewTimeoutException(String message) {
        super(message);
    }
}
