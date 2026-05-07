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
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
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
}
