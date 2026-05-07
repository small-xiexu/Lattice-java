package com.xbk.lattice.query.deepresearch.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deep Research 执行上下文注册表
 *
 * 职责：按 queryId 管理运行中的深度研究执行上下文
 *
 * @author xiexu
 */
@Component
public class DeepResearchExecutionRegistry {

    private final Map<String, DeepResearchExecutionContext> contexts = new ConcurrentHashMap<String, DeepResearchExecutionContext>();

    /**
     * 注册新的执行上下文。
     *
     * @param queryId 查询标识
     * @param maxLlmCalls 最大 LLM 调用次数
     * @param overallTimeoutMs 整体超时毫秒数
     * @return 执行上下文
     */
    public DeepResearchExecutionContext register(String queryId, int maxLlmCalls, int overallTimeoutMs) {
        long deadlineEpochMs = overallTimeoutMs <= 0 ? 0L : System.currentTimeMillis() + overallTimeoutMs;
        DeepResearchExecutionContext context = new DeepResearchExecutionContext(maxLlmCalls, deadlineEpochMs);
        contexts.put(queryId, context);
        return context;
    }

    /**
     * 按查询标识查找执行上下文。
     *
     * @param queryId 查询标识
     * @return 执行上下文
     */
    public DeepResearchExecutionContext get(String queryId) {
        return contexts.get(queryId);
    }

    /**
     * 移除执行上下文。
     *
     * @param queryId 查询标识
     */
    public void remove(String queryId) {
        contexts.remove(queryId);
    }
}
