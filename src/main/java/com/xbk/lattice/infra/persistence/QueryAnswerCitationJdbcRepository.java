package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 查询答案引用 JDBC 仓储
 *
 * 职责：负责 query_answer_citations 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QueryAnswerCitationJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建查询答案引用 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryAnswerCitationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入引用记录。
     *
     * @param record 引用记录
     */
    public void insert(QueryAnswerCitationRecord record) {
        jdbcTemplate.update(
                """
                        insert into query_answer_citations (
                            audit_id, claim_id, citation_ordinal, citation_literal, source_type, target_key,
                            validation_status, validated_by, overlap_score, matched_excerpt, reason
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.getAuditId(),
                record.getClaimId(),
                Integer.valueOf(record.getCitationOrdinal()),
                record.getLiteral(),
                record.getSourceType(),
                record.getTargetKey(),
                record.getStatus(),
                record.getValidatedBy(),
                Double.valueOf(record.getOverlapScore()),
                record.getMatchedExcerpt(),
                record.getReason()
        );
    }
}
