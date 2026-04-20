package com.xbk.lattice.source.infra;

import com.xbk.lattice.source.domain.SourceCredential;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 资料源凭据 JDBC 仓储。
 *
 * 职责：负责 source_credentials 表的读写与查询
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class SourceCredentialJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建资料源凭据 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public SourceCredentialJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询全部凭据。
     *
     * @return 凭据列表
     */
    public List<SourceCredential> findAll() {
        return jdbcTemplate.query(
                """
                        select id, credential_code, credential_type, secret_ciphertext, secret_mask,
                               enabled, created_by, updated_by, created_at, updated_at
                        from source_credentials
                        order by updated_at desc, id desc
                        """,
                this::mapRecord
        );
    }

    /**
     * 按主键查询凭据。
     *
     * @param id 主键
     * @return 凭据
     */
    public Optional<SourceCredential> findById(Long id) {
        List<SourceCredential> items = jdbcTemplate.query(
                """
                        select id, credential_code, credential_type, secret_ciphertext, secret_mask,
                               enabled, created_by, updated_by, created_at, updated_at
                        from source_credentials
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
     * 按编码查询凭据。
     *
     * @param credentialCode 凭据编码
     * @return 凭据
     */
    public Optional<SourceCredential> findByCredentialCode(String credentialCode) {
        List<SourceCredential> items = jdbcTemplate.query(
                """
                        select id, credential_code, credential_type, secret_ciphertext, secret_mask,
                               enabled, created_by, updated_by, created_at, updated_at
                        from source_credentials
                        where credential_code = ?
                        """,
                this::mapRecord,
                credentialCode
        );
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    /**
     * 保存凭据。
     *
     * @param sourceCredential 凭据
     * @return 已保存凭据
     */
    public SourceCredential save(SourceCredential sourceCredential) {
        if (sourceCredential.getId() == null) {
            return insert(sourceCredential);
        }
        update(sourceCredential);
        return findById(sourceCredential.getId()).orElseThrow();
    }

    private SourceCredential insert(SourceCredential sourceCredential) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    """
                            insert into source_credentials (
                                credential_code,
                                credential_type,
                                secret_ciphertext,
                                secret_mask,
                                enabled,
                                created_by,
                                updated_by
                            )
                            values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    new String[]{"id"}
            );
            preparedStatement.setString(1, sourceCredential.getCredentialCode());
            preparedStatement.setString(2, sourceCredential.getCredentialType());
            preparedStatement.setString(3, sourceCredential.getSecretCiphertext());
            preparedStatement.setString(4, sourceCredential.getSecretMask());
            preparedStatement.setBoolean(5, sourceCredential.isEnabled());
            preparedStatement.setString(6, sourceCredential.getCreatedBy());
            preparedStatement.setString(7, sourceCredential.getUpdatedBy());
            return preparedStatement;
        }, keyHolder);
        Number key = keyHolder.getKeyAs(Long.class);
        if (key == null) {
            throw new IllegalStateException("failed to insert source_credentials");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private void update(SourceCredential sourceCredential) {
        jdbcTemplate.update(
                """
                        update source_credentials
                        set credential_code = ?,
                            credential_type = ?,
                            secret_ciphertext = ?,
                            secret_mask = ?,
                            enabled = ?,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                sourceCredential.getCredentialCode(),
                sourceCredential.getCredentialType(),
                sourceCredential.getSecretCiphertext(),
                sourceCredential.getSecretMask(),
                sourceCredential.isEnabled(),
                sourceCredential.getUpdatedBy(),
                sourceCredential.getId()
        );
    }

    private SourceCredential mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new SourceCredential(
                resultSet.getLong("id"),
                resultSet.getString("credential_code"),
                resultSet.getString("credential_type"),
                resultSet.getString("secret_ciphertext"),
                resultSet.getString("secret_mask"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
