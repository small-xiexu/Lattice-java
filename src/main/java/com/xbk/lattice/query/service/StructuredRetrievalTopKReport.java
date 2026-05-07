package com.xbk.lattice.query.service;

/**
 * 结构化检索 topK 质量报告
 *
 * 职责：承载目标证据进入 topK 的可复算指标
 *
 * @author xiexu
 */
public class StructuredRetrievalTopKReport {

    private final int sampleCount;

    private final int targetCount;

    private final int targetHitCount;

    private final int fullyCoveredSampleCount;

    /**
     * 创建结构化检索 topK 质量报告。
     *
     * @param sampleCount 样本数
     * @param targetCount 目标证据数
     * @param targetHitCount 命中的目标证据数
     * @param fullyCoveredSampleCount 目标证据全部命中的样本数
     */
    public StructuredRetrievalTopKReport(
            int sampleCount,
            int targetCount,
            int targetHitCount,
            int fullyCoveredSampleCount
    ) {
        this.sampleCount = sampleCount;
        this.targetCount = targetCount;
        this.targetHitCount = targetHitCount;
        this.fullyCoveredSampleCount = fullyCoveredSampleCount;
    }

    /**
     * 获取样本数。
     *
     * @return 样本数
     */
    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * 获取目标证据数。
     *
     * @return 目标证据数
     */
    public int getTargetCount() {
        return targetCount;
    }

    /**
     * 获取命中的目标证据数。
     *
     * @return 命中的目标证据数
     */
    public int getTargetHitCount() {
        return targetHitCount;
    }

    /**
     * 获取目标证据全部命中的样本数。
     *
     * @return 目标证据全部命中的样本数
     */
    public int getFullyCoveredSampleCount() {
        return fullyCoveredSampleCount;
    }

    /**
     * 获取目标证据进入 topK 比例。
     *
     * @return 目标证据命中率
     */
    public double getTargetHitRate() {
        if (targetCount == 0) {
            return 0.0D;
        }
        return (double) targetHitCount / (double) targetCount;
    }

    /**
     * 获取样本全覆盖比例。
     *
     * @return 样本全覆盖比例
     */
    public double getFullyCoveredSampleRate() {
        if (sampleCount == 0) {
            return 0.0D;
        }
        return (double) fullyCoveredSampleCount / (double) sampleCount;
    }

    /**
     * 判断是否通过 topK 命中率门槛。
     *
     * @param minimumTargetHitRate 最低目标证据命中率
     * @return 通过返回 true
     */
    public boolean passesGate(double minimumTargetHitRate) {
        return targetCount > 0 && getTargetHitRate() >= minimumTargetHitRate;
    }
}
