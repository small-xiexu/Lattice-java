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
     * 验证基础 compile prompt 已明确禁止把证据不足的异常场景补写成事实。
     */
    @Test
    void baseCompilePromptShouldForbidSpeculativeAbnormalCaseCompletion() {
        assertThat(LatticePrompts.SYSTEM_COMPILE_ARTICLE)
                .contains("do NOT write a full expected result section from analogy")
                .contains("prefer omission plus evidence-gap disclosure over speculative completion");
        assertThat(LatticePrompts.SYSTEM_REVIEW)
                .contains("CHECK 5 — Speculative Abnormal Scenarios")
                .contains("flag this as a HIGH issue and require rewrite toward evidence-gap disclosure");
        assertThat(LatticePrompts.SYSTEM_REVIEW_FIX)
                .contains("源材料未直接给出结论/当前仅有上下文证据");
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

    /**
     * 验证 CompileArticleNode 会优先把结构化章节和 sourceRef 对应章节喂给 Writer。
     */
    @Test
    void compileArticleNodeShouldPreferStructuredSectionsInUserPrompt() {
        PromptCapturingLlmClient llmClient = new PromptCapturingLlmClient("""
                ---
                title: "Service Overview"
                summary: "desc"
                referential_keywords: []
                sources: ["docs/migration.md"]
                depends_on: []
                related: []
                confidence: medium
                compiled_at: "2026-04-16T11:00:00+08:00"
                review_status: pending
                ---

                # Service Overview
                """);
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                new LlmGateway(
                        llmClient,
                        llmClient,
                        new NoopRedisKeyValueStore(),
                        createLlmProperties()
                ),
                new FixedSourceFileJdbcRepository(new SourceFileRecord(
                        "docs/migration.md",
                        "summary",
                        "md",
                        700L,
                        """
                                # 总览
                                这里是文档开头概述。
                                ## 3. 服务影响总览表
                                | 服务 | 角色变化 |
                                | --- | --- |
                                | dpfm-callback-service | 新增 MQ 消费者 |
                                ## 4. 其他章节
                                其他信息
                                """,
                        "{}",
                        false,
                        "docs/migration.md"
                )),
                new DocumentSectionSelector(),
                null,
                null,
                new SchemaAwarePrompts(new CompilerProperties())
        );

        compileArticleNode.compile(new MergedConcept(
                "service-overview",
                "Service Overview",
                "desc",
                List.of("docs/migration.md"),
                List.of("dpfm-callback-service"),
                List.of(new com.xbk.lattice.compiler.domain.ConceptSection(
                        "3. 服务影响总览表",
                        List.of("| 服务 | 角色变化 |", "| dpfm-callback-service | 新增 MQ 消费者 |"),
                        List.of("docs/migration.md#3. 服务影响总览表")
                ))
        ), null);

        assertThat(llmClient.getLastUserPrompt()).contains("Structured concept sections (highest priority evidence):");
        assertThat(llmClient.getLastUserPrompt()).contains("=== Section: 3. 服务影响总览表 ===");
        assertThat(llmClient.getLastUserPrompt()).contains("docs/migration.md#3. 服务影响总览表");
        assertThat(llmClient.getLastUserPrompt()).contains("## 3. 服务影响总览表");
        assertThat(llmClient.getLastUserPrompt()).doesNotContain("## 4. 其他章节");
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

    private static final class PromptCapturingLlmClient implements LlmClient {

        private final String response;

        private String lastUserPrompt;

        private PromptCapturingLlmClient(String response) {
            this.response = response;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            this.lastUserPrompt = userPrompt;
            return new LlmCallResult(response, 120, 40);
        }

        private String getLastUserPrompt() {
            return lastUserPrompt;
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

        @Override
        public void deleteByPrefix(String keyPrefix) {
        }
    }

}
