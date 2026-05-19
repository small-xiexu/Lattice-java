package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileJobJdbcRepository 测试
 *
 * 职责：验证 compile_jobs 运行态字段的建表、持久化与状态更新行为
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class CompileJobJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileJobJdbcRepository compileJobJdbcRepository;

    /**
     * 验证 compile_jobs 表已具备运行态字段。
     */
    @Test
    void shouldCreateCompileJobRuntimeColumnsByManualDdl() {
        List<String> columnNames = jdbcTemplate.queryForList(
                """
                        select column_name
                        from information_schema.columns
                        where table_schema = 'lattice'
                          and table_name = 'compile_jobs'
                        order by ordinal_position
                        """,
                String.class
        );

        assertThat(columnNames)
                .contains("worker_id")
                .contains("last_heartbeat_at")
                .contains("running_expires_at")
                .contains("current_step")
                .contains("progress_current")
                .contains("progress_total")
                .contains("progress_message")
                .contains("progress_updated_at")
                .contains("review_mode")
                .contains("error_code");
    }

    /**
     * 验证运行态字段可随编译作业一起持久化并回读。
     */
    @Test
    void shouldSaveAndLoadCompileJobRuntimeFields() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        OffsetDateTime now = OffsetDateTime.now();
        CompileJobRecord compileJobRecord = new CompileJobRecord(
                "job-runtime-save",
                "/tmp/source-dir",
                null,
                null,
                "trace-root-1",
                true,
                "state_graph",
                "RUNNING",
                "worker-a",
                now.minusSeconds(5),
                now.plusSeconds(60),
                "compile_new_articles",
                2,
                6,
                "正在生成第 2/6 篇文章",
                now.minusSeconds(3),
                "LLM_REQUEST_TIMEOUT",
                3,
                "timeout",
                1,
                now.minusMinutes(2),
                now.minusMinutes(1),
                null
        );

        compileJobJdbcRepository.save(compileJobRecord);
        CompileJobRecord loaded = compileJobJdbcRepository.findByJobId("job-runtime-save").orElseThrow();

        assertThat(loaded.getWorkerId()).isEqualTo("worker-a");
        assertThat(loaded.getCurrentStep()).isEqualTo("compile_new_articles");
        assertThat(loaded.getProgressCurrent()).isEqualTo(2);
        assertThat(loaded.getProgressTotal()).isEqualTo(6);
        assertThat(loaded.getProgressMessage()).isEqualTo("正在生成第 2/6 篇文章");
        assertThat(loaded.getReviewMode()).isEqualTo("RULE_BASED");
        assertThat(loaded.getErrorCode()).isEqualTo("LLM_REQUEST_TIMEOUT");
        assertThat(loaded.getErrorMessage()).isEqualTo("timeout");
        assertThat(loaded.getRunningExpiresAt()).isNotNull();
        assertThat(loaded.getProgressUpdatedAt()).isNotNull();
    }

    /**
     * 验证抢占运行与重试会正确维护运行态快照。
     */
    @Test
    void shouldMarkRunningAndResetRuntimeSnapshotWhenRetrying() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-runtime-retry"));

        OffsetDateTime startedAt = OffsetDateTime.now();
        OffsetDateTime runningExpiresAt = startedAt.plusSeconds(90);
        boolean marked = compileJobJdbcRepository.markRunning(
                "job-runtime-retry",
                "worker-b",
                startedAt,
                runningExpiresAt
        );

        assertThat(marked).isTrue();
        CompileJobRecord runningRecord = compileJobJdbcRepository.findByJobId("job-runtime-retry").orElseThrow();
        assertThat(runningRecord.getStatus()).isEqualTo("RUNNING");
        assertThat(runningRecord.getWorkerId()).isEqualTo("worker-b");
        assertThat(runningRecord.getCurrentStep()).isEqualTo("initialize_job");
        assertThat(runningRecord.getProgressMessage()).isEqualTo("编译任务已启动，等待图执行");
        assertThat(runningRecord.getRunningExpiresAt()).isEqualTo(runningExpiresAt);

        compileJobJdbcRepository.retry("job-runtime-retry");

        CompileJobRecord retriedRecord = compileJobJdbcRepository.findByJobId("job-runtime-retry").orElseThrow();
        assertThat(retriedRecord.getStatus()).isEqualTo("QUEUED");
        assertThat(retriedRecord.getWorkerId()).isNull();
        assertThat(retriedRecord.getCurrentStep()).isNull();
        assertThat(retriedRecord.getProgressCurrent()).isZero();
        assertThat(retriedRecord.getProgressTotal()).isZero();
        assertThat(retriedRecord.getProgressMessage()).isNull();
        assertThat(retriedRecord.getRunningExpiresAt()).isNull();
        assertThat(retriedRecord.getErrorCode()).isNull();
        assertThat(retriedRecord.getReviewMode()).isEqualTo("RULE_BASED");
    }

    /**
     * 验证 retry 不会改变原 job 固化的审查模式。
     */
    @Test
    void shouldKeepReviewModeWhenRetryingJob() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        OffsetDateTime requestedAt = OffsetDateTime.now();
        CompileJobRecord compileJobRecord = new CompileJobRecord(
                "job-review-mode-retry",
                "/tmp/source-dir",
                null,
                null,
                "trace-root",
                false,
                "state_graph",
                "LLM",
                "FAILED",
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                "COMPILE_EXECUTION_FAILED",
                0,
                "failed",
                1,
                requestedAt,
                requestedAt,
                requestedAt
        );

        compileJobJdbcRepository.save(compileJobRecord);
        compileJobJdbcRepository.retry("job-review-mode-retry");

        CompileJobRecord retriedRecord = compileJobJdbcRepository.findByJobId("job-review-mode-retry").orElseThrow();
        assertThat(retriedRecord.getStatus()).isEqualTo("QUEUED");
        assertThat(retriedRecord.getReviewMode()).isEqualTo("LLM");
    }

    /**
     * 验证活动任务按 source_sync_run_id 优先幂等命中。
     */
    @Test
    void shouldFindActiveJobBySourceSyncRunId() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        Long sourceSyncRunId = insertSourceSyncRun(null, "sync-run-active");
        CompileJobRecord activeJob = buildCompileJobRecord(
                "job-active-sync",
                "/tmp/source-a",
                null,
                sourceSyncRunId,
                "RUNNING"
        );
        compileJobJdbcRepository.save(activeJob);

        CompileJobRecord foundJob = compileJobJdbcRepository.findActiveBySubmissionTarget(
                sourceSyncRunId,
                "/tmp/source-b",
                null,
                true
        ).orElseThrow();

        assertThat(foundJob.getJobId()).isEqualTo("job-active-sync");
    }

    /**
     * 验证活动任务按归一源目录幂等命中。
     */
    @Test
    void shouldFindActiveJobBySourceDir() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        CompileJobRecord activeJob = buildCompileJobRecord(
                "job-active-source-dir",
                "/tmp/source-dir-active",
                null,
                null,
                "QUEUED"
        );
        compileJobJdbcRepository.save(activeJob);

        CompileJobRecord foundJob = compileJobJdbcRepository.findActiveBySubmissionTarget(
                null,
                "/tmp/source-dir-active",
                null,
                false
        ).orElseThrow();

        assertThat(foundJob.getJobId()).isEqualTo("job-active-source-dir");
    }

    /**
     * 验证 default-source 不会仅凭 sourceId 锁住所有直接编译任务。
     */
    @Test
    void shouldAvoidDefaultSourceGlobalMutex() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        CompileJobRecord activeJob = buildCompileJobRecord(
                "job-default-source-a",
                "/tmp/default-source-a",
                null,
                null,
                "QUEUED"
        );
        compileJobJdbcRepository.save(activeJob);

        assertThat(compileJobJdbcRepository.findActiveBySubmissionTarget(
                null,
                "/tmp/default-source-b",
                null,
                false
        )).isEmpty();
    }

    /**
     * 验证托管资料源允许仅凭 sourceId 命中活动任务。
     */
    @Test
    void shouldFindManagedSourceActiveJobBySourceIdWhenAllowed() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        Long sourceId = insertKnowledgeSource("managed-source-id-only");
        CompileJobRecord activeJob = buildCompileJobRecord(
                "job-managed-source",
                "/tmp/managed-source-a",
                sourceId,
                null,
                "RUNNING"
        );
        compileJobJdbcRepository.save(activeJob);

        CompileJobRecord foundJob = compileJobJdbcRepository.findActiveBySubmissionTarget(
                null,
                "/tmp/managed-source-b",
                sourceId,
                true
        ).orElseThrow();

        assertThat(foundJob.getJobId()).isEqualTo("job-managed-source");
    }

    /**
     * 验证已完成或失败任务不会阻止后续重新提交。
     */
    @Test
    void shouldIgnoreTerminalJobsWhenFindingActiveSubmissionTarget() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        Long sourceId = insertKnowledgeSource("managed-source-terminal");
        Long sourceSyncRunId = insertSourceSyncRun(sourceId, "sync-run-terminal");
        compileJobJdbcRepository.save(buildCompileJobRecord(
                "job-terminal-succeeded",
                "/tmp/source-terminal",
                sourceId,
                sourceSyncRunId,
                "SUCCEEDED"
        ));
        compileJobJdbcRepository.save(buildCompileJobRecord(
                "job-terminal-failed",
                "/tmp/source-terminal",
                sourceId,
                sourceSyncRunId,
                "FAILED"
        ));

        assertThat(compileJobJdbcRepository.findActiveBySubmissionTarget(
                sourceSyncRunId,
                "/tmp/source-terminal",
                sourceId,
                true
        )).isEmpty();
    }

    /**
     * 验证运行中的任务可刷新步骤级进度快照。
     */
    @Test
    void shouldUpdateProgressSnapshotForRunningJob() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-runtime-progress"));

        OffsetDateTime startedAt = OffsetDateTime.now();
        compileJobJdbcRepository.markRunning(
                "job-runtime-progress",
                "worker-c",
                startedAt,
                startedAt.plusSeconds(90)
        );
        OffsetDateTime heartbeatAt = OffsetDateTime.now();
        OffsetDateTime runningExpiresAt = heartbeatAt.plusSeconds(90);

        boolean updated = compileJobJdbcRepository.updateProgressSnapshot(
                "job-runtime-progress",
                "worker-c",
                "review_articles",
                2,
                5,
                "正在审查文章（2/5）：payment-timeout",
                heartbeatAt,
                runningExpiresAt
        );

        assertThat(updated).isTrue();
        CompileJobRecord runningRecord = compileJobJdbcRepository.findByJobId("job-runtime-progress").orElseThrow();
        assertThat(runningRecord.getCurrentStep()).isEqualTo("review_articles");
        assertThat(runningRecord.getProgressCurrent()).isEqualTo(2);
        assertThat(runningRecord.getProgressTotal()).isEqualTo(5);
        assertThat(runningRecord.getProgressMessage()).isEqualTo("正在审查文章（2/5）：payment-timeout");
        assertThat(runningRecord.getLastHeartbeatAt()).isEqualTo(heartbeatAt);
        assertThat(runningRecord.getRunningExpiresAt()).isEqualTo(runningExpiresAt);
    }

    /**
     * 验证失败收口会分离写入错误码与错误信息。
     */
    @Test
    void shouldPersistErrorCodeAndErrorMessageSeparatelyWhenFailed() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-runtime-failed"));

        OffsetDateTime finishedAt = OffsetDateTime.now();
        compileJobJdbcRepository.markFailed(
                "job-runtime-failed",
                "COMPILE_STALE_TIMEOUT",
                "job heartbeat lost",
                finishedAt
        );

        CompileJobRecord failedRecord = compileJobJdbcRepository.findByJobId("job-runtime-failed").orElseThrow();
        assertThat(failedRecord.getStatus()).isEqualTo("FAILED");
        assertThat(failedRecord.getErrorCode()).isEqualTo("COMPILE_STALE_TIMEOUT");
        assertThat(failedRecord.getErrorMessage()).isEqualTo("job heartbeat lost");
        assertThat(failedRecord.getRunningExpiresAt()).isNull();
        assertThat(failedRecord.getFinishedAt()).isEqualTo(finishedAt);
    }

    /**
     * 构造最小排队作业记录。
     *
     * @param jobId 作业标识
     * @return 排队作业记录
     */
    private CompileJobRecord buildQueuedRecord(String jobId) {
        OffsetDateTime requestedAt = OffsetDateTime.now();
        return new CompileJobRecord(
                jobId,
                "/tmp/source-dir",
                null,
                null,
                "trace-root",
                false,
                "state_graph",
                "QUEUED",
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
     * 构造指定目标和状态的编译作业记录。
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
                "state_graph",
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
     * 插入测试资料源。
     *
     * @param sourceCode 资料源编码
     * @return 资料源主键
     */
    private Long insertKnowledgeSource(String sourceCode) {
        return jdbcTemplate.queryForObject(
                """
                        insert into lattice.knowledge_sources (
                            source_code, name, source_type, content_profile,
                            status, visibility, default_sync_mode, config_json, metadata_json
                        )
                        values (?, ?, 'UPLOAD', 'DOCUMENT', 'ACTIVE', 'NORMAL', 'FULL', '{}'::jsonb, '{}'::jsonb)
                        on conflict (source_code) do update
                        set name = excluded.name
                        returning id
                        """,
                Long.class,
                sourceCode,
                sourceCode
        );
    }

    /**
     * 插入测试同步运行。
     *
     * @param sourceId 资料源主键
     * @param manifestHash manifest 哈希
     * @return 同步运行主键
     */
    private Long insertSourceSyncRun(Long sourceId, String manifestHash) {
        return jdbcTemplate.queryForObject(
                """
                        insert into lattice.source_sync_runs (
                            source_id, source_type, manifest_hash, trigger_type,
                            resolver_mode, status, evidence_json
                        )
                        values (?, 'UPLOAD', ?, 'MANUAL', 'RULE_ONLY', 'RUNNING', '{}'::jsonb)
                        returning id
                        """,
                Long.class,
                sourceId,
                manifestHash
        );
    }
}
