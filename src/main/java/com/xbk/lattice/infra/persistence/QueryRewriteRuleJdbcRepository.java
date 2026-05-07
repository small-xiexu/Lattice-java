package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QueryRewriteRuleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Query Rewrite 规则 JDBC 仓储
 *
 * 职责：读取启用中的查询改写规则
 *
 * @author xiexu
 */
@Repository
public class QueryRewriteRuleJdbcRepository {

    private final QueryRewriteRuleMapper queryRewriteRuleMapper;

    /**
     * 创建 Query Rewrite 规则 JDBC 仓储。
     *
     * @param queryRewriteRuleMapper Query Rewrite 规则 Mapper
     */
    public QueryRewriteRuleJdbcRepository(QueryRewriteRuleMapper queryRewriteRuleMapper) {
        this.queryRewriteRuleMapper = queryRewriteRuleMapper;
    }

    /**
     * 查询启用中的改写规则。
     *
     * @return 改写规则
     */
    public List<QueryRewriteRuleRecord> findActiveRules() {
        return queryRewriteRuleMapper.findActiveRules();
    }
}
