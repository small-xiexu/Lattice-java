package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 查询答案 claim JDBC 仓储
 *
 * 职责：负责 query_answer_claims 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QueryAnswerClaimJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建查询答案 claim JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryAnswerClaimJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入 claim 记录。
     *
     * @param record claim 记录
     * @return claim 主键
     */
    public Long insert(QueryAnswerClaimRecord record) {
        return jdbcTemplate.queryForObject(
                """
                        insert into query_answer_claims (
                            audit_id, claim_index, claim_text, claim_status, citation_count
                        )
                        values (?, ?, ?, ?, ?)
                        returning claim_id
                        """,
                Long.class,
                record.getAuditId(),
                Integer.valueOf(record.getClaimIndex()),
                record.getClaimText(),
                record.getClaimStatus(),
                Integer.valueOf(record.getCitationCount())
        );
    }
}
