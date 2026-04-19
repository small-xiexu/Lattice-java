package com.xbk.lattice.governance;

import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 快照服务
 *
 * 职责：返回最近文章快照摘要，供治理与 MCP 查询
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SnapshotService {

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    /**
     * 创建快照服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     */
    public SnapshotService(ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository) {
        this(articleSnapshotJdbcRepository, null, null);
    }

    /**
     * 创建快照服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     * @param articleJdbcRepository 文章仓储
     * @param articleIdentityResolver 文章身份解析服务
     */
    @Autowired
    public SnapshotService(
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver
    ) {
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
    }

    /**
     * 创建兼容旧构造方式的快照服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     * @param articleJdbcRepository 文章仓储
     */
    public SnapshotService(
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository
    ) {
        this(
                articleSnapshotJdbcRepository,
                articleJdbcRepository,
                articleJdbcRepository == null ? null : new ArticleIdentityResolver(articleJdbcRepository)
        );
    }

    /**
     * 查询最近文章快照。
     *
     * @param limit 返回数量
     * @return 快照报告
     */
    public SnapshotReport snapshot(int limit) {
        if (articleSnapshotJdbcRepository == null) {
            return new SnapshotReport(List.of());
        }
        return new SnapshotReport(articleSnapshotJdbcRepository.findRecent(limit));
    }

    /**
     * 根据快照恢复文章。
     *
     * @param conceptId 概念标识
     * @param snapshotId 快照标识
     * @return 回滚结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RollbackResult rollback(String conceptId, long snapshotId) {
        return rollback(conceptId, null, snapshotId);
    }

    /**
     * 根据快照恢复文章。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @param snapshotId 快照标识
     * @return 回滚结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RollbackResult rollback(String articleId, Long sourceId, long snapshotId) {
        if (articleSnapshotJdbcRepository == null || articleJdbcRepository == null) {
            throw new UnsupportedOperationException("rollback dependencies not configured");
        }
        ArticleSnapshotRecord snapshotRecord = articleSnapshotJdbcRepository.findBySnapshotId(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("快照不存在: " + snapshotId));
        if (!matchesSnapshotIdentity(snapshotRecord, articleId, sourceId)) {
            throw new IllegalArgumentException("快照与概念不匹配");
        }

        ArticleRecord restoredRecord = new ArticleRecord(
                snapshotRecord.getSourceId(),
                snapshotRecord.getArticleKey(),
                snapshotRecord.getConceptId(),
                snapshotRecord.getTitle(),
                snapshotRecord.getContent(),
                snapshotRecord.getLifecycle(),
                snapshotRecord.getCompiledAt(),
                snapshotRecord.getSourcePaths(),
                snapshotRecord.getMetadataJson(),
                snapshotRecord.getSummary(),
                snapshotRecord.getReferentialKeywords(),
                snapshotRecord.getDependsOn(),
                snapshotRecord.getRelated(),
                snapshotRecord.getConfidence(),
                snapshotRecord.getReviewStatus()
        );
        articleJdbcRepository.upsert(restoredRecord);
        OffsetDateTime restoredAt = OffsetDateTime.now();
        articleSnapshotJdbcRepository.save(ArticleSnapshotRecord.fromArticle(
                restoredRecord,
                "rollback|from:" + snapshotId,
                restoredAt
        ));
        return new RollbackResult(
                restoredRecord.getSourceId(),
                restoredRecord.getArticleKey(),
                restoredRecord.getConceptId(),
                snapshotId,
                restoredAt
        );
    }

    /**
     * 判断快照是否匹配目标文章身份。
     *
     * @param snapshotRecord 快照记录
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @return 是否匹配
     */
    private boolean matchesSnapshotIdentity(ArticleSnapshotRecord snapshotRecord, String articleId, Long sourceId) {
        if (articleId == null || articleId.isBlank()) {
            return false;
        }
        if (articleIdentityResolver != null) {
            ArticleRecord articleRecord = articleIdentityResolver.resolve(articleId, sourceId).orElse(null);
            if (articleRecord != null) {
                if (articleRecord.getArticleKey() != null && articleRecord.getArticleKey().equals(snapshotRecord.getArticleKey())) {
                    return true;
                }
                return articleRecord.getConceptId().equals(snapshotRecord.getConceptId())
                        && (sourceId == null || sourceId.equals(snapshotRecord.getSourceId()));
            }
        }
        if (articleId.equals(snapshotRecord.getArticleKey())) {
            return true;
        }
        return articleId.equals(snapshotRecord.getConceptId())
                && (sourceId == null || sourceId.equals(snapshotRecord.getSourceId()));
    }
}
