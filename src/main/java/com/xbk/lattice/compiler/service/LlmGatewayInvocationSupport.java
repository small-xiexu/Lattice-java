package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.error.CompileBudgetExceededException;
import com.xbk.lattice.llm.service.AnthropicMessageApiLlmClient;
import com.xbk.lattice.llm.service.ChatModelLlmClient;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.llm.service.LlmClientFactory;
import com.xbk.lattice.llm.service.LlmInvocationContext;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.LlmInvocationExecutor;
import com.xbk.lattice.llm.service.LlmRetrySupport;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.observability.StructuredEventLogger;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * LLM 网关调用支持。
 *
 * 职责：执行 LLM 调用、缓存读写、预算守卫、重试回调与结构化事件记录。
 *
 * @author xiexu
 */
@Slf4j
abstract class LlmGatewayInvocationSupport extends LlmGatewayRouteSupport {

    /**
     * 创建 LLM 网关。
     *
     * @param compileClient 编译模型客户端
     * @param reviewClient 审查模型客户端
     * @param redisKeyValueStore Redis 键值存储
     * @param llmProperties LLM 配置
     * @param llmClientFactory LLM 客户端工厂
     * @param executionLlmSnapshotService 运行时快照服务
     * @param compileBootstrapBaseUrl 编译 fallback 地址
     * @param compileBootstrapApiKey 编译 fallback API Key
     * @param reviewBootstrapBaseUrl 审查 fallback 地址
     * @param reviewBootstrapApiKey 审查 fallback API Key
     * @param structuredEventLogger 结构化事件日志器
     */
    protected LlmGatewayInvocationSupport(
            LlmClient compileClient,
            LlmClient reviewClient,
            RedisKeyValueStore redisKeyValueStore,
            LlmProperties llmProperties,
            LlmClientFactory llmClientFactory,
            ExecutionLlmSnapshotService executionLlmSnapshotService,
            LlmInvocationExecutor llmInvocationExecutor,
            String compileBootstrapBaseUrl,
            String compileBootstrapApiKey,
            String reviewBootstrapBaseUrl,
            String reviewBootstrapApiKey,
            StructuredEventLogger structuredEventLogger
    ) {
        super(
                compileClient,
                reviewClient,
                redisKeyValueStore,
                llmProperties,
                llmClientFactory,
                executionLlmSnapshotService,
                llmInvocationExecutor,
                compileBootstrapBaseUrl,
                compileBootstrapApiKey,
                reviewBootstrapBaseUrl,
                reviewBootstrapApiKey,
                structuredEventLogger
        );
    }

    /**
     * 按策略处理 L1 prompt cache。
     *
     * @param envelope 调用信封
     * @param promptCacheWritePolicy prompt cache 写策略
     */
    protected abstract void applyPromptCacheWritePolicy(
            LlmInvocationEnvelope envelope,
            PromptCacheWritePolicy promptCacheWritePolicy
    );


