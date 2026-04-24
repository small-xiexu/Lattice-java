package com.xbk.lattice.documentparse.infra.persistence;

import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
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
 * 文档解析连接 JDBC 仓储
 *
 * 职责：提供 document_parse_connections 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DocumentParseConnectionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文档解析连接 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DocumentParseConnectionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    public ProviderConnection save(ProviderConnection connection) {
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
    public List<ProviderConnection> findAll() {
        return jdbcTemplate.query(
                """
                        select id, connection_code, provider_type, base_url,
                               credential_ciphertext, credential_mask, config_json,
                               enabled, created_by, updated_by, created_at, updated_at
                        from document_parse_connections
                        order by id desc
                        """,
                this::mapRow
        );
    }

    /**
     * 按主键查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    public Optional<ProviderConnection> findById(Long id) {
        List<ProviderConnection> records = jdbcTemplate.query(
                """
                        select id, connection_code, provider_type, base_url,
                               credential_ciphertext, credential_mask, config_json,
                               enabled, created_by, updated_by, created_at, updated_at
                        from document_parse_connections
                        where id = ?
                        """,
                this::mapRow,
                id
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 删除连接配置。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        jdbcTemplate.update("delete from document_parse_connections where id = ?", id);
    }

    /**
     * 插入连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    private ProviderConnection insert(ProviderConnection connection) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into document_parse_connections (
                                connection_code, provider_type, base_url,
                                credential_ciphertext, credential_mask, config_json,
                                enabled, created_by, updated_by
                            )
                            values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setString(1, connection.getConnectionCode());
            preparedStatement.setString(2, connection.getProviderType());
            preparedStatement.setString(3, connection.getBaseUrl());
            preparedStatement.setString(4, connection.getCredentialCiphertext());
            preparedStatement.setString(5, connection.getCredentialMask());
            preparedStatement.setString(6, connection.getConfigJson());
            preparedStatement.setBoolean(7, connection.isEnabled());
            preparedStatement.setString(8, connection.getCreatedBy());
            preparedStatement.setString(9, connection.getUpdatedBy());
            return preparedStatement;
        }, keyHolder);
        Number key = resolveGeneratedKey(keyHolder, "document_parse_connections");
        return findById(key.longValue()).orElseThrow();
    }

    /**
     * 更新连接配置。
     *
     * @param connection 连接配置
     */
    private void update(ProviderConnection connection) {
        jdbcTemplate.update(
                """
                        update document_parse_connections
                        set connection_code = ?,
                            provider_type = ?,
                            base_url = ?,
                            credential_ciphertext = ?,
                            credential_mask = ?,
                            config_json = cast(? as jsonb),
                            enabled = ?,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                connection.getConnectionCode(),
                connection.getProviderType(),
                connection.getBaseUrl(),
                connection.getCredentialCiphertext(),
                connection.getCredentialMask(),
                connection.getConfigJson(),
                connection.isEnabled(),
                connection.getUpdatedBy(),
                connection.getId()
        );
    }

    /**
     * 解析主键生成结果。
     *
     * @param keyHolder 主键持有器
     * @param tableName 表名
     * @return 主键
     */
    private Number resolveGeneratedKey(KeyHolder keyHolder, String tableName) {
        Object generatedId = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("id");
        if (generatedId instanceof Number) {
            return (Number) generatedId;
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            return key;
        }
        throw new IllegalStateException("Failed to insert " + tableName);
    }

    /**
     * 映射连接配置记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 连接配置记录
     * @throws SQLException SQL 异常
     */
    private ProviderConnection mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProviderConnection(
                resultSet.getLong("id"),
                resultSet.getString("connection_code"),
                resultSet.getString("provider_type"),
                resultSet.getString("base_url"),
                resultSet.getString("credential_ciphertext"),
                resultSet.getString("credential_mask"),
                resultSet.getString("config_json"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
