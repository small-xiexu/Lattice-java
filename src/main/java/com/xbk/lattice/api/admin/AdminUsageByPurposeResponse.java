package com.xbk.lattice.api.admin;

/**
 * 管理侧 usage 按用途汇总响应
 *
 * 职责：承载 LLM usage 的用途维度聚合结果
 *
 * @author xiexu
 */
public class AdminUsageByPurposeResponse {

    private final String purpose;

    private final int callCount;

    private final int inputTokens;

    private final int outputTokens;

    private final double costUsd;

    /**
     * 创建按用途 usage 汇总响应。
     *
     * @param purpose 用途
     * @param callCount 调用次数
     * @param inputTokens 输入 token
     * @param outputTokens 输出 token
     * @param costUsd 成本
     */
    public AdminUsageByPurposeResponse(
            String purpose,
            int callCount,
            int inputTokens,
            int outputTokens,
            double costUsd
    ) {
        this.purpose = purpose;
        this.callCount = callCount;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
    }

    /**
     * 获取用途。
     *
     * @return 用途
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * 获取调用次数。
     *
     * @return 调用次数
     */
    public int getCallCount() {
        return callCount;
    }

    /**
     * 获取输入 token。
     *
     * @return 输入 token
     */
    public int getInputTokens() {
        return inputTokens;
    }

    /**
     * 获取输出 token。
     *
     * @return 输出 token
     */
    public int getOutputTokens() {
        return outputTokens;
    }

    /**
     * 获取成本。
     *
     * @return 成本
     */
    public double getCostUsd() {
        return costUsd;
    }
}