    /**
     * 执行带缓存与预算守卫的模型调用。
     *
     * @param llmClient 模型客户端
     * @param modelName 模型标识
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     */
    protected String invoke(
            LlmClient llmClient,
            LlmRouteResolution routeResolution,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        String cacheKey = buildCacheKey(routeResolution, systemPrompt, userPrompt);
        String cachedValue = redisKeyValueStore.get(cacheKey);
        if (cachedValue != null && !cachedValue.isBlank()) {
            return cachedValue;
        }
        ensureBudgetAvailable();
        String truncatedUserPrompt = truncateUserPromptIfNecessary(systemPrompt, userPrompt, purpose);
        long startedAtNs = System.nanoTime();
        logLlmEvent("llm_call_started", routeResolution, purpose, "STARTED", startedAtNs, null, null, null);
        try {
            LlmCallResult llmCallResult = llmClient.call(systemPrompt, truncatedUserPrompt);
            double estimatedCost = estimateCostUsd(routeResolution, llmCallResult);
            synchronized (budgetLock) {
                if (spentUsd + estimatedCost > llmProperties.getBudgetUsd()) {
                    throw new CompileBudgetExceededException("LLM budget exceeded");
                }
                spentUsd += estimatedCost;
            }
            redisKeyValueStore.set(
                    cacheKey,
                    llmCallResult.getContent(),
                    Duration.ofSeconds(llmProperties.getCacheTtlSeconds())
            );
            logLlmEvent("llm_call_succeeded", routeResolution, purpose, "SUCCEEDED", startedAtNs, llmCallResult, Double.valueOf(estimatedCost), null);
            return llmCallResult.getContent();
        }
        catch (RuntimeException exception) {
            logLlmEvent("llm_call_failed", routeResolution, purpose, "FAILED", startedAtNs, null, null, exception);
            throw exception;
        }
    }
    /**
     * 执行文本生成调用，并按 compile 语义写入 L1 prompt cache。
     *
     * @param routeResolution 路由解析结果
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出文本
     */
    protected String invokeText(
            LlmRouteResolution routeResolution,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        String cacheKey = buildCacheKey(routeResolution, systemPrompt, userPrompt);
        LlmInvocationEnvelope envelope = invokeRaw(
                routeResolution,
                purpose,
                systemPrompt,
                userPrompt,
                cacheKey
        );
        applyPromptCacheWritePolicy(envelope, PromptCacheWritePolicy.WRITE);
        return envelope.getContent();
    }
    protected LlmInvocationEnvelope invokeRaw(
            LlmRouteResolution routeResolution,
            String purpose,
            String systemPrompt,
            String userPrompt,
            String cacheKeyOverride
    ) {
        String truncatedUserPrompt = truncateUserPromptIfNecessary(systemPrompt, userPrompt, purpose);
        String cacheKey = cacheKeyOverride == null || cacheKeyOverride.isBlank()
                ? buildCacheKey(routeResolution, systemPrompt, truncatedUserPrompt)
                : cacheKeyOverride;
        String cachedValue = redisKeyValueStore.get(cacheKey);
        if (cachedValue != null && !cachedValue.isBlank()) {
            return LlmInvocationEnvelope.cached(cachedValue, purpose, cacheKey, routeResolution);
        }
        ensureBudgetAvailable();
        long startedAtNs = System.nanoTime();
        logLlmEvent("llm_raw_call_started", routeResolution, purpose, "STARTED", startedAtNs, null, null, null);
        try {
            LlmInvocationEnvelope envelope = executeRawInvocation(
                    routeResolution,
                    purpose,
                    systemPrompt,
                    truncatedUserPrompt,
                    cacheKey,
                    startedAtNs
            );
            double estimatedCost = estimateCostUsd(routeResolution, new LlmCallResult(
                    envelope.getContent(),
                    envelope.getInputTokens(),
                    envelope.getOutputTokens()
            ));
            synchronized (budgetLock) {
                if (spentUsd + estimatedCost > llmProperties.getBudgetUsd()) {
                    throw new CompileBudgetExceededException("LLM budget exceeded");
                }
                spentUsd += estimatedCost;
            }
            logLlmEvent(
                    "llm_raw_call_succeeded",
                    routeResolution,
                    purpose,
                    "SUCCEEDED",
                    startedAtNs,
                    new LlmCallResult(envelope.getContent(), envelope.getInputTokens(), envelope.getOutputTokens()),
                    Double.valueOf(estimatedCost),
                    null
            );
            return envelope;
        }
        catch (RuntimeException exception) {
            logLlmEvent("llm_raw_call_failed", routeResolution, purpose, "FAILED", startedAtNs, null, null, exception);
            throw exception;
        }
    }
    protected LlmInvocationEnvelope executeRawInvocation(
            LlmRouteResolution routeResolution,
            String purpose,
            String systemPrompt,
            String userPrompt,
            String cacheKey,
            long startedAtNs
    ) {
        if (shouldUseChatClientPath(routeResolution, purpose)) {
            return llmInvocationExecutor.execute(
                    routeResolution,
                    LlmInvocationContext.from(routeResolution, purpose),
                    systemPrompt,
                    userPrompt,
                    cacheKey
            );
        }
        LlmCallResult llmCallResult = LlmRetrySupport.executeWithRetry(
                "LLM invocation",
                routeResolution,
                purpose,
                retryObservation -> logRetryAttempt(routeResolution, purpose, retryObservation),
                () -> resolveClient(routeResolution).call(systemPrompt, userPrompt)
        );
        long latencyMs = (System.nanoTime() - startedAtNs) / 1_000_000L;
        return LlmInvocationEnvelope.from(
                llmCallResult.getContent(),
                purpose,
                cacheKey,
                routeResolution,
                llmCallResult,
                latencyMs
        );
    }
    protected boolean shouldUseChatClientPath(LlmRouteResolution routeResolution, String purpose) {
        if (llmInvocationExecutor == null || !supportsChatClientInvocation(routeResolution)) {
            return false;
        }
        LlmProperties.ChatClient chatClientProperties = llmProperties == null ? null : llmProperties.getChatClient();
        if (chatClientProperties == null || !chatClientProperties.isEnabled()) {
            return false;
        }
        return isPurposeEnabledForChatClient(chatClientProperties, purpose);
    }
    protected boolean isPurposeEnabledForChatClient(LlmProperties.ChatClient chatClientProperties, String purpose) {
        String normalizedPurpose = purpose == null ? "" : purpose.trim().toLowerCase(Locale.ROOT);
        if (normalizedPurpose.startsWith("query-answer")) {
            return chatClientProperties.isQueryAnswerEnabled();
        }
        if (normalizedPurpose.startsWith("query-rewrite") || "query-revise".equals(normalizedPurpose)) {
            return chatClientProperties.isQueryRewriteEnabled();
        }
        if ("query-review".equals(normalizedPurpose)) {
            return chatClientProperties.isQueryReviewEnabled();
        }
        if ("compile-review".equals(normalizedPurpose)) {
            return chatClientProperties.isCompileReviewEnabled();
        }
        if (GOVERNANCE_JSON_PURPOSES.contains(normalizedPurpose)) {
            return chatClientProperties.isGovernanceJsonEnabled();
        }
        return true;
    }
    /**
     * 写入指定 prompt cache 键。
     *
     * @param cacheKey cache 键
     * @param content 缓存内容
     */
    protected void writePromptCache(String cacheKey, String content) {
        redisKeyValueStore.set(
                cacheKey,
                content,
                Duration.ofSeconds(llmProperties.getCacheTtlSeconds())
        );
    }
    /**
     * 驱逐指定 prompt cache 键。
     *
     * @param cacheKey cache 键
     */
    protected void evictPromptCacheKey(String cacheKey) {
        redisKeyValueStore.deleteByPrefix(cacheKey);
    }
    /**
     * 在输入超限时截断用户提示词，避免单次调用超过窗口。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param purpose 调用用途
     * @return 实际发送给模型的用户提示词
     */
    protected String truncateUserPromptIfNecessary(String systemPrompt, String userPrompt, String purpose) {
        int totalInputLength = systemPrompt.length() + userPrompt.length();
        int maxInputChars = llmProperties.getMaxInputChars();
        if (maxInputChars <= 0 || totalInputLength <= maxInputChars) {
            return userPrompt;
        }

        int budget = maxInputChars - systemPrompt.length() - 200;
        if (budget <= 0) {
            log.warn(
                    "System prompt length {} exceeds maxInputChars {}, skipping truncation for purpose {}",
                    systemPrompt.length(),
                    maxInputChars,
                    purpose
            );
            return userPrompt;
        }

        String truncatedUserPrompt = userPrompt.substring(0, budget)
                + "\n\n[... 内容已截断，超出单次调用字符限制 ...]";
        log.warn(
                "LLM input truncated: original={} chars, limit={} chars, purpose={}",
                totalInputLength,
                maxInputChars,
                purpose
        );
        return truncatedUserPrompt;
    }
    /**
     * 确认预算尚未耗尽。
     */
    protected void ensureBudgetAvailable() {
        synchronized (budgetLock) {
            if (spentUsd >= llmProperties.getBudgetUsd()) {
                throw new CompileBudgetExceededException("LLM budget exceeded");
            }
        }
    }
    protected void logLlmEvent(
            String eventName,
            LlmRouteResolution routeResolution,
            String purpose,
            String status,
            long startedAtNs,
            LlmCallResult llmCallResult,
            Double estimatedCost,
            Throwable throwable
    ) {
        if (structuredEventLogger == null) {
            return;
        }
        Map<String, Object> fields = buildLlmEventFields(routeResolution, purpose, status, startedAtNs);
        if (llmCallResult != null) {
            fields.put("inputTokens", llmCallResult.getInputTokens());
            fields.put("outputTokens", llmCallResult.getOutputTokens());
            if (llmCallResult.getProviderRequestId() != null && !llmCallResult.getProviderRequestId().isBlank()) {
                fields.put("providerRequestId", llmCallResult.getProviderRequestId());
            }
        }
        if (estimatedCost != null) {
            fields.put("estimatedCostUsd", estimatedCost);
        }
        putSourceSyncRunId(fields);
        putClientRequestId(fields);
        if (throwable != null) {
            Integer statusCode = LlmRetrySupport.resolveStatusCode(throwable);
            if (statusCode != null) {
                fields.put("statusCode", statusCode);
            }
            String providerRequestId = LlmRetrySupport.resolveProviderRequestId(throwable);
            if (providerRequestId != null && !providerRequestId.isBlank()) {
                fields.put("providerRequestId", providerRequestId);
            }
            fields.put("errorCode", LlmRetrySupport.resolveErrorCode(throwable));
            fields.put("errorSummary", LlmRetrySupport.resolveErrorSummary(throwable));
            fields.put("error", throwable.getMessage());
            structuredEventLogger.error(eventName, fields, throwable);
            return;
        }
        structuredEventLogger.info(eventName, fields);
    }
    protected Map<String, Object> buildLlmEventFields(
            LlmRouteResolution routeResolution,
            String purpose,
            String status,
            long startedAtNs
    ) {
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
            if (ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE.equals(routeResolution.getScopeType())) {
                fields.put("queryId", routeResolution.getScopeId());
            }
            if (ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE.equals(routeResolution.getScopeType())) {
                fields.put("compileJobId", routeResolution.getScopeId());
            }
        }
        fields.put("purpose", purpose);
        fields.put("status", status);
        fields.put("maxAttempts", Integer.valueOf(LlmRetrySupport.maxAttempts()));
        fields.put("latencyMs", Long.valueOf((System.nanoTime() - startedAtNs) / 1_000_000L));
        return fields;
    }
    protected void logRetryAttempt(
            LlmRouteResolution routeResolution,
            String purpose,
            LlmRetrySupport.RetryObservation retryObservation
    ) {
        if (structuredEventLogger == null || retryObservation == null) {
            return;
        }
        Map<String, Object> fields = buildLlmEventFields(routeResolution, purpose, "FAILED", System.nanoTime());
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
    protected void putSourceSyncRunId(Map<String, Object> fields) {
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
    protected void putClientRequestId(Map<String, Object> fields) {
        if (fields == null || fields.containsKey("clientRequestId")) {
            return;
        }
        String clientRequestId = resolveClientRequestId();
        if (clientRequestId != null) {
            fields.put("clientRequestId", clientRequestId);
        }
    }
    protected String resolveClientRequestId() {
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
    protected String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        return trimmedValue;
    }
    /**
     * 构建缓存键。
     *
     * @param routeResolution 路由解析结果
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return Redis 缓存键
     */
    protected String buildCacheKey(LlmRouteResolution routeResolution, String systemPrompt, String userPrompt) {
        String routeKey = routeResolution == null ? "no-route" : routeResolution.cacheDimensionKey();
        String modelName = routeResolution == null ? "unknown" : safeValue(routeResolution.getModelName());
        return llmProperties.getCacheKeyPrefix() + sha256(routeKey + "|" + modelName + "|" + systemPrompt + "|" + userPrompt);
    }
    /**
     * 计算 SHA-256。
     *
     * @param payload 原始内容
     * @return SHA-256 十六进制字符串
     */
    protected String sha256(String payload) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
    /**
     * 估算美元成本。
     *
     * @param routeResolution 路由解析结果
     * @param llmCallResult 调用结果
     * @return 估算成本
     */
    protected double estimateCostUsd(LlmRouteResolution routeResolution, LlmCallResult llmCallResult) {
        BigDecimal inputRate = routeResolution == null
                ? BigDecimal.ZERO
                : defaultIfNull(routeResolution.getInputPricePer1kTokens());
        BigDecimal outputRate = routeResolution == null
                ? BigDecimal.ZERO
                : defaultIfNull(routeResolution.getOutputPricePer1kTokens());
        BigDecimal inputCost = inputRate.multiply(BigDecimal.valueOf(llmCallResult.getInputTokens()))
                .divide(BigDecimal.valueOf(1000L), 6, BigDecimal.ROUND_HALF_UP);
        BigDecimal outputCost = outputRate.multiply(BigDecimal.valueOf(llmCallResult.getOutputTokens()))
                .divide(BigDecimal.valueOf(1000L), 6, BigDecimal.ROUND_HALF_UP);
        return inputCost.add(outputCost).doubleValue();
    }
}
