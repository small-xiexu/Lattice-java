package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;

/**
 * 答案 LLM 调用器
 *
 * 职责：收口答案生成链路对 LlmGateway 的直接调用、缓存策略写入与路由标签解析
 *
 * @author xiexu
 */
public class AnswerLlmInvoker {

    private final LlmGateway llmGateway;

    /**
     * 创建答案 LLM 调用器。
     *
     * @param llmGateway LLM 网关；为空时表示当前运行在无模型兜底模式
     */
    public AnswerLlmInvoker(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * 判断当前是否配置了 LLM 网关。
     *
     * @return 可调用返回 true
     */
    public boolean isAvailable() {
        return llmGateway != null;
    }

    /**
     * 调用结构化原始 LLM 接口。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用目的
     * @param systemPrompt system prompt
     * @param userPrompt user prompt
     * @return LLM 调用信封
     */
    public LlmInvocationEnvelope invokeRawWithScope(
            String scopeId,
            String scene,
            String agentRole,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        return llmGateway.invokeRawWithScope(scopeId, scene, agentRole, purpose, systemPrompt, userPrompt);
    }

    /**
     * 调用文本 LLM 接口。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用目的
     * @param systemPrompt system prompt
     * @param userPrompt user prompt
     * @return 模型输出文本
     */
    public String generateTextWithScope(
            String scopeId,
            String scene,
            String agentRole,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        return llmGateway.generateTextWithScope(scopeId, scene, agentRole, purpose, systemPrompt, userPrompt);
    }

    /**
     * 应用 prompt cache 写入策略。
     *
     * @param envelope 调用信封
     * @param promptCacheWritePolicy 写入策略
     */
    public void applyPromptCacheWritePolicy(
            LlmInvocationEnvelope envelope,
            PromptCacheWritePolicy promptCacheWritePolicy
    ) {
        llmGateway.applyPromptCacheWritePolicy(envelope, promptCacheWritePolicy);
    }

    /**
     * 返回当前作用域下的路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由标签
     */
    public String currentRoute(String scopeId, String scene, String agentRole) {
        if (llmGateway == null) {
            return "fallback";
        }
        try {
            return llmGateway.routeFor(scopeId, scene, agentRole);
        }
        catch (RuntimeException ex) {
            return "fallback";
        }
    }
}
