package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 质量指标服务
 *
 * 职责：汇总知识文章审查状态与反馈沉淀情况
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QualityMetricsService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

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
        this.articleJdbcRepository = articleJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
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
        }
        return new QualityMetricsReport(
                articleRecords.size(),
                passedArticles,
                pendingReviewArticles,
                needsHumanReviewArticles,
                contributionJdbcRepository.findAll().size(),
                sourceFileJdbcRepository.findAll().size()
        );
    }
}
