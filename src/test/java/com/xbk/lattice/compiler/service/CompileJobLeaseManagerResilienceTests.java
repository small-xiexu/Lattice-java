package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileJobLeaseManager 韧性测试
 *
 * 职责：验证后台心跳续租即使偶发异常，也不会导致后续续租任务永久中断
 *
 * @author xiexu
 */
class CompileJobLeaseManagerResilienceTests {

    /**
     * 验证首轮续租抛异常后，后续调度仍会继续执行。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldKeepHeartbeatSchedulerAliveAfterTransientRefreshFailure() throws Exception {
        FlakyCompileJobJdbcRepository compileJobJdbcRepository = new FlakyCompileJobJdbcRepository();
        CompileJobProperties compileJobProperties = new CompileJobProperties();
        compileJobProperties.setWorkerId("worker-test");
        compileJobProperties.setHeartbeatIntervalSeconds(1L);
        compileJobProperties.setLeaseDurationSeconds(5L);
        CompileJobLeaseManager compileJobLeaseManager = new CompileJobLeaseManager(
                compileJobJdbcRepository,
                compileJobProperties
        );
        try {
            compileJobLeaseManager.registerRunningJob("job-heartbeat-resilience");
            Thread.sleep(2600L);
        }
        finally {
            compileJobLeaseManager.destroy();
        }

        assertThat(compileJobJdbcRepository.getRefreshInvocationCount()).isGreaterThanOrEqualTo(2);
        assertThat(compileJobJdbcRepository.getSuccessfulRefreshCount()).isGreaterThanOrEqualTo(1);
    }

    /**
     * 首次续租失败、后续恢复成功的仓储替身。
     *
     * @author xiexu
     */
    private static class FlakyCompileJobJdbcRepository extends CompileJobJdbcRepository {

        private final AtomicInteger refreshInvocationCount = new AtomicInteger();

        private final AtomicInteger successfulRefreshCount = new AtomicInteger();

        private FlakyCompileJobJdbcRepository() {
            super(null);
        }

        /**
         * 首轮抛异常，后续返回成功。
         *
         * @param jobId 作业标识
         * @param workerId worker 标识
         * @param heartbeatAt 心跳时间
         * @param runningExpiresAt 租约到期时间
         * @return 是否刷新成功
         */
        @Override
        public boolean refreshHeartbeat(
                String jobId,
                String workerId,
                OffsetDateTime heartbeatAt,
                OffsetDateTime runningExpiresAt
        ) {
            int currentInvocation = refreshInvocationCount.incrementAndGet();
            if (currentInvocation == 1) {
                throw new IllegalStateException("simulated transient heartbeat failure");
            }
            successfulRefreshCount.incrementAndGet();
            return true;
        }

        /**
         * 返回空过期列表，避免恢复线程干扰测试。
         *
         * @param now 当前时间
         * @return 空列表
         */
        @Override
        public List<String> findExpiredRunningJobIds(OffsetDateTime now) {
            return List.of();
        }

        @Override
        public int requeueRunningJobsOwnedByWorker(String workerId) {
            return 0;
        }

        /**
         * 返回续租调用次数。
         *
         * @return 调用次数
         */
        private int getRefreshInvocationCount() {
            return refreshInvocationCount.get();
        }

        /**
         * 返回续租成功次数。
         *
         * @return 成功次数
         */
        private int getSuccessfulRefreshCount() {
            return successfulRefreshCount.get();
        }
    }
}
