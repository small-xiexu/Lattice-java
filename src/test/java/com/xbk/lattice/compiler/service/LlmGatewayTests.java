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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LlmGateway 测试
 *
 * 职责：验证模型路由、缓存与预算守卫
 *
 * @author xiexu
 */
class LlmGatewayTests {

    /**
     * 验证 compile 会路由到编译模型，并写入缓存。
     */
    @Test
    void shouldRouteCompileCallsToCompileClientAndCacheResult() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeLlmClient reviewClient = new FakeLlmClient("review-result", 60, 10);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                reviewClient,
                redisKeyValueStore,
                createProperties()
        );

        String result = llmGateway.compile("system-a", "user-a");

        assertThat(result).isEqualTo("compiled-article");
        assertThat(compileClient.getCallCount()).isEqualTo(1);
        assertThat(reviewClient.getCallCount()).isZero();
        assertThat(redisKeyValueStore.values).hasSize(1);
    }

    /**
     * 验证 review 会路由到审查模型。
     */
    @Test
    void shouldRouteReviewCallsToReviewClient() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeLlmClient reviewClient = new FakeLlmClient("{\"passed\":true,\"issues\":[]}", 80, 20);
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                reviewClient,
                new FakeRedisKeyValueStore(),
                createProperties()
        );

        String result = llmGateway.review("system-r", "user-r");

        assertThat(result).contains("\"passed\":true");
        assertThat(compileClient.getCallCount()).isZero();
        assertThat(reviewClient.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证缓存命中时不会重复调用模型。
     */
    @Test
    void shouldReuseCachedResponseWithoutCallingModelAgain() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 120, 30);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                redisKeyValueStore,
                createProperties()
        );

        String first = llmGateway.compile("system-a", "user-a");
        String second = llmGateway.compile("system-a", "user-a");

        assertThat(first).isEqualTo("compiled-article");
        assertThat(second).isEqualTo("compiled-article");
        assertThat(compileClient.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证超出预算会阻止后续调用。
     */
    @Test
    void shouldRejectCallsWhenBudgetIsExceeded() {
        FakeLlmClient compileClient = new FakeLlmClient("compiled-article", 700000, 700000);
        LlmProperties llmProperties = createProperties();
        llmProperties.setBudgetUsd(0.5D);
        LlmGateway llmGateway = new LlmGateway(
                compileClient,
                new FakeLlmClient("review-result", 60, 10),
                new FakeRedisKeyValueStore(),
                llmProperties
        );

        assertThatThrownBy(() -> llmGateway.compile("system-a", "user-a"))
                .isInstanceOf(BudgetExceededException.class);
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
     * LLM 客户端测试替身。
     *
     * 职责：返回固定结果，并统计调用次数
     *
     * @author xiexu
     */
    private static class FakeLlmClient implements LlmClient {

        private final String content;

        private final int inputTokens;

        private final int outputTokens;

        private int callCount;

        private FakeLlmClient(String content, int inputTokens, int outputTokens) {
            this.content = content;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.callCount = 0;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            callCount++;
            return new LlmCallResult(content, inputTokens, outputTokens);
        }

        private int getCallCount() {
            return callCount;
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

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
            ttlSeconds.put(key, ttl.toSeconds());
        }

        @Override
        public Long getExpire(String key) {
            return ttlSeconds.get(key);
        }
    }
}
