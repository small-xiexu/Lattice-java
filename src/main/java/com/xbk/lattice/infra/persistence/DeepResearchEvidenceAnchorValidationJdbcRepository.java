package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 证据锚点校验 JDBC 仓储
 *
 * 职责：负责 deep_research_evidence_anchor_validations 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchEvidenceAnchorValidationJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research 证据锚点校验 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchEvidenceAnchorValidationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入证据锚点校验记录。
     *
     * @param record 证据锚点校验记录
     */
    public void insert(DeepResearchEvidenceAnchorValidationRecord record) {
        jdbcTemplate.update(
                """
                        insert into deep_research_evidence_anchor_validations (
                            run_id, anchor_id, validation_round, validation_status,
                            validated_by, reason, matched_excerpt
                        )
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                record.getRunId(),
                record.getAnchorId(),
                Integer.valueOf(record.getValidationRound()),
                record.getValidationStatus(),
                record.getValidatedBy(),
                record.getReason(),
                record.getMatchedExcerpt()
        );
    }
}
