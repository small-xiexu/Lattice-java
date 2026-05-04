package com.xbk.lattice.llm.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * LLM 端点地址解析器。
 *
 * 职责：统一把用户填写的任意基础地址归一化为聊天、模型探测与 embedding 所需的稳定基址和端点路径
 *
 * @author xiexu
 */
@Component
public class LlmEndpointUrlResolver {

    /**
     * 规范化基础地址。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return 去除已知端点后得到的稳定基址
     */
    public String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "";
        }
        String normalized = rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String lowerCaseUrl = normalized.toLowerCase(Locale.ROOT);
        String[] knownSuffixes = {
                "/v1/chat/completions",
                "/chat/completions",
                "/v1/messages",
                "/messages",
                "/api/tags",
                "/tags",
                "/v1/models",
                "/models",
                "/api/paas/v4/embeddings",
                "/v1/embeddings",
                "/embeddings"
        };
        for (String knownSuffix : knownSuffixes) {
            if (lowerCaseUrl.endsWith(knownSuffix)) {
                return normalized.substring(0, normalized.length() - knownSuffix.length());
            }
        }
        return normalized;
    }

    /**
     * 解析聊天基础地址。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return 可直接传给聊天客户端的基础地址
     */
    public String resolveChatBaseUrl(String rawBaseUrl) {
        String normalizedRawUrl = trimTrailingSlash(rawBaseUrl);
        String lowerCaseRawUrl = normalizedRawUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseRawUrl.endsWith("/v1/chat/completions")
                || lowerCaseRawUrl.endsWith("/chat/completions")) {
            return normalizedRawUrl.substring(0, normalizedRawUrl.length() - "/chat/completions".length());
        }
        String normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl);
        String lowerCaseUrl = normalizedBaseUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl;
        }
        if (lowerCaseUrl.endsWith("/api/paas/v4")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - "/api/paas/v4".length());
        }
        return normalizedBaseUrl;
    }

    /**
     * 解析聊天 completions 路径。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return Chat Completions 路径
     */
    public String resolveChatCompletionPath(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "/v1/chat/completions";
        }
        String normalized = trimTrailingSlash(rawBaseUrl);
        String lowerCaseUrl = normalized.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/chat/completions")) {
            return "/chat/completions";
        }
        if (lowerCaseUrl.endsWith("/v1")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    /**
     * 解析模型列表探测基础地址。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return 模型列表探测使用的基础地址
     */
    public String resolveModelsBaseUrl(String rawBaseUrl) {
        String normalizedRawUrl = trimTrailingSlash(rawBaseUrl);
        String lowerCaseRawUrl = normalizedRawUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseRawUrl.endsWith("/v1/models") || lowerCaseRawUrl.endsWith("/models")) {
            return normalizedRawUrl.substring(0, normalizedRawUrl.length() - "/models".length());
        }
        String normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl);
        String lowerCaseUrl = normalizedBaseUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/api/paas/v4")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - "/api/paas/v4".length());
        }
        return normalizedBaseUrl;
    }

    /**
     * 解析模型列表探测路径。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return 模型列表探测路径
     */
    public String resolveModelsPath(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "/v1/models";
        }
        String normalized = trimTrailingSlash(rawBaseUrl);
        String lowerCaseUrl = normalized.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/models")) {
            return "/models";
        }
        if (lowerCaseUrl.endsWith("/v1")) {
            return "/models";
        }
        return "/v1/models";
    }

    /**
     * 解析 embedding 基础地址。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return embedding 使用的基础地址
     */
    public String resolveEmbeddingBaseUrl(String rawBaseUrl) {
        String normalizedRawUrl = trimTrailingSlash(rawBaseUrl);
        String lowerCaseRawUrl = normalizedRawUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseRawUrl.endsWith("/api/paas/v4/embeddings")
                || lowerCaseRawUrl.endsWith("/v1/embeddings")
                || lowerCaseRawUrl.endsWith("/embeddings")) {
            return normalizedRawUrl.substring(0, normalizedRawUrl.length() - "/embeddings".length());
        }
        String normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl);
        String lowerCaseUrl = normalizedBaseUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl;
        }
        if (lowerCaseUrl.endsWith("/api/paas/v4")) {
            return normalizedBaseUrl;
        }
        return normalizedBaseUrl;
    }

    /**
     * 解析 embedding 路径。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return embedding 路径
     */
    public String resolveEmbeddingsPath(String rawBaseUrl) {
        return "/embeddings";
    }

    /**
     * 解析 embedding 基址候选列表。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return embedding 基址候选列表
     */
    public List<String> resolveEmbeddingBaseUrlCandidates(String rawBaseUrl) {
        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        String resolvedBaseUrl = resolveEmbeddingBaseUrl(rawBaseUrl);
        if (StringUtils.hasText(resolvedBaseUrl)) {
            candidates.add(resolvedBaseUrl);
            String lowerCaseUrl = resolvedBaseUrl.toLowerCase(Locale.ROOT);
            if (!lowerCaseUrl.endsWith("/v1")) {
                candidates.add(resolvedBaseUrl + "/v1");
            }
            if (!lowerCaseUrl.endsWith("/api/paas/v4")) {
                candidates.add(resolvedBaseUrl + "/api/paas/v4");
            }
        }
        return new ArrayList<String>(candidates);
    }

    /**
     * 判断当前地址是否应走 embedding 专用路径。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return 是否命中 embedding 专用口径
     */
    public boolean usesDedicatedEmbeddingEndpoint(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return false;
        }
        String lowerCaseUrl = rawBaseUrl.trim().toLowerCase(Locale.ROOT);
        return lowerCaseUrl.contains("/api/paas/v4")
                || lowerCaseUrl.endsWith("/embeddings")
                || lowerCaseUrl.endsWith("/v1/embeddings");
    }

    /**
     * 解析 Anthropic 基础地址。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return Anthropic 基础地址
     */
    public String resolveAnthropicBaseUrl(String rawBaseUrl) {
        String normalizedRawUrl = trimTrailingSlash(rawBaseUrl);
        String lowerCaseRawUrl = normalizedRawUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseRawUrl.endsWith("/v1/messages")) {
            return normalizedRawUrl.substring(0, normalizedRawUrl.length() - "/v1/messages".length());
        }
        if (lowerCaseRawUrl.endsWith("/messages")) {
            return normalizedRawUrl.substring(0, normalizedRawUrl.length() - "/messages".length());
        }
        if (lowerCaseRawUrl.endsWith("/v1/models")) {
            return normalizedRawUrl.substring(0, normalizedRawUrl.length() - "/v1/models".length());
        }
        if (lowerCaseRawUrl.endsWith("/models")) {
            return normalizedRawUrl.substring(0, normalizedRawUrl.length() - "/models".length());
        }
        String normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl);
        String lowerCaseUrl = normalizedBaseUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/anthropic")) {
            return normalizedBaseUrl;
        }
        if (lowerCaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - "/v1".length());
        }
        return normalizedBaseUrl;
    }

    /**
     * 解析 Anthropic models 路径。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return Anthropic models 路径
     */
    public String resolveAnthropicModelsPath(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "/v1/models";
        }
        String normalized = trimTrailingSlash(rawBaseUrl);
        String lowerCaseUrl = normalized.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/models")) {
            return "/models";
        }
        if (lowerCaseUrl.endsWith("/anthropic") || lowerCaseUrl.endsWith("/v1")) {
            return "/v1/models";
        }
        return "/v1/models";
    }

    /**
     * 解析 Anthropic messages 基础地址。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return Anthropic messages 基础地址
     */
    public String resolveAnthropicMessagesBaseUrl(String rawBaseUrl) {
        return resolveAnthropicBaseUrl(rawBaseUrl);
    }

    /**
     * 解析 Anthropic messages 路径。
     *
     * @param rawBaseUrl 用户填写的原始地址
     * @return Anthropic messages 路径
     */
    public String resolveAnthropicMessagesPath(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "/v1/messages";
        }
        String normalized = trimTrailingSlash(rawBaseUrl);
        String lowerCaseUrl = normalized.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith("/messages")) {
            return "/messages";
        }
        return "/v1/messages";
    }

    private String trimTrailingSlash(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "";
        }
        String normalized = rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
