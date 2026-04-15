package com.xbk.lattice.api.admin;

/**
 * 管理侧 usage 按模型汇总响应
 *
 * 职责：承载 LLM usage 的模型维度聚合结果
 *
 * @author xiexu
 */
public class AdminUsageByModelResponse {

    private final String model;

    private final int callCount;

    private final int inputTokens;

    private final int outputTokens;

    private final double costUsd;

    /**
     * 创建按模型 usage 汇总响应。
     *
     * @param model 模型
     * @param callCount 调用次数
     * @param inputTokens 输入 token
     * @param outputTokens 输出 token
     * @param costUsd 成本
     */
    public AdminUsageByModelResponse(
            String model,
            int callCount,
            int inputTokens,
            int outputTokens,
            double costUsd
    ) {
        this.model = model;
        this.callCount = callCount;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
    }

    /**
     * 获取模型。
     *
     * @return 模型
     */
    public String getModel() {
        return model;
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
