package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.domain.ReviewStatus;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import com.xbk.lattice.query.service.ReviewResultParser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文章审查网关测试
 *
 * 职责：验证 compile reviewer 会复用 raw facade、统一 payload 语义与 prompt cache 策略
 *
 * @author xiexu
 */
class ArticleReviewerGatewayTests {

    /**
     * 验证 compile reviewer 在 payload 允许时会写入 L1 prompt cache。
     */
    @Test
    void shouldWritePromptCacheWhenCompileReviewPayloadAllowsCaching() {
        StaticLlmClient llmClient = new StaticLlmClient("""
                {"approved":true,"rewriteRequired":false,"riskLevel":"LOW","issues":[],"userFacingRewriteHints":[],"cacheWritePolicy":"WRITE"}
                """);
        ArticleReviewerGateway articleReviewerGateway = new ArticleReviewerGateway(
                createGateway(llmClient, true),
                new ReviewResultParser(),
                createProperties(true),
                new RuleBasedArticleReviewer()
        );

        ReviewResult first = articleReviewerGateway.review(validArticle(), validSources(), "job-1", "compile", "reviewer");
        ReviewResult second = articleReviewerGateway.review(validArticle(), validSources(), "job-1", "compile", "reviewer");

        assertThat(first.getStatus()).isEqualTo(ReviewStatus.PASSED);
        assertThat(second.getStatus()).isEqualTo(ReviewStatus.PASSED);
        assertThat(llmClient.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证 compile reviewer 在 payload 返回 SKIP_WRITE 时不会复用 prompt cache。
     */
    @Test
    void shouldSkipPromptCacheWhenCompileReviewPayloadDisablesCaching() {
        StaticLlmClient llmClient = new StaticLlmClient("""
                {"approved":false,"rewriteRequired":true,"riskLevel":"HIGH","issues":[{"severity":"HIGH","category":"MISSING_REFERENTIAL","description":"缺少 retry=3"}],"userFacingRewriteHints":["补齐 retry=3"],"cacheWritePolicy":"SKIP_WRITE"}
                """);
        ArticleReviewerGateway articleReviewerGateway = new ArticleReviewerGateway(
                createGateway(llmClient, true),
                new ReviewResultParser(),
                createProperties(true),
                new RuleBasedArticleReviewer()
        );

        ReviewResult first = articleReviewerGateway.review(validArticle(), validSources(), "job-1", "compile", "reviewer");
        ReviewResult second = articleReviewerGateway.review(validArticle(), validSources(), "job-1", "compile", "reviewer");

        assertThat(first.getStatus()).isEqualTo(ReviewStatus.ISSUES_FOUND);
        assertThat(second.getStatus()).isEqualTo(ReviewStatus.ISSUES_FOUND);
        assertThat(llmClient.getCallCount()).isEqualTo(2);
    }

    /**
     * 验证 compile reviewer raw 调用失败时会回退到规则审查。
     */
    @Test
    void shouldFallbackToRuleBasedReviewerWhenRawInvocationFails() {
        ArticleReviewerGateway articleReviewerGateway = new ArticleReviewerGateway(
                createGateway(new FailingLlmClient(), true),
                new ReviewResultParser(),
                createProperties(true),
                new RuleBasedArticleReviewer()
        );

        ReviewResult reviewResult = articleReviewerGateway.review(validArticle(), validSources(), "job-1", "compile", "reviewer");

        assertThat(reviewResult.isPass()).isTrue();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.PASSED);
    }

    /**
     * 创建测试用 LLM 网关。
     *
     * @param llmClient LLM 客户端
     * @param reviewEnabled 是否启用审查
     * @return LLM 网关
     */
    private LlmGateway createGateway(LlmClient llmClient, boolean reviewEnabled) {
        return new LlmGateway(
                llmClient,
                llmClient,
                new FakeRedisKeyValueStore(),
                createProperties(reviewEnabled)
        );
    }

    /**
     * 创建测试用 LLM 配置。
     *
     * @param reviewEnabled 是否启用审查
     * @return LLM 配置
     */
    private LlmProperties createProperties(boolean reviewEnabled) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:cache:");
        llmProperties.setReviewEnabled(reviewEnabled);
        return llmProperties;
    }

    /**
     * 返回可通过规则审查的测试文章。
     *
     * @return 测试文章
     */
    private String validArticle() {
        return """
                ---
                title: "Payment Timeout"
                summary: "Handles payment timeout recovery"
                sources: ["payment/analyze.json"]
                review_status: pending
                ---

                # Payment Timeout

                retry=3
                """;
    }

    /**
     * 返回可通过规则审查的测试源文。
     *
     * @return 测试源文
     */
    private String validSources() {
        return "payment/analyze.json => retry=3";
    }

    private static class StaticLlmClient implements LlmClient {

        private final String content;

        private int callCount;

        private StaticLlmClient(String content) {
            this.content = content;
            this.callCount = 0;
        }

        /**
         * 返回固定测试结果。
         *
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 调用结果
         */
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

        /**
         * 抛出固定异常。
         *
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 不返回
         */
        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            throw new IllegalStateException("boom");
        }
    }

    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        /**
         * 返回缓存值。
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
        }

        /**
         * 返回固定 TTL。
         *
         * @param key 缓存键
         * @return TTL
         */
        @Override
        public Long getExpire(String key) {
            return 1L;
        }

        /**
         * 按前缀删除缓存。
         *
         * @param keyPrefix 前缀
         */
        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }
}
