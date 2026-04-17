package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 编译步骤 JDBC 仓储
 *
 * 职责：提供 compile_job_steps 表的插入、更新与查询能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class CompileJobStepJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建编译步骤 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public CompileJobStepJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 新建运行中步骤记录。
     *
     * @param compileJobStepRecord 步骤记录
     */
    public void createRunningStep(CompileJobStepRecord compileJobStepRecord) {
        jdbcTemplate.update(
                """
                        insert into compile_job_steps (
                            job_id, step_execution_id, step_name, agent_role, model_route, sequence_no,
                            status, summary, input_summary, output_summary, error_message, started_at, finished_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                compileJobStepRecord.getJobId(),
                compileJobStepRecord.getStepExecutionId(),
                compileJobStepRecord.getStepName(),
                compileJobStepRecord.getAgentRole(),
                compileJobStepRecord.getModelRoute(),
                compileJobStepRecord.getSequenceNo(),
                compileJobStepRecord.getStatus(),
                compileJobStepRecord.getSummary(),
                compileJobStepRecord.getInputSummary(),
                compileJobStepRecord.getOutputSummary(),
                compileJobStepRecord.getErrorMessage(),
                compileJobStepRecord.getStartedAt(),
                compileJobStepRecord.getFinishedAt()
        );
    }

    /**
     * 将步骤标记为成功。
     *
     * @param stepExecutionId 单次步骤执行标识
     * @param sequenceNo 顺序号
     * @param summary 摘要
     * @param outputSummary 输出摘要
     * @param finishedAt 完成时间
     */
    public void markSucceeded(
            String stepExecutionId,
            int sequenceNo,
            String summary,
            String outputSummary,
            OffsetDateTime finishedAt
    ) {
        jdbcTemplate.update(
                """
                        update compile_job_steps
                        set status = 'succeeded',
                            summary = ?,
                            output_summary = ?,
                            error_message = null,
                            finished_at = ?
                        where step_execution_id = ?
                          and sequence_no = ?
                        """,
                summary,
                outputSummary,
                finishedAt,
                stepExecutionId,
                sequenceNo
        );
    }

    /**
     * 将步骤标记为失败。
     *
     * @param stepExecutionId 单次步骤执行标识
     * @param sequenceNo 顺序号
     * @param summary 摘要
     * @param errorMessage 错误信息
     * @param finishedAt 完成时间
     */
    public void markFailed(
            String stepExecutionId,
            int sequenceNo,
            String summary,
            String errorMessage,
            OffsetDateTime finishedAt
    ) {
        jdbcTemplate.update(
                """
                        update compile_job_steps
                        set status = 'failed',
                            summary = ?,
                            error_message = ?,
                            finished_at = ?
                        where step_execution_id = ?
                          and sequence_no = ?
                        """,
                summary,
                errorMessage,
                finishedAt,
                stepExecutionId,
                sequenceNo
        );
    }

    /**
     * 查询指定作业的步骤记录。
     *
     * @param jobId 作业标识
     * @return 步骤记录列表
     */
    public List<CompileJobStepRecord> findByJobId(String jobId) {
        return jdbcTemplate.query(
                """
                        select job_id, step_execution_id, step_name, agent_role, model_route,
                               sequence_no, status, summary, input_summary,
                               output_summary, error_message, started_at, finished_at
                        from compile_job_steps
                        where job_id = ?
                        order by sequence_no asc
                        """,
                this::mapCompileJobStepRecord,
                jobId
        );
    }

    private CompileJobStepRecord mapCompileJobStepRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new CompileJobStepRecord(
                resultSet.getString("job_id"),
                resultSet.getString("step_execution_id"),
                resultSet.getString("step_name"),
                resultSet.getString("agent_role"),
                resultSet.getString("model_route"),
                resultSet.getInt("sequence_no"),
                resultSet.getString("status"),
                resultSet.getString("summary"),
                resultSet.getString("input_summary"),
                resultSet.getString("output_summary"),
                resultSet.getString("error_message"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class)
        );
    }
}
