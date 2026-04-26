package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 证据锚点 JDBC 仓储
 *
 * 职责：负责 deep_research_evidence_anchors 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchEvidenceAnchorJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research 证据锚点 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchEvidenceAnchorJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入证据锚点记录。
     *
     * @param record 证据锚点记录
     */
    public void insert(DeepResearchEvidenceAnchorRecord record) {
        jdbcTemplate.update(
                """
                        insert into deep_research_evidence_anchors (
                            anchor_id, run_id, task_id, source_type, source_id, chunk_id,
                            path, line_start, line_end, quote_text, retrieval_score, content_hash
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.getAnchorId(),
                record.getRunId(),
                record.getTaskId(),
                record.getSourceType(),
                record.getSourceId(),
                record.getChunkId(),
                record.getPath(),
                record.getLineStart(),
                record.getLineEnd(),
                record.getQuoteText(),
                record.getRetrievalScore(),
                record.getContentHash()
        );
    }
}
