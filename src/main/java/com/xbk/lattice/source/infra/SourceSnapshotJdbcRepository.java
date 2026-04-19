package com.xbk.lattice.source.infra;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 资料源快照 JDBC 仓储。
 *
 * 职责：负责将成功同步后的 manifest 与摘要写入 source_snapshots
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class SourceSnapshotJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建资料源快照仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public SourceSnapshotJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入资料源快照。
     *
     * @param sourceId 资料源主键
     * @param syncRunId 同步运行主键
     * @param manifestHash manifest 哈希
     * @param summaryJson 摘要 JSON
     */
    public void save(Long sourceId, Long syncRunId, String manifestHash, String summaryJson) {
        jdbcTemplate.update(
                """
                        insert into source_snapshots (
                            source_id,
                            sync_run_id,
                            manifest_hash,
                            summary_json
                        )
                        values (?, ?, ?, cast(? as jsonb))
                        """,
                sourceId,
                syncRunId,
                manifestHash,
                summaryJson
        );
    }
}
