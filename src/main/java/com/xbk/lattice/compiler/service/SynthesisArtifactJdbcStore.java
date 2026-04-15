package com.xbk.lattice.compiler.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 合成产物 JDBC 存储
 *
 * 职责：将 index/timeline/tradeoffs/gaps 写入 synthesis_artifacts 表
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class SynthesisArtifactJdbcStore implements SynthesisArtifactStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建合成产物 JDBC 存储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public SynthesisArtifactJdbcStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(SynthesisArtifactRecord synthesisArtifactRecord) {
        jdbcTemplate.update(
                """
                        insert into synthesis_artifacts (artifact_type, title, content, compiled_at)
                        values (?, ?, ?, ?)
                        on conflict (artifact_type) do update
                        set title = excluded.title,
                            content = excluded.content,
                            compiled_at = excluded.compiled_at
                        """,
                synthesisArtifactRecord.getArtifactType(),
                synthesisArtifactRecord.getTitle(),
                synthesisArtifactRecord.getContent(),
                synthesisArtifactRecord.getCompiledAt()
        );
    }
}
