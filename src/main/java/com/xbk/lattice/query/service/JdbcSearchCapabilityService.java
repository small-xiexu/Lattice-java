package com.xbk.lattice.query.service;

import com.xbk.lattice.query.service.mapper.SearchCapabilityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class JdbcSearchCapabilityService implements SearchCapabilityService {

    private final SearchCapabilityMapper searchCapabilityMapper;

    /**
     * 创建检索能力探测服务。
     *
     * @param searchCapabilityMapper 检索能力 Mapper
     */
    @Autowired
    public JdbcSearchCapabilityService(SearchCapabilityMapper searchCapabilityMapper) {
        this.searchCapabilityMapper = searchCapabilityMapper;
    }

    /**
     * 返回文本搜索配置是否可用。
     *
     * @param configName 配置名
     * @return 是否可用
     */
    @Override
    public boolean supportsTextSearchConfig(String configName) {
        if (searchCapabilityMapper == null) {
            return false;
        }

        String normalizedConfigName = normalizeConfigName(configName);
        if (normalizedConfigName.isBlank()) {
            return false;
        }

        try {
            int separatorIndex = normalizedConfigName.lastIndexOf('.');
            if (separatorIndex < 0) {
                return searchCapabilityMapper.textSearchConfigExists(normalizedConfigName);
            }

            String schemaName = normalizedConfigName.substring(0, separatorIndex);
            String simpleName = normalizedConfigName.substring(separatorIndex + 1);
            return searchCapabilityMapper.schemaTextSearchConfigExists(schemaName, simpleName);
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
        if (searchCapabilityMapper == null) {
            return false;
        }

        try {
            return searchCapabilityMapper.vectorTypeAvailable();
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
        if (searchCapabilityMapper == null) {
            return false;
        }

        try {
            return searchCapabilityMapper.tableExists("article_vector_index");
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
        if (searchCapabilityMapper == null) {
            return false;
        }

        try {
            return searchCapabilityMapper.tableExists("article_chunk_vector_index");
        }
        catch (RuntimeException ex) {
            log.warn("Failed to inspect article_chunk_vector_index availability", ex);
            return false;
        }
    }

    /**
     * 返回事实证据卡向量索引表是否可用。
     *
     * @return 是否可用
     */
    @Override
    public boolean hasFactCardVectorIndex() {
        if (searchCapabilityMapper == null) {
            return false;
        }

        try {
            return searchCapabilityMapper.tableExists("fact_card_vector_index");
        }
        catch (RuntimeException ex) {
            log.warn("Failed to inspect fact_card_vector_index availability", ex);
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
