package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 答案投影 JDBC 仓储
 *
 * 职责：负责 deep_research_answer_projections 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchAnswerProjectionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research 答案投影 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchAnswerProjectionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入答案投影记录。
     *
     * @param record 答案投影记录
     */
    public void insert(DeepResearchAnswerProjectionRecord record) {
        jdbcTemplate.update(
                """
                        insert into deep_research_answer_projections (
                            run_id, answer_audit_id, projection_ordinal, anchor_id, citation_literal,
                            source_type, target_key, status, repair_round, repaired_from_projection_ordinal
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.getRunId(),
                record.getAnswerAuditId(),
                Integer.valueOf(record.getProjectionOrdinal()),
                record.getAnchorId(),
                record.getCitationLiteral(),
                record.getSourceType(),
                record.getTargetKey(),
                record.getStatus(),
                Integer.valueOf(record.getRepairRound()),
                record.getRepairedFromProjectionOrdinal()
        );
    }
}
