package com.xbk.lattice.compiler.graph;

/**
 * 步骤执行句柄
 *
 * 职责：承载单次节点执行的稳定日志关联令牌
 *
 * @author xiexu
 */
public class StepExecutionHandle {

    private final String stepExecutionId;

    private final int sequenceNo;

    /**
     * 创建步骤执行句柄。
     *
     * @param stepExecutionId 单次步骤执行标识
     * @param sequenceNo 当前作业内顺序号
     */
    public StepExecutionHandle(String stepExecutionId, int sequenceNo) {
        this.stepExecutionId = stepExecutionId;
        this.sequenceNo = sequenceNo;
    }

    /**
     * 返回单次步骤执行标识。
     *
     * @return 单次步骤执行标识
     */
    public String getStepExecutionId() {
        return stepExecutionId;
    }

    /**
     * 返回当前作业内顺序号。
     *
     * @return 当前作业内顺序号
     */
    public int getSequenceNo() {
        return sequenceNo;
    }
}
