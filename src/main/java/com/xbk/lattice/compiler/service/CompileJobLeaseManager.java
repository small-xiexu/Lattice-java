package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 编译作业租约管理器
 *
 * 职责：负责运行中作业的后台续租、步骤心跳刷新与陈旧任务自动收口
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
@Slf4j
public class CompileJobLeaseManager implements DisposableBean {

    private static final String STALE_ERROR_MESSAGE = "compile job heartbeat expired and lease timed out";

    private final CompileJobJdbcRepository compileJobJdbcRepository;

    private final CompileJobProperties compileJobProperties;

    private final ScheduledExecutorService scheduledExecutorService;

    private final Map<String, ScheduledFuture<?>> heartbeatFutures = new ConcurrentHashMap<String, ScheduledFuture<?>>();

    /**
     * 创建编译作业租约管理器。
     *
     * @param compileJobJdbcRepository 编译作业仓储
     * @param compileJobProperties 编译作业配置
     */
    public CompileJobLeaseManager(
            CompileJobJdbcRepository compileJobJdbcRepository,
            CompileJobProperties compileJobProperties
    ) {
        this.compileJobJdbcRepository = compileJobJdbcRepository;
        this.compileJobProperties = compileJobProperties;
        this.scheduledExecutorService = Executors.newScheduledThreadPool(
                2,
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setDaemon(true);
                    thread.setName("compile-job-lease-manager");
                    return thread;
                }
        );
        this.scheduledExecutorService.scheduleWithFixedDelay(
                this::recoverExpiredJobsSafely,
                compileJobProperties.getHeartbeatIntervalSeconds(),
                compileJobProperties.getHeartbeatIntervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    /**
     * 为运行中的作业注册后台续租任务。
     *
     * @param jobId 作业标识
     */
    public void registerRunningJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        cancelJob(jobId);
        ScheduledFuture<?> heartbeatFuture = scheduledExecutorService.scheduleWithFixedDelay(
                () -> refreshHeartbeat(jobId),
                compileJobProperties.getHeartbeatIntervalSeconds(),
                compileJobProperties.getHeartbeatIntervalSeconds(),
                TimeUnit.SECONDS
        );
        heartbeatFutures.put(jobId, heartbeatFuture);
    }

    /**
     * 刷新当前步骤并同步续租。
     *
     * @param jobId 作业标识
     * @param currentStep 当前步骤
     * @param progressMessage 进度提示文案
     */
    public void touchCurrentStep(String jobId, String currentStep, String progressMessage) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        OffsetDateTime heartbeatAt = OffsetDateTime.now();
        OffsetDateTime runningExpiresAt = heartbeatAt.plusSeconds(compileJobProperties.getLeaseDurationSeconds());
        compileJobJdbcRepository.updateCurrentStep(
                jobId,
                compileJobProperties.getWorkerId(),
                currentStep,
                progressMessage,
                heartbeatAt,
                runningExpiresAt
        );
    }

    /**
     * 刷新当前步骤进度并同步续租。
     *
     * @param jobId 作业标识
     * @param currentStep 当前步骤
     * @param progressCurrent 当前进度
     * @param progressTotal 总进度
     * @param progressMessage 进度提示文案
     */
    public void touchProgress(
            String jobId,
            String currentStep,
            int progressCurrent,
            int progressTotal,
            String progressMessage
    ) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        OffsetDateTime heartbeatAt = OffsetDateTime.now();
        OffsetDateTime runningExpiresAt = heartbeatAt.plusSeconds(compileJobProperties.getLeaseDurationSeconds());
        compileJobJdbcRepository.updateProgressSnapshot(
                jobId,
                compileJobProperties.getWorkerId(),
                currentStep,
                progressCurrent,
                progressTotal,
                progressMessage,
                heartbeatAt,
                runningExpiresAt
        );
    }

    /**
     * 取消指定作业的后台续租任务。
     *
     * @param jobId 作业标识
     */
    public void cancelJob(String jobId) {
        ScheduledFuture<?> heartbeatFuture = heartbeatFutures.remove(jobId);
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
        }
    }

    /**
     * 扫描并收口所有已过期的运行中作业。
     */
    public void recoverExpiredJobs() {
        OffsetDateTime now = OffsetDateTime.now();
        List<String> expiredJobIds = compileJobJdbcRepository.findExpiredRunningJobIds(now);
        for (String expiredJobId : expiredJobIds) {
            boolean updated = compileJobJdbcRepository.markFailedIfRunning(
                    expiredJobId,
                    "COMPILE_STALE_TIMEOUT",
                    STALE_ERROR_MESSAGE,
                    now
            );
            if (updated) {
                cancelJob(expiredJobId);
            }
        }
    }

    /**
     * 关闭后台续租线程池。
     */
    @Override
    public void destroy() {
        for (String jobId : heartbeatFutures.keySet()) {
            cancelJob(jobId);
        }
        scheduledExecutorService.shutdownNow();
    }

    /**
     * 刷新指定作业的后台心跳。
     *
     * @param jobId 作业标识
     */
    private void refreshHeartbeat(String jobId) {
        OffsetDateTime heartbeatAt = OffsetDateTime.now();
        OffsetDateTime runningExpiresAt = heartbeatAt.plusSeconds(compileJobProperties.getLeaseDurationSeconds());
        boolean refreshed = compileJobJdbcRepository.refreshHeartbeat(
                jobId,
                compileJobProperties.getWorkerId(),
                heartbeatAt,
                runningExpiresAt
        );
        if (!refreshed) {
            cancelJob(jobId);
        }
    }

    /**
     * 安全执行陈旧任务回收，避免后台线程异常退出。
     */
    private void recoverExpiredJobsSafely() {
        try {
            recoverExpiredJobs();
        }
        catch (RuntimeException ex) {
            log.warn("compile job stale recovery failed", ex);
        }
    }
}
