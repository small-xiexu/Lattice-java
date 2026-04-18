package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Query 向量配置 JDBC 仓储
 *
 * 职责：提供 query_vector_settings 表的读取与保存能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QueryVectorConfigJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Query 向量配置 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryVectorConfigJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询默认作用域的向量配置。
     *
     * @return 向量配置
     */
    public Optional<QueryVectorConfigRecord> findDefault() {
        return findByScope("default");
    }

    /**
     * 保存向量配置。
     *
     * @param record 向量配置记录
     * @return 保存后的记录
     */
    public QueryVectorConfigRecord save(QueryVectorConfigRecord record) {
        Optional<QueryVectorConfigRecord> existing = findByScope(record.getConfigScope());
        if (existing.isPresent()) {
            update(record);
        }
        else {
            insert(record);
        }
        return findByScope(record.getConfigScope()).orElseThrow();
    }

    /**
     * 查询指定作用域的向量配置。
     *
     * @param configScope 配置作用域
     * @return 向量配置
     */
    private Optional<QueryVectorConfigRecord> findByScope(String configScope) {
        List<QueryVectorConfigRecord> records = jdbcTemplate.query(
                """
                        select config_scope, vector_enabled, embedding_model_profile_id,
                               created_by, updated_by, created_at, updated_at
                        from query_vector_settings
                        where config_scope = ?
                        """,
                this::mapRow,
                configScope
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 插入向量配置。
     *
     * @param record 向量配置记录
     */
    private void insert(QueryVectorConfigRecord record) {
        jdbcTemplate.update(
                """
                        insert into query_vector_settings (
                            config_scope, vector_enabled, embedding_model_profile_id,
                            created_by, updated_by
                        ) values (?, ?, ?, ?, ?)
                        """,
                record.getConfigScope(),
                record.isVectorEnabled(),
                record.getEmbeddingModelProfileId(),
                record.getCreatedBy(),
                record.getUpdatedBy()
        );
    }

    /**
     * 更新向量配置。
     *
     * @param record 向量配置记录
     */
    private void update(QueryVectorConfigRecord record) {
        jdbcTemplate.update(
                """
                        update query_vector_settings
                        set vector_enabled = ?,
                            embedding_model_profile_id = ?,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where config_scope = ?
                        """,
                record.isVectorEnabled(),
                record.getEmbeddingModelProfileId(),
                record.getUpdatedBy(),
                record.getConfigScope()
        );
    }

    /**
     * 映射向量配置记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 向量配置记录
     * @throws SQLException SQL 异常
     */
    private QueryVectorConfigRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new QueryVectorConfigRecord(
                resultSet.getString("config_scope"),
                resultSet.getBoolean("vector_enabled"),
                resultSet.getObject("embedding_model_profile_id", Long.class),
                resultSet.getString("created_by"),
                resultSet.getString("updated_by"),
                readOffsetDateTime(resultSet, "created_at"),
                readOffsetDateTime(resultSet, "updated_at")
        );
    }

    /**
     * 读取时间列。
     *
     * @param resultSet 结果集
     * @param column 列名
     * @return 时间值
     * @throws SQLException SQL 异常
     */
    private OffsetDateTime readOffsetDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class);
    }
}
