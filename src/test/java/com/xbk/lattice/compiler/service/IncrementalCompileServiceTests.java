package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.model.MergedConcept;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IncrementalCompileService 测试
 *
 * 职责：验证增量编译的增强、新建与 LLM 匹配行为
 *
 * @author xiexu
 */
class IncrementalCompileServiceTests {

    /**
     * 验证概念命中已有文章时，会在保留旧来源的前提下增强旧文章。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldEnhanceExistingArticleWhenConceptIdAlreadyExists(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-timeout",
                "Payment Timeout",
                List.of("payment/base.json"),
                "现有支付超时规则",
                """
                        # Payment Timeout

                        现有支付超时规则

                        ## Timeout Rules
                        - retry=3
                        """
        ));
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        articleChunkJdbcRepository.replaceChunks("payment-timeout", List.of("retry=3"));
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("update.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"补充支付超时补偿策略\","
                        + "\"snippets\":[\"manual-review\"],"
                        + "\"sections\":[{\"heading\":\"Compensation\",\"content\":[\"manual-review\",\"retry=5\"],\"sources\":[\"payment/update.json#compensation\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord updatedArticle = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(1);
        assertThat(updatedArticle.getSourcePaths()).contains("payment/base.json", "payment/update.json");
        assertThat(updatedArticle.getContent()).contains("payment/base.json");
        assertThat(updatedArticle.getContent()).contains("payment/update.json");
        assertThat(updatedArticle.getContent()).contains("## 增量更新");
        assertThat(updatedArticle.getContent()).contains("manual-review");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("payment-timeout"))).contains("## 增量更新");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("payment-timeout"))).contains("manual-review");
        assertThat(synthesisArtifactsService.getLastConcepts()).hasSize(1);
    }

    /**
     * 验证未命中已有文章时，会新建文章并刷新合成产物。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldCreateNewArticleWhenNoExistingArticleMatches(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        Path refundDir = Files.createDirectories(tempDir.resolve("refund"));
        Files.writeString(
                refundDir.resolve("new.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"refund-status\",\"title\":\"Refund Status\",\"description\":\"退款状态流转说明\","
                        + "\"snippets\":[\"refund-created\"],"
                        + "\"sections\":[{\"heading\":\"Status Flow\",\"content\":[\"refund-created\",\"refund-paid\"],\"sources\":[\"refund/new.json#status-flow\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord createdArticle = articleJdbcRepository.findByConceptId("refund-status").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(1);
        assertThat(createdArticle.getTitle()).isEqualTo("Refund Status");
        assertThat(createdArticle.getSourcePaths()).containsExactly("refund/new.json");
        assertThat(createdArticle.getContent()).contains("# Refund Status");
        assertThat(createdArticle.getContent()).contains("refund/new.json");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("refund-status"))).contains("# Refund Status");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("refund-status"))).contains("refund-created");
        assertThat(synthesisArtifactsService.getLastConcepts()).hasSize(1);
    }

    /**
     * 验证 LLM 返回增强计划时，会优先按计划增强指定文章，而不是盲目新建。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldRespectLlmMatchPlanWhenEnhancingExistingArticle(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        articleJdbcRepository.upsert(createExistingArticle(
                "knowledge-overview",
                "Knowledge Overview",
                List.of("payment/overview.json"),
                "现有总览",
                """
                        # Knowledge Overview

                        现有总览
                        """
        ));
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        articleChunkJdbcRepository.replaceChunks("knowledge-overview", List.of("overview"));
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                createRoutingLlmGateway(),
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("update.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"refund-status\",\"title\":\"Refund Status\",\"description\":\"退款状态补充说明\","
                        + "\"snippets\":[\"refund-created\"],"
                        + "\"sections\":[{\"heading\":\"Refund Flow\",\"content\":[\"refund-created\",\"refund-paid\"],\"sources\":[\"payment/update.json#refund-flow\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord updatedArticle = articleJdbcRepository.findByConceptId("knowledge-overview").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(1);
        assertThat(updatedArticle.getSummary()).isEqualTo("更新后的知识总览");
        assertThat(updatedArticle.getSourcePaths()).contains("payment/overview.json", "payment/update.json");
        assertThat(updatedArticle.getConfidence()).isEqualTo("high");
        assertThat(updatedArticle.getReviewStatus()).isEqualTo("passed");
        assertThat(updatedArticle.getRelated()).contains("refund-status");
        assertThat(updatedArticle.getContent()).contains("新增退款状态说明");
        assertThat(articleJdbcRepository.findByConceptId("refund-status")).isEmpty();
    }

    /**
     * 创建编译配置。
     *
     * @return 编译配置
     */
    private CompilerProperties createCompilerProperties() {
        CompilerProperties compilerProperties = new CompilerProperties();
        compilerProperties.setIngestMaxChars(4096);
        compilerProperties.setBatchMaxChars(4096);
        return compilerProperties;
    }

    /**
     * 创建已有文章。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param summary 摘要
     * @param body 主体
     * @return 文章记录
     */
    private ArticleRecord createExistingArticle(
            String conceptId,
            String title,
            List<String> sourcePaths,
            String summary,
            String body
    ) {
        return new ArticleRecord(
                conceptId,
                title,
                buildArticleContent(title, summary, sourcePaths, body),
                "ACTIVE",
                OffsetDateTime.now(),
                sourcePaths,
                "{\"incremental\":false}",
                summary,
                List.of(),
                List.of(),
                List.of(),
                "medium",
                "pending"
        );
    }

