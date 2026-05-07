package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QueryRetrievalAuditMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Query 检索审计 JDBC 仓储
 *
 * 职责：负责 query_retrieval_runs 与 query_retrieval_channel_hits 的读写
 *
 * @author xiexu
 */
@Repository
public class QueryRetrievalAuditJdbcRepository {

    private final QueryRetrievalAuditMapper queryRetrievalAuditMapper;

    /**
     * 创建 Query 检索审计 JDBC 仓储。
     *
     * @param queryRetrievalAuditMapper Query 检索审计 Mapper
     */
    public QueryRetrievalAuditJdbcRepository(QueryRetrievalAuditMapper queryRetrievalAuditMapper) {
        this.queryRetrievalAuditMapper = queryRetrievalAuditMapper;
    }

    /**
     * 写入检索审计主记录。
     *
     * @param record 审计主记录
     * @return 主键
     */
    public Long insertRun(QueryRetrievalRunRecord record) {
        return queryRetrievalAuditMapper.insertRun(record);
    }

    /**
     * 写入通道命中明细。
     *
     * @param record 通道命中记录
     */
    public void insertChannelHit(QueryRetrievalChannelHitRecord record) {
        queryRetrievalAuditMapper.insertChannelHit(record);
    }

    /**
     * 查询指定 queryId 的最近一次检索审计。
     *
     * @param queryId 查询标识
     * @return 最近一次检索审计
     */
    public Optional<QueryRetrievalRunView> findLatestRunByQueryId(String queryId) {
        return Optional.ofNullable(queryRetrievalAuditMapper.findLatestRunByQueryId(queryId));
    }

    /**
     * 查询指定 queryId 的最近若干次检索审计。
     *
     * @param queryId 查询标识
     * @param limit 返回数量
     * @return 检索审计列表
     */
    public List<QueryRetrievalRunView> findRunsByQueryId(String queryId, int limit) {
        return queryRetrievalAuditMapper.findRunsByQueryId(queryId, limit);
    }

    /**
     * 查询最近若干次检索审计。
     *
     * @param limit 返回数量
     * @return 最近检索审计列表
     */
    public List<QueryRetrievalRunView> findRecentRuns(int limit) {
        return queryRetrievalAuditMapper.findRecentRuns(limit);
    }

    /**
     * 查询指定 runId 的全部通道命中明细。
     *
     * @param runId 审计主键
     * @return 通道命中明细
     */
    public List<QueryRetrievalChannelHitView> findChannelHitsByRunId(Long runId) {
        return queryRetrievalAuditMapper.findChannelHitsByRunId(runId);
    }
}
