package com.xbk.lattice.compiler.service;

/**
 * LLM 用量存储抽象
 *
 * 职责：隔离用量记录持久化，便于网关层测试
 *
 * @author xiexu
 */
public interface LlmUsageStore {

    /**
     * 保存用量记录。
     *
     * @param llmUsageRecord 用量记录
     */
    void save(LlmUsageRecord llmUsageRecord);
}
