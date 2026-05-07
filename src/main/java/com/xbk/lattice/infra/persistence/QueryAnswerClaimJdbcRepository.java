package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QueryAnswerClaimMapper;
import org.springframework.stereotype.Repository;

/**
 * 查询答案 claim JDBC 仓储
 *
 * 职责：负责 query_answer_claims 表的写入
 *
 * @author xiexu
 */
@Repository
public class QueryAnswerClaimJdbcRepository {

    private final QueryAnswerClaimMapper queryAnswerClaimMapper;

    /**
     * 创建查询答案 claim JDBC 仓储。
     *
     * @param queryAnswerClaimMapper 查询答案 claim Mapper
     */
    public QueryAnswerClaimJdbcRepository(QueryAnswerClaimMapper queryAnswerClaimMapper) {
        this.queryAnswerClaimMapper = queryAnswerClaimMapper;
    }

    /**
     * 写入 claim 记录。
     *
     * @param record claim 记录
     * @return claim 主键
     */
    public Long insert(QueryAnswerClaimRecord record) {
        return queryAnswerClaimMapper.insert(record);
    }
}
