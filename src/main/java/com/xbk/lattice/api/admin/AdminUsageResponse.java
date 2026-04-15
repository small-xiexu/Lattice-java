package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 usage 汇总响应
 *
 * 职责：承载 LLM usage 的总体与分维度聚合结果
 *
 * @author xiexu
 */
public class AdminUsageResponse {

    private final int totalCalls;

    private final int totalInputTokens;

    private final int totalOutputTokens;

    private final double totalCostUsd;

    private final List<AdminUsageByPurposeResponse> purposes;

    private final List<AdminUsageByModelResponse> models;

    /**
     * 创建管理侧 usage 汇总响应。
     *
     * @param totalCalls 总调用次数
     * @param totalInputTokens 总输入 token
     * @param totalOutputTokens 总输出 token
     * @param totalCostUsd 总成本
     * @param purposes 按用途聚合
     * @param models 按模型聚合
     */
    public AdminUsageResponse(
            int totalCalls,
            int totalInputTokens,
            int totalOutputTokens,
            double totalCostUsd,
            List<AdminUsageByPurposeResponse> purposes,
            List<AdminUsageByModelResponse> models
    ) {
        this.totalCalls = totalCalls;
        this.totalInputTokens = totalInputTokens;
        this.totalOutputTokens = totalOutputTokens;
        this.totalCostUsd = totalCostUsd;
        this.purposes = purposes;
        this.models = models;
    }

    /**
     * 获取总调用次数。
     *
     * @return 总调用次数
     */
    public int getTotalCalls() {
        return totalCalls;
    }

    /**
     * 获取总输入 token。
     *
     * @return 总输入 token
     */
    public int getTotalInputTokens() {
        return totalInputTokens;
    }

    /**
     * 获取总输出 token。
     *
     * @return 总输出 token
     */
    public int getTotalOutputTokens() {
        return totalOutputTokens;
    }

    /**
     * 获取总成本。
     *
     * @return 总成本
     */
    public double getTotalCostUsd() {
        return totalCostUsd;
    }

    /**
     * 获取按用途聚合结果。
     *
     * @return 按用途聚合结果
     */
    public List<AdminUsageByPurposeResponse> getPurposes() {
        return purposes;
    }

    /**
     * 获取按模型聚合结果。
     *
     * @return 按模型聚合结果
     */
    public List<AdminUsageByModelResponse> getModels() {
        return models;
    }
}
