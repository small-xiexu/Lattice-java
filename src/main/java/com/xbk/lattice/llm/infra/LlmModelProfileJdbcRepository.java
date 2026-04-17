package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.LlmModelProfile;
import org.postgresql.util.PGobject;
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
 * 模型配置 JDBC 仓储
 *
 * 职责：提供 llm_model_profiles 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class LlmModelProfileJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建模型配置 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public LlmModelProfileJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存模型配置。
     *
     * @param modelProfile 模型配置
     * @return 保存后的模型配置
     */
    public LlmModelProfile save(LlmModelProfile modelProfile) {
        if (modelProfile.getId() == null) {
            return insert(modelProfile);
        }
        update(modelProfile);
        return findById(modelProfile.getId()).orElseThrow();
    }

    /**
     * 查询全部模型配置。
     *
     * @return 模型配置列表
     */
    public List<LlmModelProfile> findAll() {
        return jdbcTemplate.query(
                """
                        select id, model_code, connection_id, model_name, temperature, max_tokens,
                               timeout_seconds, input_price_per_1k_tokens, output_price_per_1k_tokens,
                               extra_options_json, enabled, remarks, created_by, updated_by, created_at, updated_at
                        from llm_model_profiles
                        order by id desc
                        """,
                this::mapRecord
        );
    }

    /**
     * 按主键查询模型配置。
     *
     * @param id 主键
     * @return 模型配置
     */
    public Optional<LlmModelProfile> findById(Long id) {
        List<LlmModelProfile> items = jdbcTemplate.query(
                """
                        select id, model_code, connection_id, model_name, temperature, max_tokens,
                               timeout_seconds, input_price_per_1k_tokens, output_price_per_1k_tokens,
                               extra_options_json, enabled, remarks, created_by, updated_by, created_at, updated_at
                        from llm_model_profiles
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
     * 按主键查询启用中的模型配置。
     *
     * @param id 主键
     * @return 启用中的模型配置
     */
    public Optional<LlmModelProfile> findEnabledById(Long id) {
        List<LlmModelProfile> items = jdbcTemplate.query(
                """
                        select id, model_code, connection_id, model_name, temperature, max_tokens,
                               timeout_seconds, input_price_per_1k_tokens, output_price_per_1k_tokens,
                               extra_options_json, enabled, remarks, created_by, updated_by, created_at, updated_at
                        from llm_model_profiles
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
     * 删除模型配置。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        jdbcTemplate.update("delete from llm_model_profiles where id = ?", id);
    }

    private LlmModelProfile insert(LlmModelProfile modelProfile) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into llm_model_profiles (
                                model_code, connection_id, model_name, temperature, max_tokens, timeout_seconds,
                                input_price_per_1k_tokens, output_price_per_1k_tokens, extra_options_json,
                                enabled, remarks, created_by, updated_by
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            PGobject extraOptions = new PGobject();
            extraOptions.setType("jsonb");
            extraOptions.setValue(modelProfile.getExtraOptionsJson());
            preparedStatement.setString(1, modelProfile.getModelCode());
            preparedStatement.setLong(2, modelProfile.getConnectionId());
            preparedStatement.setString(3, modelProfile.getModelName());
            preparedStatement.setBigDecimal(4, modelProfile.getTemperature());
            preparedStatement.setObject(5, modelProfile.getMaxTokens());
            preparedStatement.setObject(6, modelProfile.getTimeoutSeconds());
            preparedStatement.setBigDecimal(7, modelProfile.getInputPricePer1kTokens());
            preparedStatement.setBigDecimal(8, modelProfile.getOutputPricePer1kTokens());
            preparedStatement.setObject(9, extraOptions);
            preparedStatement.setBoolean(10, modelProfile.isEnabled());
            preparedStatement.setString(11, modelProfile.getRemarks());
            preparedStatement.setString(12, modelProfile.getCreatedBy());
            preparedStatement.setString(13, modelProfile.getUpdatedBy());
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
            throw new IllegalStateException("Failed to insert llm_model_profiles");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private void update(LlmModelProfile modelProfile) {
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            update llm_model_profiles
                            set model_code = ?,
                                connection_id = ?,
                                model_name = ?,
                                temperature = ?,
                                max_tokens = ?,
                                timeout_seconds = ?,
                                input_price_per_1k_tokens = ?,
                                output_price_per_1k_tokens = ?,
                                extra_options_json = ?,
                                enabled = ?,
                                remarks = ?,
                                updated_by = ?,
                                updated_at = current_timestamp
                            where id = ?
                            """
            );
            PGobject extraOptions = new PGobject();
            extraOptions.setType("jsonb");
            extraOptions.setValue(modelProfile.getExtraOptionsJson());
            preparedStatement.setString(1, modelProfile.getModelCode());
            preparedStatement.setLong(2, modelProfile.getConnectionId());
            preparedStatement.setString(3, modelProfile.getModelName());
            preparedStatement.setBigDecimal(4, modelProfile.getTemperature());
            preparedStatement.setObject(5, modelProfile.getMaxTokens());
            preparedStatement.setObject(6, modelProfile.getTimeoutSeconds());
            preparedStatement.setBigDecimal(7, modelProfile.getInputPricePer1kTokens());
            preparedStatement.setBigDecimal(8, modelProfile.getOutputPricePer1kTokens());
            preparedStatement.setObject(9, extraOptions);
            preparedStatement.setBoolean(10, modelProfile.isEnabled());
            preparedStatement.setString(11, modelProfile.getRemarks());
            preparedStatement.setString(12, modelProfile.getUpdatedBy());
            preparedStatement.setLong(13, modelProfile.getId());
            return preparedStatement;
        });
    }

    private LlmModelProfile mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new LlmModelProfile(
                resultSet.getLong("id"),
                resultSet.getString("model_code"),
                resultSet.getLong("connection_id"),
                resultSet.getString("model_name"),
                resultSet.getBigDecimal("temperature"),
                resultSet.getObject("max_tokens", Integer.class),
                resultSet.getObject("timeout_seconds", Integer.class),
                resultSet.getBigDecimal("input_price_per_1k_tokens"),
                resultSet.getBigDecimal("output_price_per_1k_tokens"),
                resultSet.getString("extra_options_json"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("remarks"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
