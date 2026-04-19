package com.xbk.lattice.source.infra;

import com.xbk.lattice.source.domain.KnowledgeSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
 * 资料源 JDBC 仓储
 *
 * 职责：提供 knowledge_sources 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class KnowledgeSourceJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public KnowledgeSourceJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public List<KnowledgeSource> findAll() {
        return jdbcTemplate.query(
                """
                        select id, source_code, name, source_type, content_profile, status, visibility,
                               default_sync_mode, config_json, metadata_json, latest_manifest_hash,
                               last_sync_run_id, last_sync_status, last_sync_at, created_at, updated_at
                        from knowledge_sources
                        order by id desc
                        """,
                this::mapRecord
        );
    }

    public Optional<KnowledgeSource> findById(Long id) {
        List<KnowledgeSource> items = jdbcTemplate.query(
                """
                        select id, source_code, name, source_type, content_profile, status, visibility,
                               default_sync_mode, config_json, metadata_json, latest_manifest_hash,
                               last_sync_run_id, last_sync_status, last_sync_at, created_at, updated_at
                        from knowledge_sources
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

    public Optional<KnowledgeSource> findBySourceCode(String sourceCode) {
        List<KnowledgeSource> items = jdbcTemplate.query(
                """
                        select id, source_code, name, source_type, content_profile, status, visibility,
                               default_sync_mode, config_json, metadata_json, latest_manifest_hash,
                               last_sync_run_id, last_sync_status, last_sync_at, created_at, updated_at
                        from knowledge_sources
                        where source_code = ?
                        """,
                this::mapRecord,
                sourceCode
        );
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    /**
     * 统计资料源数量。
     *
     * 职责：为后台分页列表返回总条数，并默认排除 legacy-default
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @return 资料源总数
     */
    public long countAll(String keyword, String status, String sourceType) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder sqlBuilder = new StringBuilder("""
                select count(*)
                from knowledge_sources
                where source_code <> 'legacy-default'
                """);
        appendFilters(sqlBuilder, parameters, keyword, status, sourceType);
        Long count = namedParameterJdbcTemplate.queryForObject(sqlBuilder.toString(), parameters, Long.class);
        return count == null ? 0L : count.longValue();
    }

    /**
     * 分页查询资料源。
     *
     * 职责：为后台列表提供带过滤条件的分页结果
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @param offset 偏移量
     * @param limit 分页大小
     * @return 资料源列表
     */
    public List<KnowledgeSource> findPage(
            String keyword,
            String status,
            String sourceType,
            int offset,
            int limit
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("offset", offset);
        parameters.addValue("limit", limit);
        StringBuilder sqlBuilder = new StringBuilder("""
                select id, source_code, name, source_type, content_profile, status, visibility,
                       default_sync_mode, config_json, metadata_json, latest_manifest_hash,
                       last_sync_run_id, last_sync_status, last_sync_at, created_at, updated_at
                from knowledge_sources
                where source_code <> 'legacy-default'
                """);
        appendFilters(sqlBuilder, parameters, keyword, status, sourceType);
        sqlBuilder.append("""
                
                order by updated_at desc, id desc
                limit :limit offset :offset
                """);
        return namedParameterJdbcTemplate.query(sqlBuilder.toString(), parameters, this::mapRecord);
    }

    public KnowledgeSource save(KnowledgeSource source) {
        if (source.getId() == null) {
            return insert(source);
        }
        update(source);
        return findById(source.getId()).orElseThrow();
    }

    private KnowledgeSource insert(KnowledgeSource source) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into knowledge_sources (
                                source_code, name, source_type, content_profile, status, visibility,
                                default_sync_mode, config_json, metadata_json, latest_manifest_hash,
                                last_sync_run_id, last_sync_status, last_sync_at
                            )
                            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setString(1, source.getSourceCode());
            preparedStatement.setString(2, source.getName());
            preparedStatement.setString(3, source.getSourceType());
            preparedStatement.setString(4, source.getContentProfile());
            preparedStatement.setString(5, source.getStatus());
            preparedStatement.setString(6, source.getVisibility());
            preparedStatement.setString(7, source.getDefaultSyncMode());
            preparedStatement.setString(8, source.getConfigJson());
            preparedStatement.setString(9, source.getMetadataJson());
            preparedStatement.setString(10, source.getLatestManifestHash());
            preparedStatement.setObject(11, source.getLastSyncRunId());
            preparedStatement.setString(12, source.getLastSyncStatus());
            preparedStatement.setObject(13, source.getLastSyncAt());
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
            throw new IllegalStateException("Failed to insert knowledge_sources");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private void update(KnowledgeSource source) {
        jdbcTemplate.update(
                """
                        update knowledge_sources
                        set source_code = ?,
                            name = ?,
                            source_type = ?,
                            content_profile = ?,
                            status = ?,
                            visibility = ?,
                            default_sync_mode = ?,
                            config_json = cast(? as jsonb),
                            metadata_json = cast(? as jsonb),
                            latest_manifest_hash = ?,
                            last_sync_run_id = ?,
                            last_sync_status = ?,
                            last_sync_at = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                source.getSourceCode(),
                source.getName(),
                source.getSourceType(),
                source.getContentProfile(),
                source.getStatus(),
                source.getVisibility(),
                source.getDefaultSyncMode(),
                source.getConfigJson(),
                source.getMetadataJson(),
                source.getLatestManifestHash(),
                source.getLastSyncRunId(),
                source.getLastSyncStatus(),
                source.getLastSyncAt(),
                source.getId()
        );
    }

    private KnowledgeSource mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        Object lastSyncRunId = resultSet.getObject("last_sync_run_id");
        return new KnowledgeSource(
                resultSet.getLong("id"),
                resultSet.getString("source_code"),
                resultSet.getString("name"),
                resultSet.getString("source_type"),
                resultSet.getString("content_profile"),
                resultSet.getString("status"),
                resultSet.getString("visibility"),
                resultSet.getString("default_sync_mode"),
                resultSet.getString("config_json"),
                resultSet.getString("metadata_json"),
                resultSet.getString("latest_manifest_hash"),
                lastSyncRunId == null ? null : resultSet.getLong("last_sync_run_id"),
                resultSet.getString("last_sync_status"),
                resultSet.getObject("last_sync_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    /**
     * 拼接资料源后台过滤条件。
     *
     * 职责：统一构造列表查询与总数查询的 where 条件
     *
     * @param sqlBuilder SQL 构造器
     * @param parameters 参数集合
     * @param keyword 关键词
     * @param status 状态
     * @param sourceType 类型
     */
    private void appendFilters(
            StringBuilder sqlBuilder,
            MapSqlParameterSource parameters,
            String keyword,
            String status,
            String sourceType
    ) {
        if (keyword != null && !keyword.isBlank()) {
            sqlBuilder.append("""
                    
                      and (
                          source_code ilike :keyword
                          or name ilike :keyword
                      )
                    """);
            parameters.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sqlBuilder.append("""
                    
                      and status = :status
                    """);
            parameters.addValue("status", status.trim());
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sqlBuilder.append("""
                    
                      and source_type = :sourceType
                    """);
            parameters.addValue("sourceType", sourceType.trim());
        }
    }
}
