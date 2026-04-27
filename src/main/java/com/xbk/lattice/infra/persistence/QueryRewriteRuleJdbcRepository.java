package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Profile("jdbc")
public class QueryRewriteRuleJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Query Rewrite 规则 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryRewriteRuleJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询启用中的改写规则。
     *
     * @return 改写规则
     */
    public List<QueryRewriteRuleRecord> findActiveRules() {
        if (jdbcTemplate == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select id, rule_code, source_pattern, rewrite_text, scope, priority
                        from query_rewrite_rules
                        where enabled = true
                        order by priority desc, id asc
                        """,
                (resultSet, rowNum) -> new QueryRewriteRuleRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("rule_code"),
                        resultSet.getString("source_pattern"),
                        resultSet.getString("rewrite_text"),
                        resultSet.getString("scope"),
                        resultSet.getInt("priority")
                )
        );
    }
}
