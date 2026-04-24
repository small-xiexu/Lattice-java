package com.xbk.lattice.llm.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.observability.StructuredEventLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LLM 调用执行器
 *
 * 职责：基于动态 ChatClient 执行最小 raw 调用，并返回保留路由元数据的 invocation envelope
 *
 * @author xiexu
 */
@Slf4j
@Service
@Profile("jdbc")
public class LlmInvocationExecutor {

    private final ChatClientRegistry chatClientRegistry;

    private final LlmProperties llmProperties;

    private final StructuredEventLogger structuredEventLogger;

    /**
     * 创建 LLM 调用执行器。
     *
     * @param chatClientRegistry 动态 ChatClient 注册表
     * @param llmProperties LLM 配置
     */
    public LlmInvocationExecutor(
            ChatClientRegistry chatClientRegistry,
            LlmProperties llmProperties,
            StructuredEventLogger structuredEventLogger
    ) {
        this.chatClientRegistry = chatClientRegistry;
        this.llmProperties = llmProperties;
        this.structuredEventLogger = structuredEventLogger;
    }

    /**
     * 创建无结构化日志观测器的 LLM 调用执行器。
     *
     * @param chatClientRegistry 动态 ChatClient 注册表
     * @param llmProperties LLM 配置
     */
    public LlmInvocationExecutor(ChatClientRegistry chatClientRegistry, LlmProperties llmProperties) {
        this(chatClientRegistry, llmProperties, null);
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
        ChatClientResponse response = executeChatClientCallWithRetry(
                routeResolution,
                effectiveContext,
                systemPrompt,
                userPrompt
        );
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

    /**
     * 以带瞬时异常重试的方式执行 ChatClient 调用。
     *
     * @param routeResolution 路由解析结果
     * @param invocationContext 调用上下文
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return ChatClient 响应
     */
    private ChatClientResponse executeChatClientCallWithRetry(
            LlmRouteResolution routeResolution,
            LlmInvocationContext invocationContext,
            String systemPrompt,
            String userPrompt
    ) {
        return LlmRetrySupport.executeWithRetry(
                "ChatClient invocation",
                routeResolution,
                invocationContext == null ? "" : invocationContext.getPurpose(),
                retryObservation -> logRetryAttempt(routeResolution, invocationContext, retryObservation),
                () -> chatClientRegistry.getOrCreate(routeResolution)
                        .getChatClient()
                        .prompt()
                        .advisors(spec -> spec.params(invocationContext.toAdvisorParams()))
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .chatClientResponse()
        );
    }

    private void logRetryAttempt(
            LlmRouteResolution routeResolution,
            LlmInvocationContext invocationContext,
            LlmRetrySupport.RetryObservation retryObservation
    ) {
        if (structuredEventLogger == null || retryObservation == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        if (routeResolution != null) {
            fields.put("scene", routeResolution.getScene());
            fields.put("agentRole", routeResolution.getAgentRole());
            fields.put("scopeType", routeResolution.getScopeType());
            fields.put("scopeId", routeResolution.getScopeId());
            fields.put("routeLabel", routeResolution.getRouteLabel());
            fields.put("providerType", routeResolution.getProviderType());
            fields.put("baseUrl", routeResolution.getBaseUrl());
            fields.put("modelName", routeResolution.getModelName());
            if ("query_request".equals(routeResolution.getScopeType())) {
                fields.put("queryId", routeResolution.getScopeId());
            }
            if ("compile_job".equals(routeResolution.getScopeType())) {
                fields.put("compileJobId", routeResolution.getScopeId());
            }
        }
        fields.put("purpose", invocationContext == null ? "" : invocationContext.getPurpose());
        fields.put("attemptNo", Integer.valueOf(retryObservation.getAttemptNo()));
        fields.put("maxAttempts", Integer.valueOf(retryObservation.getMaxAttempts()));
        fields.put("willRetry", Boolean.valueOf(retryObservation.isWillRetry()));
        fields.put("backoffMs", Long.valueOf(retryObservation.getBackoffMillis()));
        putSourceSyncRunId(fields);
        putClientRequestId(fields);
        if (retryObservation.getStatusCode() != null) {
            fields.put("statusCode", retryObservation.getStatusCode());
        }
        String providerRequestId = LlmRetrySupport.resolveProviderRequestId(retryObservation.getException());
        if (providerRequestId != null && !providerRequestId.isBlank()) {
            fields.put("providerRequestId", providerRequestId);
        }
        fields.put("errorCode", retryObservation.getErrorCode());
        fields.put("errorSummary", retryObservation.getErrorSummary());
        structuredEventLogger.warn("llm_retry_attempt_failed", fields, retryObservation.getException());
    }

    /**
     * 从 MDC 注入资料同步运行标识。
     *
     * @param fields 结构化字段
     */
    private void putSourceSyncRunId(Map<String, Object> fields) {
        if (fields == null || fields.containsKey("sourceSyncRunId")) {
            return;
        }
        String sourceSyncRunId = trimToNull(MDC.get("sourceSyncRunId"));
        if (sourceSyncRunId == null) {
            return;
        }
        try {
            fields.put("sourceSyncRunId", Long.valueOf(sourceSyncRunId));
        }
        catch (NumberFormatException exception) {
            fields.put("sourceSyncRunId", sourceSyncRunId);
        }
    }

    /**
     * 从 MDC 注入客户端请求标识。
     *
     * @param fields 结构化字段
     */
    private void putClientRequestId(Map<String, Object> fields) {
        if (fields == null || fields.containsKey("clientRequestId")) {
            return;
        }
        String clientRequestId = resolveClientRequestId();
        if (clientRequestId != null) {
            fields.put("clientRequestId", clientRequestId);
        }
    }

    private String resolveClientRequestId() {
        String clientRequestId = trimToNull(MDC.get("clientRequestId"));
        if (clientRequestId != null) {
            return clientRequestId;
        }
        String rootTraceId = trimToNull(MDC.get("rootTraceId"));
        if (rootTraceId != null) {
            return rootTraceId;
        }
        return trimToNull(MDC.get("traceId"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        return trimmedValue;
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
