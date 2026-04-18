package com.xbk.lattice.query.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * JDBC 检索能力探测服务
 *
 * 职责：基于 PostgreSQL 元数据探测 FTS 与 pgvector 增强能力
 *
 * @author xiexu
 */
@Slf4j
@Service
@Profile("jdbc")
public class JdbcSearchCapabilityService implements SearchCapabilityService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 JDBC 检索能力探测服务。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public JdbcSearchCapabilityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 返回文本搜索配置是否可用。
     *
     * @param configName 配置名
     * @return 是否可用
     */
    @Override
    public boolean supportsTextSearchConfig(String configName) {
        if (jdbcTemplate == null) {
            return false;
        }

        String normalizedConfigName = normalizeConfigName(configName);
        if (normalizedConfigName.isBlank()) {
            return false;
        }

        try {
            int separatorIndex = normalizedConfigName.lastIndexOf('.');
            if (separatorIndex < 0) {
                Boolean available = jdbcTemplate.queryForObject(
                        "select exists(select 1 from pg_catalog.pg_ts_config where cfgname = ?)",
                        Boolean.class,
                        normalizedConfigName
                );
                return Boolean.TRUE.equals(available);
            }

            String schemaName = normalizedConfigName.substring(0, separatorIndex);
            String simpleName = normalizedConfigName.substring(separatorIndex + 1);
            Boolean available = jdbcTemplate.queryForObject(
                    """
                            select exists(
                                select 1
                                from pg_catalog.pg_ts_config c
                                join pg_catalog.pg_namespace n on n.oid = c.cfgnamespace
                                where c.cfgname = ?
                                  and n.nspname = ?
                            )
                            """,
                    Boolean.class,
                    simpleName,
                    schemaName
            );
            return Boolean.TRUE.equals(available);
        }
        catch (RuntimeException ex) {
            log.warn("Failed to inspect text search config: {}", normalizedConfigName, ex);
            return false;
        }
    }

    /**
     * 返回 vector 类型是否可用。
     *
     * @return 是否可用
     */
    @Override
    public boolean supportsVectorType() {
        if (jdbcTemplate == null) {
            return false;
        }

        try {
            Boolean available = jdbcTemplate.queryForObject(
                    "select coalesce(to_regtype('vector'), to_regtype('public.vector')) is not null",
                    Boolean.class
            );
            return Boolean.TRUE.equals(available);
        }
        catch (RuntimeException ex) {
            log.warn("Failed to inspect vector type availability", ex);
            return false;
        }
    }

    /**
     * 返回文章向量索引表是否可用。
     *
     * @return 是否可用
     */
    @Override
    public boolean hasArticleVectorIndex() {
        if (jdbcTemplate == null) {
            return false;
        }

        try {
            Boolean available = jdbcTemplate.queryForObject(
                    "select to_regclass(current_schema() || '.article_vector_index') is not null",
                    Boolean.class
            );
            return Boolean.TRUE.equals(available);
        }
        catch (RuntimeException ex) {
            log.warn("Failed to inspect article_vector_index availability", ex);
            return false;
        }
    }

    /**
     * 返回文章分块向量索引表是否可用。
     *
     * @return 是否可用
     */
    @Override
    public boolean hasArticleChunkVectorIndex() {
        if (jdbcTemplate == null) {
            return false;
        }

        try {
            Boolean available = jdbcTemplate.queryForObject(
                    "select to_regclass(current_schema() || '.article_chunk_vector_index') is not null",
                    Boolean.class
            );
            return Boolean.TRUE.equals(available);
        }
        catch (RuntimeException ex) {
            log.warn("Failed to inspect article_chunk_vector_index availability", ex);
            return false;
        }
    }

    /**
     * 规范化配置名。
     *
     * @param configName 配置名
     * @return 规范化后的配置名
     */
    private String normalizeConfigName(String configName) {
        if (configName == null) {
            return "";
        }
        return configName.trim();
    }
}
