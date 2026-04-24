package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 证据卡 JDBC 仓储
 *
 * 职责：负责 deep_research_evidence_cards 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchEvidenceCardJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research 证据卡 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchEvidenceCardJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入证据卡记录。
     *
     * @param record 证据卡记录
     */
    public void insert(DeepResearchEvidenceCardRecord record) {
        jdbcTemplate.update(
                """
                        insert into deep_research_evidence_cards (
                            run_id, evidence_id, layer_index, task_id, scope,
                            findings_json, gaps_json, related_leads_json
                        )
                        values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                        """,
                record.getRunId(),
                record.getEvidenceId(),
                Integer.valueOf(record.getLayerIndex()),
                record.getTaskId(),
                record.getScope(),
                record.getFindingsJson(),
                record.getGapsJson(),
                record.getRelatedLeadsJson()
        );
    }
}
