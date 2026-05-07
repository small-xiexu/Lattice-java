package com.xbk.lattice.governance.repo;

import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.RepoSnapshotItemRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RepoSnapshotService 测试
 *
 * 职责：验证整库快照可持久化文章、产物与贡献
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
class RepoSnapshotServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ContributionJdbcRepository contributionJdbcRepository;

    @Autowired
    private SynthesisArtifactJdbcStore synthesisArtifactJdbcStore;

    @Autowired
    private RepoSnapshotService repoSnapshotService;

    @Autowired
    private RepoSnapshotJdbcRepository repoSnapshotJdbcRepository;

    /**
     * 验证整库快照会同时写入主记录与明细。
     */
    @Test
    void shouldPersistRepoSnapshotAndItems() {
        resetTables();
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                "# Payment Timeout",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-16T17:00:00+08:00"),
                List.of("payment/a.md"),
                "{}"
        ));
        contributionJdbcRepository.save(new ContributionRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
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

        RepoSnapshotRecord repoSnapshotRecord = repoSnapshotService.snapshot("manual", "manual snapshot", "abc1234");
        List<RepoSnapshotRecord> snapshots = repoSnapshotJdbcRepository.findRecent(10);
        List<RepoSnapshotItemRecord> items = repoSnapshotJdbcRepository.findItemsBySnapshotId(repoSnapshotRecord.getId());

        assertThat(repoSnapshotRecord.getId()).isPositive();
        assertThat(repoSnapshotRecord.getArticleCount()).isEqualTo(1);
        assertThat(snapshots).hasSize(1);
        assertThat(items).hasSize(3);
        assertThat(items).anyMatch(item -> "article".equals(item.getEntityType()) && "payment-timeout".equals(item.getEntityId()));
        assertThat(items).anyMatch(item -> "artifact".equals(item.getEntityType()) && "index".equals(item.getEntityId()));
        assertThat(items).anyMatch(item -> "contribution".equals(item.getEntityType()));
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.repo_snapshot_items");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.repo_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }
}
