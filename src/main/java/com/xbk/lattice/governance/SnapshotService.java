package com.xbk.lattice.governance;

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
import java.util.Optional;

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

    /**
     * 创建快照服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     */
    public SnapshotService(ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository) {
        this(articleSnapshotJdbcRepository, null);
    }

    /**
     * 创建快照服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     * @param articleJdbcRepository 文章仓储
     */
    @Autowired
    public SnapshotService(
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository
    ) {
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
        this.articleJdbcRepository = articleJdbcRepository;
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
        if (articleSnapshotJdbcRepository == null || articleJdbcRepository == null) {
            throw new UnsupportedOperationException("rollback dependencies not configured");
        }
        ArticleSnapshotRecord snapshotRecord = articleSnapshotJdbcRepository.findBySnapshotId(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("快照不存在: " + snapshotId));
        if (!snapshotRecord.getConceptId().equals(conceptId)) {
            throw new IllegalArgumentException("快照与概念不匹配");
        }

        ArticleRecord restoredRecord = new ArticleRecord(
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
        articleSnapshotJdbcRepository.save(new ArticleSnapshotRecord(
                -1L,
                restoredRecord.getConceptId(),
                restoredRecord.getTitle(),
                restoredRecord.getContent(),
                restoredRecord.getLifecycle(),
                restoredRecord.getCompiledAt(),
                restoredRecord.getSourcePaths(),
                restoredRecord.getMetadataJson(),
                restoredRecord.getSummary(),
                restoredRecord.getReferentialKeywords(),
                restoredRecord.getDependsOn(),
                restoredRecord.getRelated(),
                restoredRecord.getConfidence(),
                restoredRecord.getReviewStatus(),
                "rollback|from:" + snapshotId,
                restoredAt
        ));
        return new RollbackResult(conceptId, snapshotId, restoredAt);
    }
}
