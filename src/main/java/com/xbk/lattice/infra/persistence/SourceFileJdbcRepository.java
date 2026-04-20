package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * SourceFile JDBC 仓储
 *
 * 职责：提供最小源文件落盘与读取能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class SourceFileJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 SourceFile JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public SourceFileJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存或更新源文件记录。
     *
     * @param sourceFileRecord 源文件记录
     */
    public SourceFileRecord upsert(SourceFileRecord sourceFileRecord) {
        if (jdbcTemplate == null) {
            return sourceFileRecord;
        }

        if (sourceFileRecord.getSourceId() == null) {
            SourceFileRecord legacyBoundRecord = bindLegacyDefaultSource(sourceFileRecord);
            return upsertSourceAwareRecord(legacyBoundRecord);
        }
        return upsertSourceAwareRecord(sourceFileRecord);
    }

    /**
     * 按路径查询源文件记录。
     *
     * @param filePath 文件路径
     * @return 源文件记录
     */
    public Optional<SourceFileRecord> findByPath(String filePath) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                where file_path = ? or relative_path = ?
                order by indexed_at desc, id desc
                limit 1
                """;
        List<SourceFileRecord> records = jdbcTemplate.query(sql, this::mapSourceFileRecord, filePath, filePath);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 按资料源和相对路径查询源文件记录。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @return 源文件记录
     */
    public Optional<SourceFileRecord> findBySourceIdAndRelativePath(Long sourceId, String relativePath) {
        if (jdbcTemplate == null || sourceId == null) {
            return Optional.empty();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                where source_id = ?
                  and relative_path = ?
                """;
        List<SourceFileRecord> records = jdbcTemplate.query(sql, this::mapSourceFileRecord, sourceId, relativePath);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 查询全部源文件记录。
     *
     * @return 源文件记录列表
     */
    public List<SourceFileRecord> findAll() {
        if (jdbcTemplate == null) {
            return List.of();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                order by source_id asc nulls first, relative_path asc nulls first, file_path asc
                """;
        return jdbcTemplate.query(sql, this::mapSourceFileRecord);
    }

    /**
     * 查询指定资料源下的全部源文件记录。
     *
     * @param sourceId 资料源主键
     * @return 源文件记录列表
     */
    public List<SourceFileRecord> findBySourceId(Long sourceId) {
        if (jdbcTemplate == null || sourceId == null) {
            return List.of();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                where source_id = ?
                order by relative_path asc, id asc
                """;
        return jdbcTemplate.query(sql, this::mapSourceFileRecord, sourceId);
    }

    private SourceFileRecord upsertSourceAwareRecord(SourceFileRecord sourceFileRecord) {
        String sql = """
                insert into source_files (
                    source_id, file_path, relative_path, source_sync_run_id, content_preview,
                    format, file_size, content_text, metadata_json, is_verbatim, raw_path
                )
                values (?, ?, ?, ?, ?, ?, ?, ?::text, ?::jsonb, ?, ?)
                on conflict (source_id, relative_path) do update
                set file_path = excluded.file_path,
                    source_sync_run_id = excluded.source_sync_run_id,
                    content_preview = excluded.content_preview,
                    format = excluded.format,
                    file_size = excluded.file_size,
                    content_text = excluded.content_text,
                    metadata_json = excluded.metadata_json,
                    is_verbatim = excluded.is_verbatim,
                    raw_path = excluded.raw_path,
                    indexed_at = CURRENT_TIMESTAMP
                returning id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                          format, file_size, content_text, metadata_json::text as metadata_json,
                          is_verbatim, raw_path
                """;
        return jdbcTemplate.queryForObject(
                sql,
                this::mapSourceFileRecord,
                sourceFileRecord.getSourceId(),
                sourceFileRecord.getFilePath(),
                sourceFileRecord.getRelativePath(),
                sourceFileRecord.getSourceSyncRunId(),
                sourceFileRecord.getContentPreview(),
                sourceFileRecord.getFormat(),
                sourceFileRecord.getFileSize(),
                sourceFileRecord.getContentText(),
                sourceFileRecord.getMetadataJson(),
                sourceFileRecord.isVerbatim(),
                sourceFileRecord.getRawPath()
        );
    }

    private SourceFileRecord bindLegacyDefaultSource(SourceFileRecord sourceFileRecord) {
        Long legacyDefaultSourceId = resolveLegacyDefaultSourceId();
        String relativePath = sourceFileRecord.getRelativePath();
        if (relativePath == null || relativePath.isBlank()) {
            relativePath = sourceFileRecord.getFilePath();
        }
        return new SourceFileRecord(
                sourceFileRecord.getId(),
                legacyDefaultSourceId,
                sourceFileRecord.getFilePath(),
                relativePath,
                sourceFileRecord.getSourceSyncRunId(),
                sourceFileRecord.getContentPreview(),
                sourceFileRecord.getFormat(),
                sourceFileRecord.getFileSize(),
                sourceFileRecord.getContentText(),
                sourceFileRecord.getMetadataJson(),
                sourceFileRecord.isVerbatim(),
                sourceFileRecord.getRawPath()
        );
    }

    private Long resolveLegacyDefaultSourceId() {
        List<Long> sourceIds = jdbcTemplate.queryForList(
                "select id from knowledge_sources where source_code = 'legacy-default'",
                Long.class
        );
        if (!sourceIds.isEmpty()) {
            return sourceIds.get(0);
        }

        jdbcTemplate.update(
                """
                        insert into knowledge_sources (
                            source_code,
                            name,
                            source_type,
                            content_profile,
                            status,
                            visibility,
                            default_sync_mode,
                            config_json,
                            metadata_json
                        )
                        values (
                            'legacy-default',
                            'Legacy Default Source',
                            'UPLOAD',
                            'DOCUMENT',
                            'ACTIVE',
                            'NORMAL',
                            'FULL',
                            '{}'::jsonb,
                            '{"legacyDefault":true}'::jsonb
                        )
                        on conflict (source_code) do nothing
                        """
        );

        List<Long> ensuredSourceIds = jdbcTemplate.queryForList(
                "select id from knowledge_sources where source_code = 'legacy-default'",
                Long.class
        );
        if (ensuredSourceIds.isEmpty()) {
            throw new IllegalStateException("legacy-default knowledge source is missing");
        }
        return ensuredSourceIds.get(0);
    }

    /**
     * 映射单行源文件记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 源文件记录
     * @throws SQLException SQL 异常
     */
    private SourceFileRecord mapSourceFileRecord(ResultSet resultSet, int rowNum) throws SQLException {
        Object sourceId = resultSet.getObject("source_id");
        Object sourceSyncRunId = resultSet.getObject("source_sync_run_id");
        return new SourceFileRecord(
                resultSet.getLong("id"),
                sourceId == null ? null : resultSet.getLong("source_id"),
                resultSet.getString("file_path"),
                resultSet.getString("relative_path"),
                sourceSyncRunId == null ? null : resultSet.getLong("source_sync_run_id"),
                resultSet.getString("content_preview"),
                resultSet.getString("format"),
                resultSet.getLong("file_size"),
                resultSet.getString("content_text"),
                resultSet.getString("metadata_json"),
                resultSet.getBoolean("is_verbatim"),
                resultSet.getString("raw_path")
        );
    }
}
