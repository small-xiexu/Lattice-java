package com.xbk.lattice.documentparse.infra;

import com.xbk.lattice.documentparse.domain.DocumentParseSettings;
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
 * 文档解析设置 JDBC 仓储
 *
 * 职责：提供 document_parse_settings 表的读写能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DocumentParseSettingsJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文档解析设置 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DocumentParseSettingsJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询默认配置。
     *
     * @return 默认配置
     */
    public Optional<DocumentParseSettings> findDefault() {
        List<DocumentParseSettings> items = jdbcTemplate.query(
                """
                        select id, config_scope, default_connection_id, image_ocr_enabled,
                               scanned_pdf_ocr_enabled, cleanup_enabled, cleanup_model_profile_id,
                               created_by, updated_by, created_at, updated_at
                        from document_parse_settings
                        where config_scope = ?
                        """,
                this::mapRecord,
                DocumentParseSettings.DEFAULT_SCOPE
        );
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    /**
     * 保存默认配置。
     *
     * @param settings 配置
     * @return 保存后的配置
     */
    public DocumentParseSettings save(DocumentParseSettings settings) {
        if (settings.getId() == null) {
            return insert(settings);
        }
        update(settings);
        return findDefault().orElseThrow();
    }

    private DocumentParseSettings insert(DocumentParseSettings settings) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into document_parse_settings (
                                config_scope, default_connection_id, image_ocr_enabled,
                                scanned_pdf_ocr_enabled, cleanup_enabled, cleanup_model_profile_id,
                                created_by, updated_by
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setString(1, settings.getConfigScope());
            preparedStatement.setObject(2, settings.getDefaultConnectionId());
            preparedStatement.setBoolean(3, settings.isImageOcrEnabled());
            preparedStatement.setBoolean(4, settings.isScannedPdfOcrEnabled());
            preparedStatement.setBoolean(5, settings.isCleanupEnabled());
            preparedStatement.setObject(6, settings.getCleanupModelProfileId());
            preparedStatement.setString(7, settings.getCreatedBy());
            preparedStatement.setString(8, settings.getUpdatedBy());
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
            throw new IllegalStateException("Failed to insert document_parse_settings");
        }
        return findDefault().orElseThrow();
    }

    private void update(DocumentParseSettings settings) {
        jdbcTemplate.update(
                """
                        update document_parse_settings
                        set default_connection_id = ?,
                            image_ocr_enabled = ?,
                            scanned_pdf_ocr_enabled = ?,
                            cleanup_enabled = ?,
                            cleanup_model_profile_id = ?,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                settings.getDefaultConnectionId(),
                settings.isImageOcrEnabled(),
                settings.isScannedPdfOcrEnabled(),
                settings.isCleanupEnabled(),
                settings.getCleanupModelProfileId(),
                settings.getUpdatedBy(),
                settings.getId()
        );
    }

    private DocumentParseSettings mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        Object defaultConnectionId = resultSet.getObject("default_connection_id");
        Object cleanupModelProfileId = resultSet.getObject("cleanup_model_profile_id");
        return new DocumentParseSettings(
                resultSet.getLong("id"),
                resultSet.getString("config_scope"),
                defaultConnectionId == null ? null : resultSet.getLong("default_connection_id"),
                resultSet.getBoolean("image_ocr_enabled"),
                resultSet.getBoolean("scanned_pdf_ocr_enabled"),
                resultSet.getBoolean("cleanup_enabled"),
                cleanupModelProfileId == null ? null : resultSet.getLong("cleanup_model_profile_id"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
