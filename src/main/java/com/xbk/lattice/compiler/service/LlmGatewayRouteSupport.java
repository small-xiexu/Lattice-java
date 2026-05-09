package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.LlmProperties;
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
 * LLM 网关路由支持。
 *
 * 职责：解析模型中心路由、bootstrap fallback 与协议客户端选择。
 *
 * @author xiexu
 */
@Slf4j
abstract class LlmGatewayRouteSupport {

    protected static final String ROLE_WRITER = ExecutionLlmSnapshotService.ROLE_WRITER;

    protected static final String ROLE_REVIEWER = ExecutionLlmSnapshotService.ROLE_REVIEWER;

    protected static final String ROLE_FIXER = ExecutionLlmSnapshotService.ROLE_FIXER;

    protected static final Set<String> GOVERNANCE_JSON_PURPOSES = Set.of(
            "cross-validate",
            "check-propagation",
            "analyze"
    );

    protected final LlmClient compileClient;

    protected final LlmClient reviewClient;

    protected final RedisKeyValueStore redisKeyValueStore;

    protected final LlmProperties llmProperties;

    protected final LlmClientFactory llmClientFactory;

    protected final ExecutionLlmSnapshotService executionLlmSnapshotService;

    protected final String compileBootstrapBaseUrl;

    protected final String compileBootstrapApiKey;

    protected final String reviewBootstrapBaseUrl;

    protected final String reviewBootstrapApiKey;

    protected String compileBootstrapModelName;

    protected String reviewBootstrapModelName;

    protected final LlmInvocationExecutor llmInvocationExecutor;

    protected final StructuredEventLogger structuredEventLogger;

    protected final Object budgetLock = new Object();

    protected double spentUsd;

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
    protected LlmGatewayRouteSupport(
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
        this.compileClient = compileClient;
        this.reviewClient = reviewClient;
        this.redisKeyValueStore = redisKeyValueStore;
        this.llmProperties = llmProperties;
        this.llmClientFactory = llmClientFactory;
        this.executionLlmSnapshotService = executionLlmSnapshotService;
        this.llmInvocationExecutor = llmInvocationExecutor;
        this.compileBootstrapBaseUrl = compileBootstrapBaseUrl;
        this.compileBootstrapApiKey = compileBootstrapApiKey;
        this.reviewBootstrapBaseUrl = reviewBootstrapBaseUrl;
        this.reviewBootstrapApiKey = reviewBootstrapApiKey;
        this.compileBootstrapModelName = llmProperties == null ? "" : resolveBootstrapModelName("", llmProperties.getCompileModel());
        this.reviewBootstrapModelName = llmProperties == null ? "" : resolveBootstrapModelName("", llmProperties.getReviewerModel());
        this.structuredEventLogger = structuredEventLogger;
        this.spentUsd = 0.0D;
    }

