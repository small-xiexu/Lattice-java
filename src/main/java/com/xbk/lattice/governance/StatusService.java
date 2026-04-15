package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
public class StatusService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final PendingQueryManager pendingQueryManager;

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
        this.articleJdbcRepository = articleJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.pendingQueryManager = pendingQueryManager;
    }

    /**
     * 生成当前状态快照。
     *
     * @return 状态快照
     */
    public StatusSnapshot snapshot() {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        int reviewPendingArticleCount = 0;
        for (ArticleRecord articleRecord : articleRecords) {
            if (!"passed".equalsIgnoreCase(articleRecord.getReviewStatus())) {
                reviewPendingArticleCount++;
            }
        }
        return new StatusSnapshot(
                articleRecords.size(),
                sourceFileJdbcRepository.findAll().size(),
                contributionJdbcRepository.findAll().size(),
                pendingQueryManager.listPendingQueries().size(),
                reviewPendingArticleCount
        );
    }
}
