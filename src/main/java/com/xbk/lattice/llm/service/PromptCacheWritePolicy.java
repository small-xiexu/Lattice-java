package com.xbk.lattice.llm.service;

/**
 * Prompt 缓存写策略
 *
 * 职责：标识 L1 prompt cache 在拿到上层语义后应执行的最小动作
 *
 * @author xiexu
 */
public enum PromptCacheWritePolicy {

    WRITE,

    SKIP_WRITE,

    EVICT_AFTER_READ
}
