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
                            job_id, source_dir, incremental, orchestration_mode, status, persisted_count,
                            error_message, attempt_count, requested_at, started_at, finished_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (job_id) do update
                        set source_dir = excluded.source_dir,
                            incremental = excluded.incremental,
                            orchestration_mode = excluded.orchestration_mode,
                            status = excluded.status,
                            persisted_count = excluded.persisted_count,
                            error_message = excluded.error_message,
                            attempt_count = excluded.attempt_count,
                            requested_at = excluded.requested_at,
                            started_at = excluded.started_at,
                            finished_at = excluded.finished_at
                        """,
                compileJobRecord.getJobId(),
                compileJobRecord.getSourceDir(),
                compileJobRecord.isIncremental(),
                compileJobRecord.getOrchestrationMode(),
                compileJobRecord.getStatus(),
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
                        select job_id, source_dir, incremental, orchestration_mode, status, persisted_count,
                               error_message, attempt_count, requested_at, started_at, finished_at
                        from compile_jobs
                        order by requested_at desc, job_id desc
                        """,
                this::mapCompileJobRecord
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
                        select job_id, source_dir, incremental, orchestration_mode, status, persisted_count,
                               error_message, attempt_count, requested_at, started_at, finished_at
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
                        select job_id, source_dir, incremental, orchestration_mode, status, persisted_count,
                               error_message, attempt_count, requested_at, started_at, finished_at
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
     * @param startedAt 开始时间
     * @return 是否成功抢占
     */
    public boolean markRunning(String jobId, OffsetDateTime startedAt) {
        int updatedRows = jdbcTemplate.update(
                """
                        update compile_jobs
                        set status = 'RUNNING',
                            started_at = ?,
                            finished_at = null,
                            error_message = null,
                            attempt_count = attempt_count + 1
                        where job_id = ?
                          and status = 'QUEUED'
                        """,
                startedAt,
                jobId
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
                            persisted_count = ?,
                            error_message = null,
                            finished_at = ?
                        where job_id = ?
                        """,
                persistedCount,
                finishedAt,
                jobId
        );
    }

    /**
     * 将作业标记为失败。
     *
     * @param jobId 作业标识
     * @param errorMessage 错误信息
     * @param finishedAt 完成时间
     */
    public void markFailed(String jobId, String errorMessage, OffsetDateTime finishedAt) {
        jdbcTemplate.update(
                """
                        update compile_jobs
                        set status = 'FAILED',
                            error_message = ?,
                            finished_at = ?
                        where job_id = ?
                        """,
                errorMessage,
                finishedAt,
                jobId
        );
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
                resultSet.getBoolean("incremental"),
                resultSet.getString("orchestration_mode"),
                resultSet.getString("status"),
                resultSet.getInt("persisted_count"),
                resultSet.getString("error_message"),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("requested_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class)
        );
    }
}
