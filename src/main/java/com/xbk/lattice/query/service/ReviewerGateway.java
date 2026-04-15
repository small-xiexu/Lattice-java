package com.xbk.lattice.query.service;

/**
 * 审查网关
 *
 * 职责：抽象 ReviewerAgent 的原始审查输出来源
 *
 * @author xiexu
 */
public interface ReviewerGateway {

    /**
     * 执行单轮审查。
     *
     * @param reviewPrompt 审查提示词
     * @return 原始审查输出
     */
    String review(String reviewPrompt);
}
