package com.xbk.lattice.governance;

import java.time.OffsetDateTime;

/**
 * 质量趋势摘要
 *
 * 职责：承载最近时间窗口内质量指标的 delta 变化
 *
 * @author xiexu
 */
public class QualityMetricsTrend {

    private final int days;

    private final OffsetDateTime latestMeasuredAt;

    private final double reviewPassRateDelta;

    private final double groundingRateDelta;

    private final double referentialRateDelta;

    private final int totalArticlesDelta;

    /**
     * 创建质量趋势摘要。
     */
    public QualityMetricsTrend(
            int days,
            OffsetDateTime latestMeasuredAt,
            double reviewPassRateDelta,
            double groundingRateDelta,
            double referentialRateDelta,
            int totalArticlesDelta
    ) {
        this.days = days;
        this.latestMeasuredAt = latestMeasuredAt;
        this.reviewPassRateDelta = reviewPassRateDelta;
        this.groundingRateDelta = groundingRateDelta;
        this.referentialRateDelta = referentialRateDelta;
        this.totalArticlesDelta = totalArticlesDelta;
    }

    public int getDays() {
        return days;
    }

    public OffsetDateTime getLatestMeasuredAt() {
        return latestMeasuredAt;
    }

    public double getReviewPassRateDelta() {
        return reviewPassRateDelta;
    }

    public double getGroundingRateDelta() {
        return groundingRateDelta;
    }

    public double getReferentialRateDelta() {
        return referentialRateDelta;
    }

    public int getTotalArticlesDelta() {
        return totalArticlesDelta;
    }
}
