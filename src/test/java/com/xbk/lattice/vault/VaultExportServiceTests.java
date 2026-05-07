package com.xbk.lattice.vault;

import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VaultExportService 测试
 *
 * 职责：验证文章、合成产物与贡献可导出为 Vault 文件树
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
class VaultExportServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ContributionJdbcRepository contributionJdbcRepository;

    @Autowired
    private SynthesisArtifactJdbcStore synthesisArtifactJdbcStore;

    @Autowired
    private VaultExportService vaultExportService;

    @Autowired
    private SourceService sourceService;

    /**
     * 验证 Vault 导出会写出文章、合成产物、贡献与 manifest。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExportManagedVault(@TempDir Path tempDir) throws Exception {
        resetTables();
        Long sourceId = createManagedSourceId();
        articleJdbcRepository.upsert(new ArticleRecord(
                sourceId,
                "payments-docs--payment-timeout",
                "payment-timeout",
                "Payment Timeout",
                """
                        ---
                        title: "Payment Timeout"
                        review_status: pending
                        ---

                        # Payment Timeout
                        """,
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T17:00:00+08:00"),
                List.of("payment/a.md"),
                "{\"description\":\"payment summary\"}",
                "payment summary",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                "pending"
        ));
        contributionJdbcRepository.save(new ContributionRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "payment timeout?",
                "retry=3",
                "{}",
                "tester",
                OffsetDateTime.parse("2026-04-16T17:10:00+08:00")
        ));
        synthesisArtifactJdbcStore.save(new SynthesisArtifactRecord(
                "index",
                "Knowledge Index",
                "# Index",
                OffsetDateTime.parse("2026-04-16T17:20:00+08:00")
        ));

        VaultExportResult result = vaultExportService.export(tempDir);

        assertThat(result.getWrittenFiles()).isGreaterThanOrEqualTo(3);
        assertThat(Files.exists(tempDir.resolve("concepts/payments-docs--payment-timeout.md"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("concepts/payments-docs--payment-timeout.md"), StandardCharsets.UTF_8))
                .contains("# Payment Timeout");
        assertThat(Files.exists(tempDir.resolve("index.md"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("index.md"), StandardCharsets.UTF_8)).contains("# Index");
        assertThat(Files.list(tempDir.resolve("_contributions")).count()).isEqualTo(1L);
        assertThat(Files.exists(tempDir.resolve("_meta/README.md"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("_meta/export-manifest.json"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("_meta/export-manifest.json"), StandardCharsets.UTF_8))
                .doesNotContain("exportedAt");
        assertThat(Files.exists(tempDir.resolve(".git"))).isTrue();
    }

    /**
     * 验证当磁盘文件已漂移但 manifest 仍指向旧内容时，导出会强制重写受管文件。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldRewriteDriftedManagedFileWhenManifestHashMatches(@TempDir Path tempDir) throws Exception {
        resetTables();
        Long sourceId = createManagedSourceId();
        articleJdbcRepository.upsert(new ArticleRecord(
                sourceId,
                "payments-docs--payment-timeout",
                "payment-timeout",
                "Payment Timeout",
                "# Payment Timeout\n\nretry=3\n",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T17:30:00+08:00"),
                List.of("payment/a.md"),
                "{\"description\":\"payment summary\"}",
                "payment summary",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                "pending"
        ));

        Path vaultDir = tempDir.resolve("vault");
        Path articlePath = vaultDir.resolve("concepts/payments-docs--payment-timeout.md");

        vaultExportService.export(vaultDir);
        Files.writeString(articlePath, "# Payment Timeout\n\nretry=9\n", StandardCharsets.UTF_8);

        VaultExportResult result = vaultExportService.export(vaultDir);

        assertThat(result.getWrittenFiles()).isGreaterThanOrEqualTo(1);
        assertThat(Files.readString(articlePath, StandardCharsets.UTF_8)).contains("retry=3");
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
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
