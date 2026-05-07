package com.xbk.lattice.compiler.service;

import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;

import java.util.List;

/**
 * 事实证据卡审查结果
 *
 * 职责：承载卡级质量审查状态与原因
 *
 * @author xiexu
 */
public class FactCardReviewResult {

    private final FactCardReviewStatus reviewStatus;

    private final List<String> reasons;

    /**
     * 创建事实证据卡审查结果。
     *
     * @param reviewStatus 审查状态
     * @param reasons 审查原因
     */
    public FactCardReviewResult(FactCardReviewStatus reviewStatus, List<String> reasons) {
        this.reviewStatus = reviewStatus == null ? FactCardReviewStatus.NEEDS_HUMAN_REVIEW : reviewStatus;
        this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    /**
     * 创建有效审查结果。
     *
     * @return 审查结果
     */
    public static FactCardReviewResult valid() {
        return new FactCardReviewResult(FactCardReviewStatus.VALID, List.of());
    }

    /**
     * 创建指定状态的审查结果。
     *
     * @param reviewStatus 审查状态
     * @param reasons 审查原因
     * @return 审查结果
     */
    public static FactCardReviewResult of(FactCardReviewStatus reviewStatus, List<String> reasons) {
        return new FactCardReviewResult(reviewStatus, reasons);
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public FactCardReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取审查原因。
     *
     * @return 审查原因
     */
    public List<String> getReasons() {
        return reasons;
    }
}
