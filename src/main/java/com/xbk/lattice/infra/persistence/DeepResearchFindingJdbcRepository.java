package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research finding JDBC 仓储
 *
 * 职责：负责 deep_research_findings 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchFindingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research finding JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchFindingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入 finding 记录。
     *
     * @param record finding 记录
     */
    public void insert(DeepResearchFindingRecord record) {
        jdbcTemplate.update(
                """
                        insert into deep_research_findings (
                            finding_id, run_id, task_id, fact_key, subject, predicate,
                            value_text, value_type, unit, qualifier, claim_text, support_level,
                            confidence, anchor_ids_json
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """,
                record.getFindingId(),
                record.getRunId(),
                record.getTaskId(),
                record.getFactKey(),
                record.getSubject(),
                record.getPredicate(),
                record.getValueText(),
                record.getValueType(),
                record.getUnit(),
                record.getQualifier(),
                record.getClaimText(),
                record.getSupportLevel(),
                Double.valueOf(record.getConfidence()),
                record.getAnchorIdsJson()
        );
    }
}
