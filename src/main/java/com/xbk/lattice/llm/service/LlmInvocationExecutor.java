package com.xbk.lattice.llm.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * LLM 调用执行器
 *
 * 职责：基于动态 ChatClient 执行最小 raw 调用，并返回保留路由元数据的 invocation envelope
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LlmInvocationExecutor {

    private final ChatClientRegistry chatClientRegistry;

    private final LlmProperties llmProperties;

    /**
     * 创建 LLM 调用执行器。
     *
     * @param chatClientRegistry 动态 ChatClient 注册表
     * @param llmProperties LLM 配置
     */
    public LlmInvocationExecutor(ChatClientRegistry chatClientRegistry, LlmProperties llmProperties) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmProperties = llmProperties;
    }

    /**
     * 执行一次最小 raw 调用。
     *
     * @param routeResolution 路由解析结果
     * @param invocationContext 调用上下文
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param cacheKey prompt cache 键
     * @return 调用信封
     */
    public LlmInvocationEnvelope execute(
            LlmRouteResolution routeResolution,
            LlmInvocationContext invocationContext,
            String systemPrompt,
            String userPrompt,
            String cacheKey
    ) {
        long startedAtNs = System.nanoTime();
        LlmInvocationContext effectiveContext = invocationContext == null
                ? LlmInvocationContext.from(routeResolution, "")
                : invocationContext;
        ChatClientResponse response = chatClientRegistry.getOrCreate(routeResolution)
                .getChatClient()
                .prompt()
                .advisors(spec -> spec.params(effectiveContext.toAdvisorParams()))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatClientResponse();
        String content = extractContent(response);
        Usage usage = response.chatResponse() == null || response.chatResponse().getMetadata() == null
                ? null
                : response.chatResponse().getMetadata().getUsage();
        int inputTokens = readTokenCount(usage == null ? null : usage.getPromptTokens(), systemPrompt + userPrompt);
        int outputTokens = readTokenCount(usage == null ? null : usage.getCompletionTokens(), content);
        long latencyMs = (System.nanoTime() - startedAtNs) / 1_000_000L;
        LlmCallResult llmCallResult = new LlmCallResult(content, inputTokens, outputTokens);
        return LlmInvocationEnvelope.from(
                content,
                effectiveContext.getPurpose(),
                resolveCacheKey(cacheKey, routeResolution, systemPrompt, userPrompt),
                routeResolution,
                llmCallResult,
                latencyMs
        );
    }

    private String extractContent(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return "";
        }
        if (response.chatResponse().getResult().getOutput() == null) {
            return "";
        }
        String text = response.chatResponse().getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private int readTokenCount(Integer tokenCount, String fallbackText) {
        if (tokenCount != null && tokenCount.intValue() >= 0) {
            return tokenCount.intValue();
        }
        return estimateTokens(fallbackText);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkChars = 0;
        for (int index = 0; index < text.length(); index++) {
            if (String.valueOf(text.charAt(index)).matches("[\\u4e00-\\u9fff]")) {
                cjkChars++;
            }
        }
        int nonCjkChars = text.length() - cjkChars;
        return (int) Math.ceil(cjkChars * 1.5D + nonCjkChars * 0.4D);
    }

    private String resolveCacheKey(
            String cacheKey,
            LlmRouteResolution routeResolution,
            String systemPrompt,
            String userPrompt
    ) {
        if (cacheKey != null && !cacheKey.isBlank()) {
            return cacheKey;
        }
        String routeKey = routeResolution == null ? "no-route" : routeResolution.cacheDimensionKey();
        String modelName = routeResolution == null || routeResolution.getModelName() == null
                ? "unknown"
                : routeResolution.getModelName();
        return llmProperties.getCacheKeyPrefix()
                + sha256(routeKey + "|" + modelName + "|" + systemPrompt + "|" + userPrompt);
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
