package com.xbk.lattice.source.infra;

import com.xbk.lattice.source.infra.mapper.SourceSnapshotMapper;
import org.springframework.stereotype.Repository;

/**
 * 资料源快照 JDBC 仓储。
 *
 * 职责：负责将成功同步后的 manifest 与摘要写入 source_snapshots
 *
 * @author xiexu
 */
@Repository
public class SourceSnapshotJdbcRepository {

    private final SourceSnapshotMapper sourceSnapshotMapper;

    /**
     * 创建资料源快照仓储。
     *
     * @param sourceSnapshotMapper 资料源快照 Mapper
     */
    public SourceSnapshotJdbcRepository(SourceSnapshotMapper sourceSnapshotMapper) {
        this.sourceSnapshotMapper = sourceSnapshotMapper;
    }

    /**
     * 写入资料源快照。
     *
     * @param sourceId 资料源主键
     * @param syncRunId 同步运行主键
     * @param manifestHash manifest 哈希
     * @param summaryJson 摘要 JSON
     */
    public void save(Long sourceId, Long syncRunId, String manifestHash, String summaryJson) {
        sourceSnapshotMapper.save(sourceId, syncRunId, manifestHash, summaryJson);
    }
}
