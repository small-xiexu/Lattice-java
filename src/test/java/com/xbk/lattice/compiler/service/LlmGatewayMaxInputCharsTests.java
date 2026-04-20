package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmGateway 最大输入长度测试
 *
 * 职责：验证超长输入会在调用 LLM 前被截断，避免超出单次调用窗口
 *
 * @author xiexu
 */
class LlmGatewayMaxInputCharsTests {

    /**
     * 验证超出默认输入上限时，会截断用户提示词再调用 LLM。
     */
    @Test
    void shouldTruncateUserPromptWhenInputExceedsDefaultLimit() {
        CapturingLlmClient compileClient = new CapturingLlmClient("compiled");
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new CapturingLlmClient("review"),
                new FakeRedisKeyValueStore(),
                createProperties()
        );
        String systemPrompt = "system";
        String userPrompt = "x".repeat(70000);

        String result = llmGateway.compile(systemPrompt, userPrompt);

        assertThat(result).isEqualTo("compiled");
        assertThat(compileClient.getLastUserPrompt()).contains("[... 内容已截断，超出单次调用字符限制 ...]");
        assertThat(compileClient.getLastUserPrompt().length()).isLessThan(userPrompt.length());
    }

    /**
     * 创建默认 LLM 配置。
     *
     * @return LLM 配置
     */
    private LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:cache:");
        return llmProperties;
    }

    /**
     * 记录最后一次提示词的 LLM 客户端替身。
     *
     * 职责：捕获 LlmGateway 实际传给下游客户端的提示词内容
     *
     * @author xiexu
     */
    private static class CapturingLlmClient implements LlmClient {

        private final String content;

        private String lastUserPrompt;

        /**
         * 创建捕获型客户端。
         *
         * @param content 固定返回内容
         */
        private CapturingLlmClient(String content) {
            this.content = content;
        }

        /**
         * 执行调用并记录最后一次用户提示词。
         *
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 固定调用结果
         */
        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            this.lastUserPrompt = userPrompt;
            return new LlmCallResult(content, 120, 30);
        }

        /**
         * 返回最后一次用户提示词。
         *
         * @return 用户提示词
         */
        private String getLastUserPrompt() {
            return lastUserPrompt;
        }
    }

    /**
     * Redis 键值存储测试替身。
     *
     * 职责：记录缓存值与 TTL
     *
     * @author xiexu
     */
    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        private final Map<String, Long> ttlSeconds = new LinkedHashMap<String, Long>();

        /**
         * 读取缓存值。
         *
         * @param key 缓存键
         * @return 缓存值
         */
        @Override
        public String get(String key) {
            return values.get(key);
        }

        /**
         * 写入缓存值。
         *
         * @param key 缓存键
         * @param value 缓存值
         * @param ttl 过期时间
         */
        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
            ttlSeconds.put(key, ttl.toSeconds());
        }

        /**
         * 读取缓存 TTL。
         *
         * @param key 缓存键
         * @return TTL 秒数
         */
        @Override
        public Long getExpire(String key) {
            return ttlSeconds.get(key);
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
            ttlSeconds.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }
}
