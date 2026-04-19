package com.xbk.lattice.source.infra;

import com.xbk.lattice.source.domain.SourceSyncRun;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 资料源同步运行 JDBC 仓储
 *
 * 职责：提供 source_sync_runs 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class SourceSyncRunJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public SourceSyncRunJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SourceSyncRun save(SourceSyncRun run) {
        if (run.getId() == null) {
            return insert(run);
        }
        update(run);
        return findById(run.getId()).orElseThrow();
    }

    public Optional<SourceSyncRun> findById(Long id) {
        List<SourceSyncRun> items = jdbcTemplate.query(
                """
                        select id, source_id, source_type, manifest_hash, trigger_type, resolver_mode,
                               resolver_decision, sync_action, status, matched_source_id, compile_job_id,
                               evidence_json, error_message, requested_at, updated_at, started_at, finished_at
                        from source_sync_runs
                        where id = ?
                        """,
                this::mapRecord,
                id
        );
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    public List<SourceSyncRun> findBySourceId(Long sourceId) {
        return jdbcTemplate.query(
                """
                        select id, source_id, source_type, manifest_hash, trigger_type, resolver_mode,
                               resolver_decision, sync_action, status, matched_source_id, compile_job_id,
                               evidence_json, error_message, requested_at, updated_at, started_at, finished_at
                        from source_sync_runs
                        where source_id = ?
                        order by requested_at desc, id desc
                        """,
                this::mapRecord,
                sourceId
        );
    }

    /**
     * 查询资料包 prelock 命中的活动运行。
     *
     * @param manifestHash 资料包 manifest 哈希
     * @return 活动中的预绑定运行
     */
    public Optional<SourceSyncRun> findActivePrelockByManifestHash(String manifestHash) {
        List<SourceSyncRun> items = jdbcTemplate.query(
                """
                        select id, source_id, source_type, manifest_hash, trigger_type, resolver_mode,
                               resolver_decision, sync_action, status, matched_source_id, compile_job_id,
                               evidence_json, error_message, requested_at, updated_at, started_at, finished_at
                        from source_sync_runs
                        where manifest_hash = ?
                          and source_id is null
                          and status in ('QUEUED', 'MATCHING', 'MATERIALIZING', 'COMPILE_QUEUED', 'RUNNING')
                        order by requested_at desc, id desc
                        limit 1
                        """,
                this::mapRecord,
                manifestHash
        );
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    /**
     * 查询资料源当前活动运行。
     *
     * @param sourceId 资料源主键
     * @return 活动运行列表
     */
    public List<SourceSyncRun> findActiveBySourceId(Long sourceId) {
        return jdbcTemplate.query(
                """
                        select id, source_id, source_type, manifest_hash, trigger_type, resolver_mode,
                               resolver_decision, sync_action, status, matched_source_id, compile_job_id,
                               evidence_json, error_message, requested_at, updated_at, started_at, finished_at
                        from source_sync_runs
                        where source_id = ?
                          and status in ('QUEUED', 'MATCHING', 'MATERIALIZING', 'COMPILE_QUEUED', 'RUNNING')
                        order by requested_at desc, id desc
                        """,
                this::mapRecord,
                sourceId
        );
    }

    private SourceSyncRun insert(SourceSyncRun run) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into source_sync_runs (
                                source_id, source_type, manifest_hash, trigger_type, resolver_mode,
                                resolver_decision, sync_action, status, matched_source_id, compile_job_id,
                                evidence_json, error_message, requested_at, updated_at, started_at, finished_at
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setObject(1, run.getSourceId());
            preparedStatement.setString(2, run.getSourceType());
            preparedStatement.setString(3, run.getManifestHash());
            preparedStatement.setString(4, run.getTriggerType());
            preparedStatement.setString(5, run.getResolverMode());
            preparedStatement.setString(6, run.getResolverDecision());
            preparedStatement.setString(7, run.getSyncAction());
            preparedStatement.setString(8, run.getStatus());
            preparedStatement.setObject(9, run.getMatchedSourceId());
            preparedStatement.setString(10, run.getCompileJobId());
            preparedStatement.setString(11, run.getEvidenceJson());
            preparedStatement.setString(12, run.getErrorMessage());
            preparedStatement.setObject(13, run.getRequestedAt());
            preparedStatement.setObject(14, run.getUpdatedAt());
            preparedStatement.setObject(15, run.getStartedAt());
            preparedStatement.setObject(16, run.getFinishedAt());
            return preparedStatement;
        }, keyHolder);
        Number key;
        Object generatedId = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("id");
        if (generatedId instanceof Number) {
            key = (Number) generatedId;
        }
        else {
            key = keyHolder.getKey();
        }
        if (key == null) {
            throw new IllegalStateException("Failed to insert source_sync_runs");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private void update(SourceSyncRun run) {
        jdbcTemplate.update(
                """
                        update source_sync_runs
                        set source_id = ?,
                            source_type = ?,
                            manifest_hash = ?,
                            trigger_type = ?,
                            resolver_mode = ?,
                            resolver_decision = ?,
                            sync_action = ?,
                            status = ?,
                            matched_source_id = ?,
                            compile_job_id = ?,
                            evidence_json = cast(? as jsonb),
                            error_message = ?,
                            updated_at = ?,
                            started_at = ?,
                            finished_at = ?
                        where id = ?
                        """,
                run.getSourceId(),
                run.getSourceType(),
                run.getManifestHash(),
                run.getTriggerType(),
                run.getResolverMode(),
                run.getResolverDecision(),
                run.getSyncAction(),
                run.getStatus(),
                run.getMatchedSourceId(),
                run.getCompileJobId(),
                run.getEvidenceJson(),
                run.getErrorMessage(),
                run.getUpdatedAt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getId()
        );
    }

    private SourceSyncRun mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        Object sourceId = resultSet.getObject("source_id");
        Object matchedSourceId = resultSet.getObject("matched_source_id");
        return new SourceSyncRun(
                resultSet.getLong("id"),
                sourceId == null ? null : resultSet.getLong("source_id"),
                resultSet.getString("source_type"),
                resultSet.getString("manifest_hash"),
                resultSet.getString("trigger_type"),
                resultSet.getString("resolver_mode"),
                resultSet.getString("resolver_decision"),
                resultSet.getString("sync_action"),
                resultSet.getString("status"),
                matchedSourceId == null ? null : resultSet.getLong("matched_source_id"),
                resultSet.getString("compile_job_id"),
                resultSet.getString("evidence_json"),
                resultSet.getString("error_message"),
                resultSet.getObject("requested_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class)
        );
    }
}
