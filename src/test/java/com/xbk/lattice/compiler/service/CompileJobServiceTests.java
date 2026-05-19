package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.infra.persistence.mapper.CompileJobMapper;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.infra.mapper.KnowledgeSourceMapper;
import com.xbk.lattice.source.infra.KnowledgeSourceJdbcRepository;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileJobService 测试
 *
 * 职责：验证编译作业提交层的幂等与目标归一逻辑
 *
 * @author xiexu
 */
class CompileJobServiceTests {

    @TempDir
    private Path tempDir;

    /**
     * 验证活动作业命中时直接返回已有 job 且不再插入。
     */
    @Test
    void shouldReturnExistingActiveJobWithoutSavingDuplicate() {
        TestCompileJobJdbcRepository compileJobJdbcRepository = new TestCompileJobJdbcRepository();
        TestKnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository = new TestKnowledgeSourceJdbcRepository();
        CompileJobService compileJobService = buildCompileJobService(
                compileJobJdbcRepository,
                knowledgeSourceJdbcRepository
        );
        KnowledgeSource defaultSource = buildKnowledgeSource(1L, "default-source");
        knowledgeSourceJdbcRepository.setDefaultSource(defaultSource);
        Path sourceDir = tempDir.resolve("kb").resolve("..").resolve("kb");
        String normalizedSourceDir = sourceDir.toAbsolutePath().normalize().toString();
        CompileJobRecord activeJob = buildCompileJobRecord(
                "job-active-existing",
                normalizedSourceDir,
                1L,
                null,
                "QUEUED"
        );
        compileJobJdbcRepository.setActiveJob(activeJob);

        CompileJobRecord returnedJob = compileJobService.submit(
                sourceDir.toString(),
                false,
                true,
                CompileOrchestrationModes.STATE_GRAPH
        );

        assertThat(returnedJob.getJobId()).isEqualTo("job-active-existing");
        assertThat(compileJobJdbcRepository.getLastSourceDir()).isEqualTo(normalizedSourceDir);
        assertThat(compileJobJdbcRepository.isLastAllowSourceIdOnly()).isFalse();
        assertThat(compileJobJdbcRepository.isSaveCalled()).isFalse();
    }

    /**
     * 验证显式托管资料源允许按 sourceId 独立匹配活动作业。
     */
    @Test
    void shouldAllowManagedSourceIdOnlyActiveMatch() {
        TestCompileJobJdbcRepository compileJobJdbcRepository = new TestCompileJobJdbcRepository();
        TestKnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository = new TestKnowledgeSourceJdbcRepository();
        CompileJobService compileJobService = buildCompileJobService(
                compileJobJdbcRepository,
                knowledgeSourceJdbcRepository
        );
        KnowledgeSource managedSource = buildKnowledgeSource(2L, "managed-source");
        knowledgeSourceJdbcRepository.setSourceById(managedSource);
        Path sourceDir = tempDir.resolve("managed");
        String normalizedSourceDir = sourceDir.toAbsolutePath().normalize().toString();
        CompileJobRecord activeJob = buildCompileJobRecord(
                "job-managed-existing",
                tempDir.resolve("other-managed").toAbsolutePath().normalize().toString(),
                2L,
                null,
                "RUNNING"
        );
        compileJobJdbcRepository.setActiveJob(activeJob);

        CompileJobRecord returnedJob = compileJobService.submit(
                sourceDir.toString(),
                false,
                true,
                CompileOrchestrationModes.STATE_GRAPH,
                2L,
                null
        );

        assertThat(returnedJob.getJobId()).isEqualTo("job-managed-existing");
        assertThat(compileJobJdbcRepository.getLastSourceDir()).isEqualTo(normalizedSourceDir);
        assertThat(compileJobJdbcRepository.getLastSourceId()).isEqualTo(2L);
        assertThat(compileJobJdbcRepository.isLastAllowSourceIdOnly()).isTrue();
        assertThat(compileJobJdbcRepository.isSaveCalled()).isFalse();
    }

    /**
     * 构造编译作业服务。
     *
     * @param compileJobJdbcRepository 编译作业仓储
     * @param knowledgeSourceJdbcRepository 资料源仓储
     * @return 编译作业服务
     */
    private CompileJobService buildCompileJobService(
            CompileJobJdbcRepository compileJobJdbcRepository,
            KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository
    ) {
        ObjectProvider<Tracer> tracerProvider = new NullTracerProvider();
        CompileOrchestratorRegistry compileOrchestratorRegistry = new CompileOrchestratorRegistry(List.of());
        CompileJobProperties compileJobProperties = new CompileJobProperties();
        return new CompileJobService(
                compileJobJdbcRepository,
                compileOrchestratorRegistry,
                knowledgeSourceJdbcRepository,
                null,
                tracerProvider,
                compileJobProperties,
                null
        );
    }

