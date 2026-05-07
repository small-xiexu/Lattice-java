package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.observability.StructuredEventLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Autowired;
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
public class LlmInvocationExecutor {

    private static final String OPENAI_PROVIDER = "openai";

    private static final String OPENAI_COMPATIBLE_PROVIDER = "openai_compatible";

    private static final String STRUCTURED_QUERY_RESPONSE_FORMAT_JSON = """
            {
              "response_format": {
                "type": "json_schema",
                "json_schema": {
                  "name": "query_answer_payload",
                  "strict": true,
                  "schema": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": [
                      "answerMarkdown",
                      "answerOutcome",
                      "answerCacheable"
                    ],
                    "properties": {
                      "answerMarkdown": {
                        "type": "string",
                        "minLength": 1
                      },
                      "answerOutcome": {
                        "type": "string",
                        "enum": [
                          "SUCCESS",
                          "INSUFFICIENT_EVIDENCE",
                          "NO_RELEVANT_KNOWLEDGE",
                          "PARTIAL_ANSWER"
                        ]
                      },
                      "answerCacheable": {
                        "type": "boolean"
                      }
                    }
                  }
                }
              }
            }
            """;

    private final ChatClientRegistry chatClientRegistry;

    private final LlmProperties llmProperties;

    private final StructuredEventLogger structuredEventLogger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建 LLM 调用执行器。
     *
     * @param chatClientRegistry 动态 ChatClient 注册表
     * @param llmProperties LLM 配置
     */
    @Autowired
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
        LlmRouteResolution effectiveRouteResolution = augmentRouteForStructuredOutput(routeResolution, invocationContext);
        return LlmRetrySupport.executeWithRetry(
                "ChatClient invocation",
                effectiveRouteResolution,
                invocationContext == null ? "" : invocationContext.getPurpose(),
                retryObservation -> logRetryAttempt(effectiveRouteResolution, invocationContext, retryObservation),
                () -> chatClientRegistry.getOrCreate(effectiveRouteResolution)
                        .getChatClient()
                        .prompt()
                        .advisors(spec -> spec.params(invocationContext.toAdvisorParams()))
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .chatClientResponse()
        );
    }

    /**
     * 针对结构化问答用途，为 OpenAI 路由补充严格 JSON Schema 约束。
     *
     * @param routeResolution 原始路由
     * @param invocationContext 调用上下文
     * @return 增强后的路由
     */
    private LlmRouteResolution augmentRouteForStructuredOutput(
            LlmRouteResolution routeResolution,
            LlmInvocationContext invocationContext
    ) {
        if (!shouldForceJsonResponseFormat(routeResolution, invocationContext)) {
            return routeResolution;
        }
        String mergedExtraOptionsJson = mergeJsonObject(
                routeResolution.getExtraOptionsJson(),
                STRUCTURED_QUERY_RESPONSE_FORMAT_JSON
        );
        return new LlmRouteResolution(
                routeResolution.getScopeType(),
                routeResolution.getScopeId(),
                routeResolution.getScene(),
                routeResolution.getAgentRole(),
                routeResolution.getBindingId(),
                routeResolution.getSnapshotId(),
                routeResolution.getSnapshotVersion(),
                routeResolution.getRouteLabel(),
                routeResolution.getProviderType(),
                routeResolution.getBaseUrl(),
                routeResolution.getApiKey(),
                routeResolution.getModelName(),
                routeResolution.getTemperature(),
                routeResolution.getMaxTokens(),
                routeResolution.getTimeoutSeconds(),
                mergedExtraOptionsJson,
                routeResolution.getInputPricePer1kTokens(),
                routeResolution.getOutputPricePer1kTokens(),
                routeResolution.isSnapshotBacked()
        );
    }

    /**
     * 判断当前是否应强制开启 JSON response_format。
     *
     * @param routeResolution 原始路由
     * @param invocationContext 调用上下文
     * @return 需要强制返回 true
     */
    private boolean shouldForceJsonResponseFormat(
            LlmRouteResolution routeResolution,
            LlmInvocationContext invocationContext
    ) {
        if (routeResolution == null || invocationContext == null) {
            return false;
        }
        String providerType = normalizeProviderType(routeResolution.getProviderType());
        if (!OPENAI_PROVIDER.equals(providerType) && !OPENAI_COMPATIBLE_PROVIDER.equals(providerType)) {
            return false;
        }
        String purpose = invocationContext.getPurpose();
        return "query-answer-structured".equals(purpose)
                || "query-rewrite-from-review-structured".equals(purpose);
    }

    /**
     * 合并扩展参数 JSON。
     *
     * @param baseJson 基础 JSON
     * @param overlayJson 覆盖 JSON
     * @return 合并后的 JSON
     */
    private String mergeJsonObject(String baseJson, String overlayJson) {
        try {
            JsonNode baseNode = parseObjectNode(baseJson);
            JsonNode overlayNode = parseObjectNode(overlayJson);
            if (baseNode instanceof com.fasterxml.jackson.databind.node.ObjectNode baseObjectNode
                    && overlayNode instanceof com.fasterxml.jackson.databind.node.ObjectNode overlayObjectNode) {
                baseObjectNode.setAll(overlayObjectNode);
                return objectMapper.writeValueAsString(baseObjectNode);
            }
            return overlayJson;
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to merge structured output options", exception);
        }
    }

    /**
     * 解析对象 JSON 节点；空值时返回空对象。
     *
     * @param jsonValue JSON 字符串
     * @return 对象节点
     */
    private JsonNode parseObjectNode(String jsonValue) throws JsonProcessingException {
        if (jsonValue == null || jsonValue.isBlank()) {
            return objectMapper.createObjectNode();
        }
        JsonNode jsonNode = objectMapper.readTree(jsonValue);
        if (jsonNode == null || jsonNode.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!jsonNode.isObject()) {
            throw new IllegalStateException("extraOptionsJson must be a JSON object");
        }
        return jsonNode.deepCopy();
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

    private String normalizeProviderType(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            return OPENAI_PROVIDER;
        }
        return providerType.trim().toLowerCase(Locale.ROOT);
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
