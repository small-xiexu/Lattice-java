package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 运行 JDBC 仓储
 *
 * 职责：负责 deep_research_runs 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchRunJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research 运行 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchRunJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入运行主记录。
     *
     * @param record 运行记录
     * @return 运行主键
     */
    public Long insert(DeepResearchRunRecord record) {
        return jdbcTemplate.queryForObject(
                """
                        insert into deep_research_runs (
                            query_id, question, route_reason, plan_json, layer_count, task_count,
                            llm_call_count, citation_coverage, partial_answer, has_conflicts, final_answer_audit_id
                        )
                        values (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                        returning run_id
                        """,
                Long.class,
                record.getQueryId(),
                record.getQuestion(),
                record.getRouteReason(),
                record.getPlanJson(),
                Integer.valueOf(record.getLayerCount()),
                Integer.valueOf(record.getTaskCount()),
                Integer.valueOf(record.getLlmCallCount()),
                Double.valueOf(record.getCitationCoverage()),
                Boolean.valueOf(record.isPartialAnswer()),
                Boolean.valueOf(record.isHasConflicts()),
                record.getFinalAnswerAuditId()
        );
    }
}
