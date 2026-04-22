package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReviewFixService 测试
 *
 * 职责：验证 review-fix 会走 fixer 文本生成入口
 *
 * @author xiexu
 */
class ReviewFixServiceTests {

    /**
     * 验证未传 scope 时，review-fix 会默认使用 compile/fixer 路由执行文本生成。
     */
    @Test
    void shouldUseCompileFixerRouteForUnscopedReviewFix() {
        RecordingLlmGateway llmGateway = new RecordingLlmGateway();
        ReviewFixService reviewFixService = new ReviewFixService(llmGateway);

        String fixedContent = reviewFixService.applyFix(
                "# 原文",
                List.of(new ReviewIssue("missing_referential", "HIGH", "缺少重试次数来源")),
                "payment/source.md => retry=5"
        );

        assertThat(fixedContent).isEqualTo("fixed-markdown");
        assertThat(llmGateway.generateTextCallCount).isEqualTo(1);
        assertThat(llmGateway.lastScene).isEqualTo("compile");
        assertThat(llmGateway.lastAgentRole).isEqualTo("fixer");
        assertThat(llmGateway.lastPurpose).isEqualTo("review-fix");
    }

    /**
     * 记录参数的网关替身。
     *
     * 职责：验证 ReviewFixService 实际走的是哪个文本生成入口
     *
     * @author xiexu
     */
    private static class RecordingLlmGateway extends LlmGateway {

        private int generateTextCallCount;

        private String lastScene;

        private String lastAgentRole;

        private String lastPurpose;

        private RecordingLlmGateway() {
            super(
                    new NoOpLlmClient(),
                    new NoOpLlmClient(),
                    new NoOpRedisKeyValueStore(),
                    createProperties()
            );
        }

        @Override
        public String generateText(
                String scene,
                String agentRole,
                String purpose,
                String systemPrompt,
                String userPrompt
        ) {
            generateTextCallCount++;
            lastScene = scene;
            lastAgentRole = agentRole;
            lastPurpose = purpose;
            return "fixed-markdown";
        }
    }

    /**
     * 空操作客户端。
     *
     * 职责：满足父类构造器签名
     *
     * @author xiexu
     */
    private static class NoOpLlmClient implements LlmClient {

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult("", 0, 0);
        }
    }

    /**
     * 空操作 Redis 存储。
     *
     * 职责：满足父类构造器签名
     *
     * @author xiexu
     */
    private static class NoOpRedisKeyValueStore implements RedisKeyValueStore {

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
            return null;
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }

    /**
     * 创建测试配置。
     *
     * @return LLM 配置
     */
    private static LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:cache:");
        return llmProperties;
    }
}
