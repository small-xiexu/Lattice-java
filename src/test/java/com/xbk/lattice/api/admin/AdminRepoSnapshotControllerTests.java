package com.xbk.lattice.api.admin;

import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import com.xbk.lattice.vault.VaultExportService;
import com.xbk.lattice.vault.snapshot.VaultGitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminRepoSnapshotController 集成测试
 *
 * 职责：验证 repo diff / rollback 管理接口
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
@AutoConfigureMockMvc
class AdminRepoSnapshotControllerTests {

    @Autowired
    private MockMvc mockMvc;

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

    /**
     * 验证 repo baseline 管理接口会建立带 gitCommit 的 repo snapshot。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateRepoBaselineSnapshot(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path vaultDir = tempDir.resolve("vault");

        seedBaseline();

        mockMvc.perform(post("/api/v1/admin/snapshot/repo/baseline")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"vaultDir\":\"" + escapeJson(vaultDir.toString()) + "\",\"description\":\"baseline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").isNumber())
                .andExpect(jsonPath("$.gitCommit").isString())
                .andExpect(jsonPath("$.createdNewCommit").value(true))
                .andExpect(jsonPath("$.articleCount").value(1))
                .andExpect(jsonPath("$.vaultDir").value(vaultDir.toString()));

        List<RepoSnapshotRecord> snapshots = repoSnapshotService.history(10).getItems();
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getGitCommit()).isNotBlank();
        assertThat(vaultGitService.headCommitId(vaultDir)).isEqualTo(snapshots.get(0).getGitCommit());
    }

    /**
     * 验证 repo diff 与 repo rollback 管理接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeRepoDiffAndRollbackApis(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path vaultDir = tempDir.resolve("vault");

        seedBaseline();
        vaultExportService.export(vaultDir);
        String baselineCommitId = vaultGitService.commitAll(vaultDir, "[lattice:manual] baseline");
        long snapshotId = repoSnapshotService.snapshot("manual", "baseline", baselineCommitId).getId();

        seedMutated();
        vaultExportService.export(vaultDir);
        vaultGitService.commitAll(vaultDir, "[lattice:manual] mutated");

        mockMvc.perform(get("/api/v1/admin/snapshot/repo/{snapshotId}/diff", snapshotId)
                        .param("vaultDir", vaultDir.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value(snapshotId))
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.items[0].filePath").exists());

        mockMvc.perform(post("/api/v1/admin/rollback/repo")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"snapshotId\":" + snapshotId + ",\"vaultDir\":\"" + escapeJson(vaultDir.toString()) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restoredSnapshotId").value(snapshotId));

        assertThat(articleJdbcRepository.findByConceptId("payment-timeout")).isPresent();
        assertThat(articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow().getContent()).contains("retry=3");
    }

    private void seedBaseline() {
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                "# Payment Timeout\n\nretry=3\n",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T20:00:00+08:00"),
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
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "payment timeout?",
                "retry=3",
                "{}",
                "tester",
                OffsetDateTime.parse("2026-04-16T20:10:00+08:00")
        ));
        synthesisArtifactJdbcStore.save(new SynthesisArtifactRecord(
                "index",
                "Knowledge Index",
                "# Index baseline",
                OffsetDateTime.parse("2026-04-16T20:20:00+08:00")
        ));
    }

    private void seedMutated() {
        articleJdbcRepository.deleteAll();
        contributionJdbcRepository.deleteAll();
        synthesisArtifactJdbcStore.deleteAll();

        articleJdbcRepository.upsert(new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                "# Payment Timeout\n\nretry=5\n",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T21:00:00+08:00"),
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
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "payment timeout?",
                "retry=5",
                "{}",
                "tester",
                OffsetDateTime.parse("2026-04-16T21:10:00+08:00")
        ));
        synthesisArtifactJdbcStore.save(new SynthesisArtifactRecord(
                "index",
                "Knowledge Index",
                "# Index mutated",
                OffsetDateTime.parse("2026-04-16T21:20:00+08:00")
        ));
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.repo_snapshot_items");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.repo_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }
}
