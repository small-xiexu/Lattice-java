package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompilePipelineService 测试
 *
 * 职责：验证最小编译链路可将源目录内容编译并落入 articles 表
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_compile_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_compile_test",
        "spring.flyway.default-schema=lattice_b1_compile_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.compiler.ingest-max-chars=800",
        "lattice.compiler.batch-max-chars=10"
})
class CompilePipelineServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompilePipelineService compilePipelineService;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ArticleChunkJdbcRepository articleChunkJdbcRepository;

    /**
     * 验证源目录可按 groupKey 编译成 article 并落表。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldCompileSourcesIntoArticlesAndPersistThem(@TempDir Path tempDir) throws IOException {
        resetCompileTables();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Path fulfillmentDir = Files.createDirectories(tempDir.resolve("fulfillment"));
        Files.writeString(paymentDir.resolve("order.md"), "order-flow", StandardCharsets.UTF_8);
        Files.writeString(paymentDir.resolve("refund.md"), "refund-flow", StandardCharsets.UTF_8);
        Files.writeString(fulfillmentDir.resolve("fc.md"), "fc-routing", StandardCharsets.UTF_8);

        CompileResult compileResult = compilePipelineService.compile(tempDir);

        assertThat(compileResult.getPersistedCount()).isEqualTo(2);
        Integer sourceFileCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b1_compile_test.source_files",
                Integer.class
        );
        Optional<ArticleRecord> paymentArticle = articleJdbcRepository.findByConceptId("payment");
        Optional<ArticleRecord> fulfillmentArticle = articleJdbcRepository.findByConceptId("fulfillment");
        List<String> paymentChunks = articleChunkJdbcRepository.findChunkTexts("payment");
        List<String> fulfillmentChunks = articleChunkJdbcRepository.findChunkTexts("fulfillment");

        assertThat(sourceFileCount).isEqualTo(3);
        assertThat(paymentChunks).isNotEmpty();
        assertThat(fulfillmentChunks).isNotEmpty();
        assertThat(String.join("\n", paymentChunks)).contains("# Payment");
        assertThat(String.join("\n", paymentChunks)).contains("payment/order.md");
        assertThat(String.join("\n", fulfillmentChunks)).contains("# Fulfillment");
        assertThat(paymentArticle).isPresent();
        assertThat(paymentArticle.orElseThrow().getTitle()).isEqualTo("Payment");
        assertThat(paymentArticle.orElseThrow().getContent()).contains("---");
        assertThat(paymentArticle.orElseThrow().getContent()).contains("title: \"Payment\"");
        assertThat(paymentArticle.orElseThrow().getContent()).contains("review_status: passed");
        assertThat(paymentArticle.orElseThrow().getContent()).contains("payment/order.md");
        assertThat(paymentArticle.orElseThrow().getContent()).contains("payment/refund.md");
        assertThat(paymentArticle.orElseThrow().getSummary()).isNotBlank();

        assertThat(fulfillmentArticle).isPresent();
        assertThat(fulfillmentArticle.orElseThrow().getTitle()).isEqualTo("Fulfillment");
        assertThat(fulfillmentArticle.orElseThrow().getContent()).contains("title: \"Fulfillment\"");
        assertThat(fulfillmentArticle.orElseThrow().getContent()).contains("fulfillment/fc.md");
    }

    /**
     * 验证结构化分析结果会把 description 编译进文章摘要骨架。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldRenderDescriptionIntoStructuredArticleSkeleton(@TempDir Path tempDir) throws IOException {
        resetCompileTables();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"Handles payment timeout recovery\","
                        + "\"snippets\":[\"timeout retry\",\"timeout fallback\"],"
                        + "\"sections\":["
                        + "{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\",\"interval=30s\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]},"
                        + "{\"heading\":\"Fallback\",\"content\":[\"manual-review\"]}"
                        + "]"
                        + "}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = compilePipelineService.compile(tempDir);
        Optional<ArticleRecord> paymentTimeoutArticle = articleJdbcRepository.findByConceptId("payment-timeout");

        assertThat(compileResult.getPersistedCount()).isEqualTo(1);
        assertThat(paymentTimeoutArticle).isPresent();
        assertThat(paymentTimeoutArticle.orElseThrow().getTitle()).isEqualTo("Payment Timeout");
        assertThat(paymentTimeoutArticle.orElseThrow().getSourcePaths()).containsExactly("payment/analyze.json");
        assertThat(paymentTimeoutArticle.orElseThrow().getMetadataJson()).contains("Handles payment timeout recovery");
        assertThat(paymentTimeoutArticle.orElseThrow().getMetadataJson()).contains("sectionCount");
        assertThat(paymentTimeoutArticle.orElseThrow().getMetadataJson()).contains("2");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("summary: \"Handles payment timeout recovery\"");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("referential_keywords:");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("review_status: passed");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("## Timeout Rules");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("- retry=3");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("- interval=30s");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("> Sources: payment/analyze.json#timeout-rules");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("## Fallback");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("- manual-review");
        assertThat(paymentTimeoutArticle.orElseThrow().getContent()).contains("> Sources: payment/analyze.json#Fallback");
        assertThat(paymentTimeoutArticle.orElseThrow().getSummary()).isEqualTo("Handles payment timeout recovery");
        assertThat(paymentTimeoutArticle.orElseThrow().getReferentialKeywords()).contains("retry=3", "interval=30s");
        assertThat(paymentTimeoutArticle.orElseThrow().getConfidence()).isEqualTo("medium");
        assertThat(paymentTimeoutArticle.orElseThrow().getReviewStatus()).isEqualTo("passed");
    }

    /**
     * 验证编译完成后会生成四类合成产物。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldGenerateSynthesisArtifactsAfterCompile(@TempDir Path tempDir) throws IOException {
        resetCompileTables();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"Handles payment timeout recovery\","
                        + "\"snippets\":[\"timeout-a\"],"
                        + "\"sections\":[{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        compilePipelineService.compile(tempDir);

        Integer synthesisCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b1_compile_test.synthesis_artifacts",
                Integer.class
        );
        Integer repoSnapshotCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b1_compile_test.repo_snapshots",
                Integer.class
        );

        assertThat(synthesisCount).isEqualTo(4);
        assertThat(repoSnapshotCount).isEqualTo(1);
    }

    /**
     * 重置编译相关测试表，避免测试之间相互污染。
     */
    private void resetCompileTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_compile_test.repo_snapshot_items");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_compile_test.repo_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_compile_test.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_compile_test.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_compile_test.articles CASCADE");
    }
}