    protected LlmRouteResolution resolveScopedRoute(String scopeId, String scene, String agentRole) {
        String normalizedScene = normalizeScene(scene);
        if (ExecutionLlmSnapshotService.DEEP_RESEARCH_SCENE.equals(normalizedScene)) {
            if (executionLlmSnapshotService == null) {
                throw new IllegalStateException("deep_research scene 缺少 ExecutionLlmSnapshotService，无法解析运行时路由");
            }
            if (scopeId == null || scopeId.isBlank()) {
                throw new IllegalStateException("deep_research scene 缺少 scopeId，无法解析运行时路由");
            }
            String scopeType = resolveScopeType(normalizedScene);
            Optional<LlmRouteResolution> routeResolution = executionLlmSnapshotService.resolveRoute(
                    scopeType,
                    scopeId,
                    normalizedScene,
                    agentRole
            );
            if (routeResolution.isPresent()) {
                return routeResolution.orElseThrow();
            }
            throw new IllegalStateException("No llm route configured for " + normalizedScene + "/" + agentRole
                    + " scopeId=" + scopeId);
        }
        if (executionLlmSnapshotService == null || scopeId == null || scopeId.isBlank() || scene == null || scene.isBlank()) {
            return resolveBootstrapRoute(scene, agentRole, scopeId);
        }
        String scopeType = resolveScopeType(normalizedScene);
        Optional<LlmRouteResolution> routeResolution = executionLlmSnapshotService.resolveRoute(
                scopeType,
                scopeId,
                normalizedScene,
                agentRole
        );
        if (routeResolution.isPresent()) {
            return routeResolution.orElseThrow();
        }
        if (executionLlmSnapshotService.isBootstrapAllowed(normalizedScene)) {
            return resolveBootstrapRoute(scene, agentRole, scopeId);
        }
        throw new IllegalStateException("No llm route configured for " + normalizedScene + "/" + agentRole
                + " scopeId=" + scopeId);
    }
    protected LlmRouteResolution resolveBootstrapRoute(String scene, String agentRole) {
        return resolveBootstrapRoute(scene, agentRole, null);
    }
    protected LlmRouteResolution resolveBootstrapRoute(String scene, String agentRole, String scopeId) {
        LlmRouteResolution bootstrapRoute;
        String normalizedRole = normalizeAgentRole(agentRole);
        if (executionLlmSnapshotService != null) {
            bootstrapRoute = executionLlmSnapshotService.bootstrapRoute(
                    normalizeScene(scene),
                    normalizedRole,
                    compileBootstrapBaseUrl,
                    compileBootstrapApiKey,
                    reviewBootstrapBaseUrl,
                    reviewBootstrapApiKey
            );
        }
        else {
            if (ROLE_REVIEWER.equals(normalizedRole)) {
                bootstrapRoute = createBootstrapReviewerRoute(scene, normalizedRole);
            }
            else {
                bootstrapRoute = createBootstrapCompileRoute(scene, normalizedRole);
            }
        }
        bootstrapRoute = replaceBootstrapModelNameIfNecessary(bootstrapRoute, normalizedRole);
        return attachScopeIfNecessary(bootstrapRoute, scene, scopeId);
    }
    /**
     * 在 bootstrap fallback 路由上补入真实模型名，避免把 provider 占位值误当成 ChatClient model。
     *
     * @param routeResolution 路由解析结果
     * @param agentRole Agent 角色
     * @return 路由解析结果
     */
    protected LlmRouteResolution replaceBootstrapModelNameIfNecessary(
            LlmRouteResolution routeResolution,
            String agentRole
    ) {
        if (routeResolution == null || routeResolution.isSnapshotBacked()) {
            return routeResolution;
        }
        String effectiveModelName = ROLE_REVIEWER.equals(agentRole)
                ? reviewBootstrapModelName
                : compileBootstrapModelName;
        if (effectiveModelName == null || effectiveModelName.isBlank()) {
            return routeResolution;
        }
        if (effectiveModelName.equals(routeResolution.getModelName())) {
            return routeResolution;
        }
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
                effectiveModelName,
                routeResolution.getTemperature(),
                routeResolution.getMaxTokens(),
                routeResolution.getTimeoutSeconds(),
                routeResolution.getExtraOptionsJson(),
                routeResolution.getInputPricePer1kTokens(),
                routeResolution.getOutputPricePer1kTokens(),
                routeResolution.isSnapshotBacked()
        );
    }
    /**
     * 返回 bootstrap fallback 的有效模型名。
     *
     * @param configuredModelName Spring AI 或运行时配置中的模型名
     * @param fallbackModelName 回退模型名
     * @return 有效模型名
     */
    protected String resolveBootstrapModelName(String configuredModelName, String fallbackModelName) {
        if (configuredModelName != null && !configuredModelName.isBlank()) {
            return configuredModelName.trim();
        }
        if (fallbackModelName != null && !fallbackModelName.isBlank()) {
            return fallbackModelName.trim();
        }
        return "";
    }
    protected LlmRouteResolution attachScopeIfNecessary(
            LlmRouteResolution routeResolution,
            String scene,
            String scopeId
    ) {
        if (routeResolution == null || scopeId == null || scopeId.isBlank()) {
            return routeResolution;
        }
        if (scopeId.equals(routeResolution.getScopeId())) {
            return routeResolution;
        }
        String effectiveScopeType = routeResolution.getScopeType();
        if (effectiveScopeType == null || effectiveScopeType.isBlank()) {
            effectiveScopeType = resolveScopeType(scene);
        }
        return new LlmRouteResolution(
                effectiveScopeType,
                scopeId,
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
                routeResolution.getExtraOptionsJson(),
                routeResolution.getInputPricePer1kTokens(),
                routeResolution.getOutputPricePer1kTokens(),
                routeResolution.isSnapshotBacked()
        );
    }
    protected LlmRouteResolution createBootstrapReviewerRoute(String scene, String normalizedRole) {
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
                Integer.valueOf(resolveBootstrapTimeoutSeconds(scene, normalizedRole)),
                "{}",
                llmProperties.getPricing().getReviewerInputPricePer1kTokens(),
                llmProperties.getPricing().getReviewerOutputPricePer1kTokens(),
                false
        );
    }
    protected LlmRouteResolution createBootstrapCompileRoute(String scene, String normalizedRole) {
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
                Integer.valueOf(resolveBootstrapTimeoutSeconds(scene, normalizedRole)),
                "{}",
                llmProperties.getPricing().getCompileInputPricePer1kTokens(),
                llmProperties.getPricing().getCompileOutputPricePer1kTokens(),
                false
        );
    }
    protected LlmClient resolveClient(LlmRouteResolution routeResolution) {
        if (routeResolution != null && routeResolution.isSnapshotBacked() && llmClientFactory != null) {
            return llmClientFactory.getClient(routeResolution);
        }
        if (routeResolution != null && ROLE_REVIEWER.equals(routeResolution.getAgentRole())) {
            return reviewClient;
        }
        return compileClient;
    }
    protected boolean supportsChatClientInvocation(LlmRouteResolution routeResolution) {
        if (routeResolution == null) {
            return false;
        }
        String providerType = normalizeProviderType(routeResolution.getProviderType());
        return "openai".equals(providerType)
                || "openai_compatible".equals(providerType)
                || "anthropic".equals(providerType);
    }

    /**
     * 规范化模型标识。
     *
     * @param modelName 原始模型标识
     * @return 规范化模型标识
     */
    protected String normalizeModelName(String modelName) {
        return modelName == null ? "unknown" : modelName.trim().toLowerCase(Locale.ROOT);
    }

    protected String resolveScopeType(String scene) {
        String normalizedScene = normalizeScene(scene);
        if (ExecutionLlmSnapshotService.COMPILE_SCENE.equals(normalizedScene)) {
            return ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE;
        }
        if (ExecutionLlmSnapshotService.QUERY_SCENE.equals(normalizedScene)) {
            return ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE;
        }
        if (ExecutionLlmSnapshotService.DEEP_RESEARCH_SCENE.equals(normalizedScene)) {
            return ExecutionLlmSnapshotService.DEEP_RESEARCH_SCOPE_TYPE;
        }
        return normalizedScene + "_scope";
    }
    protected String normalizeScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return ExecutionLlmSnapshotService.COMPILE_SCENE;
        }
        return scene.trim().toLowerCase(Locale.ROOT);
    }
    protected int resolveBootstrapTimeoutSeconds(String scene, String agentRole) {
        String normalizedScene = normalizeScene(scene);
        if (!ExecutionLlmSnapshotService.COMPILE_SCENE.equals(normalizedScene)) {
            return 300;
        }
        if (ROLE_WRITER.equals(agentRole)) {
            return llmProperties.getCompileTimeout().getWriterSeconds();
        }
        if (ROLE_REVIEWER.equals(agentRole)) {
            return llmProperties.getCompileTimeout().getReviewerSeconds();
        }
        if (ROLE_FIXER.equals(agentRole)) {
            return llmProperties.getCompileTimeout().getFixerSeconds();
        }
        return 300;
    }
    protected String normalizeAgentRole(String agentRole) {
        if (agentRole == null || agentRole.isBlank()) {
            return ROLE_WRITER;
        }
        return agentRole.trim().toLowerCase(Locale.ROOT);
    }
    protected String normalizeProviderType(String routeOrProvider) {
        String normalized = normalizeModelName(routeOrProvider);
        if (normalized.contains("anthropic") || normalized.contains("claude")) {
            return "anthropic";
        }
        if (normalized.contains("compatible")) {
            return "openai_compatible";
        }
        return "openai";
    }
    protected BigDecimal defaultIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
    protected String safeValue(String value) {
        return value == null ? "" : value;
    }
}
