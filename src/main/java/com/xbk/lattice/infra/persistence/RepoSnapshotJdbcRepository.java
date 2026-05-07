package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.RepoSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 整库快照 JDBC 仓储
 *
 * 职责：提供整库级 snapshot 主表与明细表的持久化能力
 *
 * @author xiexu
 */
@Repository
public class RepoSnapshotJdbcRepository {

    private final RepoSnapshotMapper repoSnapshotMapper;

    /**
     * 创建整库快照 JDBC 仓储。
     *
     * @param repoSnapshotMapper 整库快照 Mapper
     */
    public RepoSnapshotJdbcRepository(RepoSnapshotMapper repoSnapshotMapper) {
        this.repoSnapshotMapper = repoSnapshotMapper;
    }

    /**
     * 保存快照主记录。
     *
     * @param repoSnapshotRecord 快照主记录
     * @return 生成后的快照标识
     */
    public long save(RepoSnapshotRecord repoSnapshotRecord) {
        Long key = repoSnapshotMapper.insert(repoSnapshotRecord);
        if (key == null) {
            throw new IllegalStateException("保存 repo snapshot 失败，未返回主键");
        }
        return key;
    }

    /**
     * 批量保存快照明细。
     *
     * @param itemRecords 明细记录
     */
    public void saveItems(List<RepoSnapshotItemRecord> itemRecords) {
        for (RepoSnapshotItemRecord itemRecord : itemRecords) {
            repoSnapshotMapper.insertItem(itemRecord);
        }
    }

    /**
     * 查询最近整库快照。
     *
     * @param limit 返回数量
     * @return 整库快照列表
     */
    public List<RepoSnapshotRecord> findRecent(int limit) {
        int safeLimit = Math.max(1, limit);
        return repoSnapshotMapper.findRecent(safeLimit);
    }

    /**
     * 查询指定整库快照主记录。
     *
     * @param snapshotId 快照标识
     * @return 快照主记录
     */
    public Optional<RepoSnapshotRecord> findById(long snapshotId) {
        return Optional.ofNullable(repoSnapshotMapper.findById(snapshotId));
    }

    /**
     * 查询指定整库快照的明细。
     *
     * @param snapshotId 快照标识
     * @return 明细列表
     */
    public List<RepoSnapshotItemRecord> findItemsBySnapshotId(long snapshotId) {
        return repoSnapshotMapper.findItemsBySnapshotId(snapshotId);
    }
}
