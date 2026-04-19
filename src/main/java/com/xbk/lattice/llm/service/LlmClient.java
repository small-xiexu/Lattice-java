package com.xbk.lattice.llm.service;

/**
 * LLM 客户端抽象
 *
 * 职责：隔离具体模型 SDK 调用，便于网关层测试
 *
 * @author xiexu
 */
public interface LlmClient {

    /**
     * 调用模型。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 调用结果
     */
    LlmCallResult call(String systemPrompt, String userPrompt);
}
