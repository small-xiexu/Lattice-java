package com.xbk.lattice.llm.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
@Slf4j
@Service
@Profile("jdbc")
public class LlmInvocationExecutor {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    private static final long BASE_RETRY_BACKOFF_MILLIS = 300L;

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
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return chatClientRegistry.getOrCreate(routeResolution)
                        .getChatClient()
                        .prompt()
                        .advisors(spec -> spec.params(invocationContext.toAdvisorParams()))
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .chatClientResponse();
            }
            catch (RuntimeException exception) {
                lastException = exception;
                if (!isRetryable(exception) || attempt >= MAX_RETRY_ATTEMPTS) {
                    throw exception;
                }
                String routeLabel = routeResolution == null ? "" : routeResolution.getRouteLabel();
                String purpose = invocationContext == null ? "" : invocationContext.getPurpose();
                log.warn(
                        "ChatClient invocation failed on attempt {}/{} and will retry. routeLabel: {}, purpose: {}, reason: {}",
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        routeLabel,
                        purpose,
                        summarizeException(exception)
                );
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException == null
                ? new IllegalStateException("ChatClient invocation failed without exception")
                : lastException;
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

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            int statusCode = responseException.getStatusCode().value();
            return statusCode == 408
                    || statusCode == 409
                    || statusCode == 425
                    || statusCode == 429
                    || statusCode >= 500;
        }
        if (!(exception instanceof RestClientException) && !(exception instanceof IllegalStateException)) {
            return false;
        }
        Throwable rootCause = rootCause(exception);
        return rootCause instanceof EOFException
                || rootCause instanceof SocketException
                || rootCause instanceof SocketTimeoutException
                || rootCause instanceof IOException;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String summarizeException(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    private void sleepBeforeRetry(int attempt) {
        long backoffMillis = BASE_RETRY_BACKOFF_MILLIS * attempt;
        try {
            Thread.sleep(backoffMillis);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ChatClient invocation retry interrupted", exception);
        }
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
