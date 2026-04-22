package com.xbk.lattice.llm.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmInvocationEnvelope 测试
 *
 * 职责：验证 migrated path 调用信封会保留原始文本、路由与 token 元数据
 *
 * @author xiexu
 */
class LlmInvocationEnvelopeTests {

    /**
     * 验证可从模型调用结果构造最小调用信封。
     */
    @Test
    void shouldCreateEnvelopeFromCallResult() {
        LlmRouteResolution routeResolution = new LlmRouteResolution(
                "query_request",
                "query-1",
                "query",
                "answer",
                Long.valueOf(11L),
                Long.valueOf(22L),
                Integer.valueOf(3),
                "query.answer.openai",
                "openai",
                "https://api.openai.test",
                "test-key",
                "gpt-5.4",
                new BigDecimal("0.2"),
                Integer.valueOf(512),
                Integer.valueOf(30),
                "{\"reasoning_effort\":\"medium\"}",
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                true
        );
        LlmCallResult llmCallResult = new LlmCallResult("{\"approved\":true}", 128, 32);

        LlmInvocationEnvelope envelope = LlmInvocationEnvelope.from(
                llmCallResult.getContent(),
                "query-review",
                "llm:cache:test",
                routeResolution,
                llmCallResult,
                88L
        );

        assertThat(envelope.getContent()).isEqualTo("{\"approved\":true}");
        assertThat(envelope.getPurpose()).isEqualTo("query-review");
        assertThat(envelope.getCacheKey()).isEqualTo("llm:cache:test");
        assertThat(envelope.getRouteResolution()).isSameAs(routeResolution);
        assertThat(envelope.getInputTokens()).isEqualTo(128);
        assertThat(envelope.getOutputTokens()).isEqualTo(32);
        assertThat(envelope.getLatencyMs()).isEqualTo(88L);
        assertThat(envelope.isPromptCacheHit()).isFalse();
    }

    /**
     * 验证 prompt cache 命中时会生成零 token 的缓存信封。
     */
    @Test
    void shouldCreateCachedEnvelope() {
        LlmRouteResolution routeResolution = new LlmRouteResolution(
                "query_request",
                "query-1",
                "query",
                "answer",
                Long.valueOf(11L),
                Long.valueOf(22L),
                Integer.valueOf(3),
                "query.answer.openai",
                "openai",
                "https://api.openai.test",
                "test-key",
                "gpt-5.4",
                new BigDecimal("0.2"),
                Integer.valueOf(512),
                Integer.valueOf(30),
                "{\"reasoning_effort\":\"medium\"}",
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                true
        );

        LlmInvocationEnvelope envelope = LlmInvocationEnvelope.cached(
                "{\"approved\":true}",
                "query-review",
                "llm:cache:test",
                routeResolution
        );

        assertThat(envelope.getContent()).isEqualTo("{\"approved\":true}");
        assertThat(envelope.getPurpose()).isEqualTo("query-review");
        assertThat(envelope.getCacheKey()).isEqualTo("llm:cache:test");
        assertThat(envelope.getInputTokens()).isZero();
        assertThat(envelope.getOutputTokens()).isZero();
        assertThat(envelope.getLatencyMs()).isZero();
        assertThat(envelope.isPromptCacheHit()).isTrue();
    }
}
