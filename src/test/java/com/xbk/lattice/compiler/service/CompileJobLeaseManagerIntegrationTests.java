package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileJobLeaseManager 集成测试
 *
 * 职责：验证运行中任务的后台续租与陈旧任务自动失败收口
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.compiler.jobs.heartbeat-interval-seconds=1",
        "lattice.compiler.jobs.lease-duration-seconds=5",
        "lattice.compiler.jobs.worker-enabled=false"
})
class CompileJobLeaseManagerIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileJobJdbcRepository compileJobJdbcRepository;

    @Autowired
    private CompileJobLeaseManager compileJobLeaseManager;

    @Autowired
    private CompileJobProperties compileJobProperties;

    /**
     * 验证后台续租会刷新运行中任务的心跳与租约。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldRefreshHeartbeatForRegisteredRunningJob() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-lease-heartbeat"));
        OffsetDateTime startedAt = OffsetDateTime.now();
        compileJobJdbcRepository.markRunning(
                "job-lease-heartbeat",
                compileJobProperties.getWorkerId(),
                startedAt,
                startedAt.plusSeconds(5)
        );
        CompileJobRecord initialRecord = compileJobJdbcRepository.findByJobId("job-lease-heartbeat").orElseThrow();

        try {
            compileJobLeaseManager.registerRunningJob("job-lease-heartbeat");
            Thread.sleep(1500L);
        }
        finally {
            compileJobLeaseManager.cancelJob("job-lease-heartbeat");
        }

        CompileJobRecord refreshedRecord = compileJobJdbcRepository.findByJobId("job-lease-heartbeat").orElseThrow();
        assertThat(refreshedRecord.getLastHeartbeatAt()).isAfter(initialRecord.getLastHeartbeatAt());
        assertThat(refreshedRecord.getRunningExpiresAt()).isAfter(initialRecord.getRunningExpiresAt());
    }

    /**
     * 验证已过期的运行中任务会被自动收口为失败。
     */
    @Test
    void shouldFailExpiredRunningJobDuringRecovery() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-lease-expired"));
        OffsetDateTime startedAt = OffsetDateTime.now().minusMinutes(5);
        compileJobJdbcRepository.markRunning(
                "job-lease-expired",
                compileJobProperties.getWorkerId(),
                startedAt,
                startedAt.plusSeconds(5)
        );

        compileJobLeaseManager.recoverExpiredJobs();

        CompileJobRecord failedRecord = compileJobJdbcRepository.findByJobId("job-lease-expired").orElseThrow();
        assertThat(failedRecord.getStatus()).isEqualTo("FAILED");
        assertThat(failedRecord.getErrorCode()).isEqualTo("COMPILE_STALE_TIMEOUT");
        assertThat(failedRecord.getErrorMessage()).contains("heartbeat expired");
    }

    /**
     * 验证运行进度快照更新会同步刷新心跳与租约。
     */
    @Test
    void shouldUpdateProgressSnapshotWhenTouchingProgress() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-lease-progress"));
        OffsetDateTime startedAt = OffsetDateTime.now();
        compileJobJdbcRepository.markRunning(
                "job-lease-progress",
                compileJobProperties.getWorkerId(),
                startedAt,
                startedAt.plusSeconds(5)
        );

        compileJobLeaseManager.touchProgress(
                "job-lease-progress",
                "compile_new_articles",
                1,
                3,
                "正在生成文章（1/3）：payment-timeout"
        );

        CompileJobRecord runningRecord = compileJobJdbcRepository.findByJobId("job-lease-progress").orElseThrow();
        assertThat(runningRecord.getCurrentStep()).isEqualTo("compile_new_articles");
        assertThat(runningRecord.getProgressCurrent()).isEqualTo(1);
        assertThat(runningRecord.getProgressTotal()).isEqualTo(3);
        assertThat(runningRecord.getProgressMessage()).isEqualTo("正在生成文章（1/3）：payment-timeout");
        assertThat(runningRecord.getLastHeartbeatAt()).isNotNull();
        assertThat(runningRecord.getRunningExpiresAt()).isAfter(runningRecord.getLastHeartbeatAt());
    }

    /**
     * 验证多 worker 竞争同一运行中任务时，只有持有租约的 worker 可以继续续租和推进。
     */
    @Test
    void shouldKeepSingleWorkerOwnershipWhenAnotherWorkerTouchesSameJob() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-lease-single-owner"));
        OffsetDateTime startedAt = OffsetDateTime.now();
        boolean claimed = compileJobJdbcRepository.markRunning(
                "job-lease-single-owner",
                compileJobProperties.getWorkerId(),
                startedAt,
                startedAt.plusSeconds(5)
        );
        assertThat(claimed).isTrue();

        CompileJobProperties competingWorkerProperties = new CompileJobProperties();
        competingWorkerProperties.setWorkerId("worker-competing");
        competingWorkerProperties.setHeartbeatIntervalSeconds(compileJobProperties.getHeartbeatIntervalSeconds());
        competingWorkerProperties.setLeaseDurationSeconds(compileJobProperties.getLeaseDurationSeconds());
        CompileJobLeaseManager competingWorkerLeaseManager = new CompileJobLeaseManager(
                compileJobJdbcRepository,
                competingWorkerProperties
        );
        try {
            boolean competingClaimed = compileJobJdbcRepository.markRunning(
                    "job-lease-single-owner",
                    competingWorkerProperties.getWorkerId(),
                    startedAt.plusSeconds(1),
                    startedAt.plusSeconds(6)
            );
            assertThat(competingClaimed).isFalse();

            CompileJobRecord beforeCompetingTouch = compileJobJdbcRepository.findByJobId("job-lease-single-owner").orElseThrow();
            competingWorkerLeaseManager.touchProgress(
                    "job-lease-single-owner",
                    "review_articles",
                    2,
                    3,
                    "竞争 worker 试图推进审查"
            );

            CompileJobRecord afterCompetingTouch = compileJobJdbcRepository.findByJobId("job-lease-single-owner").orElseThrow();
            assertThat(afterCompetingTouch.getWorkerId()).isEqualTo(compileJobProperties.getWorkerId());
            assertThat(afterCompetingTouch.getCurrentStep()).isEqualTo(beforeCompetingTouch.getCurrentStep());
            assertThat(afterCompetingTouch.getProgressMessage()).isEqualTo(beforeCompetingTouch.getProgressMessage());

            compileJobLeaseManager.touchProgress(
                    "job-lease-single-owner",
                    "compile_new_articles",
                    1,
                    3,
                    "持有者继续生成文章（1/3）"
            );

            CompileJobRecord ownerTouchedRecord = compileJobJdbcRepository.findByJobId("job-lease-single-owner").orElseThrow();
            assertThat(ownerTouchedRecord.getWorkerId()).isEqualTo(compileJobProperties.getWorkerId());
            assertThat(ownerTouchedRecord.getCurrentStep()).isEqualTo("compile_new_articles");
            assertThat(ownerTouchedRecord.getProgressMessage()).isEqualTo("持有者继续生成文章（1/3）");
        }
        finally {
            competingWorkerLeaseManager.destroy();
        }
    }

    /**
     * 验证 lease manager 销毁时，会把当前 worker 持有的运行中作业回收到 QUEUED，避免实例重启后长时间悬挂。
     */
    @Test
    void shouldRequeueRunningJobsOwnedByWorkerOnDestroy() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs CASCADE");
        compileJobJdbcRepository.save(buildQueuedRecord("job-lease-requeue"));
        OffsetDateTime startedAt = OffsetDateTime.now();
        boolean claimed = compileJobJdbcRepository.markRunning(
                "job-lease-requeue",
                compileJobProperties.getWorkerId(),
                startedAt,
                startedAt.plusSeconds(30)
        );
        assertThat(claimed).isTrue();

        compileJobLeaseManager.destroy();

        CompileJobRecord queuedRecord = compileJobJdbcRepository.findByJobId("job-lease-requeue").orElseThrow();
        assertThat(queuedRecord.getStatus()).isEqualTo("QUEUED");
        assertThat(queuedRecord.getWorkerId()).isNull();
        assertThat(queuedRecord.getRunningExpiresAt()).isNull();
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
