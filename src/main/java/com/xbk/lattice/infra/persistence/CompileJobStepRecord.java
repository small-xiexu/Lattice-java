package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 编译步骤记录
 *
 * 职责：表示 compile_job_steps 表中的单条节点执行记录
 *
 * @author xiexu
 */
public class CompileJobStepRecord {

    private final String jobId;

    private final String stepExecutionId;

    private final String stepName;

    private final String agentRole;

    private final String modelRoute;

    private final int sequenceNo;

    private final String status;

    private final String summary;

    private final String inputSummary;

    private final String outputSummary;

    private final String errorMessage;

    private final OffsetDateTime startedAt;

    private final OffsetDateTime finishedAt;

    /**
     * 创建编译步骤记录。
     *
     * @param jobId 作业标识
     * @param stepExecutionId 单次步骤执行标识
     * @param stepName 步骤名称
     * @param agentRole Agent 角色
     * @param modelRoute 模型路由
     * @param sequenceNo 顺序号
     * @param status 状态
     * @param summary 摘要
     * @param inputSummary 输入摘要
     * @param outputSummary 输出摘要
     * @param errorMessage 错误信息
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     */
    public CompileJobStepRecord(
            String jobId,
            String stepExecutionId,
            String stepName,
            String agentRole,
            String modelRoute,
            int sequenceNo,
            String status,
            String summary,
            String inputSummary,
            String outputSummary,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        this.jobId = jobId;
        this.stepExecutionId = stepExecutionId;
        this.stepName = stepName;
        this.agentRole = agentRole;
        this.modelRoute = modelRoute;
        this.sequenceNo = sequenceNo;
        this.status = status;
        this.summary = summary;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /**
     * 获取作业标识。
     *
     * @return 作业标识
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * 获取单次步骤执行标识。
     *
     * @return 单次步骤执行标识
     */
    public String getStepExecutionId() {
        return stepExecutionId;
    }

    /**
     * 获取步骤名称。
     *
     * @return 步骤名称
     */
    public String getStepName() {
        return stepName;
    }

    /**
     * 获取 Agent 角色。
     *
     * @return Agent 角色
     */
    public String getAgentRole() {
        return agentRole;
    }

    /**
     * 获取模型路由。
     *
     * @return 模型路由
     */
    public String getModelRoute() {
        return modelRoute;
    }

    /**
     * 获取顺序号。
     *
     * @return 顺序号
     */
    public int getSequenceNo() {
        return sequenceNo;
    }

    /**
     * 获取状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取摘要。
     *
     * @return 摘要
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 获取输入摘要。
     *
     * @return 输入摘要
     */
    public String getInputSummary() {
        return inputSummary;
    }

    /**
     * 获取输出摘要。
     *
     * @return 输出摘要
     */
    public String getOutputSummary() {
        return outputSummary;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 获取开始时间。
     *
     * @return 开始时间
     */
    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * 获取结束时间。
     *
     * @return 结束时间
     */
    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
