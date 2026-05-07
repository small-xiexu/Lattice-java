package com.xbk.lattice.vault;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.service.SourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VaultSyncService 测试
 *
 * 职责：验证 concepts/*.md 的受控回写与冲突检测
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class VaultSyncServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private SynthesisArtifactJdbcStore synthesisArtifactJdbcStore;

    @Autowired
    private VaultExportService vaultExportService;

    @Autowired
    private VaultSyncService vaultSyncService;

    @Autowired
    private SourceService sourceService;

    /**
     * 验证概念文章修改后可受控回写到数据库。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldSyncConceptMarkdownBackToDatabase(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path vaultDir = tempDir.resolve("vault");
        Long sourceId = createManagedSourceId();
        seedArticle(sourceId, "# Payment Timeout\n\nretry=3\n", "Payment Timeout");
        seedArtifact("index", "# Index baseline\n");
        vaultExportService.export(vaultDir);

        Path articleFile = vaultDir.resolve("concepts/payments-docs--payment-timeout.md");
        Files.writeString(
                articleFile,
                """
                        ---
                        title: "Payment Timeout Updated"
                        summary: "updated summary"
                        confidence: "high"
                        depends_on: ["order-timeout"]
                        related: ["payment-retry"]
                        referential_keywords: ["retry=5"]
                        review_status: "passed"
                        ---

                        # Payment Timeout

                        retry=5
                        """,
                StandardCharsets.UTF_8
        );

        VaultSyncResult result = vaultSyncService.sync(vaultDir, false);
        ArticleRecord updatedRecord = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();

        assertThat(result.getSyncedFiles()).isEqualTo(1);
        assertThat(result.getConflictCount()).isZero();
        assertThat(updatedRecord.getTitle()).isEqualTo("Payment Timeout Updated");
        assertThat(updatedRecord.getSummary()).isEqualTo("updated summary");
        assertThat(updatedRecord.getConfidence()).isEqualTo("high");
        assertThat(updatedRecord.getDependsOn()).containsExactly("order-timeout");
        assertThat(updatedRecord.getRelated()).containsExactly("payment-retry");
        assertThat(updatedRecord.getReferentialKeywords()).containsExactly("retry=5");
        assertThat(updatedRecord.getReviewStatus()).isEqualTo("pending");
        assertThat(updatedRecord.getContent()).contains("review_status: \"pending\"");
        assertThat(updatedRecord.getContent()).contains("retry=5");
    }

    /**
     * 验证合成产物修改后可受控回写到数据库。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldSyncArtifactMarkdownBackToDatabase(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path vaultDir = tempDir.resolve("vault");
        Long sourceId = createManagedSourceId();
        seedArticle(sourceId, "# Payment Timeout\n\nretry=3\n", "Payment Timeout");
        seedArtifact("index", "# Index baseline\n");
        vaultExportService.export(vaultDir);

        Files.writeString(
                vaultDir.resolve("index.md"),
                "# Index updated\n\nmanual patch\n",
                StandardCharsets.UTF_8
        );

        VaultSyncResult result = vaultSyncService.sync(vaultDir, false);
        SynthesisArtifactRecord updatedRecord = synthesisArtifactJdbcStore.findAll().stream()
                .filter(item -> "index".equals(item.getArtifactType()))
                .findFirst()
                .orElseThrow();

        assertThat(result.getSyncedFiles()).isEqualTo(1);
        assertThat(updatedRecord.getTitle()).isEqualTo("Index updated");
        assertThat(updatedRecord.getContent()).contains("manual patch");
    }

    /**
     * 验证 DB 与文件都发生变化时会报告冲突。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldReportConflictWhenDatabaseChangedAfterExport(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path vaultDir = tempDir.resolve("vault");
        Long sourceId = createManagedSourceId();
        seedArticle(sourceId, "# Payment Timeout\n\nretry=3\n", "Payment Timeout");
        vaultExportService.export(vaultDir);

        articleJdbcRepository.upsert(new ArticleRecord(
                sourceId,
                "payments-docs--payment-timeout",
                "payment-timeout",
                "Payment Timeout DB",
                "# Payment Timeout\n\nretry=4\n",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T22:00:00+08:00"),
                List.of("payment/a.md"),
                "{\"description\":\"db-mutated\"}",
                "db mutated",
                List.of("retry=4"),
                List.of(),
                List.of(),
                "medium",
                "pending"
        ));
        Files.writeString(
                vaultDir.resolve("concepts/payments-docs--payment-timeout.md"),
                """
                        ---
                        title: "Payment Timeout File"
                        ---

                        # Payment Timeout

                        retry=5
                        """,
                StandardCharsets.UTF_8
        );

        VaultSyncResult result = vaultSyncService.sync(vaultDir, false);

        assertThat(result.getSyncedFiles()).isZero();
        assertThat(result.getConflictCount()).isEqualTo(1);
        assertThat(result.getConflicts().get(0).getFilePath()).isEqualTo("concepts/payments-docs--payment-timeout.md");
        assertThat(articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow().getTitle()).isEqualTo("Payment Timeout DB");
    }

    private void seedArticle(Long sourceId, String contentBody, String title) {
        articleJdbcRepository.upsert(new ArticleRecord(
                sourceId,
                "payments-docs--payment-timeout",
                "payment-timeout",
                title,
                """
                        ---
                        title: "%s"
                        summary: "baseline"
                        referential_keywords: ["retry=3"]
                        source_paths: ["payment/a.md"]
                        depends_on: []
                        related: []
                        confidence: "medium"
                        review_status: "pending"
                        ---

                        %s
                        """.formatted(title, contentBody.strip()),
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T21:00:00+08:00"),
                List.of("payment/a.md"),
                "{\"description\":\"baseline\"}",
                "baseline",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                "pending"
        ));
    }

    private void seedArtifact(String artifactType, String content) {
        synthesisArtifactJdbcStore.save(new SynthesisArtifactRecord(
                artifactType,
                "Index baseline",
                content,
                OffsetDateTime.parse("2026-04-16T21:05:00+08:00")
        ));
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.knowledge_sources RESTART IDENTITY CASCADE");
    }

    private Long createManagedSourceId() {
        KnowledgeSource source = sourceService.save(new KnowledgeSource(
                null,
                "payments-docs",
                "Payments Docs",
                "UPLOAD",
                "DOCUMENT",
                "ACTIVE",
                "NORMAL",
                "AUTO",
                "{}",
                "{}",
                null,
                null,
                null,
                null,
                null,
                null
        ));
        return source.getId();
    }
}
