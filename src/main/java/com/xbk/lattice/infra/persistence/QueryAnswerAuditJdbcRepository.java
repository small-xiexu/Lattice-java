package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QueryAnswerAuditMapper;
import org.springframework.stereotype.Repository;

/**
 * 查询答案审计 JDBC 仓储
 *
 * 职责：负责 query_answer_audits 表的写入
 *
 * @author xiexu
 */
@Repository
public class QueryAnswerAuditJdbcRepository {

    private final QueryAnswerAuditMapper queryAnswerAuditMapper;

    /**
     * 创建查询答案审计 JDBC 仓储。
     *
     * @param queryAnswerAuditMapper 查询答案审计 Mapper
     */
    public QueryAnswerAuditJdbcRepository(QueryAnswerAuditMapper queryAnswerAuditMapper) {
        this.queryAnswerAuditMapper = queryAnswerAuditMapper;
    }

    /**
     * 写入审计主记录。
     *
     * @param record 审计记录
     * @return 审计主键
     */
    public Long insert(QueryAnswerAuditRecord record) {
        return queryAnswerAuditMapper.insert(record);
    }
}
