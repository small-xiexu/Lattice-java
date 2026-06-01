package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Deep Research 摘要。
 *
 * <p>向调用方暴露深度研究路径的执行概览。当查询触发了多层深度研究编排时，
 * 这个摘要记录研究的层数、任务数、证据卡数量、LLM 调用统计和质量指标。
 * 普通问答场景下 routed 为 false，调用方通过判 routed 区分是否经过了深度研究。
 *
 * @author xiexu
 */
@Getter
public class DeepResearchSummary {

    /**
     * 是否进入了 Deep Research 路径。
     *
     * <p>当查询被 Deep Research 路由判定需要多层研究时为 true。调用方通过这个字段
     * 判断本次查询是否经历了深度研究编排——为 false 时其余字段的值无实际意义。</p>
     */
    private final boolean routed;

    /**
     * 研究层数。
     *
     * <p>Deep Research 编排执行的研究层数。每一层可以包含多个子任务，
     * 层数越多表示研究越深入。普通问答为 0。</p>
     */
    private final int layerCount;

    /**
     * 任务总数。
     *
     * <p>跨所有研究层执行的子任务总数，包括研究任务、汇总任务和交叉验证任务。
     * 该值反映了深度研究编排的计算量和调用规模。</p>
     */
    private final int taskCount;

    /**
     * 证据卡数量。
     *
     * <p>深度研究过程中生成的证据卡总数。每张证据卡包含一个研究子问题的发现、
     * 来源引用和置信度评估。调用方可以据此了解深度研究产出了多少条结构化证据。</p>
     */
    private final int evidenceCardCount;

    /**
     * LLM 调用次数。
     *
     * <p>深度研究编排过程中对 LLM 的总调用次数。这个值可以用于成本估算和性能分析。</p>
     */
    private final int llmCallCount;

    /**
     * 最终引用覆盖率。
     *
     * <p>深度研究答案中已验证引用占总引用的比例，取值范围 0.0 到 1.0。
     * 这个指标反映了深度研究产出的答案在引用层面的可靠性。</p>
     */
    private final double citationCoverage;

    /**
     * 是否为部分答案。
     *
     * <p>当深度研究无法完成全部研究计划、只能给出部分结论时为 true。
     * 调用方可以据此决定是否需要向用户提示"答案可能不完整"。</p>
     */
    private final boolean partialAnswer;

    /**
     * 是否存在冲突证据。
     *
     * <p>当深度研究过程中发现不同来源之间存在矛盾或冲突的证据时为 true。
     * 调用方可以据此决定是否需要向用户展示冲突信息，帮助用户理解答案的不确定性。</p>
     */
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
}
