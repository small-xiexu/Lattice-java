package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 历史服务
 *
 * 职责：按 conceptId 返回文章快照历史
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class HistoryService {

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    /**
     * 创建历史服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     */
    public HistoryService(ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository) {
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
    }

    /**
     * 查询指定概念的历史快照。
     *
     * @param conceptId 概念标识
     * @param limit 返回数量
     * @return 历史报告
     */
    public HistoryReport history(String conceptId, int limit) {
        if (articleSnapshotJdbcRepository == null) {
            return new HistoryReport(conceptId, List.of());
        }
        return new HistoryReport(conceptId, articleSnapshotJdbcRepository.findByConceptId(conceptId, limit));
    }
}
