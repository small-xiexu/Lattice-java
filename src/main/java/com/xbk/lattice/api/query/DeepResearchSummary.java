package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Deep Research 摘要
 *
 * 职责：向 Query API 暴露深度研究路径的执行概览
 *
 * @author xiexu
 */
public class DeepResearchSummary {

    private final boolean routed;

    private final int layerCount;

    private final int taskCount;

    private final int evidenceCardCount;

    private final int llmCallCount;

    private final double citationCoverage;

    private final boolean partialAnswer;

    private final boolean hasConflicts;

    /**
     * 创建 Deep Research 摘要。
     *
     * @param routed 是否进入 Deep Research
     * @param layerCount 层数
     * @param taskCount 任务数
     * @param evidenceCardCount 证据卡数量
     * @param llmCallCount LLM 调用次数
     * @param citationCoverage 最终引用覆盖率
     * @param partialAnswer 是否为部分答案
     * @param hasConflicts 是否存在冲突证据
     */
    @JsonCreator
    public DeepResearchSummary(
            @JsonProperty("routed") boolean routed,
            @JsonProperty("layerCount") int layerCount,
            @JsonProperty("taskCount") int taskCount,
            @JsonProperty("evidenceCardCount") int evidenceCardCount,
            @JsonProperty("llmCallCount") int llmCallCount,
            @JsonProperty("citationCoverage") double citationCoverage,
            @JsonProperty("partialAnswer") boolean partialAnswer,
            @JsonProperty("hasConflicts") boolean hasConflicts
    ) {
        this.routed = routed;
        this.layerCount = layerCount;
        this.taskCount = taskCount;
        this.evidenceCardCount = evidenceCardCount;
        this.llmCallCount = llmCallCount;
        this.citationCoverage = citationCoverage;
        this.partialAnswer = partialAnswer;
        this.hasConflicts = hasConflicts;
    }

    /**
     * 返回是否进入 Deep Research。
     *
     * @return 是否进入 Deep Research
     */
    public boolean isRouted() {
        return routed;
    }

    /**
     * 返回研究层数。
     *
     * @return 研究层数
     */
    public int getLayerCount() {
        return layerCount;
    }

    /**
     * 返回任务数量。
     *
     * @return 任务数量
     */
    public int getTaskCount() {
        return taskCount;
    }

    /**
     * 返回证据卡数量。
     *
     * @return 证据卡数量
     */
    public int getEvidenceCardCount() {
        return evidenceCardCount;
    }

    /**
     * 返回 LLM 调用次数。
     *
     * @return LLM 调用次数
     */
    public int getLlmCallCount() {
        return llmCallCount;
    }

    /**
     * 返回最终引用覆盖率。
     *
     * @return 最终引用覆盖率
     */
    public double getCitationCoverage() {
        return citationCoverage;
    }

    /**
     * 返回是否为部分答案。
     *
     * @return 是否为部分答案
     */
    public boolean isPartialAnswer() {
        return partialAnswer;
    }

    /**
     * 返回是否存在冲突证据。
     *
     * @return 是否存在冲突证据
     */
    public boolean isHasConflicts() {
        return hasConflicts;
    }
}
