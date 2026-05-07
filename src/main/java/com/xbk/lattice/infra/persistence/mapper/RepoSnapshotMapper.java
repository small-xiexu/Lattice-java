package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.RepoSnapshotItemRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 整库快照 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 repo_snapshots 与 repo_snapshot_items 表
 *
 * @author xiexu
 */
@Mapper
public interface RepoSnapshotMapper {

    /**
     * 写入快照主记录。
     *
     * @param record 快照主记录
     * @return 快照主键
     */
    Long insert(@Param("record") RepoSnapshotRecord record);

    /**
     * 写入快照明细。
     *
     * @param record 快照明细
     * @return 影响行数
     */
    int insertItem(@Param("record") RepoSnapshotItemRecord record);

    /**
     * 查询最近整库快照。
     *
     * @param limit 返回上限
     * @return 整库快照列表
     */
    List<RepoSnapshotRecord> findRecent(@Param("limit") int limit);

    /**
     * 按快照标识查询主记录。
     *
     * @param snapshotId 快照标识
     * @return 快照主记录
     */
    RepoSnapshotRecord findById(@Param("snapshotId") long snapshotId);

    /**
     * 查询指定整库快照的明细。
     *
     * @param snapshotId 快照标识
     * @return 明细列表
     */
    List<RepoSnapshotItemRecord> findItemsBySnapshotId(@Param("snapshotId") long snapshotId);
}
