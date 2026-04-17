package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.ExecutionLlmSnapshot;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 运行时快照 JDBC 仓储
 *
 * 职责：提供 execution_llm_snapshots 表的写入与查询能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ExecutionLlmSnapshotJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建运行时快照 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ExecutionLlmSnapshotJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量保存快照。
     *
     * @param snapshots 快照列表
     */
    public void saveAll(List<ExecutionLlmSnapshot> snapshots) {
        for (ExecutionLlmSnapshot snapshot : snapshots) {
            save(snapshot);
        }
    }

    /**
     * 按作用域查询快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 快照列表
     */
    public List<ExecutionLlmSnapshot> findByScope(String scopeType, String scopeId, String scene) {
        return jdbcTemplate.query(
                """
                        select id, scope_type, scope_id, scene, agent_role, binding_id, model_profile_id,
                               connection_id, route_label, provider_type, base_url, model_name, temperature,
                               max_tokens, timeout_seconds, extra_options_json, input_price_per_1k_tokens,
                               output_price_per_1k_tokens, snapshot_version, created_at
                        from execution_llm_snapshots
                        where scope_type = ?
                          and scope_id = ?
                          and scene = ?
                        order by agent_role asc
                        """,
                this::mapRecord,
                scopeType,
                scopeId,
                scene
        );
    }

    /**
     * 按作用域和角色查询单条快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 快照
     */
    public Optional<ExecutionLlmSnapshot> findByScopeAndRole(
            String scopeType,
            String scopeId,
            String scene,
            String agentRole
    ) {
        List<ExecutionLlmSnapshot> items = jdbcTemplate.query(
                """
                        select id, scope_type, scope_id, scene, agent_role, binding_id, model_profile_id,
                               connection_id, route_label, provider_type, base_url, model_name, temperature,
                               max_tokens, timeout_seconds, extra_options_json, input_price_per_1k_tokens,
                               output_price_per_1k_tokens, snapshot_version, created_at
                        from execution_llm_snapshots
                        where scope_type = ?
                          and scope_id = ?
                          and scene = ?
                          and agent_role = ?
                        """,
                this::mapRecord,
                scopeType,
                scopeId,
                scene,
                agentRole
        );
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(items.get(0));
    }

    /**
     * 删除某个作用域下的全部快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     */
    public void deleteByScope(String scopeType, String scopeId, String scene) {
        jdbcTemplate.update(
                "delete from execution_llm_snapshots where scope_type = ? and scope_id = ? and scene = ?",
                scopeType,
                scopeId,
                scene
        );
    }

    private void save(ExecutionLlmSnapshot snapshot) {
        jdbcTemplate.update(dbConnection -> {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    """
                            insert into execution_llm_snapshots (
                                scope_type, scope_id, scene, agent_role, binding_id, model_profile_id,
                                connection_id, route_label, provider_type, base_url, model_name, temperature,
                                max_tokens, timeout_seconds, extra_options_json, input_price_per_1k_tokens,
                                output_price_per_1k_tokens, snapshot_version
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            on conflict (scope_type, scope_id, scene, agent_role) do update
                            set binding_id = excluded.binding_id,
                                model_profile_id = excluded.model_profile_id,
                                connection_id = excluded.connection_id,
                                route_label = excluded.route_label,
                                provider_type = excluded.provider_type,
                                base_url = excluded.base_url,
                                model_name = excluded.model_name,
                                temperature = excluded.temperature,
                                max_tokens = excluded.max_tokens,
                                timeout_seconds = excluded.timeout_seconds,
                                extra_options_json = excluded.extra_options_json,
                                input_price_per_1k_tokens = excluded.input_price_per_1k_tokens,
                                output_price_per_1k_tokens = excluded.output_price_per_1k_tokens,
                                snapshot_version = excluded.snapshot_version
                            """
            );
            PGobject extraOptions = new PGobject();
            extraOptions.setType("jsonb");
            extraOptions.setValue(snapshot.getExtraOptionsJson());
            List<Object> values = new ArrayList<Object>();
            values.add(snapshot.getScopeType());
            values.add(snapshot.getScopeId());
            values.add(snapshot.getScene());
            values.add(snapshot.getAgentRole());
            values.add(snapshot.getBindingId());
            values.add(snapshot.getModelProfileId());
            values.add(snapshot.getConnectionId());
            values.add(snapshot.getRouteLabel());
            values.add(snapshot.getProviderType());
            values.add(snapshot.getBaseUrl());
            values.add(snapshot.getModelName());
            values.add(snapshot.getTemperature());
            values.add(snapshot.getMaxTokens());
            values.add(snapshot.getTimeoutSeconds());
            values.add(extraOptions);
            values.add(snapshot.getInputPricePer1kTokens());
            values.add(snapshot.getOutputPricePer1kTokens());
            values.add(snapshot.getSnapshotVersion());
            for (int index = 0; index < values.size(); index++) {
                preparedStatement.setObject(index + 1, values.get(index));
            }
            return preparedStatement;
        });
    }

    private ExecutionLlmSnapshot mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExecutionLlmSnapshot(
                resultSet.getLong("id"),
                resultSet.getString("scope_type"),
                resultSet.getString("scope_id"),
                resultSet.getString("scene"),
                resultSet.getString("agent_role"),
                resultSet.getLong("binding_id"),
                resultSet.getLong("model_profile_id"),
                resultSet.getLong("connection_id"),
                resultSet.getString("route_label"),
                resultSet.getString("provider_type"),
                resultSet.getString("base_url"),
                resultSet.getString("model_name"),
                resultSet.getBigDecimal("temperature"),
                resultSet.getObject("max_tokens", Integer.class),
                resultSet.getObject("timeout_seconds", Integer.class),
                resultSet.getString("extra_options_json"),
                resultSet.getBigDecimal("input_price_per_1k_tokens"),
                resultSet.getBigDecimal("output_price_per_1k_tokens"),
                resultSet.getObject("snapshot_version", Integer.class),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
