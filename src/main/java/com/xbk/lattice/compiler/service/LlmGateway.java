package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmClientFactory;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * LLM 网关
 *
 * 职责：统一封装编译/审查模型路由、缓存与预算守卫
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
@Slf4j
public class LlmGateway {

    private static final String ROLE_WRITER = ExecutionLlmSnapshotService.ROLE_WRITER;

    private static final String ROLE_REVIEWER = ExecutionLlmSnapshotService.ROLE_REVIEWER;

    private static final String ROLE_FIXER = ExecutionLlmSnapshotService.ROLE_FIXER;

    private final LlmClient compileClient;

    private final LlmClient reviewClient;

    private final RedisKeyValueStore redisKeyValueStore;

    private final LlmProperties llmProperties;

    private final LlmClientFactory llmClientFactory;

    private final ExecutionLlmSnapshotService executionLlmSnapshotService;

    private final String compileBootstrapBaseUrl;

    private final String compileBootstrapApiKey;

    private final String reviewBootstrapBaseUrl;

    private final String reviewBootstrapApiKey;

    private double spentUsd;

    /**
     * 创建 LLM 网关。
     *
     * @param openAiChatModel OpenAI ChatModel
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param anthropicConnectionProperties Anthropic 连接配置
     * @param anthropicChatProperties Anthropic Chat 配置
     * @param redisKeyValueStore Redis 键值存储
     * @param llmProperties LLM 配置
     * @param llmClientFactory LLM 客户端工厂
     * @param executionLlmSnapshotService 运行时快照服务
     */
    @Autowired
    public LlmGateway(
            OpenAiChatModel openAiChatModel,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.base-url:}") String openAiBaseUrl,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            AnthropicConnectionProperties anthropicConnectionProperties,
            AnthropicChatProperties anthropicChatProperties,
            RedisKeyValueStore redisKeyValueStore,
            LlmProperties llmProperties,
            LlmClientFactory llmClientFactory,
            ExecutionLlmSnapshotService executionLlmSnapshotService
    ) {
        this(
                new ChatModelLlmClient(openAiChatModel),
                new AnthropicMessageApiLlmClient(
                        restClientBuilder,
                        objectMapper,
                        anthropicConnectionProperties,
                        anthropicChatProperties
                ),
                redisKeyValueStore,
                llmProperties,
                llmClientFactory,
                executionLlmSnapshotService,
                openAiBaseUrl,
                openAiApiKey,
                anthropicConnectionProperties.getBaseUrl(),
                anthropicConnectionProperties.getApiKey()
        );
    }

    /**
     * 创建 LLM 网关（测试构造器）。
     *
     * @param compileClient 编译模型客户端
     * @param reviewClient 审查模型客户端
     * @param redisKeyValueStore Redis 键值存储
     * @param llmProperties LLM 配置
     */
    LlmGateway(
            LlmClient compileClient,
            LlmClient reviewClient,
            RedisKeyValueStore redisKeyValueStore,
            LlmProperties llmProperties
    ) {
        this(
                compileClient,
                reviewClient,
                redisKeyValueStore,
                llmProperties,
                null,
                null,
                "",
                "",
                "",
                ""
        );
    }

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
     */
    LlmGateway(
            LlmClient compileClient,
            LlmClient reviewClient,
            RedisKeyValueStore redisKeyValueStore,
            LlmProperties llmProperties,
            LlmClientFactory llmClientFactory,
            ExecutionLlmSnapshotService executionLlmSnapshotService,
            String compileBootstrapBaseUrl,
            String compileBootstrapApiKey,
            String reviewBootstrapBaseUrl,
            String reviewBootstrapApiKey
    ) {
        this.compileClient = compileClient;
        this.reviewClient = reviewClient;
        this.redisKeyValueStore = redisKeyValueStore;
        this.llmProperties = llmProperties;
        this.llmClientFactory = llmClientFactory;
        this.executionLlmSnapshotService = executionLlmSnapshotService;
        this.compileBootstrapBaseUrl = compileBootstrapBaseUrl;
        this.compileBootstrapApiKey = compileBootstrapApiKey;
        this.reviewBootstrapBaseUrl = reviewBootstrapBaseUrl;
        this.reviewBootstrapApiKey = reviewBootstrapApiKey;
        this.spentUsd = 0.0D;
    }

    /**
     * 调用编译模型。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     */
    public String compile(String systemPrompt, String userPrompt) {
        return compile("compile", systemPrompt, userPrompt);
    }

    /**
     * 按用途调用编译模型。
     *
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     */
    public String compile(String purpose, String systemPrompt, String userPrompt) {
        LlmRouteResolution routeResolution = resolveBootstrapRoute(ExecutionLlmSnapshotService.COMPILE_SCENE, ROLE_WRITER);
        return invoke(resolveClient(routeResolution), routeResolution, purpose, systemPrompt, userPrompt);
    }

    /**
     * 按作用域调用编译模型。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     */
    public String compileWithScope(
            String scopeId,
            String scene,
            String agentRole,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        LlmRouteResolution routeResolution = resolveScopedRoute(scopeId, scene, agentRole);
        return invoke(resolveClient(routeResolution), routeResolution, purpose, systemPrompt, userPrompt);
    }

    /**
     * 调用审查模型。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     */
    public String review(String systemPrompt, String userPrompt) {
        return review("review", systemPrompt, userPrompt);
    }

    /**
     * 按用途调用审查模型。
     *
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     */
    public String review(String purpose, String systemPrompt, String userPrompt) {
        LlmRouteResolution routeResolution = resolveBootstrapRoute(ExecutionLlmSnapshotService.COMPILE_SCENE, ROLE_REVIEWER);
        return invoke(resolveClient(routeResolution), routeResolution, purpose, systemPrompt, userPrompt);
    }

    /**
     * 按作用域调用审查模型。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     */
    public String reviewWithScope(
            String scopeId,
            String scene,
            String agentRole,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        LlmRouteResolution routeResolution = resolveScopedRoute(scopeId, scene, agentRole);
        return invoke(resolveClient(routeResolution), routeResolution, purpose, systemPrompt, userPrompt);
    }

    /**
     * 返回编译角色当前路由标签。
     *
     * @return 编译角色路由标签
     */
    public String compileRoute() {
        return resolveBootstrapRoute(ExecutionLlmSnapshotService.COMPILE_SCENE, ROLE_WRITER).getRouteLabel();
    }

    /**
     * 返回某个作用域下编译角色当前路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 编译角色路由标签
     */
    public String compileRoute(String scopeId, String scene) {
        return resolveScopedRoute(scopeId, scene, ROLE_WRITER).getRouteLabel();
    }

    /**
     * 返回审查角色当前路由标签。
     *
     * @return 审查角色路由标签
     */
    public String reviewRoute() {
        return resolveBootstrapRoute(ExecutionLlmSnapshotService.COMPILE_SCENE, ROLE_REVIEWER).getRouteLabel();
    }

    /**
     * 返回某个作用域下审查角色当前路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 审查角色路由标签
     */
    public String reviewRoute(String scopeId, String scene) {
        return resolveScopedRoute(scopeId, scene, ROLE_REVIEWER).getRouteLabel();
    }

    /**
     * 返回修复角色当前路由标签。
     *
     * @return 修复角色路由标签
     */
    public String fixRoute() {
        return resolveBootstrapRoute(ExecutionLlmSnapshotService.COMPILE_SCENE, ROLE_FIXER).getRouteLabel();
    }

    /**
     * 返回某个作用域下修复角色当前路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 修复角色路由标签
     */
    public String fixRoute(String scopeId, String scene) {
        return resolveScopedRoute(scopeId, scene, ROLE_FIXER).getRouteLabel();
    }

    /**
     * 返回是否启用真实审查。
     *
     * @return 是否启用真实审查
     */
    public boolean isReviewEnabled() {
        return llmProperties.isReviewEnabled();
    }

    /**
     * 获取已累计成本。
     *
     * @return 已累计成本
     */
    double getSpentUsd() {
        return spentUsd;
    }

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
    private String invoke(
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
        LlmCallResult llmCallResult = llmClient.call(systemPrompt, truncatedUserPrompt);
        double estimatedCost = estimateCostUsd(routeResolution, llmCallResult);
        if (spentUsd + estimatedCost > llmProperties.getBudgetUsd()) {
            throw new BudgetExceededException("LLM budget exceeded");
        }
        spentUsd += estimatedCost;
        redisKeyValueStore.set(
                cacheKey,
                llmCallResult.getContent(),
                Duration.ofSeconds(llmProperties.getCacheTtlSeconds())
        );
        return llmCallResult.getContent();
    }

    /**
     * 在输入超限时截断用户提示词，避免单次调用超过窗口。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param purpose 调用用途
     * @return 实际发送给模型的用户提示词
     */
    private String truncateUserPromptIfNecessary(String systemPrompt, String userPrompt, String purpose) {
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
    private void ensureBudgetAvailable() {
        if (spentUsd >= llmProperties.getBudgetUsd()) {
            throw new BudgetExceededException("LLM budget exceeded");
        }
    }

    /**
     * 构建缓存键。
     *
     * @param routeResolution 路由解析结果
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return Redis 缓存键
     */
    private String buildCacheKey(LlmRouteResolution routeResolution, String systemPrompt, String userPrompt) {
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
    private String sha256(String payload) {
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
    private double estimateCostUsd(LlmRouteResolution routeResolution, LlmCallResult llmCallResult) {
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

    /**
     * 规范化模型标识。
     *
     * @param modelName 原始模型标识
     * @return 规范化模型标识
     */
    private String normalizeModelName(String modelName) {
        return modelName == null ? "unknown" : modelName.trim().toLowerCase(Locale.ROOT);
    }

    private LlmRouteResolution resolveScopedRoute(String scopeId, String scene, String agentRole) {
        if (executionLlmSnapshotService == null || scopeId == null || scopeId.isBlank() || scene == null || scene.isBlank()) {
            return resolveBootstrapRoute(scene, agentRole);
        }
        String scopeType = resolveScopeType(scene);
        Optional<LlmRouteResolution> routeResolution = executionLlmSnapshotService.resolveRoute(scopeType, scopeId, scene, agentRole);
        if (routeResolution.isPresent()) {
            return routeResolution.orElseThrow();
        }
        if (executionLlmSnapshotService.isBootstrapEnabled()) {
            return resolveBootstrapRoute(scene, agentRole);
        }
        throw new IllegalStateException("No llm route configured for " + scene + "/" + agentRole + " scopeId=" + scopeId);
    }

    private LlmRouteResolution resolveBootstrapRoute(String scene, String agentRole) {
        if (executionLlmSnapshotService != null) {
            return executionLlmSnapshotService.bootstrapRoute(
                    normalizeScene(scene),
                    normalizeAgentRole(agentRole),
                    compileBootstrapBaseUrl,
                    compileBootstrapApiKey,
                    reviewBootstrapBaseUrl,
                    reviewBootstrapApiKey
            );
        }
        String normalizedRole = normalizeAgentRole(agentRole);
        if (ROLE_REVIEWER.equals(normalizedRole)) {
            return new LlmRouteResolution(
                    resolveScopeType(scene),
                    null,
                    normalizeScene(scene),
                    normalizedRole,
                    null,
                    null,
                    Integer.valueOf(0),
                    normalizeModelName(llmProperties.getReviewerModel()),
                    normalizeProviderType(llmProperties.getReviewerModel()),
                    reviewBootstrapBaseUrl,
                    reviewBootstrapApiKey,
                    llmProperties.getReviewerModel(),
                    null,
                    null,
                    null,
                    "{}",
                    llmProperties.getPricing().getReviewerInputPricePer1kTokens(),
                    llmProperties.getPricing().getReviewerOutputPricePer1kTokens(),
                    false
            );
        }
        return new LlmRouteResolution(
                resolveScopeType(scene),
                null,
                normalizeScene(scene),
                normalizedRole,
                null,
                null,
                Integer.valueOf(0),
                normalizeModelName(llmProperties.getCompileModel()),
                normalizeProviderType(llmProperties.getCompileModel()),
                compileBootstrapBaseUrl,
                compileBootstrapApiKey,
                llmProperties.getCompileModel(),
                null,
                null,
                null,
                "{}",
                llmProperties.getPricing().getCompileInputPricePer1kTokens(),
                llmProperties.getPricing().getCompileOutputPricePer1kTokens(),
                false
        );
    }

    private LlmClient resolveClient(LlmRouteResolution routeResolution) {
        if (routeResolution != null && routeResolution.isSnapshotBacked() && llmClientFactory != null) {
            return llmClientFactory.getClient(routeResolution);
        }
        if (routeResolution != null && ROLE_REVIEWER.equals(routeResolution.getAgentRole())) {
            return reviewClient;
        }
        return compileClient;
    }

    private String resolveScopeType(String scene) {
        String normalizedScene = normalizeScene(scene);
        if (ExecutionLlmSnapshotService.COMPILE_SCENE.equals(normalizedScene)) {
            return ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE;
        }
        return normalizedScene + "_scope";
    }

    private String normalizeScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return ExecutionLlmSnapshotService.COMPILE_SCENE;
        }
        return scene.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAgentRole(String agentRole) {
        if (agentRole == null || agentRole.isBlank()) {
            return ROLE_WRITER;
        }
        return agentRole.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeProviderType(String routeOrProvider) {
        String normalized = normalizeModelName(routeOrProvider);
        if (normalized.contains("anthropic") || normalized.contains("claude")) {
            return "anthropic";
        }
        if (normalized.contains("compatible")) {
            return "openai_compatible";
        }
        return "openai";
    }

    private BigDecimal defaultIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
