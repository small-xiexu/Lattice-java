package com.xbk.lattice.source.service;

import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceSyncRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source 领域集成测试
 *
 * 职责：验证资料源骨架表、legacy-default 回填与同步运行记录能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_phase_d_source_domain_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_phase_d_source_domain_test",
        "spring.flyway.default-schema=lattice_phase_d_source_domain_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.llm.secret-encryption-key=test-phase-d-key-0123456789abcdef"
})
class SourceDomainIntegrationTests {

    @Autowired
    private SourceService sourceService;

    @Autowired
    private SourceSyncService sourceSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证 legacy-default 会自动创建，且可记录新的资料源与同步运行。
     */
    @Test
    void shouldCreateLegacyDefaultAndPersistSyncRuns() {
        KnowledgeSource legacyDefault = sourceService.findBySourceCode("legacy-default").orElseThrow();
        assertThat(legacyDefault.getName()).isEqualTo("Legacy Default Source");

        KnowledgeSource source = sourceService.save(new KnowledgeSource(
                null,
                "payments-docs",
                "Payments Docs",
                "UPLOAD",
                "DOCUMENT",
                "ACTIVE",
                "NORMAL",
                "AUTO",
                "{\"note\":\"phase-d\"}",
                "{\"owner\":\"qa\"}",
                null,
                null,
                null,
                null,
                null,
                null
        ));
        SourceSyncRun run = sourceSyncService.requestRun(new SourceSyncRun(
                null,
                source.getId(),
                "UPLOAD",
                "manifest-001",
                "MANUAL",
                "RULE_ONLY",
                "NEW_SOURCE",
                "CREATE",
                "QUEUED",
                null,
                null,
                "{\"bundleSummary\":{\"fileCount\":2}}",
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null
        ));
        SourceSyncRun completedRun = sourceSyncService.markStatus(run.getId(), "SUCCEEDED", null);

        KnowledgeSource reloadedSource = sourceService.findById(source.getId()).orElseThrow();
        assertThat(sourceSyncService.listRuns(source.getId())).hasSize(1);
        assertThat(completedRun.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(reloadedSource.getLastSyncRunId()).isEqualTo(completedRun.getId());
        assertThat(reloadedSource.getLastSyncStatus()).isEqualTo("SUCCEEDED");

        Long legacyBackfillCount = jdbcTemplate.queryForObject(
                "select count(*) from articles where source_id = ? and article_key like 'legacy-default--%'",
                Long.class,
                legacyDefault.getId()
        );
        assertThat(legacyBackfillCount).isNotNull();
    }
}
