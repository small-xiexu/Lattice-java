package com.xbk.lattice.documentparse.infra;

import com.xbk.lattice.documentparse.domain.DocumentParseProviderConnection;
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
 * 职责：提供 document_parse_provider_connections 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DocumentParseProviderConnectionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文档解析连接 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DocumentParseProviderConnectionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    public DocumentParseProviderConnection save(DocumentParseProviderConnection connection) {
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
    public List<DocumentParseProviderConnection> findAll() {
        return jdbcTemplate.query(
                """
                        select id, connection_code, provider_type, base_url, endpoint_path,
                               credential_ciphertext, credential_mask, extra_config_json,
                               enabled, created_by, updated_by, created_at, updated_at
                        from document_parse_provider_connections
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
    public Optional<DocumentParseProviderConnection> findById(Long id) {
        List<DocumentParseProviderConnection> items = jdbcTemplate.query(
                """
                        select id, connection_code, provider_type, base_url, endpoint_path,
                               credential_ciphertext, credential_mask, extra_config_json,
                               enabled, created_by, updated_by, created_at, updated_at
                        from document_parse_provider_connections
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
     * 删除连接配置。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        jdbcTemplate.update("delete from document_parse_provider_connections where id = ?", id);
    }

    private DocumentParseProviderConnection insert(DocumentParseProviderConnection connection) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into document_parse_provider_connections (
                                connection_code, provider_type, base_url, endpoint_path,
                                credential_ciphertext, credential_mask, extra_config_json,
                                enabled, created_by, updated_by
                            )
                            values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setString(1, connection.getConnectionCode());
            preparedStatement.setString(2, connection.getProviderType());
            preparedStatement.setString(3, connection.getBaseUrl());
            preparedStatement.setString(4, connection.getEndpointPath());
            preparedStatement.setString(5, connection.getCredentialCiphertext());
            preparedStatement.setString(6, connection.getCredentialMask());
            preparedStatement.setString(7, connection.getExtraConfigJson());
            preparedStatement.setBoolean(8, connection.isEnabled());
            preparedStatement.setString(9, connection.getCreatedBy());
            preparedStatement.setString(10, connection.getUpdatedBy());
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
            throw new IllegalStateException("Failed to insert document_parse_provider_connections");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private void update(DocumentParseProviderConnection connection) {
        jdbcTemplate.update(
                """
                        update document_parse_provider_connections
                        set connection_code = ?,
                            provider_type = ?,
                            base_url = ?,
                            endpoint_path = ?,
                            credential_ciphertext = ?,
                            credential_mask = ?,
                            extra_config_json = cast(? as jsonb),
                            enabled = ?,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                connection.getConnectionCode(),
                connection.getProviderType(),
                connection.getBaseUrl(),
                connection.getEndpointPath(),
                connection.getCredentialCiphertext(),
                connection.getCredentialMask(),
                connection.getExtraConfigJson(),
                connection.isEnabled(),
                connection.getUpdatedBy(),
                connection.getId()
        );
    }

    private DocumentParseProviderConnection mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new DocumentParseProviderConnection(
                resultSet.getLong("id"),
                resultSet.getString("connection_code"),
                resultSet.getString("provider_type"),
                resultSet.getString("base_url"),
                resultSet.getString("endpoint_path"),
                resultSet.getString("credential_ciphertext"),
                resultSet.getString("credential_mask"),
                resultSet.getString("extra_config_json"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
