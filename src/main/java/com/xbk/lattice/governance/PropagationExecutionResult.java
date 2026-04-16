package com.xbk.lattice.governance;

/**
 * 传播执行结果
 *
 * 职责：承载单次下游传播执行后的处理统计
 *
 * @author xiexu
 */
public class PropagationExecutionResult {

    private final int processed;

    private final int updated;

    private final int skipped;

    /**
     * 创建传播执行结果。
     *
     * @param processed 总处理数
     * @param updated 实际更新数
     * @param skipped 跳过数
     */
    public PropagationExecutionResult(int processed, int updated, int skipped) {
        this.processed = processed;
        this.updated = updated;
        this.skipped = skipped;
    }

    public int getProcessed() {
        return processed;
    }

    public int getUpdated() {
        return updated;
    }

    public int getSkipped() {
        return skipped;
    }
}
