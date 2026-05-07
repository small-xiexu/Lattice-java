package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.PendingQueryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PendingQuery JDBC 仓储
 *
 * 职责：提供待确认查询的最小持久化能力
 *
 * @author xiexu
 */
@Repository
public class PendingQueryJdbcRepository {

    private final PendingQueryMapper pendingQueryMapper;

    /**
     * 创建 PendingQuery JDBC 仓储。
     *
     * @param pendingQueryMapper 待确认查询 Mapper
     */
    public PendingQueryJdbcRepository(PendingQueryMapper pendingQueryMapper) {
        this.pendingQueryMapper = pendingQueryMapper;
    }

    /**
     * 保存或更新待确认查询。
     *
     * @param pendingQueryRecord 待确认查询记录
     */
    public void upsert(PendingQueryRecord pendingQueryRecord) {
        pendingQueryMapper.upsert(pendingQueryRecord);
    }

    /**
     * 按 queryId 查询待确认记录。
     *
     * @param queryId 查询标识
     * @return 待确认记录
     */
    public Optional<PendingQueryRecord> findByQueryId(String queryId) {
        return Optional.ofNullable(pendingQueryMapper.findByQueryId(queryId));
    }

    /**
     * 查询全部未过期的待确认记录。
     *
     * @return 待确认记录列表
     */
    public List<PendingQueryRecord> findAllActive() {
        return pendingQueryMapper.findAllActive();
    }

    /**
     * 删除待确认记录。
     *
     * @param queryId 查询标识
     */
    public void deleteByQueryId(String queryId) {
        pendingQueryMapper.deleteByQueryId(queryId);
    }
}
