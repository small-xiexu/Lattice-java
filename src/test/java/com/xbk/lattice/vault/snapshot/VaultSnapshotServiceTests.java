package com.xbk.lattice.vault.snapshot;

import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.governance.repo.RepoRollbackResult;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import com.xbk.lattice.vault.VaultExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VaultSnapshotService 测试
 *
 * 职责：验证整库级 diff 与 rollback 能回放 DB 并同步 Vault Git
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b9_vault_snapshot_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b9_vault_snapshot_test",
        "spring.flyway.default-schema=lattice_b9_vault_snapshot_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class VaultSnapshotServiceTests {

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
    private VaultGitService vaultGitService;

    @Autowired
    private RepoSnapshotService repoSnapshotService;

    @Autowired
    private RepoSnapshotJdbcRepository repoSnapshotJdbcRepository;

    @Autowired
    private VaultSnapshotService vaultSnapshotService;

    /**
     * 验证可基于 repo snapshot 计算 Vault diff，并执行整库 rollback。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldDiffAndRollbackRepoSnapshot(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path vaultDir = tempDir.resolve("vault");

        seedBaselineState();
        vaultExportService.export(vaultDir);
        String baselineCommitId = vaultGitService.commitAll(vaultDir, "[lattice:manual] baseline");
        RepoSnapshotRecord baselineSnapshot = repoSnapshotService.snapshot("manual", "baseline", baselineCommitId);

        seedMutatedState();
        vaultExportService.export(vaultDir);
        String mutatedCommitId = vaultGitService.commitAll(vaultDir, "[lattice:manual] mutated");

        List<VaultDiffSummary> diffSummaries = vaultSnapshotService.diff(vaultDir, baselineSnapshot.getId());
        RepoRollbackResult rollbackResult = vaultSnapshotService.rollback(vaultDir, baselineSnapshot.getId());
        List<RepoSnapshotRecord> latestSnapshots = repoSnapshotJdbcRepository.findRecent(5);

        assertThat(mutatedCommitId).isNotBlank();
        assertThat(diffSummaries).extracting(VaultDiffSummary::getFilePath)
                .contains("concepts/payment-timeout.md", "index.md");
        assertThat(rollbackResult.getRestoredSnapshotId()).isEqualTo(baselineSnapshot.getId());
        assertThat(articleJdbcRepository.findByConceptId("payment-timeout")).isPresent();
        assertThat(articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow().getContent())
                .contains("retry=3");
        assertThat(contributionJdbcRepository.findAll()).hasSize(1);
        assertThat(contributionJdbcRepository.findAll().get(0).getAnswer()).isEqualTo("retry=3");
        assertThat(synthesisArtifactJdbcStore.findAll()).extracting(SynthesisArtifactRecord::getContent)
                .contains("# Index baseline");
        assertThat(vaultGitService.headCommitId(vaultDir)).isNotBlank();
        assertThat(latestSnapshots.get(0).getTriggerEvent()).isEqualTo("rollback");
    }

    private void seedBaselineState() {
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                "# Payment Timeout\n\nretry=3\n",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T18:00:00+08:00"),
                List.of("payment/a.md"),
                "{\"description\":\"baseline\"}",
                "baseline",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                "passed"
        ));
        contributionJdbcRepository.save(new ContributionRecord(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "payment timeout?",
                "retry=3",
                "{}",
                "tester",
                OffsetDateTime.parse("2026-04-16T18:10:00+08:00")
        ));
        synthesisArtifactJdbcStore.save(new SynthesisArtifactRecord(
                "index",
                "Knowledge Index",
                "# Index baseline",
                OffsetDateTime.parse("2026-04-16T18:20:00+08:00")
        ));
    }

    private void seedMutatedState() {
        articleJdbcRepository.deleteAll();
        contributionJdbcRepository.deleteAll();
        synthesisArtifactJdbcStore.deleteAll();

        articleJdbcRepository.upsert(new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                "# Payment Timeout\n\nretry=5\n",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T19:00:00+08:00"),
                List.of("payment/a.md"),
                "{\"description\":\"mutated\"}",
                "mutated",
                List.of("retry=5"),
                List.of(),
                List.of(),
                "high",
                "needs_human_review"
        ));
        contributionJdbcRepository.save(new ContributionRecord(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "payment timeout?",
                "retry=5",
                "{}",
                "tester",
                OffsetDateTime.parse("2026-04-16T19:10:00+08:00")
        ));
        synthesisArtifactJdbcStore.save(new SynthesisArtifactRecord(
                "index",
                "Knowledge Index",
                "# Index mutated",
                OffsetDateTime.parse("2026-04-16T19:20:00+08:00")
        ));
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_vault_snapshot_test.repo_snapshot_items");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_vault_snapshot_test.repo_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_vault_snapshot_test.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_vault_snapshot_test.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_vault_snapshot_test.articles CASCADE");
    }
}
