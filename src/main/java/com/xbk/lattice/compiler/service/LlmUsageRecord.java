package com.xbk.lattice.compiler.service;

import java.time.OffsetDateTime;

/**
 * LLM 用量记录
 *
 * 职责：表示单次模型调用的 token、成本与用途信息
 *
 * @author xiexu
 */
public class LlmUsageRecord {

    private final String callId;

    private final String model;

    private final String purpose;

    private final int inputTokens;

    private final int outputTokens;

    private final double costUsd;

    private final OffsetDateTime calledAt;

    /**
     * 创建 LLM 用量记录。
     *
     * @param callId 调用标识
     * @param model 模型标识
     * @param purpose 用途
     * @param inputTokens 输入 token
     * @param outputTokens 输出 token
     * @param costUsd 估算成本
     * @param calledAt 调用时间
     */
    public LlmUsageRecord(
            String callId,
            String model,
            String purpose,
            int inputTokens,
            int outputTokens,
            double costUsd,
            OffsetDateTime calledAt
    ) {
        this.callId = callId;
        this.model = model;
        this.purpose = purpose;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
        this.calledAt = calledAt;
    }

    /**
     * 获取调用标识。
     *
     * @return 调用标识
     */
    public String getCallId() {
        return callId;
    }

    /**
     * 获取模型标识。
     *
     * @return 模型标识
     */
    public String getModel() {
        return model;
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
     * 获取估算成本。
     *
     * @return 估算成本
     */
    public double getCostUsd() {
        return costUsd;
    }

    /**
     * 获取调用时间。
     *
     * @return 调用时间
     */
    public OffsetDateTime getCalledAt() {
        return calledAt;
    }
}
