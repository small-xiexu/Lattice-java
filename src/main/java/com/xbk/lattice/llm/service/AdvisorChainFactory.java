package com.xbk.lattice.llm.service;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Advisor 链工厂
 *
 * 职责：为动态 ChatClient 提供可复用的最小调用上下文 Advisor 链
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class AdvisorChainFactory {

    private final List<Advisor> defaultAdvisors = List.of(new InvocationContextAdvisor());

    /**
     * 返回默认 Advisor 链。
     *
     * @return Advisor 链
     */
    public List<Advisor> createDefaultAdvisors() {
        return defaultAdvisors;
    }

    /**
     * 最小调用上下文 Advisor。
     *
     * 职责：读取 per-request 上下文，并将关键字段回写到响应上下文用于调试与断言
     *
     * @author xiexu
     */
    private static class InvocationContextAdvisor implements CallAdvisor {

        @Override
        public String getName() {
            return "llm-invocation-context-advisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            LlmInvocationContext invocationContext = LlmInvocationContext.from(request.context());
            ChatClientResponse response = chain.nextCall(request);
            return response.mutate()
                    .context("capturedScene", invocationContext.getScene())
                    .context("capturedPurpose", invocationContext.getPurpose())
                    .context("capturedScopeId", invocationContext.getScopeId())
                    .context("capturedAgentRole", invocationContext.getAgentRole())
                    .context("capturedRouteLabel", invocationContext.getRouteLabel())
                    .build();
        }
    }
}
