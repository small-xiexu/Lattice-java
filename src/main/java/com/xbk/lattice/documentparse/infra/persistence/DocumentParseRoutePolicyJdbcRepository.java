package com.xbk.lattice.documentparse.infra.persistence;

import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档解析路由策略 JDBC 仓储
 *
 * 职责：提供 document_parse_route_policies 表的读写能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class DocumentParseRoutePolicyJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文档解析路由策略 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DocumentParseRoutePolicyJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询默认作用域策略。
     *
     * @return 默认作用域策略
     */
    public Optional<ParseRoutePolicy> findDefault() {
        return findByScope(ParseRoutePolicy.DEFAULT_SCOPE);
    }

    /**
     * 保存策略。
     *
     * @param policy 路由策略
     * @return 保存后的路由策略
     */
    public ParseRoutePolicy save(ParseRoutePolicy policy) {
        Optional<ParseRoutePolicy> existing = findByScope(policy.getPolicyScope());
        if (existing.isPresent()) {
            update(policy);
        }
        else {
            insert(policy);
        }
        return findByScope(policy.getPolicyScope()).orElseThrow();
    }

    /**
     * 查询指定作用域策略。
     *
     * @param policyScope 作用域
     * @return 路由策略
     */
    private Optional<ParseRoutePolicy> findByScope(String policyScope) {
        List<ParseRoutePolicy> records = jdbcTemplate.query(
                """
                        select id, policy_scope, image_connection_id, scanned_pdf_connection_id,
                               cleanup_enabled, cleanup_model_profile_id, fallback_policy_json,
                               created_by, updated_by, created_at, updated_at
                        from document_parse_route_policies
                        where policy_scope = ?
                        """,
                this::mapRow,
                policyScope
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 插入路由策略。
     *
     * @param policy 路由策略
     */
    private void insert(ParseRoutePolicy policy) {
        jdbcTemplate.update(
                """
                        insert into document_parse_route_policies (
                            policy_scope, image_connection_id, scanned_pdf_connection_id,
                            cleanup_enabled, cleanup_model_profile_id, fallback_policy_json,
                            created_by, updated_by
                        ) values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                        """,
                policy.getPolicyScope(),
                policy.getImageConnectionId(),
                policy.getScannedPdfConnectionId(),
                policy.isCleanupEnabled(),
                policy.getCleanupModelProfileId(),
                policy.getFallbackPolicyJson(),
                policy.getCreatedBy(),
                policy.getUpdatedBy()
        );
    }

    /**
     * 更新路由策略。
     *
     * @param policy 路由策略
     */
    private void update(ParseRoutePolicy policy) {
        jdbcTemplate.update(
                """
                        update document_parse_route_policies
                        set image_connection_id = ?,
                            scanned_pdf_connection_id = ?,
                            cleanup_enabled = ?,
                            cleanup_model_profile_id = ?,
                            fallback_policy_json = cast(? as jsonb),
                            updated_by = ?,
                            updated_at = current_timestamp
                        where policy_scope = ?
                        """,
                policy.getImageConnectionId(),
                policy.getScannedPdfConnectionId(),
                policy.isCleanupEnabled(),
                policy.getCleanupModelProfileId(),
                policy.getFallbackPolicyJson(),
                policy.getUpdatedBy(),
                policy.getPolicyScope()
        );
    }

    /**
     * 映射路由策略记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 路由策略记录
     * @throws SQLException SQL 异常
     */
    private ParseRoutePolicy mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ParseRoutePolicy(
                resultSet.getLong("id"),
                resultSet.getString("policy_scope"),
                resultSet.getObject("image_connection_id", Long.class),
                resultSet.getObject("scanned_pdf_connection_id", Long.class),
                resultSet.getBoolean("cleanup_enabled"),
                resultSet.getObject("cleanup_model_profile_id", Long.class),
                resultSet.getString("fallback_policy_json"),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
