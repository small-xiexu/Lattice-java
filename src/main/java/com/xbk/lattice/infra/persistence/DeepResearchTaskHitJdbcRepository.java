package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 任务命中 JDBC 仓储
 *
 * 职责：负责 deep_research_task_hits 表的写入
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DeepResearchTaskHitJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Deep Research 任务命中 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DeepResearchTaskHitJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入任务命中记录。
     *
     * @param record 命中记录
     */
    public void insert(DeepResearchTaskHitRecord record) {
        jdbcTemplate.update(
                """
                        insert into deep_research_task_hits (
                            run_id, task_id, hit_ordinal, channel, evidence_type, source_id,
                            article_key, concept_id, title, chunk_id, path, original_score,
                            rrf_score, fused_score, content_excerpt
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.getRunId(),
                record.getTaskId(),
                Integer.valueOf(record.getHitOrdinal()),
                record.getChannel(),
                record.getEvidenceType(),
                record.getSourceId(),
                record.getArticleKey(),
                record.getConceptId(),
                record.getTitle(),
                record.getChunkId(),
                record.getPath(),
                record.getOriginalScore(),
                record.getRrfScore(),
                record.getFusedScore(),
                record.getContentExcerpt()
        );
    }
}
