package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.LlmProviderConnection;
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
 * Provider 连接 JDBC 仓储
 *
 * 职责：提供 llm_provider_connections 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class LlmProviderConnectionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Provider 连接 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public LlmProviderConnectionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    public LlmProviderConnection save(LlmProviderConnection connection) {
        if (connection.getId() == null) {
            return insert(connection);
        }
        update(connection);
        return findById(connection.getId()).orElseThrow();
    }

    /**
     * 查询全部连接配置。
     *
     * @return 连接配置列表
     */
    public List<LlmProviderConnection> findAll() {
        return jdbcTemplate.query(
                """
                        select id, connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask,
                               enabled, remarks, created_by, updated_by, created_at, updated_at
                        from llm_provider_connections
                        order by id desc
                        """,
                this::mapRecord
        );
    }

    /**
     * 按主键查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    public Optional<LlmProviderConnection> findById(Long id) {
        List<LlmProviderConnection> items = jdbcTemplate.query(
                """
                        select id, connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask,
                               enabled, remarks, created_by, updated_by, created_at, updated_at
                        from llm_provider_connections
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

    /**
     * 按主键查询启用中的连接配置。
     *
     * @param id 主键
     * @return 启用中的连接配置
     */
    public Optional<LlmProviderConnection> findEnabledById(Long id) {
        List<LlmProviderConnection> items = jdbcTemplate.query(
                """
                        select id, connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask,
                               enabled, remarks, created_by, updated_by, created_at, updated_at
                        from llm_provider_connections
                        where id = ?
                          and enabled = true
                        """,
                this::mapRecord,
                id
        );
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    /**
     * 删除连接配置。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        jdbcTemplate.update("delete from llm_provider_connections where id = ?", id);
    }

    private LlmProviderConnection insert(LlmProviderConnection connection) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into llm_provider_connections (
                                connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask,
                                enabled, remarks, created_by, updated_by
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setString(1, connection.getConnectionCode());
            preparedStatement.setString(2, connection.getProviderType());
            preparedStatement.setString(3, connection.getBaseUrl());
            preparedStatement.setString(4, connection.getApiKeyCiphertext());
            preparedStatement.setString(5, connection.getApiKeyMask());
            preparedStatement.setBoolean(6, connection.isEnabled());
            preparedStatement.setString(7, connection.getRemarks());
            preparedStatement.setString(8, connection.getCreatedBy());
            preparedStatement.setString(9, connection.getUpdatedBy());
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
            throw new IllegalStateException("Failed to insert llm_provider_connections");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private void update(LlmProviderConnection connection) {
        jdbcTemplate.update(
                """
                        update llm_provider_connections
                        set connection_code = ?,
                            provider_type = ?,
                            base_url = ?,
                            api_key_ciphertext = ?,
                            api_key_mask = ?,
                            enabled = ?,
                            remarks = ?,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                connection.getConnectionCode(),
                connection.getProviderType(),
                connection.getBaseUrl(),
                connection.getApiKeyCiphertext(),
                connection.getApiKeyMask(),
                connection.isEnabled(),
                connection.getRemarks(),
                connection.getUpdatedBy(),
                connection.getId()
        );
    }

    private LlmProviderConnection mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new LlmProviderConnection(
                resultSet.getLong("id"),
                resultSet.getString("connection_code"),
                resultSet.getString("provider_type"),
                resultSet.getString("base_url"),
                resultSet.getString("api_key_ciphertext"),
                resultSet.getString("api_key_mask"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("remarks"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
