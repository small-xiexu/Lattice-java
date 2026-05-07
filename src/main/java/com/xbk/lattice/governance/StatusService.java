package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.AnswerFeedbackJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 状态服务
 *
 * 职责：汇总知识库文章、源文件、反馈与待确认状态
 *
 * @author xiexu
 */
@Service
public class StatusService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final PendingQueryManager pendingQueryManager;

    private final AnswerFeedbackJdbcRepository answerFeedbackJdbcRepository;

    /**
     * 创建状态服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param contributionJdbcRepository contribution 仓储
     * @param pendingQueryManager pending 查询管理器
     */
    public StatusService(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository,
            PendingQueryManager pendingQueryManager
    ) {
        this(articleJdbcRepository, sourceFileJdbcRepository, contributionJdbcRepository, pendingQueryManager, null);
    }

    /**
     * 创建状态服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param contributionJdbcRepository contribution 仓储
     * @param pendingQueryManager pending 查询管理器
     * @param answerFeedbackJdbcRepository 答案反馈仓储
     */
    @Autowired
    public StatusService(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository,
            PendingQueryManager pendingQueryManager,
            AnswerFeedbackJdbcRepository answerFeedbackJdbcRepository
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.pendingQueryManager = pendingQueryManager;
        this.answerFeedbackJdbcRepository = answerFeedbackJdbcRepository;
    }

    /**
     * 生成当前状态快照。
     *
     * @return 状态快照
     */
    public StatusSnapshot snapshot() {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        int reviewPendingArticleCount = 0;
        int highRiskArticleCount = 0;
        int hotspotPendingVerificationCount = 0;
        int userReportedAnswerCount = 0;
        for (ArticleRecord articleRecord : articleRecords) {
            if (!"passed".equalsIgnoreCase(articleRecord.getReviewStatus())) {
                reviewPendingArticleCount++;
            }
            if ("high".equalsIgnoreCase(articleRecord.getRiskLevel())) {
                highRiskArticleCount++;
            }
            if (articleRecord.isHotspot() && articleRecord.isRequiresResultVerification()) {
                hotspotPendingVerificationCount++;
            }
            if (containsRiskReason(articleRecord, "user_reported")) {
                userReportedAnswerCount++;
            }
        }
        return new StatusSnapshot(
                articleRecords.size(),
                sourceFileJdbcRepository.findAll().size(),
                contributionJdbcRepository.findAll().size(),
                pendingQueryManager.listPendingQueries().size(),
                reviewPendingArticleCount,
                highRiskArticleCount,
                hotspotPendingVerificationCount,
                userReportedAnswerCount,
                answerFeedbackJdbcRepository == null ? 0 : answerFeedbackJdbcRepository.countByStatus(AnswerFeedbackService.STATUS_PENDING)
        );
    }

    /**
     * 判断文章是否包含指定风险原因。
     *
     * @param articleRecord 文章记录
     * @param riskReason 风险原因
     * @return 是否包含
     */
    private boolean containsRiskReason(ArticleRecord articleRecord, String riskReason) {
        if (articleRecord.getRiskReasons() == null || articleRecord.getRiskReasons().isEmpty()) {
            return false;
        }
        for (String currentRiskReason : articleRecord.getRiskReasons()) {
            if (riskReason.equalsIgnoreCase(currentRiskReason)) {
                return true;
            }
        }
        return false;
    }
}
