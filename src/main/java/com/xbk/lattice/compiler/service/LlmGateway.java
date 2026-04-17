package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;

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

    private final LlmClient compileClient;

    private final LlmClient reviewClient;

    private final RedisKeyValueStore redisKeyValueStore;

    private final LlmProperties llmProperties;

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
     */
    @Autowired
    public LlmGateway(
            OpenAiChatModel openAiChatModel,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AnthropicConnectionProperties anthropicConnectionProperties,
            AnthropicChatProperties anthropicChatProperties,
            RedisKeyValueStore redisKeyValueStore,
            LlmProperties llmProperties
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
                llmProperties
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
        this.compileClient = compileClient;
        this.reviewClient = reviewClient;
        this.redisKeyValueStore = redisKeyValueStore;
        this.llmProperties = llmProperties;
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
        return invoke(
                compileClient,
                normalizeModelName(llmProperties.getCompileModel()),
                purpose,
                systemPrompt,
                userPrompt
        );
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
        return invoke(
                reviewClient,
                normalizeModelName(llmProperties.getReviewerModel()),
                purpose,
                systemPrompt,
                userPrompt
        );
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
            String modelName,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        String cacheKey = buildCacheKey(modelName, systemPrompt, userPrompt);
        String cachedValue = redisKeyValueStore.get(cacheKey);
        if (cachedValue != null && !cachedValue.isBlank()) {
            return cachedValue;
        }
        ensureBudgetAvailable();
        String truncatedUserPrompt = truncateUserPromptIfNecessary(systemPrompt, userPrompt, purpose);
        LlmCallResult llmCallResult = llmClient.call(systemPrompt, truncatedUserPrompt);
        double estimatedCost = estimateCostUsd(modelName, llmCallResult);
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
     * @param modelName 模型标识
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return Redis 缓存键
     */
    private String buildCacheKey(String modelName, String systemPrompt, String userPrompt) {
        return llmProperties.getCacheKeyPrefix() + sha256(modelName + "|" + systemPrompt + "|" + userPrompt);
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
     * @param modelName 模型标识
     * @param llmCallResult 调用结果
     * @return 估算成本
     */
    private double estimateCostUsd(String modelName, LlmCallResult llmCallResult) {
        double inputRatePerMillion = modelName.contains("anthropic") ? 3.0D : 0.55D;
        double outputRatePerMillion = modelName.contains("anthropic") ? 15.0D : 2.19D;
        double inputCost = llmCallResult.getInputTokens() * inputRatePerMillion / 1_000_000D;
        double outputCost = llmCallResult.getOutputTokens() * outputRatePerMillion / 1_000_000D;
        return inputCost + outputCost;
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
}
