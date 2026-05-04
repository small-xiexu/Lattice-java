package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
                .containsExactlyInAnyOrder("index", "timeline", "tradeoffs", "gaps");
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
        SynthesisArtifactRecord indexRecord = findRecord(synthesisArtifactStore.records, "index");
        assertThat(indexRecord.getContent()).contains("# Knowledge Base Index");
        assertThat(indexRecord.getContent()).contains("Payment Timeout");
    }

    /**
     * 验证带作用域的合成产物生成会复用 compile job 的冻结路由。
     */
    @Test
    void shouldGenerateSynthesisArtifactsWithScopedRoute() {
        FakeSynthesisArtifactStore synthesisArtifactStore = new FakeSynthesisArtifactStore();
        RecordingScopedGateway llmGateway = new RecordingScopedGateway();
        SynthesisArtifactsService synthesisArtifactsService = new SynthesisArtifactsService(
                llmGateway,
                synthesisArtifactStore
        );

        synthesisArtifactsService.generateAll(
                "job-123",
                List.of(new MergedConcept(
                        "payment-timeout",
                        "Payment Timeout",
                        "Handles timeout recovery",
                        List.of("payment/a.md"),
                        List.of("snippet")
                ))
        );

        assertThat(synthesisArtifactStore.records).hasSize(4);
        assertThat(llmGateway.getScopedInvocations()).isEqualTo(4);
        assertThat(llmGateway.getBootstrapInvocations()).isZero();
    }

    /**
     * 按产物类型查找保存记录。
     *
     * @param records 保存记录列表
     * @param artifactType 产物类型
     * @return 匹配的保存记录
     */
    private SynthesisArtifactRecord findRecord(List<SynthesisArtifactRecord> records, String artifactType) {
        for (SynthesisArtifactRecord record : records) {
            if (artifactType.equals(record.getArtifactType())) {
                return record;
            }
        }
        throw new AssertionError("missing artifact record: " + artifactType);
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

    private static class RecordingScopedGateway extends LlmGateway {

        private int scopedInvocations;

        private int bootstrapInvocations;

        private RecordingScopedGateway() {
            super(
                    new StaticLlmClient("ignored"),
                    new StaticLlmClient("{}"),
                    new NoopRedisKeyValueStore(),
                    createStaticLlmProperties()
            );
        }

        /**
         * 记录不带作用域的调用。
         *
         * @param scene 场景
         * @param agentRole Agent 角色
         * @param purpose 调用用途
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 固定文本
         */
        @Override
        public String generateText(
                String scene,
                String agentRole,
                String purpose,
                String systemPrompt,
                String userPrompt
        ) {
            bootstrapInvocations++;
            return "bootstrap";
        }

        /**
         * 记录带作用域的调用。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @param purpose 调用用途
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 固定文本
         */
        @Override
        public String generateTextWithScope(
                String scopeId,
                String scene,
                String agentRole,
                String purpose,
                String systemPrompt,
                String userPrompt
        ) {
            scopedInvocations++;
            return "scoped";
        }

        private int getScopedInvocations() {
            return scopedInvocations;
        }

        private int getBootstrapInvocations() {
            return bootstrapInvocations;
        }

        private static LlmProperties createStaticLlmProperties() {
            LlmProperties llmProperties = new LlmProperties();
            llmProperties.setCompileModel("openai");
            llmProperties.setReviewerModel("anthropic");
            llmProperties.setBudgetUsd(10.0D);
            llmProperties.setCacheTtlSeconds(3600L);
            llmProperties.setCacheKeyPrefix("llm:test:");
            return llmProperties;
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

        private final List<SynthesisArtifactRecord> records = new CopyOnWriteArrayList<SynthesisArtifactRecord>();

        @Override
        public void save(SynthesisArtifactRecord synthesisArtifactRecord) {
            records.add(synthesisArtifactRecord);
        }
    }
}
