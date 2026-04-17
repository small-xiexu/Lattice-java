package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.AgentModelBinding;
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
 * Agent 绑定 JDBC 仓储
 *
 * 职责：提供 agent_model_bindings 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class AgentModelBindingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Agent 绑定 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public AgentModelBindingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存 Agent 绑定。
     *
     * @param binding Agent 绑定
     * @return 保存后的 Agent 绑定
     */
    public AgentModelBinding save(AgentModelBinding binding) {
        if (binding.getId() == null) {
            return insert(binding);
        }
        update(binding);
        return findById(binding.getId()).orElseThrow();
    }

    /**
     * 查询全部 Agent 绑定。
     *
     * @return Agent 绑定列表
     */
    public List<AgentModelBinding> findAll() {
        return jdbcTemplate.query(
                """
                        select id, scene, agent_role, primary_model_profile_id, fallback_model_profile_id,
                               route_label, enabled, remarks, created_by, updated_by, created_at, updated_at
                        from agent_model_bindings
                        order by scene asc, agent_role asc
                        """,
                this::mapRecord
        );
    }

    /**
     * 查询某个场景下启用中的 Agent 绑定。
     *
     * @param scene 场景
     * @return Agent 绑定列表
     */
    public List<AgentModelBinding> findEnabledByScene(String scene) {
        return jdbcTemplate.query(
                """
                        select id, scene, agent_role, primary_model_profile_id, fallback_model_profile_id,
                               route_label, enabled, remarks, created_by, updated_by, created_at, updated_at
                        from agent_model_bindings
                        where scene = ?
                          and enabled = true
                        order by agent_role asc
                        """,
                this::mapRecord,
                scene
        );
    }

    /**
     * 按主键查询 Agent 绑定。
     *
     * @param id 主键
     * @return Agent 绑定
     */
    public Optional<AgentModelBinding> findById(Long id) {
        List<AgentModelBinding> items = jdbcTemplate.query(
                """
                        select id, scene, agent_role, primary_model_profile_id, fallback_model_profile_id,
                               route_label, enabled, remarks, created_by, updated_by, created_at, updated_at
                        from agent_model_bindings
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
     * 删除 Agent 绑定。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        jdbcTemplate.update("delete from agent_model_bindings where id = ?", id);
    }

    private AgentModelBinding insert(AgentModelBinding binding) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into agent_model_bindings (
                                scene, agent_role, primary_model_profile_id, fallback_model_profile_id,
                                route_label, enabled, remarks, created_by, updated_by
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            preparedStatement.setString(1, binding.getScene());
            preparedStatement.setString(2, binding.getAgentRole());
            preparedStatement.setLong(3, binding.getPrimaryModelProfileId());
            preparedStatement.setObject(4, binding.getFallbackModelProfileId());
            preparedStatement.setString(5, binding.getRouteLabel());
            preparedStatement.setBoolean(6, binding.isEnabled());
            preparedStatement.setString(7, binding.getRemarks());
            preparedStatement.setString(8, binding.getCreatedBy());
            preparedStatement.setString(9, binding.getUpdatedBy());
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
            throw new IllegalStateException("Failed to insert agent_model_bindings");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private void update(AgentModelBinding binding) {
        jdbcTemplate.update(
                """
                        update agent_model_bindings
                        set scene = ?,
                            agent_role = ?,
                            primary_model_profile_id = ?,
                            fallback_model_profile_id = ?,
                            route_label = ?,
                            enabled = ?,
                            remarks = ?,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                binding.getScene(),
                binding.getAgentRole(),
                binding.getPrimaryModelProfileId(),
                binding.getFallbackModelProfileId(),
                binding.getRouteLabel(),
                binding.isEnabled(),
                binding.getRemarks(),
                binding.getUpdatedBy(),
                binding.getId()
        );
    }

    private AgentModelBinding mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentModelBinding(
                resultSet.getLong("id"),
                resultSet.getString("scene"),
                resultSet.getString("agent_role"),
                resultSet.getLong("primary_model_profile_id"),
                resultSet.getObject("fallback_model_profile_id", Long.class),
                resultSet.getString("route_label"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("remarks"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
