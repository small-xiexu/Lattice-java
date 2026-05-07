package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QueryAnswerCitationMapper;
import org.springframework.stereotype.Repository;

/**
 * 查询答案引用 JDBC 仓储
 *
 * 职责：负责 query_answer_citations 表的写入
 *
 * @author xiexu
 */
@Repository
public class QueryAnswerCitationJdbcRepository {

    private final QueryAnswerCitationMapper queryAnswerCitationMapper;

    /**
     * 创建查询答案引用 JDBC 仓储。
     *
     * @param queryAnswerCitationMapper 查询答案引用 Mapper
     */
    public QueryAnswerCitationJdbcRepository(QueryAnswerCitationMapper queryAnswerCitationMapper) {
        this.queryAnswerCitationMapper = queryAnswerCitationMapper;
    }

    /**
     * 写入引用记录。
     *
     * @param record 引用记录
     */
    public void insert(QueryAnswerCitationRecord record) {
        queryAnswerCitationMapper.insert(record);
    }
}
