package com.xbk.lattice.governance;

import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
public class HistoryService {

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    /**
     * 创建历史服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     * @param articleIdentityResolver 文章身份解析服务
     */
    @Autowired
    public HistoryService(
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver
    ) {
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
    }

    /**
     * 创建兼容旧构造方式的历史服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     */
    public HistoryService(ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository) {
        this(articleSnapshotJdbcRepository, null);
    }

    /**
     * 查询指定概念的历史快照。
     *
     * @param conceptId 概念标识
     * @param limit 返回数量
     * @return 历史报告
     */
    public HistoryReport history(String conceptId, int limit) {
        return history(conceptId, null, limit);
    }

    /**
     * 查询指定文章的历史快照。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @param limit 返回数量
     * @return 历史报告
     */
    public HistoryReport history(String articleId, Long sourceId, int limit) {
        if (articleSnapshotJdbcRepository == null) {
            return new HistoryReport(sourceId, articleId, articleId, List.of());
        }
        ArticleRecord articleRecord = articleIdentityResolver == null
                ? null
                : articleIdentityResolver.resolve(articleId, sourceId).orElse(null);
        if (articleRecord != null && articleRecord.getArticleKey() != null && !articleRecord.getArticleKey().isBlank()) {
            return new HistoryReport(
                    articleRecord.getSourceId(),
                    articleRecord.getArticleKey(),
                    articleRecord.getConceptId(),
                    articleSnapshotJdbcRepository.findByArticleKey(articleRecord.getArticleKey(), limit)
            );
        }
        return new HistoryReport(
                sourceId,
                articleId,
                articleId,
                articleSnapshotJdbcRepository.findByConceptId(articleId, limit)
        );
    }
}
