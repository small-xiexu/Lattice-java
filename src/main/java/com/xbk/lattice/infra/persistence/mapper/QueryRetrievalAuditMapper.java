package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitRecord;
import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitView;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunRecord;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Query 检索审计 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_retrieval_runs 与 query_retrieval_channel_hits 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryRetrievalAuditMapper {

    /**
     * 写入检索审计主记录。
     *
     * @param record 审计主记录
     * @return 主键
     */
    Long insertRun(@Param("record") QueryRetrievalRunRecord record);

    /**
     * 写入通道命中明细。
     *
     * @param record 通道命中记录
     * @return 影响行数
     */
    int insertChannelHit(@Param("record") QueryRetrievalChannelHitRecord record);

    /**
     * 查询指定 queryId 的最近一次检索审计。
     *
     * @param queryId 查询标识
     * @return 最近一次检索审计
     */
    QueryRetrievalRunView findLatestRunByQueryId(@Param("queryId") String queryId);

    /**
     * 查询指定 queryId 的最近若干次检索审计。
     *
     * @param queryId 查询标识
     * @param limit 返回上限
     * @return 检索审计列表
     */
    List<QueryRetrievalRunView> findRunsByQueryId(@Param("queryId") String queryId, @Param("limit") int limit);

    /**
     * 查询最近若干次检索审计。
     *
     * @param limit 返回上限
     * @return 检索审计列表
     */
    List<QueryRetrievalRunView> findRecentRuns(@Param("limit") int limit);

    /**
     * 查询指定 runId 的通道命中明细。
     *
     * @param runId 审计主键
     * @return 通道命中明细
     */
    List<QueryRetrievalChannelHitView> findChannelHitsByRunId(@Param("runId") Long runId);
}
