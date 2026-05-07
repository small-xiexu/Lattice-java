package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;

import java.util.Locale;

/**
 * Fact Card 审查状态使用策略
 *
 * 职责：统一定义不同卡级 review status 在 query 检索、融合与回答中的使用方式
 *
 * @author xiexu
 */
public final class FactCardReviewUsagePolicy {

    private static final double LOW_CONFIDENCE_SCORE_FACTOR = 0.45D;

    private static final double NEEDS_HUMAN_REVIEW_SCORE_FACTOR = 0.20D;

    private FactCardReviewUsagePolicy() {
    }

    /**
     * 判断 fact card 是否允许作为 query 候选。
     *
     * @param reviewStatus 审查状态
     * @return 允许返回 true
     */
    public static boolean allowsQueryCandidate(String reviewStatus) {
        FactCardReviewStatus status = parseStatus(reviewStatus);
        return status != FactCardReviewStatus.CONFLICT;
    }

    /**
     * 判断 fact card 是否允许作为结构化主证据。
     *
     * @param reviewStatus 审查状态
     * @return 允许返回 true
     */
    public static boolean allowsPrimaryEvidence(String reviewStatus) {
        FactCardReviewStatus status = parseStatus(reviewStatus);
        return status == FactCardReviewStatus.VALID
                || status == FactCardReviewStatus.INCOMPLETE
                || status == FactCardReviewStatus.LOW_CONFIDENCE;
    }

    /**
     * 判断 fact card 是否只作背景使用。
     *
     * @param reviewStatus 审查状态
     * @return 只作背景返回 true
     */
    public static boolean isBackgroundOnly(String reviewStatus) {
        return parseStatus(reviewStatus) == FactCardReviewStatus.NEEDS_HUMAN_REVIEW;
    }

    /**
     * 应用 fact card 审查状态对应的分数调整。
     *
     * @param score 原始分数
     * @param reviewStatus 审查状态
     * @return 调整后分数
     */
    public static double adjustScore(double score, String reviewStatus) {
        FactCardReviewStatus status = parseStatus(reviewStatus);
        if (status == FactCardReviewStatus.LOW_CONFIDENCE) {
            return score * LOW_CONFIDENCE_SCORE_FACTOR;
        }
        if (status == FactCardReviewStatus.NEEDS_HUMAN_REVIEW) {
            return score * NEEDS_HUMAN_REVIEW_SCORE_FACTOR;
        }
        return score;
    }

    /**
     * 按审查状态文本解析枚举。
     *
     * @param reviewStatus 审查状态文本
     * @return 审查状态
     */
    private static FactCardReviewStatus parseStatus(String reviewStatus) {
        if (reviewStatus == null || reviewStatus.isBlank()) {
            return FactCardReviewStatus.LOW_CONFIDENCE;
        }
        String normalizedStatus = reviewStatus.trim().toLowerCase(Locale.ROOT);
        if ("valid".equals(normalizedStatus)) {
            return FactCardReviewStatus.VALID;
        }
        if ("incomplete".equals(normalizedStatus)) {
            return FactCardReviewStatus.INCOMPLETE;
        }
        if ("conflict".equals(normalizedStatus)) {
            return FactCardReviewStatus.CONFLICT;
        }
        if ("low_confidence".equals(normalizedStatus)) {
            return FactCardReviewStatus.LOW_CONFIDENCE;
        }
        if ("needs_human_review".equals(normalizedStatus)) {
            return FactCardReviewStatus.NEEDS_HUMAN_REVIEW;
        }
        return FactCardReviewStatus.LOW_CONFIDENCE;
    }
}
