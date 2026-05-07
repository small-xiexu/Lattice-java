package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.CompileJobStepMapper;
import org.springframework.stereotype.Repository;

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
public class CompileJobStepJdbcRepository {

    private final CompileJobStepMapper compileJobStepMapper;

    /**
     * 创建编译步骤 JDBC 仓储。
     *
     * @param compileJobStepMapper 编译步骤 Mapper
     */
    public CompileJobStepJdbcRepository(CompileJobStepMapper compileJobStepMapper) {
        this.compileJobStepMapper = compileJobStepMapper;
    }

    /**
     * 新建运行中步骤记录。
     *
     * @param compileJobStepRecord 步骤记录
     */
    public void createRunningStep(CompileJobStepRecord compileJobStepRecord) {
        compileJobStepMapper.createRunningStep(compileJobStepRecord);
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
        compileJobStepMapper.markSucceeded(stepExecutionId, sequenceNo, summary, outputSummary, finishedAt);
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
        compileJobStepMapper.markFailed(stepExecutionId, sequenceNo, summary, errorMessage, finishedAt);
    }

    /**
     * 查询指定作业的步骤记录。
     *
     * @param jobId 作业标识
     * @return 步骤记录列表
     */
    public List<CompileJobStepRecord> findByJobId(String jobId) {
        return compileJobStepMapper.findByJobId(jobId);
    }
}
