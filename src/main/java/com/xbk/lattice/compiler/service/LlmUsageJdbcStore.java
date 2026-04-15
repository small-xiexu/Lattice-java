package com.xbk.lattice.compiler.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * LLM 用量 JDBC 存储
 *
 * 职责：将单次 LLM 调用用量写入 llm_usage 表
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class LlmUsageJdbcStore implements LlmUsageStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 LLM 用量 JDBC 存储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public LlmUsageJdbcStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存用量记录。
     *
     * @param llmUsageRecord 用量记录
     */
    @Override
    public void save(LlmUsageRecord llmUsageRecord) {
        jdbcTemplate.update(
                """
                        insert into llm_usage (
                            call_id, model, purpose, input_tokens, output_tokens, cost_usd, called_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                llmUsageRecord.getCallId(),
                llmUsageRecord.getModel(),
                llmUsageRecord.getPurpose(),
                llmUsageRecord.getInputTokens(),
                llmUsageRecord.getOutputTokens(),
                llmUsageRecord.getCostUsd(),
                llmUsageRecord.getCalledAt()
        );
    }
}
