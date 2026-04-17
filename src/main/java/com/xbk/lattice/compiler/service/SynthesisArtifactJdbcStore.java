package com.xbk.lattice.compiler.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    /**
     * 查询全部合成产物。
     *
     * @return 合成产物列表
     */
    public List<SynthesisArtifactRecord> findAll() {
        return jdbcTemplate.query(
                """
                        select artifact_type, title, content, compiled_at
                        from synthesis_artifacts
                        order by artifact_type asc
                        """,
                (resultSet, rowNum) -> new SynthesisArtifactRecord(
                        resultSet.getString("artifact_type"),
                        resultSet.getString("title"),
                        resultSet.getString("content"),
                        resultSet.getObject("compiled_at", java.time.OffsetDateTime.class)
                )
        );
    }

    /**
     * 清空全部合成产物。
     */
    public void deleteAll() {
        jdbcTemplate.execute("TRUNCATE TABLE synthesis_artifacts");
    }
}
