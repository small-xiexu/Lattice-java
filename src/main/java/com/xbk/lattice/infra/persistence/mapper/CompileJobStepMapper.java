package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.CompileJobStepRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 编译步骤 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 compile_job_steps 表
 *
 * @author xiexu
 */
@Mapper
public interface CompileJobStepMapper {

    /**
     * 新建运行中步骤记录。
     *
     * @param record 步骤记录
     * @return 影响行数
     */
    int createRunningStep(@Param("record") CompileJobStepRecord record);

    /**
     * 将步骤标记为成功。
     *
     * @param stepExecutionId 单次步骤执行标识
     * @param sequenceNo 顺序号
     * @param summary 摘要
     * @param outputSummary 输出摘要
     * @param finishedAt 完成时间
     * @return 影响行数
     */
    int markSucceeded(
            @Param("stepExecutionId") String stepExecutionId,
            @Param("sequenceNo") int sequenceNo,
            @Param("summary") String summary,
            @Param("outputSummary") String outputSummary,
            @Param("finishedAt") OffsetDateTime finishedAt
    );

    /**
     * 将步骤标记为失败。
     *
     * @param stepExecutionId 单次步骤执行标识
     * @param sequenceNo 顺序号
     * @param summary 摘要
     * @param errorMessage 错误信息
     * @param finishedAt 完成时间
     * @return 影响行数
     */
    int markFailed(
            @Param("stepExecutionId") String stepExecutionId,
            @Param("sequenceNo") int sequenceNo,
            @Param("summary") String summary,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") OffsetDateTime finishedAt
    );

    /**
     * 查询指定作业的步骤记录。
     *
     * @param jobId 作业标识
     * @return 步骤记录列表
     */
    List<CompileJobStepRecord> findByJobId(@Param("jobId") String jobId);
}