    /**
     * 构造资料源。
     *
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @return 资料源
     */
    private KnowledgeSource buildKnowledgeSource(Long sourceId, String sourceCode) {
        OffsetDateTime now = OffsetDateTime.now();
        return new KnowledgeSource(
                sourceId,
                sourceCode,
                sourceCode,
                "LOCAL_DIR",
                "default",
                "ACTIVE",
                "PUBLIC",
                "MANUAL",
                "{}",
                "{}",
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    /**
     * 构造编译作业记录。
     *
     * @param jobId 作业标识
     * @param sourceDir 源目录
     * @param sourceId 资料源主键
     * @param sourceSyncRunId 同步运行主键
     * @param status 状态
     * @return 编译作业记录
     */
    private CompileJobRecord buildCompileJobRecord(
            String jobId,
            String sourceDir,
            Long sourceId,
            Long sourceSyncRunId,
            String status
    ) {
        OffsetDateTime requestedAt = OffsetDateTime.now();
        return new CompileJobRecord(
                jobId,
                sourceDir,
                sourceId,
                sourceSyncRunId,
                "trace-root",
                false,
                CompileOrchestrationModes.STATE_GRAPH,
                "LLM",
                status,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                null,
                0,
                null,
                0,
                requestedAt,
                null,
                null
        );
    }

    /**
     * 空 Tracer 提供器。
     *
     * @author xiexu
     */
    private static class NullTracerProvider implements ObjectProvider<Tracer> {

        /**
         * 返回空 Tracer。
         *
         * @return 空 Tracer
         */
        @Override
        public Tracer getObject(Object... args) {
            return null;
        }

        /**
         * 返回空 Tracer。
         *
         * @return 空 Tracer
         */
        @Override
        public Tracer getIfAvailable() {
            return null;
        }

        /**
         * 返回空 Tracer。
         *
         * @return 空 Tracer
         */
        @Override
        public Tracer getIfUnique() {
            return null;
        }

        /**
         * 返回空 Tracer。
         *
         * @return 空 Tracer
         */
        @Override
        public Tracer getObject() {
            return null;
        }
    }

    /**
     * 编译作业仓储测试替身。
     *
     * @author xiexu
     */
    private static class TestCompileJobJdbcRepository extends CompileJobJdbcRepository {

        private CompileJobRecord activeJob;

        private String lastSourceDir;

        private Long lastSourceId;

        private boolean lastAllowSourceIdOnly;

        private boolean saveCalled;

        private TestCompileJobJdbcRepository() {
            super((CompileJobMapper) null);
        }

        /**
         * 设置活动作业。
         *
         * @param activeJob 活动作业
         */
        private void setActiveJob(CompileJobRecord activeJob) {
            this.activeJob = activeJob;
        }

        /**
         * 查询活动作业并记录入参。
         *
         * @param sourceSyncRunId 资料源同步运行主键
         * @param sourceDir 源目录
         * @param sourceId 资料源主键
         * @param allowSourceIdOnly 是否允许仅按资料源主键匹配
         * @return 活动作业
         */
        @Override
        public Optional<CompileJobRecord> findActiveBySubmissionTarget(
                Long sourceSyncRunId,
                String sourceDir,
                Long sourceId,
                boolean allowSourceIdOnly
        ) {
            this.lastSourceDir = sourceDir;
            this.lastSourceId = sourceId;
            this.lastAllowSourceIdOnly = allowSourceIdOnly;
            return Optional.ofNullable(activeJob);
        }

        /**
         * 记录保存调用。
         *
         * @param compileJobRecord 编译作业记录
         */
        @Override
        public void save(CompileJobRecord compileJobRecord) {
            this.saveCalled = true;
        }

        /**
         * 返回最近查询的源目录。
         *
         * @return 源目录
         */
        private String getLastSourceDir() {
            return lastSourceDir;
        }

        /**
         * 返回最近查询的资料源主键。
         *
         * @return 资料源主键
         */
        private Long getLastSourceId() {
            return lastSourceId;
        }

        /**
         * 返回最近是否允许 sourceId 独立匹配。
         *
         * @return 是否允许 sourceId 独立匹配
         */
        private boolean isLastAllowSourceIdOnly() {
            return lastAllowSourceIdOnly;
        }

        /**
         * 返回是否发生保存调用。
         *
         * @return 是否发生保存调用
         */
        private boolean isSaveCalled() {
            return saveCalled;
        }
    }

    /**
     * 资料源仓储测试替身。
     *
     * @author xiexu
     */
    private static class TestKnowledgeSourceJdbcRepository extends KnowledgeSourceJdbcRepository {

        private KnowledgeSource defaultSource;

        private KnowledgeSource sourceById;

        private TestKnowledgeSourceJdbcRepository() {
            super((KnowledgeSourceMapper) null);
        }

        /**
         * 设置默认资料源。
         *
         * @param defaultSource 默认资料源
         */
        private void setDefaultSource(KnowledgeSource defaultSource) {
            this.defaultSource = defaultSource;
        }

        /**
         * 设置按主键返回的资料源。
         *
         * @param sourceById 资料源
         */
        private void setSourceById(KnowledgeSource sourceById) {
            this.sourceById = sourceById;
        }

        /**
         * 按资料源编码查询资料源。
         *
         * @param sourceCode 资料源编码
         * @return 资料源
         */
        @Override
        public Optional<KnowledgeSource> findBySourceCode(String sourceCode) {
            return Optional.ofNullable(defaultSource);
        }

        /**
         * 按资料源主键查询资料源。
         *
         * @param id 资料源主键
         * @return 资料源
         */
        @Override
        public Optional<KnowledgeSource> findById(Long id) {
            return Optional.ofNullable(sourceById);
        }
    }

}
