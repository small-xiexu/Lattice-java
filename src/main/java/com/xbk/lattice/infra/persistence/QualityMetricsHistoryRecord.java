package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 质量指标历史记录
 *
 * 职责：承载单次质量测量的快照与派生比率
 *
 * @author xiexu
 */
public class QualityMetricsHistoryRecord {

    private final long id;

    private final OffsetDateTime measuredAt;

    private final int totalArticles;

    private final int passedArticles;

    private final int pendingArticles;

    private final int needsReview;

    private final int contributions;

    private final int sourceCount;

    private final double reviewPassRate;

    private final double groundingRate;

    private final double referentialRate;

    /**
     * 创建质量指标历史记录。
     */
    public QualityMetricsHistoryRecord(
            long id,
            OffsetDateTime measuredAt,
            int totalArticles,
            int passedArticles,
            int pendingArticles,
            int needsReview,
            int contributions,
            int sourceCount,
            double reviewPassRate,
            double groundingRate,
            double referentialRate
    ) {
        this.id = id;
        this.measuredAt = measuredAt;
        this.totalArticles = totalArticles;
        this.passedArticles = passedArticles;
        this.pendingArticles = pendingArticles;
        this.needsReview = needsReview;
        this.contributions = contributions;
        this.sourceCount = sourceCount;
        this.reviewPassRate = reviewPassRate;
        this.groundingRate = groundingRate;
        this.referentialRate = referentialRate;
    }

    public long getId() {
        return id;
    }

    public OffsetDateTime getMeasuredAt() {
        return measuredAt;
    }

    public int getTotalArticles() {
        return totalArticles;
    }

    public int getPassedArticles() {
        return passedArticles;
    }

    public int getPendingArticles() {
        return pendingArticles;
    }

    public int getNeedsReview() {
        return needsReview;
    }

    public int getContributions() {
        return contributions;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public double getReviewPassRate() {
        return reviewPassRate;
    }

    public double getGroundingRate() {
        return groundingRate;
    }

    public double getReferentialRate() {
        return referentialRate;
    }
}
