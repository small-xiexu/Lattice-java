package com.xbk.lattice.llm.service;

/**
 * LLM 调用信封
 *
 * 职责：承载 migrated path 在原始文本解析前需要保留的调用元数据
 *
 * @author xiexu
 */
public class LlmInvocationEnvelope {

    private final String content;

    private final String purpose;

    private final String cacheKey;

    private final LlmRouteResolution routeResolution;

    private final int inputTokens;

    private final int outputTokens;

    private final long latencyMs;

    private final boolean promptCacheHit;

    /**
     * 创建 LLM 调用信封。
     *
     * @param content 原始输出文本
     * @param purpose 调用用途
     * @param cacheKey prompt cache 键
     * @param routeResolution 路由解析结果
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param latencyMs 调用延迟
     */
    public LlmInvocationEnvelope(
            String content,
            String purpose,
            String cacheKey,
            LlmRouteResolution routeResolution,
            int inputTokens,
            int outputTokens,
            long latencyMs
    ) {
        this(content, purpose, cacheKey, routeResolution, inputTokens, outputTokens, latencyMs, false);
    }

    /**
     * 创建 LLM 调用信封。
     *
     * @param content 原始输出文本
     * @param purpose 调用用途
     * @param cacheKey prompt cache 键
     * @param routeResolution 路由解析结果
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param latencyMs 调用延迟
     * @param promptCacheHit 是否命中 L1 prompt cache
     */
    public LlmInvocationEnvelope(
            String content,
            String purpose,
            String cacheKey,
            LlmRouteResolution routeResolution,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            boolean promptCacheHit
    ) {
        this.content = content;
        this.purpose = purpose;
        this.cacheKey = cacheKey;
        this.routeResolution = routeResolution;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.promptCacheHit = promptCacheHit;
    }

    /**
     * 根据模型调用结果构造信封。
     *
     * @param content 原始输出文本
     * @param purpose 调用用途
     * @param cacheKey prompt cache 键
     * @param routeResolution 路由解析结果
     * @param llmCallResult 模型调用结果
     * @param latencyMs 调用延迟
     * @return LLM 调用信封
     */
    public static LlmInvocationEnvelope from(
            String content,
            String purpose,
            String cacheKey,
            LlmRouteResolution routeResolution,
            LlmCallResult llmCallResult,
            long latencyMs
    ) {
        int inputTokens = llmCallResult == null ? 0 : llmCallResult.getInputTokens();
        int outputTokens = llmCallResult == null ? 0 : llmCallResult.getOutputTokens();
        return new LlmInvocationEnvelope(
                content,
                purpose,
                cacheKey,
                routeResolution,
                inputTokens,
                outputTokens,
                latencyMs,
                false
        );
    }

    /**
     * 根据 prompt cache 命中结果构造调用信封。
     *
     * @param content 原始输出文本
     * @param purpose 调用用途
     * @param cacheKey prompt cache 键
     * @param routeResolution 路由解析结果
     * @return LLM 调用信封
     */
    public static LlmInvocationEnvelope cached(
            String content,
            String purpose,
            String cacheKey,
            LlmRouteResolution routeResolution
    ) {
        return new LlmInvocationEnvelope(
                content,
                purpose,
                cacheKey,
                routeResolution,
                0,
                0,
                0L,
                true
        );
    }

    /**
     * 获取原始输出文本。
     *
     * @return 原始输出文本
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取调用用途。
     *
     * @return 调用用途
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * 获取 prompt cache 键。
     *
     * @return prompt cache 键
     */
    public String getCacheKey() {
        return cacheKey;
    }

    /**
     * 获取路由解析结果。
     *
     * @return 路由解析结果
     */
    public LlmRouteResolution getRouteResolution() {
        return routeResolution;
    }

    /**
     * 获取输入 token 数。
     *
     * @return 输入 token 数
     */
    public int getInputTokens() {
        return inputTokens;
    }

    /**
     * 获取输出 token 数。
     *
     * @return 输出 token 数
     */
    public int getOutputTokens() {
        return outputTokens;
    }

    /**
     * 获取调用延迟。
     *
     * @return 调用延迟
     */
    public long getLatencyMs() {
        return latencyMs;
    }

    /**
     * 返回是否命中 L1 prompt cache。
     *
     * @return 是否命中 L1 prompt cache
     */
    public boolean isPromptCacheHit() {
        return promptCacheHit;
    }
}
