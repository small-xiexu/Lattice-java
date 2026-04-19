package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.node.AnalyzeNode;
import com.xbk.lattice.compiler.node.CompileArticleNode;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SchemaAwarePrompts 测试
 *
 * 职责：验证 SCHEMA.md 会叠加到 Analyze/Compile 的 system prompt 中
 *
 * @author xiexu
 */
class SchemaAwarePromptsTests {

    /**
     * 验证存在 SCHEMA.md 时会把规则附加到 analyze prompt。
     */
    @Test
    void shouldAppendSchemaContentWhenSchemaFileExists(@TempDir Path tempDir) throws Exception {
        Files.writeString(
                tempDir.resolve("SCHEMA.md"),
                "Always emphasize payment-domain terminology.",
                StandardCharsets.UTF_8
        );
        SchemaAwarePrompts schemaAwarePrompts = new SchemaAwarePrompts(new CompilerProperties());

        String prompt = schemaAwarePrompts.getAnalyzePrompt(tempDir);

        assertThat(prompt).contains(LatticePrompts.SYSTEM_ANALYZE.trim().substring(0, 20));
        assertThat(prompt).contains("User-Defined Schema Rules");
        assertThat(prompt).contains("Always emphasize payment-domain terminology.");
    }

    /**
     * 验证不存在 SCHEMA.md 时保持原始 prompt。
     */
    @Test
    void shouldReturnBasePromptWhenSchemaFileIsMissing(@TempDir Path tempDir) {
        SchemaAwarePrompts schemaAwarePrompts = new SchemaAwarePrompts(new CompilerProperties());

        String prompt = schemaAwarePrompts.getCompileArticlePrompt(tempDir);

        assertThat(prompt).isEqualTo(LatticePrompts.SYSTEM_COMPILE_ARTICLE);
    }

    /**
     * 验证 AnalyzeNode 会使用 schema-aware analyze prompt。
     */
    @Test
    void analyzeNodeShouldUseSchemaAwarePrompt(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("SCHEMA.md"), "Prefer payment-specific naming.", StandardCharsets.UTF_8);
        CapturingLlmClient llmClient = new CapturingLlmClient("""
                {"concepts":[{"id":"payment-timeout","title":"Payment Timeout","description":"desc","sources":[],"relationships":[]}]}
                """);
        AnalyzeNode analyzeNode = new AnalyzeNode(
                new LlmGateway(
                        llmClient,
                        llmClient,
                        new NoopRedisKeyValueStore(),
                        createLlmProperties()
                ),
                new SchemaAwarePrompts(new CompilerProperties())
        );

        analyzeNode.analyze("payment-service", List.of(
                new SourceBatch("batch-1", "payment-service", List.of(
                        RawSource.text("payment/order.md", "timeout retry rule", "md", 18L)
                ))
        ), tempDir);

        assertThat(llmClient.getLastSystemPrompt()).contains("Prefer payment-specific naming.");
    }

    /**
     * 验证 CompileArticleNode 会使用 schema-aware compile prompt。
     */
    @Test
    void compileArticleNodeShouldUseSchemaAwarePrompt(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("SCHEMA.md"), "Write summaries in a compliance-first style.", StandardCharsets.UTF_8);
        CapturingLlmClient llmClient = new CapturingLlmClient("""
                ---
                title: "Payment Timeout"
                summary: "desc"
                referential_keywords: []
                sources: ["payment/order.md"]
                depends_on: []
                related: []
                confidence: medium
                compiled_at: "2026-04-16T11:00:00+08:00"
                review_status: pending
                ---

                # Payment Timeout
                """);
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                new LlmGateway(
                        llmClient,
                        llmClient,
                        new NoopRedisKeyValueStore(),
                        createLlmProperties()
                ),
                new FixedSourceFileJdbcRepository(new SourceFileRecord(
                        "payment/order.md",
                        "timeout",
                        "md",
                        7L,
                        "# Timeout\nretry=3",
                        "{}",
                        false,
                        "payment/order.md"
                )),
                new DocumentSectionSelector(),
                null,
                null,
                new SchemaAwarePrompts(new CompilerProperties())
        );

        compileArticleNode.compile(new MergedConcept(
                "payment-timeout",
                "Payment Timeout",
                "desc",
                List.of("payment/order.md"),
                List.of("retry=3")
        ), tempDir);

        assertThat(llmClient.getLastSystemPrompt()).contains("Write summaries in a compliance-first style.");
    }

    private LlmProperties createLlmProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return llmProperties;
    }

    private static class CapturingLlmClient implements LlmClient {

        private final String response;

        private String lastSystemPrompt;

        private CapturingLlmClient(String response) {
            this.response = response;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            this.lastSystemPrompt = systemPrompt;
            return new LlmCallResult(response, 120, 40);
        }

        private String getLastSystemPrompt() {
            return lastSystemPrompt;
        }
    }

    private static class FixedSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> records = new LinkedHashMap<String, SourceFileRecord>();

        private FixedSourceFileJdbcRepository(SourceFileRecord record) {
            super(new JdbcTemplate());
            this.records.put(record.getFilePath(), record);
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(records.get(filePath));
        }
    }

    private static class NoopRedisKeyValueStore implements RedisKeyValueStore {

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            // 无操作
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }
    }

}
