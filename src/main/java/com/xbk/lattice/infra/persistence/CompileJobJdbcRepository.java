package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
@Profile("jdbc")
public class CompileJobJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建编译作业 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public CompileJobJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存编译作业。
     *
     * @param compileJobRecord 编译作业记录
     */
    public void save(CompileJobRecord compileJobRecord) {
        jdbcTemplate.update(
                """
                        insert into compile_jobs (
                            job_id, source_dir, source_id, source_sync_run_id, root_trace_id, incremental,
                            orchestration_mode, status, worker_id, last_heartbeat_at, running_expires_at,
                            current_step, progress_current, progress_total, progress_message, progress_updated_at,
                            error_code, persisted_count, error_message,
                            attempt_count, requested_at, started_at, finished_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (job_id) do update
                        set source_dir = excluded.source_dir,
                            source_id = excluded.source_id,
                            source_sync_run_id = excluded.source_sync_run_id,
                            root_trace_id = excluded.root_trace_id,
                            incremental = excluded.incremental,
                            orchestration_mode = excluded.orchestration_mode,
                            status = excluded.status,
                            worker_id = excluded.worker_id,
                            last_heartbeat_at = excluded.last_heartbeat_at,
                            running_expires_at = excluded.running_expires_at,
                            current_step = excluded.current_step,
                            progress_current = excluded.progress_current,
                            progress_total = excluded.progress_total,
                            progress_message = excluded.progress_message,
                            progress_updated_at = excluded.progress_updated_at,
                            error_code = excluded.error_code,
                            persisted_count = excluded.persisted_count,
                            error_message = excluded.error_message,
                            attempt_count = excluded.attempt_count,
                            requested_at = excluded.requested_at,
                            started_at = excluded.started_at,
                            finished_at = excluded.finished_at
                        """,
                compileJobRecord.getJobId(),
                compileJobRecord.getSourceDir(),
                compileJobRecord.getSourceId(),
                compileJobRecord.getSourceSyncRunId(),
                compileJobRecord.getRootTraceId(),
                compileJobRecord.isIncremental(),
                compileJobRecord.getOrchestrationMode(),
                compileJobRecord.getStatus(),
                compileJobRecord.getWorkerId(),
                compileJobRecord.getLastHeartbeatAt(),
                compileJobRecord.getRunningExpiresAt(),
                compileJobRecord.getCurrentStep(),
                compileJobRecord.getProgressCurrent(),
                compileJobRecord.getProgressTotal(),
                compileJobRecord.getProgressMessage(),
                compileJobRecord.getProgressUpdatedAt(),
                compileJobRecord.getErrorCode(),
                compileJobRecord.getPersistedCount(),
                compileJobRecord.getErrorMessage(),
                compileJobRecord.getAttemptCount(),
                compileJobRecord.getRequestedAt(),
                compileJobRecord.getStartedAt(),
                compileJobRecord.getFinishedAt()
        );
    }

    /**
     * 查询全部编译作业。
     *
     * @return 编译作业列表
     */
    public List<CompileJobRecord> findAll() {
        return jdbcTemplate.query(
                """
                        select job_id, source_dir, source_id, source_sync_run_id, incremental,
                               root_trace_id, orchestration_mode, status, worker_id, last_heartbeat_at,
                               running_expires_at, current_step, progress_current, progress_total,
                               progress_message, progress_updated_at, error_code, persisted_count, error_message,
                               attempt_count, requested_at, started_at, finished_at
                        from compile_jobs
                        order by requested_at desc, job_id desc
                        """,
                this::mapCompileJobRecord
        );
    }

    /**
     * 查询最近的编译作业。
     *
     * @param limit 返回数量
     * @return 最近编译作业列表
     */
    public List<CompileJobRecord> findRecent(int limit) {
        return jdbcTemplate.query(
                """
                        select job_id, source_dir, source_id, source_sync_run_id, incremental,
                               root_trace_id, orchestration_mode, status, worker_id, last_heartbeat_at,
                               running_expires_at, current_step, progress_current, progress_total,
                               progress_message, progress_updated_at, error_code, persisted_count, error_message,
                               attempt_count, requested_at, started_at, finished_at
                        from compile_jobs
                        order by requested_at desc, job_id desc
                        limit ?
                        """,
                this::mapCompileJobRecord,
                Math.max(limit, 1)
        );
    }

    /**
     * 查询最近的独立编译作业。
     *
     * @param limit 返回数量
     * @return 最近独立编译作业列表
     */
    public List<CompileJobRecord> findRecentStandalone(int limit) {
        return jdbcTemplate.query(
                """
                        select job_id, source_dir, source_id, source_sync_run_id, incremental,
                               root_trace_id, orchestration_mode, status, worker_id, last_heartbeat_at,
                               running_expires_at, current_step, progress_current, progress_total,
                               progress_message, progress_updated_at, error_code, persisted_count, error_message,
                               attempt_count, requested_at, started_at, finished_at
                        from compile_jobs
                        where source_sync_run_id is null
                        order by requested_at desc, job_id desc
                        limit ?
                        """,
                this::mapCompileJobRecord,
                Math.max(limit, 1)
        );
    }

    /**
     * 按作业标识查询编译作业。
     *
     * @param jobId 作业标识
     * @return 编译作业
     */
    public Optional<CompileJobRecord> findByJobId(String jobId) {
        List<CompileJobRecord> records = jdbcTemplate.query(
                """
                        select job_id, source_dir, source_id, source_sync_run_id, incremental,
                               root_trace_id, orchestration_mode, status, worker_id, last_heartbeat_at,
                               running_expires_at, current_step, progress_current, progress_total,
                               progress_message, progress_updated_at, error_code, persisted_count, error_message,
                               attempt_count, requested_at, started_at, finished_at
                        from compile_jobs
                        where job_id = ?
                        """,
                this::mapCompileJobRecord,
                jobId
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 查询最早排队中的作业。
     *
     * @return 最早排队中的作业
     */
    public Optional<CompileJobRecord> findNextQueued() {
        List<CompileJobRecord> records = jdbcTemplate.query(
                """
                        select job_id, source_dir, source_id, source_sync_run_id, incremental,
                               root_trace_id, orchestration_mode, status, worker_id, last_heartbeat_at,
                               running_expires_at, current_step, progress_current, progress_total,
                               progress_message, progress_updated_at, error_code, persisted_count, error_message,
                               attempt_count, requested_at, started_at, finished_at
                        from compile_jobs
                        where status = 'QUEUED'
                        order by requested_at asc, job_id asc
                        limit 1
                        """,
                this::mapCompileJobRecord
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
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
        int updatedRows = jdbcTemplate.update(
                """
                        update compile_jobs
                        set status = 'RUNNING',
                            worker_id = ?,
                            last_heartbeat_at = ?,
                            running_expires_at = ?,
                            current_step = 'initialize_job',
                            progress_current = 0,
                            progress_total = 0,
                            progress_message = '编译任务已启动，等待图执行',
                            progress_updated_at = ?,
                            error_code = null,
                            started_at = ?,
                            finished_at = null,
                            error_message = null,
                            attempt_count = attempt_count + 1
                        where job_id = ?
                          and status = 'QUEUED'
                        """,
                workerId,
                startedAt,
                runningExpiresAt,
                startedAt,
                startedAt,
                jobId
        );
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
        int updatedRows = jdbcTemplate.update(
                """
                        update compile_jobs
                        set last_heartbeat_at = ?,
                            running_expires_at = ?
                        where job_id = ?
                          and status = 'RUNNING'
                          and worker_id = ?
                        """,
                heartbeatAt,
                runningExpiresAt,
                jobId,
                workerId
        );
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
        int updatedRows = jdbcTemplate.update(
                """
                        update compile_jobs
                        set current_step = ?,
                            progress_message = ?,
                            progress_updated_at = ?,
                            last_heartbeat_at = ?,
                            running_expires_at = ?
                        where job_id = ?
                          and status = 'RUNNING'
                          and worker_id = ?
                        """,
                currentStep,
                progressMessage,
                heartbeatAt,
                heartbeatAt,
                runningExpiresAt,
                jobId,
                workerId
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
        int updatedRows = jdbcTemplate.update(
                """
                        update compile_jobs
                        set current_step = ?,
                            progress_current = ?,
                            progress_total = ?,
                            progress_message = ?,
                            progress_updated_at = ?,
                            last_heartbeat_at = ?,
                            running_expires_at = ?
                        where job_id = ?
                          and status = 'RUNNING'
                          and worker_id = ?
                        """,
                currentStep,
                progressCurrent,
                progressTotal,
                progressMessage,
                heartbeatAt,
                heartbeatAt,
                runningExpiresAt,
                jobId,
                workerId
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
        jdbcTemplate.update(
                """
                        update compile_jobs
                        set status = 'SUCCEEDED',
                            last_heartbeat_at = ?,
                            running_expires_at = null,
                            current_step = 'finalize_job',
                            progress_message = '编译完成',
                            progress_updated_at = ?,
                            error_code = null,
                            persisted_count = ?,
                            error_message = null,
                            finished_at = ?
                        where job_id = ?
                        """,
                finishedAt,
                finishedAt,
                persistedCount,
                finishedAt,
                jobId
        );
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
        jdbcTemplate.update(
                """
                        update compile_jobs
                        set status = 'FAILED',
                            last_heartbeat_at = ?,
                            running_expires_at = null,
                            progress_updated_at = ?,
                            error_code = ?,
                            error_message = ?,
                            finished_at = ?
                        where job_id = ?
                        """,
                finishedAt,
                finishedAt,
                errorCode,
                errorMessage,
                finishedAt,
                jobId
        );
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
        int updatedRows = jdbcTemplate.update(
                """
                        update compile_jobs
                        set status = 'FAILED',
                            last_heartbeat_at = ?,
                            running_expires_at = null,
                            progress_updated_at = ?,
                            error_code = ?,
                            error_message = ?,
                            finished_at = ?
                        where job_id = ?
                          and status = 'RUNNING'
                        """,
                finishedAt,
                finishedAt,
                errorCode,
                errorMessage,
                finishedAt,
                jobId
        );
        return updatedRows > 0;
    }

    /**
     * 重试已失败作业。
     *
     * @param jobId 作业标识
     */
    public void retry(String jobId) {
        jdbcTemplate.update(
                """
                        update compile_jobs
                        set status = 'QUEUED',
                            worker_id = null,
                            last_heartbeat_at = null,
                            running_expires_at = null,
                            current_step = null,
                            progress_current = 0,
                            progress_total = 0,
                            progress_message = null,
                            progress_updated_at = null,
                            error_code = null,
                            persisted_count = 0,
                            error_message = null,
                            started_at = null,
                            finished_at = null
                        where job_id = ?
                        """,
                jobId
        );
    }

    /**
     * 查询已过期的运行中作业。
     *
     * @param now 当前时间
     * @return 过期作业标识列表
     */
    public List<String> findExpiredRunningJobIds(OffsetDateTime now) {
        return jdbcTemplate.queryForList(
                """
                        select job_id
                        from compile_jobs
                        where status = 'RUNNING'
                          and running_expires_at is not null
                          and running_expires_at < ?
                        order by running_expires_at asc, job_id asc
                        """,
                String.class,
                now
        );
    }

    /**
     * 映射编译作业记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 编译作业记录
     * @throws SQLException SQL 异常
     */
    private CompileJobRecord mapCompileJobRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new CompileJobRecord(
                resultSet.getString("job_id"),
                resultSet.getString("source_dir"),
                readLong(resultSet, "source_id"),
                readLong(resultSet, "source_sync_run_id"),
                resultSet.getString("root_trace_id"),
                resultSet.getBoolean("incremental"),
                resultSet.getString("orchestration_mode"),
                resultSet.getString("status"),
                resultSet.getString("worker_id"),
                resultSet.getObject("last_heartbeat_at", OffsetDateTime.class),
                resultSet.getObject("running_expires_at", OffsetDateTime.class),
                resultSet.getString("current_step"),
                resultSet.getInt("progress_current"),
                resultSet.getInt("progress_total"),
                resultSet.getString("progress_message"),
                resultSet.getObject("progress_updated_at", OffsetDateTime.class),
                resultSet.getString("error_code"),
                resultSet.getInt("persisted_count"),
                resultSet.getString("error_message"),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("requested_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class)
        );
    }

    private Long readLong(ResultSet resultSet, String columnName) throws SQLException {
        Object value = resultSet.getObject(columnName);
        return value == null ? null : resultSet.getLong(columnName);
    }
}
