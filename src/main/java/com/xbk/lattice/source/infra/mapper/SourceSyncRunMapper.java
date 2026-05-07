package com.xbk.lattice.source.infra.mapper;

import com.xbk.lattice.source.domain.SourceSyncRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资料源同步运行 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 source_sync_runs 表
 *
 * @author xiexu
 */
@Mapper
public interface SourceSyncRunMapper {

    /**
     * 插入同步运行。
     *
     * @param run 同步运行
     * @return 主键
     */
    Long insert(@Param("run") SourceSyncRun run);

    /**
     * 更新同步运行。
     *
     * @param run 同步运行
     * @return 影响行数
     */
    int update(@Param("run") SourceSyncRun run);

    /**
     * 按主键查询同步运行。
     *
     * @param id 主键
     * @return 同步运行
     */
    SourceSyncRun findById(@Param("id") Long id);

    /**
     * 按资料源查询同步运行。
     *
     * @param sourceId 资料源主键
     * @return 同步运行列表
     */
    List<SourceSyncRun> findBySourceId(@Param("sourceId") Long sourceId);

    /**
     * 查询最近的同步运行。
     *
     * @param limit 返回数量
     * @return 最近运行列表
     */
    List<SourceSyncRun> findRecent(@Param("limit") int limit);

    /**
     * 查询资料包 prelock 命中的活动运行。
     *
     * @param manifestHash 资料包 manifest 哈希
     * @return 活动中的预绑定运行
     */
    SourceSyncRun findActivePrelockByManifestHash(@Param("manifestHash") String manifestHash);

    /**
     * 查询资料源当前活动运行。
     *
     * @param sourceId 资料源主键
     * @return 活动运行列表
     */
    List<SourceSyncRun> findActiveBySourceId(@Param("sourceId") Long sourceId);
}