    /**
     * 构建文章 Markdown。
     *
     * @param title 标题
     * @param summary 摘要
     * @param sourcePaths 来源路径
     * @param body 主体
     * @return Markdown 内容
     */
    private String buildArticleContent(String title, String summary, List<String> sourcePaths, String body) {
        return """
                ---
                title: "%s"
                summary: "%s"
                referential_keywords: []
                sources: %s
                depends_on: []
                related: []
                confidence: medium
                compiled_at: "%s"
                review_status: pending
                ---

                %s
                """.formatted(title, summary, formatYamlList(sourcePaths), OffsetDateTime.now(), body).trim();
    }

    /**
     * 格式化 YAML 行内列表。
     *
     * @param values 值列表
     * @return YAML 行内列表
     */
    private String formatYamlList(List<String> values) {
        List<String> escaped = new ArrayList<String>();
        for (String value : values) {
            escaped.add("\"" + value + "\"");
        }
        return "[" + String.join(", ", escaped) + "]";
    }

    /**
     * 创建可按 Prompt 路由结果的 LLM 网关。
     *
     * @return LLM 网关
     */
    private LlmGateway createRoutingLlmGateway() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return new LlmGateway(
                new RoutingLlmClient(),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                new NoopLlmUsageStore(),
                llmProperties
        );
    }

    /**
     * 路由式 LLM 客户端。
     *
     * 职责：按不同系统 Prompt 返回不同结果
     *
     * @author xiexu
     */
    private static class RoutingLlmClient implements LlmClient {

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            if (LatticePrompts.SYSTEM_INCREMENTAL_MATCH.equals(systemPrompt)) {
                return new LlmCallResult("""
                        {
                          "enhancements": [
                            {
                              "target_article_id": "knowledge-overview",
                              "new_info_summary": "补充退款状态说明",
                              "source_refs": ["payment/update.json"]
                            }
                          ],
                          "new_articles": []
                        }
                        """, 100, 50);
            }
            if (LatticePrompts.SYSTEM_INCREMENTAL_ENHANCE.equals(systemPrompt)) {
                return new LlmCallResult("""
                        ---
                        title: "Knowledge Overview"
                        summary: "更新后的知识总览"
                        referential_keywords: ["retry=5"]
                        sources: ["payment/overview.json", "payment/update.json"]
                        depends_on: ["compile-pipeline"]
                        related: ["refund-status"]
                        confidence: high
                        compiled_at: "2026-04-15T12:00:00Z"
                        review_status: passed
                        ---

                        # Knowledge Overview

                        新增退款状态说明
                        """, 200, 120);
            }
            return new LlmCallResult("", 10, 5);
        }
    }

    /**
     * 固定返回内容的 LLM 客户端。
     *
     * @author xiexu
     */
    private static class StaticLlmClient implements LlmClient {

        private final String content;

        private StaticLlmClient(String content) {
            this.content = content;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult(content, 10, 5);
        }
    }

    /**
     * 空操作 Redis 存储。
     *
     * @author xiexu
     */
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
    }

    /**
     * 空操作 usage 存储。
     *
     * @author xiexu
     */
    private static class NoopLlmUsageStore implements LlmUsageStore {

        @Override
        public void save(LlmUsageRecord llmUsageRecord) {
        }
    }

    /**
     * 文章仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> records = new LinkedHashMap<String, ArticleRecord>();

        private FakeArticleJdbcRepository() {
            super(null);
        }

        @Override
        public void upsert(ArticleRecord articleRecord) {
            records.put(articleRecord.getConceptId(), articleRecord);
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(records.get(conceptId));
        }

        public List<ArticleRecord> findAll() {
            return new ArrayList<ArticleRecord>(records.values());
        }
    }

    /**
     * 文章 chunk 仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeArticleChunkJdbcRepository extends ArticleChunkJdbcRepository {

        private final Map<String, List<String>> chunks = new LinkedHashMap<String, List<String>>();

        private FakeArticleChunkJdbcRepository() {
            super(null);
        }

        @Override
        public void replaceChunks(String conceptId, List<String> chunkTexts) {
            chunks.put(conceptId, new ArrayList<String>(chunkTexts));
        }

        @Override
        public List<String> findChunkTexts(String conceptId) {
            return chunks.getOrDefault(conceptId, List.of());
        }
    }

    /**
     * 源文件仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> records = new LinkedHashMap<String, SourceFileRecord>();

        private FakeSourceFileJdbcRepository() {
            super(null);
        }

        @Override
        public void upsert(SourceFileRecord sourceFileRecord) {
            records.put(sourceFileRecord.getFilePath(), sourceFileRecord);
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(records.get(filePath));
        }
    }

    /**
     * 合成产物服务测试替身。
     *
     * @author xiexu
     */
    private static class RecordingSynthesisArtifactsService extends SynthesisArtifactsService {

        private List<MergedConcept> lastConcepts = List.of();

        private RecordingSynthesisArtifactsService() {
            super(null, null);
        }

        @Override
        public void generateAll(List<MergedConcept> mergedConcepts) {
            lastConcepts = new ArrayList<MergedConcept>(mergedConcepts);
        }

        private List<MergedConcept> getLastConcepts() {
            return lastConcepts;
        }
    }
}
