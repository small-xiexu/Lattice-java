package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.QualityMetricsHistoryJdbcRepository;
import com.xbk.lattice.infra.persistence.QualityMetricsHistoryRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 质量指标服务
 *
 * 职责：汇总知识文章审查状态与反馈沉淀情况
 *
 * @author xiexu
 */
@Service
public class QualityMetricsService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final QualityMetricsHistoryJdbcRepository qualityMetricsHistoryJdbcRepository;

    /**
     * 创建质量指标服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param contributionJdbcRepository contribution 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public QualityMetricsService(
            ArticleJdbcRepository articleJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this(
                articleJdbcRepository,
                contributionJdbcRepository,
                sourceFileJdbcRepository,
                null
        );
    }

    /**
     * 创建质量指标服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param contributionJdbcRepository contribution 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param qualityMetricsHistoryJdbcRepository 质量历史仓储
     */
    @Autowired
    public QualityMetricsService(
            ArticleJdbcRepository articleJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            QualityMetricsHistoryJdbcRepository qualityMetricsHistoryJdbcRepository
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.qualityMetricsHistoryJdbcRepository = qualityMetricsHistoryJdbcRepository;
    }

    /**
     * 生成最小质量指标报告。
     *
     * @return 质量指标报告
     */
    public QualityMetricsReport measure() {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        int passedArticles = 0;
        int pendingReviewArticles = 0;
        int needsHumanReviewArticles = 0;
        int groundedArticles = 0;
        int referentialArticles = 0;
        for (ArticleRecord articleRecord : articleRecords) {
            if ("passed".equalsIgnoreCase(articleRecord.getReviewStatus())) {
                passedArticles++;
            }
            else if ("needs_human_review".equalsIgnoreCase(articleRecord.getReviewStatus())) {
                needsHumanReviewArticles++;
            }
            else {
                pendingReviewArticles++;
            }
            if (articleRecord.getSourcePaths() != null && !articleRecord.getSourcePaths().isEmpty()) {
                groundedArticles++;
            }
            if (articleRecord.getReferentialKeywords() != null && !articleRecord.getReferentialKeywords().isEmpty()) {
                referentialArticles++;
            }
        }
        int contributionCount = contributionJdbcRepository.findAll().size();
        int sourceFileCount = sourceFileJdbcRepository.findAll().size();
        double reviewPassRate = percentage(passedArticles, articleRecords.size());
        double groundingRate = percentage(groundedArticles, articleRecords.size());
        double referentialRate = percentage(referentialArticles, articleRecords.size());
        if (qualityMetricsHistoryJdbcRepository != null) {
            qualityMetricsHistoryJdbcRepository.save(new QualityMetricsHistoryRecord(
                    0L,
                    OffsetDateTime.now(),
                    articleRecords.size(),
                    passedArticles,
                    pendingReviewArticles,
                    needsHumanReviewArticles,
                    contributionCount,
                    sourceFileCount,
                    reviewPassRate,
                    groundingRate,
                    referentialRate
            ));
        }
        return new QualityMetricsReport(
                articleRecords.size(),
                passedArticles,
                pendingReviewArticles,
                needsHumanReviewArticles,
                contributionCount,
                sourceFileCount
        );
    }

    /**
     * 计算最近 N 天的质量趋势。
     *
     * @param days 天数
     * @return 趋势摘要
     */
    public QualityMetricsTrend trend(int days) {
        if (qualityMetricsHistoryJdbcRepository == null) {
            return new QualityMetricsTrend(days, null, 0.0D, 0.0D, 0.0D, 0);
        }
        List<QualityMetricsHistoryRecord> records = qualityMetricsHistoryJdbcRepository.findSince(days);
        if (records.isEmpty()) {
            return new QualityMetricsTrend(days, null, 0.0D, 0.0D, 0.0D, 0);
        }
        QualityMetricsHistoryRecord latest = records.get(0);
        QualityMetricsHistoryRecord earliest = records.get(0);
        for (QualityMetricsHistoryRecord record : records) {
            if (record.getMeasuredAt() != null
                    && (latest.getMeasuredAt() == null || record.getMeasuredAt().isAfter(latest.getMeasuredAt()))) {
                latest = record;
            }
            if (record.getMeasuredAt() != null
                    && (earliest.getMeasuredAt() == null || record.getMeasuredAt().isBefore(earliest.getMeasuredAt()))) {
                earliest = record;
            }
        }
        return new QualityMetricsTrend(
                days,
                latest.getMeasuredAt(),
                roundTwoDecimals(latest.getReviewPassRate() - earliest.getReviewPassRate()),
                roundTwoDecimals(latest.getGroundingRate() - earliest.getGroundingRate()),
                roundTwoDecimals(latest.getReferentialRate() - earliest.getReferentialRate()),
                latest.getTotalArticles() - earliest.getTotalArticles()
        );
    }

    private double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0D;
        }
        return roundTwoDecimals((double) numerator * 100.0D / (double) denominator);
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
