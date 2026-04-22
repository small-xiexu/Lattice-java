package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmReviewerGateway 测试
 *
 * 职责：验证 Query reviewer 会优先走 raw facade，并在异常时回退到本地规则审查
 *
 * @author xiexu
 */
class LlmReviewerGatewayTests {

    @Test
    void shouldUseLlmGatewayRawFacadeWhenReviewIsEnabled() throws Exception {
        LlmReviewerGateway reviewerGateway = new LlmReviewerGateway(
                createGateway(new StaticLlmClient("""
                        {"approved":true,"rewriteRequired":false,"riskLevel":"LOW","issues":[],"userFacingRewriteHints":[],"cacheWritePolicy":"WRITE"}
                        """), true),
                new LocalReviewerGateway(),
                new ReviewResultParser()
        );

        String reviewResult = reviewerGateway.review(
                "query-1",
                "query",
                "reviewer",
                "question=payment timeout retry=3\nanswer=retry=3\nsources=payment/context.md"
        );

        assertThat(reviewResult).contains("\"approved\":true");
    }

    @Test
    void shouldFallbackToLocalReviewerWhenRawInvocationFails() throws Exception {
        LlmReviewerGateway reviewerGateway = new LlmReviewerGateway(
                createGateway(new FailingLlmClient(), true),
                new LocalReviewerGateway(),
                new ReviewResultParser()
        );

        String reviewResult = reviewerGateway.review(
                "query-1",
                "query",
                "reviewer",
                "question=payment timeout retry=3\nanswer=retry=3\nsources=payment/context.md"
        );

        assertThat(reviewResult).contains("\"approved\":true");
    }

    /**
     * 验证 reviewer 在返回 WRITE 时会复用 L1 prompt cache。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldWritePromptCacheWhenReviewPayloadAllowsCaching() throws Exception {
        StaticLlmClient llmClient = new StaticLlmClient("""
                {"approved":true,"rewriteRequired":false,"riskLevel":"LOW","issues":[],"userFacingRewriteHints":[],"cacheWritePolicy":"WRITE"}
                """);
        LlmReviewerGateway reviewerGateway = new LlmReviewerGateway(
                createGateway(llmClient, true),
                new LocalReviewerGateway(),
                new ReviewResultParser()
        );

        reviewerGateway.review(
                "query-1",
                "query",
                "reviewer",
                "question=payment timeout retry=3\nanswer=retry=3\nsources=payment/context.md"
        );
        reviewerGateway.review(
                "query-1",
                "query",
                "reviewer",
                "question=payment timeout retry=3\nanswer=retry=3\nsources=payment/context.md"
        );

        assertThat(llmClient.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证 reviewer 在返回 SKIP_WRITE 时不会复用 L1 prompt cache。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldSkipPromptCacheWhenReviewPayloadDisablesCaching() throws Exception {
        StaticLlmClient llmClient = new StaticLlmClient("""
                {"approved":false,"rewriteRequired":true,"riskLevel":"HIGH","issues":[{"severity":"HIGH","category":"FACT","description":"结论缺少证据"}],"userFacingRewriteHints":["请补齐来源"],"cacheWritePolicy":"SKIP_WRITE"}
                """);
        LlmReviewerGateway reviewerGateway = new LlmReviewerGateway(
                createGateway(llmClient, true),
                new LocalReviewerGateway(),
                new ReviewResultParser()
        );

        reviewerGateway.review(
                "query-1",
                "query",
                "reviewer",
                "question=payment timeout retry=3\nanswer=retry=3\nsources=payment/context.md"
        );
        reviewerGateway.review(
                "query-1",
                "query",
                "reviewer",
                "question=payment timeout retry=3\nanswer=retry=3\nsources=payment/context.md"
        );

        assertThat(llmClient.getCallCount()).isEqualTo(2);
    }

    private LlmGateway createGateway(LlmClient llmClient, boolean reviewEnabled) throws Exception {
        Constructor<LlmGateway> constructor = LlmGateway.class.getDeclaredConstructor(
                LlmClient.class,
                LlmClient.class,
                RedisKeyValueStore.class,
                LlmProperties.class
        );
        constructor.setAccessible(true);
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:cache:");
        llmProperties.setReviewEnabled(reviewEnabled);
        return constructor.newInstance(
                llmClient,
                llmClient,
                new FakeRedisKeyValueStore(),
                llmProperties
        );
    }

    private static class StaticLlmClient implements LlmClient {

        private final String content;

        private int callCount;

        private StaticLlmClient(String content) {
            this.content = content;
            this.callCount = 0;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            callCount++;
            return new LlmCallResult(content, 128, 32);
        }

        /**
         * 返回调用次数。
         *
         * @return 调用次数
         */
        private int getCallCount() {
            return callCount;
        }
    }

    private static class FailingLlmClient implements LlmClient {

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            throw new IllegalStateException("boom");
        }
    }

    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public Long getExpire(String key) {
            return 1L;
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }
}
