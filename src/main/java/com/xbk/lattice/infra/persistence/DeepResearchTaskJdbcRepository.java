package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 任务 JDBC 仓储
 *
 * 职责：负责 deep_research_tasks 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchTaskJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research 任务 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchTaskJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入任务记录。
     *
     * @param record 任务记录
     */
    public void insert(DeepResearchTaskRecord record) {
        jdbcTemplate.update(
                """
                        insert into deep_research_tasks (
                            task_id, run_id, layer_index, task_type, question,
                            expected_fact_schema_json, preferred_upstream_task_ids_json, status,
                            llm_call_count, timed_out, error_reason, started_at, finished_at
                        )
                        values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        """,
                record.getTaskId(),
                record.getRunId(),
                Integer.valueOf(record.getLayerIndex()),
                record.getTaskType(),
                record.getQuestion(),
                record.getExpectedFactSchemaJson(),
                record.getPreferredUpstreamTaskIdsJson(),
                record.getStatus(),
                Integer.valueOf(record.getLlmCallCount()),
                Boolean.valueOf(record.isTimedOut()),
                record.getErrorReason(),
                record.getStartedAt(),
                record.getFinishedAt()
        );
    }
}
