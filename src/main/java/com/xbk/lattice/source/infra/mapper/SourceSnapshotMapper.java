package com.xbk.lattice.source.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 资料源快照 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 source_snapshots 表
 *
 * @author xiexu
 */
@Mapper
public interface SourceSnapshotMapper {

    /**
     * 写入资料源快照。
     *
     * @param sourceId 资料源主键
     * @param syncRunId 同步运行主键
     * @param manifestHash manifest 哈希
     * @param summaryJson 摘要 JSON
     * @return 影响行数
     */
    int save(
            @Param("sourceId") Long sourceId,
            @Param("syncRunId") Long syncRunId,
            @Param("manifestHash") String manifestHash,
            @Param("summaryJson") String summaryJson
    );
}
