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
 * LLM 网关
 *
 * 职责：统一封装编译/审查模型路由、缓存与预算守卫
 *
 * @author xiexu
 */
@Service
@Slf4j
public class LlmGateway extends LlmGatewayInvocationSupport {

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
     * @param structuredEventLogger 结构化事件日志器
     */
    @Autowired
    public LlmGateway(
            OpenAiChatModel openAiChatModel,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.base-url:}") String openAiBaseUrl,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String openAiChatOptionsModel,
            AnthropicConnectionProperties anthropicConnectionProperties,
            AnthropicChatProperties anthropicChatProperties,
            RedisKeyValueStore redisKeyValueStore,
            LlmProperties llmProperties,
            LlmClientFactory llmClientFactory,
            ExecutionLlmSnapshotService executionLlmSnapshotService,
            LlmInvocationExecutor llmInvocationExecutor,
            StructuredEventLogger structuredEventLogger
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
                llmInvocationExecutor,
                openAiBaseUrl,
                openAiApiKey,
                anthropicConnectionProperties.getBaseUrl(),
                anthropicConnectionProperties.getApiKey(),
                structuredEventLogger
        );
        this.compileBootstrapModelName = resolveBootstrapModelName(openAiChatOptionsModel, this.compileBootstrapModelName);
        String anthropicModelName = anthropicChatProperties.getOptions() == null
                ? ""
                : anthropicChatProperties.getOptions().getModel();
        this.reviewBootstrapModelName = resolveBootstrapModelName(anthropicModelName, this.reviewBootstrapModelName);
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
                null,
                "",
                "",
                "",
                "",
                null
        );
    }
    /**
     * 创建 LLM 网关（带结构化日志的测试构造器）。
     *
     * @param compileClient 编译模型客户端
     * @param reviewClient 审查模型客户端
     * @param redisKeyValueStore Redis 键值存储
     * @param llmProperties LLM 配置
     * @param structuredEventLogger 结构化事件日志器
     */
    LlmGateway(
            LlmClient compileClient,
            LlmClient reviewClient,
            RedisKeyValueStore redisKeyValueStore,
            LlmProperties llmProperties,
            StructuredEventLogger structuredEventLogger
    ) {
        this(
                compileClient,
                reviewClient,
                redisKeyValueStore,
                llmProperties,
                null,
                null,
                null,
                "",
                "",
                "",
                "",
                structuredEventLogger
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
     * @param structuredEventLogger 结构化事件日志器
     */
    LlmGateway(
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
     * 执行文本生成调用，并保留编译路径的 L1 prompt cache 写入语义。
     *
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出文本
     */
    public String generateText(
            String scene,
            String agentRole,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        LlmRouteResolution routeResolution = resolveBootstrapRoute(scene, agentRole);
        return invokeText(routeResolution, purpose, systemPrompt, userPrompt);
    }
    /**
     * 在指定作用域下执行文本生成调用，并保留编译路径的 L1 prompt cache 写入语义。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
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
        LlmRouteResolution routeResolution = resolveScopedRoute(scopeId, scene, agentRole);
        return invokeText(routeResolution, purpose, systemPrompt, userPrompt);
    }
    /**
     * 执行最小 raw 调用，并返回保留路由与 token 元数据的调用信封。
     *
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 调用信封
     */
    public LlmInvocationEnvelope invokeRaw(
            String scene,
            String agentRole,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        LlmRouteResolution routeResolution = resolveBootstrapRoute(scene, agentRole);
        return invokeRaw(routeResolution, purpose, systemPrompt, userPrompt, null);
    }
    /**
     * 在指定作用域下执行最小 raw 调用，并返回保留路由与 token 元数据的调用信封。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param purpose 调用用途
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 调用信封
     */
    public LlmInvocationEnvelope invokeRawWithScope(
            String scopeId,
            String scene,
            String agentRole,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        LlmRouteResolution routeResolution = resolveScopedRoute(scopeId, scene, agentRole);
        return invokeRaw(routeResolution, purpose, systemPrompt, userPrompt, null);
    }
    /**
     * 按策略处理 L1 prompt cache。
     *
     * @param envelope 调用信封
     * @param promptCacheWritePolicy prompt cache 写策略
     */
    public void applyPromptCacheWritePolicy(
            LlmInvocationEnvelope envelope,
            PromptCacheWritePolicy promptCacheWritePolicy
    ) {
        if (envelope == null || promptCacheWritePolicy == null) {
            return;
        }
        String cacheKey = envelope.getCacheKey();
        if (cacheKey == null || cacheKey.isBlank()) {
            return;
        }
        if (promptCacheWritePolicy == PromptCacheWritePolicy.EVICT_AFTER_READ) {
            evictPromptCacheKey(cacheKey);
            return;
        }
        if (promptCacheWritePolicy == PromptCacheWritePolicy.SKIP_WRITE || envelope.isPromptCacheHit()) {
            return;
        }
        String content = envelope.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        writePromptCache(cacheKey, content);
    }
    /**
     * 清理全部 L1 prompt cache。
     */
    public void evictPromptCache() {
        String cacheKeyPrefix = llmProperties.getCacheKeyPrefix();
        if (cacheKeyPrefix == null || cacheKeyPrefix.isBlank()) {
            return;
        }
        redisKeyValueStore.deleteByPrefix(cacheKeyPrefix);
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
     * 返回指定场景与角色的当前路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由标签
     */
    public String routeFor(String scopeId, String scene, String agentRole) {
        return resolveScopedRoute(scopeId, scene, agentRole).getRouteLabel();
    }
    /**
     * 返回指定场景与角色当前实际命中的路由。
     *
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由解析结果
     */
    public LlmRouteResolution routeResolution(String scene, String agentRole) {
        return resolveBootstrapRoute(scene, agentRole);
    }
    /**
     * 返回指定作用域、场景与角色当前实际命中的路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由解析结果
     */
    public LlmRouteResolution routeResolutionFor(String scopeId, String scene, String agentRole) {
        return resolveScopedRoute(scopeId, scene, agentRole);
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
     * 返回当前预算累计消耗。
     *
     * @return 已消耗金额
     */
    double getSpentUsd() {
        synchronized (budgetLock) {
            return spentUsd;
        }
    }
}
