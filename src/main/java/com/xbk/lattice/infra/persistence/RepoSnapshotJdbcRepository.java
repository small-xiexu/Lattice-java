package com.xbk.lattice.infra.persistence;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 整库快照 JDBC 仓储
 *
 * 职责：提供整库级 snapshot 主表与明细表的持久化能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class RepoSnapshotJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建整库快照 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public RepoSnapshotJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存快照主记录。
     *
     * @param repoSnapshotRecord 快照主记录
     * @return 生成后的快照标识
     */
    public long save(RepoSnapshotRecord repoSnapshotRecord) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    """
                            insert into repo_snapshots (created_at, trigger_event, git_commit, description, article_count)
                            values (?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setObject(1, repoSnapshotRecord.getCreatedAt());
            preparedStatement.setString(2, repoSnapshotRecord.getTriggerEvent());
            preparedStatement.setString(3, repoSnapshotRecord.getGitCommit());
            preparedStatement.setString(4, repoSnapshotRecord.getDescription());
            preparedStatement.setInt(5, repoSnapshotRecord.getArticleCount());
            return preparedStatement;
        }, keyHolder);
        Number key;
        if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number currentKey) {
            key = currentKey;
        }
        else {
            key = keyHolder.getKey();
        }
        if (key == null) {
            throw new IllegalStateException("保存 repo snapshot 失败，未返回主键");
        }
        return key.longValue();
    }

    /**
     * 批量保存快照明细。
     *
     * @param itemRecords 明细记录
     */
    public void saveItems(List<RepoSnapshotItemRecord> itemRecords) {
        String sql = """
                insert into repo_snapshot_items (snapshot_id, entity_type, entity_id, content_hash, payload)
                values (?, ?, ?, ?, ?::jsonb)
                """;
        for (RepoSnapshotItemRecord itemRecord : itemRecords) {
            jdbcTemplate.update(connection -> {
                PGobject payloadObject = new PGobject();
                payloadObject.setType("jsonb");
                payloadObject.setValue(itemRecord.getPayloadJson());
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setLong(1, itemRecord.getSnapshotId());
                preparedStatement.setString(2, itemRecord.getEntityType());
                preparedStatement.setString(3, itemRecord.getEntityId());
                preparedStatement.setString(4, itemRecord.getContentHash());
                preparedStatement.setObject(5, payloadObject);
                return preparedStatement;
            });
        }
    }

    /**
     * 查询最近整库快照。
     *
     * @param limit 返回数量
     * @return 整库快照列表
     */
    public List<RepoSnapshotRecord> findRecent(int limit) {
        int safeLimit = Math.max(1, limit);
        return jdbcTemplate.query(
                """
                        select id, created_at, trigger_event, git_commit, description, article_count
                        from repo_snapshots
                        order by created_at desc, id desc
                        limit ?
                        """,
                (resultSet, rowNum) -> new RepoSnapshotRecord(
                        resultSet.getLong("id"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getString("trigger_event"),
                        resultSet.getString("git_commit"),
                        resultSet.getString("description"),
                        resultSet.getInt("article_count")
                ),
                safeLimit
        );
    }

    /**
     * 查询指定整库快照主记录。
     *
     * @param snapshotId 快照标识
     * @return 快照主记录
     */
    public Optional<RepoSnapshotRecord> findById(long snapshotId) {
        List<RepoSnapshotRecord> records = jdbcTemplate.query(
                """
                        select id, created_at, trigger_event, git_commit, description, article_count
                        from repo_snapshots
                        where id = ?
                        """,
                (resultSet, rowNum) -> new RepoSnapshotRecord(
                        resultSet.getLong("id"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getString("trigger_event"),
                        resultSet.getString("git_commit"),
                        resultSet.getString("description"),
                        resultSet.getInt("article_count")
                ),
                snapshotId
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 查询指定整库快照的明细。
     *
     * @param snapshotId 快照标识
     * @return 明细列表
     */
    public List<RepoSnapshotItemRecord> findItemsBySnapshotId(long snapshotId) {
        return jdbcTemplate.query(
                """
                        select id, snapshot_id, entity_type, entity_id, content_hash, payload::text as payload_json
                        from repo_snapshot_items
                        where snapshot_id = ?
                        order by entity_type asc, entity_id asc
                        """,
                (resultSet, rowNum) -> new RepoSnapshotItemRecord(
                        resultSet.getLong("id"),
                        resultSet.getLong("snapshot_id"),
                        resultSet.getString("entity_type"),
                        resultSet.getString("entity_id"),
                        resultSet.getString("content_hash"),
                        resultSet.getString("payload_json")
                ),
                snapshotId
        );
    }
}
