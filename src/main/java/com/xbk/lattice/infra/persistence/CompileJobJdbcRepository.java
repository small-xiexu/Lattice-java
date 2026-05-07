package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.CompileJobMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 编译作业 JDBC 仓储
 *
 * 职责：提供 compile_jobs 表的持久化与状态更新能力
 *
 * @author xiexu
 */
@Repository
public class CompileJobJdbcRepository {

    private final CompileJobMapper compileJobMapper;

    /**
     * 创建编译作业 JDBC 仓储。
     *
     * @param compileJobMapper 编译作业 Mapper
     */
    public CompileJobJdbcRepository(CompileJobMapper compileJobMapper) {
        this.compileJobMapper = compileJobMapper;
    }

    /**
     * 保存编译作业。
     *
     * @param compileJobRecord 编译作业记录
     */
    public void save(CompileJobRecord compileJobRecord) {
        compileJobMapper.upsert(compileJobRecord);
    }

    /**
     * 查询全部编译作业。
     *
     * @return 编译作业列表
     */
    public List<CompileJobRecord> findAll() {
        return compileJobMapper.findAll();
    }

    /**
     * 查询最近的编译作业。
     *
     * @param limit 返回数量
     * @return 最近编译作业列表
     */
    public List<CompileJobRecord> findRecent(int limit) {
        return compileJobMapper.findRecent(Math.max(limit, 1));
    }

    /**
     * 查询最近的独立编译作业。
     *
     * @param limit 返回数量
     * @return 最近独立编译作业列表
     */
    public List<CompileJobRecord> findRecentStandalone(int limit) {
        return compileJobMapper.findRecentStandalone(Math.max(limit, 1));
    }

    /**
     * 按资料源查询最近独立编译作业。
     *
     * @param sourceId 资料源主键
     * @param limit 返回数量
     * @return 最近独立编译作业列表
     */
    public List<CompileJobRecord> findRecentStandaloneBySourceId(Long sourceId, int limit) {
        return compileJobMapper.findRecentStandaloneBySourceId(sourceId, Math.max(limit, 1));
    }

    /**
     * 按作业标识查询编译作业。
     *
     * @param jobId 作业标识
     * @return 编译作业
     */
    public Optional<CompileJobRecord> findByJobId(String jobId) {
        return Optional.ofNullable(compileJobMapper.findByJobId(jobId));
    }

    /**
     * 查询最早排队中的作业。
     *
     * @return 最早排队中的作业
     */
    public Optional<CompileJobRecord> findNextQueued() {
        return Optional.ofNullable(compileJobMapper.findNextQueued());
    }

    /**
     * 将排队作业标记为运行中。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param startedAt 开始时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 是否成功抢占
     */
    public boolean markRunning(String jobId, String workerId, OffsetDateTime startedAt, OffsetDateTime runningExpiresAt) {
        int updatedRows = compileJobMapper.markRunning(jobId, workerId, startedAt, runningExpiresAt);
        return updatedRows > 0;
    }

    /**
     * 刷新运行中的任务心跳与租约。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param heartbeatAt 心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 是否刷新成功
     */
    public boolean refreshHeartbeat(
            String jobId,
            String workerId,
            OffsetDateTime heartbeatAt,
            OffsetDateTime runningExpiresAt
    ) {
        int updatedRows = compileJobMapper.refreshHeartbeat(jobId, workerId, heartbeatAt, runningExpiresAt);
        return updatedRows > 0;
    }

    /**
     * 刷新运行中的当前步骤与心跳。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param currentStep 当前步骤
     * @param progressMessage 进度提示文案
     * @param heartbeatAt 心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 是否刷新成功
     */
    public boolean updateCurrentStep(
            String jobId,
            String workerId,
            String currentStep,
            String progressMessage,
            OffsetDateTime heartbeatAt,
            OffsetDateTime runningExpiresAt
    ) {
        int updatedRows = compileJobMapper.updateCurrentStep(
                jobId,
                workerId,
                currentStep,
                progressMessage,
                heartbeatAt,
                runningExpiresAt
        );
        return updatedRows > 0;
    }

    /**
     * 刷新运行中的步骤进度快照与心跳。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param currentStep 当前步骤
     * @param progressCurrent 当前进度
     * @param progressTotal 总进度
     * @param progressMessage 进度提示文案
     * @param heartbeatAt 心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 是否刷新成功
     */
    public boolean updateProgressSnapshot(
            String jobId,
            String workerId,
            String currentStep,
            int progressCurrent,
            int progressTotal,
            String progressMessage,
            OffsetDateTime heartbeatAt,
            OffsetDateTime runningExpiresAt
    ) {
        int updatedRows = compileJobMapper.updateProgressSnapshot(
                jobId,
                workerId,
                currentStep,
                progressCurrent,
                progressTotal,
                progressMessage,
                heartbeatAt,
                runningExpiresAt
        );
        return updatedRows > 0;
    }

    /**
     * 将作业标记为成功。
     *
     * @param jobId 作业标识
     * @param persistedCount 持久化数量
     * @param finishedAt 完成时间
     */
    public void markSucceeded(String jobId, int persistedCount, OffsetDateTime finishedAt) {
        compileJobMapper.markSucceeded(jobId, persistedCount, finishedAt);
    }

    /**
     * 将作业标记为失败。
     *
     * @param jobId 作业标识
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @param finishedAt 完成时间
     */
    public void markFailed(String jobId, String errorCode, String errorMessage, OffsetDateTime finishedAt) {
        compileJobMapper.markFailed(jobId, errorCode, errorMessage, finishedAt);
    }

    /**
     * 仅在作业仍处于运行中时标记失败。
     *
     * @param jobId 作业标识
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @param finishedAt 完成时间
     * @return 是否更新成功
     */
    public boolean markFailedIfRunning(String jobId, String errorCode, String errorMessage, OffsetDateTime finishedAt) {
        int updatedRows = compileJobMapper.markFailedIfRunning(jobId, errorCode, errorMessage, finishedAt);
        return updatedRows > 0;
    }

    /**
     * 重试已失败作业。
     *
     * @param jobId 作业标识
     */
    public void retry(String jobId) {
        compileJobMapper.retry(jobId);
    }

    /**
     * 回收指定 worker 持有的运行中作业，重置为排队态，避免实例退出后长时间滞留 RUNNING。
     *
     * @param workerId worker 标识
     * @return 被回收的作业数
     */
    public int requeueRunningJobsOwnedByWorker(String workerId) {
        return compileJobMapper.requeueRunningJobsOwnedByWorker(workerId);
    }

    /**
     * 查询已过期的运行中作业。
     *
     * @param now 当前时间
     * @return 过期作业标识列表
     */
    public List<String> findExpiredRunningJobIds(OffsetDateTime now) {
        return compileJobMapper.findExpiredRunningJobIds(now);
    }
}
