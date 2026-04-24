package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 查询答案审计 JDBC 仓储
 *
 * 职责：负责 query_answer_audits 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QueryAnswerAuditJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建查询答案审计 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryAnswerAuditJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入审计主记录。
     *
     * @param record 审计记录
     * @return 审计主键
     */
    public Long insert(QueryAnswerAuditRecord record) {
        return jdbcTemplate.queryForObject(
                """
                        insert into query_answer_audits (
                            query_id, answer_version, question, answer_markdown, answer_outcome,
                            generation_mode, review_status, citation_coverage, unsupported_claim_count,
                            verified_citation_count, demoted_citation_count, skipped_citation_count,
                            cacheable, route_type, model_snapshot_json
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        returning audit_id
                        """,
                Long.class,
                record.getQueryId(),
                Integer.valueOf(record.getAnswerVersion()),
                record.getQuestion(),
                record.getAnswerMarkdown(),
                record.getAnswerOutcome(),
                record.getGenerationMode(),
                record.getReviewStatus(),
                Double.valueOf(record.getCitationCoverage()),
                Integer.valueOf(record.getUnsupportedClaimCount()),
                Integer.valueOf(record.getVerifiedCitationCount()),
                Integer.valueOf(record.getDemotedCitationCount()),
                Integer.valueOf(record.getSkippedCitationCount()),
                Boolean.valueOf(record.isCacheable()),
                record.getRouteType(),
                record.getModelSnapshotJson()
        );
    }
}
