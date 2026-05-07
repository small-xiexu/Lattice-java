package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * PendingQuery MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 pending_queries 表
 *
 * @author xiexu
 */
@Mapper
public interface PendingQueryMapper {

    /**
     * 保存或更新待确认查询。
     *
     * @param record 待确认查询记录
     * @return 影响行数
     */
    int upsert(@Param("record") PendingQueryRecord record);

    /**
     * 按 queryId 查询待确认记录。
     *
     * @param queryId 查询标识
     * @return 待确认记录
     */
    PendingQueryRecord findByQueryId(@Param("queryId") String queryId);

    /**
     * 查询全部未过期的待确认记录。
     *
     * @return 待确认记录列表
     */
    List<PendingQueryRecord> findAllActive();

    /**
     * 删除待确认记录。
     *
     * @param queryId 查询标识
     * @return 影响行数
     */
    int deleteByQueryId(@Param("queryId") String queryId);
}
