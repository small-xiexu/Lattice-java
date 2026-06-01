package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Citation 检查摘要。
 *
 * <p>向调用方暴露最终答案的引用核验概览，包括各项引用计数、覆盖率、claim 层面的核验结果。
 * 调用方通过这个摘要快速判断答案的引用质量，不需要逐条查看引用细节。
 *
 * @author xiexu
 */
@Getter
public class CitationCheckSummary {

    /**
     * 已验证引用数。
     *
     * <p>引用核验链路确认可在来源中找到支撑的引用数量。调用方可以用它作为引用可信度的正面指标。</p>
     */
    private final int verifiedCount;

    /**
     * 已降级引用数。
     *
     * <p>引用核验链路标记为疑似编造或无法验证的引用数量。当这个值较高时，
     * 调用方应考虑向用户提示答案引用可能存在不实之处。</p>
     */
    private final int demotedCount;

    /**
     * 已跳过引用数。
     *
     * <p>引用核验链路因超出核验范围、来源不可用等原因未执行核验的引用数量。
     * 跳过不代表引用有问题，只代表未进行核验。</p>
     */
    private final int skippedCount;

    /**
     * 引用覆盖率。
     *
     * <p>已验证引用数占总引用数的比例，取值范围 0.0 到 1.0。
     * 1.0 表示所有引用都已通过核验。这个指标是 citationCheck 的核心质量信号。</p>
     */
    private final double coverageRate;

    /**
     * 是否无引用。
     *
     * <p>当答案没有任何引用标记时为 true。这通常意味着答案来自 fallback 确定性模板
     * 或系统判定不需要引用。调用方可以据此决定是否隐藏引用面板。</p>
     */
    private final boolean noCitation;

    /**
     * claim 总数。
     *
     * <p>答案被拆分出的可独立核验断言（claim）的总数。这个值反映了答案的复杂度——
     * claim 越多，答案包含的事实主张越多，引用核验的覆盖面要求越高。</p>
     */
    private final int claimCount;

    /**
     * 不受支持 claim 数。
     *
     * <p>没有任何已验证引用支撑的 claim 数量。当这个值大于 0 时，
     * 表示答案中存在完全没有依据的断言，调用方应高度关注。</p>
     */
    private final int unsupportedClaimCount;

    /**
     * 创建 Citation 检查摘要。
     *
     * @param verifiedCount 已验证引用数
     * @param demotedCount 已降级引用数
     * @param skippedCount 已跳过引用数
     * @param coverageRate 引用覆盖率
     * @param noCitation 是否无引用
     */
    public CitationCheckSummary(
            @JsonProperty("verifiedCount") int verifiedCount,
            @JsonProperty("demotedCount") int demotedCount,
            @JsonProperty("skippedCount") int skippedCount,
            @JsonProperty("coverageRate") double coverageRate,
            @JsonProperty("noCitation") boolean noCitation
    ) {
        this(verifiedCount, demotedCount, skippedCount, coverageRate, noCitation, 0, 0);
    }

    /**
     * 创建 Citation 检查摘要。
     *
     * @param verifiedCount 已验证引用数
     * @param demotedCount 已降级引用数
     * @param skippedCount 已跳过引用数
     * @param coverageRate 引用覆盖率
     * @param noCitation 是否无引用
     * @param claimCount claim 总数
     * @param unsupportedClaimCount 不受支持 claim 数
     */
    @JsonCreator
    public CitationCheckSummary(
            @JsonProperty("verifiedCount") int verifiedCount,
            @JsonProperty("demotedCount") int demotedCount,
            @JsonProperty("skippedCount") int skippedCount,
            @JsonProperty("coverageRate") double coverageRate,
            @JsonProperty("noCitation") boolean noCitation,
            @JsonProperty("claimCount") int claimCount,
            @JsonProperty("unsupportedClaimCount") int unsupportedClaimCount
    ) {
        this.verifiedCount = verifiedCount;
        this.demotedCount = demotedCount;
        this.skippedCount = skippedCount;
        this.coverageRate = coverageRate;
        this.noCitation = noCitation;
        this.claimCount = claimCount;
        this.unsupportedClaimCount = unsupportedClaimCount;
    }
}
