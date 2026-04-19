package com.xbk.lattice.source.service;

import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceSyncRun;
import com.xbk.lattice.source.infra.KnowledgeSourceJdbcRepository;
import com.xbk.lattice.source.infra.SourceSyncRunJdbcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 资料源同步服务
 *
 * 职责：提供同步运行的最小创建、更新与查询能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SourceSyncService {

    private final SourceSyncRunJdbcRepository sourceSyncRunJdbcRepository;

    private final KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository;

    public SourceSyncService(
            SourceSyncRunJdbcRepository sourceSyncRunJdbcRepository,
            KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository
    ) {
        this.sourceSyncRunJdbcRepository = sourceSyncRunJdbcRepository;
        this.knowledgeSourceJdbcRepository = knowledgeSourceJdbcRepository;
    }

    /**
     * 查询同步运行。
     *
     * @param runId 运行主键
     * @return 同步运行
     */
    public java.util.Optional<SourceSyncRun> findById(Long runId) {
        return sourceSyncRunJdbcRepository.findById(runId);
    }

    /**
     * 查询资料包 prelock 命中的活动运行。
     *
     * @param manifestHash manifest 哈希
     * @return 活动运行
     */
    public java.util.Optional<SourceSyncRun> findActivePrelockByManifestHash(String manifestHash) {
        return sourceSyncRunJdbcRepository.findActivePrelockByManifestHash(manifestHash);
    }

    /**
     * 查询资料源的活动运行。
     *
     * @param sourceId 资料源主键
     * @return 活动运行列表
     */
    public List<SourceSyncRun> listActiveRuns(Long sourceId) {
        return sourceSyncRunJdbcRepository.findActiveBySourceId(sourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRun requestRun(SourceSyncRun run) {
        return saveRun(run);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRun markStatus(Long runId, String status, String errorMessage) {
        SourceSyncRun existingRun = sourceSyncRunJdbcRepository.findById(runId).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        SourceSyncRun updatedRun = new SourceSyncRun(
                existingRun.getId(),
                existingRun.getSourceId(),
                existingRun.getSourceType(),
                existingRun.getManifestHash(),
                existingRun.getTriggerType(),
                existingRun.getResolverMode(),
                existingRun.getResolverDecision(),
                existingRun.getSyncAction(),
                status,
                existingRun.getMatchedSourceId(),
                existingRun.getCompileJobId(),
                existingRun.getEvidenceJson(),
                errorMessage,
                existingRun.getRequestedAt(),
                now,
                existingRun.getStartedAt() == null && "RUNNING".equals(status) ? now : existingRun.getStartedAt(),
                isTerminal(status) ? now : existingRun.getFinishedAt()
        );
        return saveRun(updatedRun);
    }

    /**
     * 保存同步运行并同步资料源最近状态。
     *
     * @param run 同步运行
     * @return 保存后的同步运行
     */
    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRun saveRun(SourceSyncRun run) {
        SourceSyncRun savedRun = sourceSyncRunJdbcRepository.save(run);
        OffsetDateTime syncAt = savedRun.getUpdatedAt() == null ? OffsetDateTime.now() : savedRun.getUpdatedAt();
        if (savedRun.getSourceId() != null) {
            KnowledgeSource source = knowledgeSourceJdbcRepository.findById(savedRun.getSourceId()).orElseThrow();
            KnowledgeSource updatedSource = new KnowledgeSource(
                    source.getId(),
                    source.getSourceCode(),
                    source.getName(),
                    source.getSourceType(),
                    source.getContentProfile(),
                    source.getStatus(),
                    source.getVisibility(),
                    source.getDefaultSyncMode(),
                    source.getConfigJson(),
                    source.getMetadataJson(),
                    source.getLatestManifestHash(),
                    savedRun.getId(),
                    savedRun.getStatus(),
                    syncAt,
                    source.getCreatedAt(),
                    source.getUpdatedAt()
            );
            knowledgeSourceJdbcRepository.save(updatedSource);
        }
        return savedRun;
    }

    public List<SourceSyncRun> listRuns(Long sourceId) {
        return sourceSyncRunJdbcRepository.findBySourceId(sourceId);
    }

    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status)
                || "FAILED".equals(status)
                || "SKIPPED_NO_CHANGE".equals(status);
    }
}
