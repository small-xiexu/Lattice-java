package com.xbk.lattice.query.service;

import com.xbk.lattice.query.domain.AnswerOutcome;

import java.util.List;

/**
 * 答案覆盖校验结果
 *
 * 职责：承载结构化答案覆盖状态、缺失项与建议答案语义
 *
 * @author xiexu
 */
public class AnswerCoverageCheckResult {

    private final AnswerCoverageStatus coverageStatus;

    private final List<String> missingItems;

    private final AnswerOutcome suggestedOutcome;

    /**
     * 创建答案覆盖校验结果。
     *
     * @param coverageStatus 覆盖状态
     * @param missingItems 缺失项
     * @param suggestedOutcome 建议答案语义
     */
    public AnswerCoverageCheckResult(
            AnswerCoverageStatus coverageStatus,
            List<String> missingItems,
            AnswerOutcome suggestedOutcome
    ) {
        this.coverageStatus = coverageStatus == null ? AnswerCoverageStatus.NOT_APPLICABLE : coverageStatus;
        this.missingItems = missingItems == null ? List.of() : List.copyOf(missingItems);
        this.suggestedOutcome = suggestedOutcome == null ? AnswerOutcome.SUCCESS : suggestedOutcome;
    }

    /**
     * 创建不适用覆盖校验的结果。
     *
     * @return 覆盖校验结果
     */
    public static AnswerCoverageCheckResult notApplicable() {
        return new AnswerCoverageCheckResult(
                AnswerCoverageStatus.NOT_APPLICABLE,
                List.of(),
                AnswerOutcome.SUCCESS
        );
    }

    /**
     * 创建完整覆盖结果。
     *
     * @return 覆盖校验结果
     */
    public static AnswerCoverageCheckResult covered() {
        return new AnswerCoverageCheckResult(
                AnswerCoverageStatus.COVERED,
                List.of(),
                AnswerOutcome.SUCCESS
        );
    }

    /**
     * 创建部分覆盖结果。
     *
     * @param missingItems 缺失项
     * @return 覆盖校验结果
     */
    public static AnswerCoverageCheckResult partial(List<String> missingItems) {
        return new AnswerCoverageCheckResult(
                AnswerCoverageStatus.PARTIAL,
                missingItems,
                AnswerOutcome.PARTIAL_ANSWER
        );
    }

    /**
     * 创建缺失覆盖结果。
     *
     * @param missingItems 缺失项
     * @return 覆盖校验结果
     */
    public static AnswerCoverageCheckResult missing(List<String> missingItems) {
        return new AnswerCoverageCheckResult(
                AnswerCoverageStatus.MISSING,
                missingItems,
                AnswerOutcome.INSUFFICIENT_EVIDENCE
        );
    }

    /**
     * 获取覆盖状态。
     *
     * @return 覆盖状态
     */
    public AnswerCoverageStatus getCoverageStatus() {
        return coverageStatus;
    }

    /**
     * 获取缺失项。
     *
     * @return 缺失项
     */
    public List<String> getMissingItems() {
        return missingItems;
    }

    /**
     * 获取建议答案语义。
     *
     * @return 建议答案语义
     */
    public AnswerOutcome getSuggestedOutcome() {
        return suggestedOutcome;
    }
}
