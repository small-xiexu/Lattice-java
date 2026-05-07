package com.xbk.lattice.source.infra;

import com.xbk.lattice.source.domain.SourceSyncRun;
import com.xbk.lattice.source.infra.mapper.SourceSyncRunMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 资料源同步运行 JDBC 仓储
 *
 * 职责：提供 source_sync_runs 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
public class SourceSyncRunJdbcRepository {

    private final SourceSyncRunMapper sourceSyncRunMapper;

    public SourceSyncRunJdbcRepository(SourceSyncRunMapper sourceSyncRunMapper) {
        this.sourceSyncRunMapper = sourceSyncRunMapper;
    }

    public SourceSyncRun save(SourceSyncRun run) {
        if (run.getId() == null) {
            return insert(run);
        }
        update(run);
        return findById(run.getId()).orElseThrow();
    }

    public Optional<SourceSyncRun> findById(Long id) {
        return Optional.ofNullable(sourceSyncRunMapper.findById(id));
    }

    public List<SourceSyncRun> findBySourceId(Long sourceId) {
        return sourceSyncRunMapper.findBySourceId(sourceId);
    }

    /**
     * 查询最近的同步运行。
     *
     * @param limit 返回数量
     * @return 最近运行列表
     */
    public List<SourceSyncRun> findRecent(int limit) {
        return sourceSyncRunMapper.findRecent(Math.max(limit, 1));
    }

    /**
     * 查询资料包 prelock 命中的活动运行。
     *
     * @param manifestHash 资料包 manifest 哈希
     * @return 活动中的预绑定运行
     */
    public Optional<SourceSyncRun> findActivePrelockByManifestHash(String manifestHash) {
        return Optional.ofNullable(sourceSyncRunMapper.findActivePrelockByManifestHash(manifestHash));
    }

    /**
     * 查询资料源当前活动运行。
     *
     * @param sourceId 资料源主键
     * @return 活动运行列表
     */
    public List<SourceSyncRun> findActiveBySourceId(Long sourceId) {
        return sourceSyncRunMapper.findActiveBySourceId(sourceId);
    }

    private SourceSyncRun insert(SourceSyncRun run) {
        Long id = sourceSyncRunMapper.insert(run);
        if (id == null) {
            throw new IllegalStateException("Failed to insert source_sync_runs");
        }
        return findById(id).orElseThrow();
    }

    private void update(SourceSyncRun run) {
        sourceSyncRunMapper.update(run);
    }
}
