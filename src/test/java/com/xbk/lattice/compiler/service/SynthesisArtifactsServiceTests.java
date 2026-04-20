package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SynthesisArtifactsService 测试
 *
 * 职责：验证四类合成产物会生成并保存
 *
 * @author xiexu
 */
class SynthesisArtifactsServiceTests {

    /**
     * 验证会生成并保存四类合成产物。
     */
    @Test
    void shouldGenerateAndStoreAllSynthesisArtifacts() {
        FakeSynthesisArtifactStore synthesisArtifactStore = new FakeSynthesisArtifactStore();
        SynthesisArtifactsService synthesisArtifactsService = new SynthesisArtifactsService(
                createLlmGateway("generated-content"),
                synthesisArtifactStore
        );

        synthesisArtifactsService.generateAll(List.of(
                new MergedConcept("payment-timeout", "Payment Timeout", "Handles timeout recovery", List.of("payment/a.md"), List.of("snippet"))
        ));

        assertThat(synthesisArtifactStore.records).hasSize(4);
        assertThat(synthesisArtifactStore.records)
                .extracting(SynthesisArtifactRecord::getArtifactType)
                .containsExactly("index", "timeline", "tradeoffs", "gaps");
        assertThat(synthesisArtifactStore.records)
                .extracting(SynthesisArtifactRecord::getContent)
                .allMatch(content -> content.equals("generated-content"));
    }

    /**
     * 验证 LLM 失败时，会回退为确定性内容。
     */
    @Test
    void shouldFallbackWhenLlmGenerationFails() {
        FakeSynthesisArtifactStore synthesisArtifactStore = new FakeSynthesisArtifactStore();
        SynthesisArtifactsService synthesisArtifactsService = new SynthesisArtifactsService(
                createFailingGateway(),
                synthesisArtifactStore
        );

        synthesisArtifactsService.generateAll(List.of(
                new MergedConcept("payment-timeout", "Payment Timeout", "Handles timeout recovery", List.of("payment/a.md"), List.of("snippet"))
        ));

        assertThat(synthesisArtifactStore.records).hasSize(4);
        assertThat(synthesisArtifactStore.records.get(0).getContent()).contains("# Knowledge Base Index");
        assertThat(synthesisArtifactStore.records.get(0).getContent()).contains("Payment Timeout");
    }

    private LlmGateway createLlmGateway(String compileResponse) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return new LlmGateway(
                new StaticLlmClient(compileResponse),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                llmProperties
        );
    }

    private LlmGateway createFailingGateway() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return new LlmGateway(
                new FailingLlmClient(),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                llmProperties
        );
    }

    private static class StaticLlmClient implements LlmClient {

        private final String content;

        private StaticLlmClient(String content) {
            this.content = content;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult(content, 100, 50);
        }
    }

    private static class FailingLlmClient implements LlmClient {

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            throw new IllegalStateException("llm failed");
        }
    }

    private static class NoopRedisKeyValueStore implements RedisKeyValueStore {

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
        }
    }

    private static class FakeSynthesisArtifactStore implements SynthesisArtifactStore {

        private final List<SynthesisArtifactRecord> records = new ArrayList<SynthesisArtifactRecord>();

        @Override
        public void save(SynthesisArtifactRecord synthesisArtifactRecord) {
            records.add(synthesisArtifactRecord);
        }
    }
}
