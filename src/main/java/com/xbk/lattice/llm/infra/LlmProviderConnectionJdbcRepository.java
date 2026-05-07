package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.infra.mapper.LlmProviderConnectionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Provider 连接 JDBC 仓储
 *
 * 职责：提供 llm_provider_connections 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
public class LlmProviderConnectionJdbcRepository {

    private final LlmProviderConnectionMapper llmProviderConnectionMapper;

    /**
     * 创建 Provider 连接 JDBC 仓储。
     *
     * @param llmProviderConnectionMapper Provider 连接 Mapper
     */
    public LlmProviderConnectionJdbcRepository(LlmProviderConnectionMapper llmProviderConnectionMapper) {
        this.llmProviderConnectionMapper = llmProviderConnectionMapper;
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    public LlmProviderConnection save(LlmProviderConnection connection) {
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
    public List<LlmProviderConnection> findAll() {
        return llmProviderConnectionMapper.findAll();
    }

    /**
     * 按主键查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    public Optional<LlmProviderConnection> findById(Long id) {
        return Optional.ofNullable(llmProviderConnectionMapper.findById(id));
    }

    /**
     * 按主键查询启用中的连接配置。
     *
     * @param id 主键
     * @return 启用中的连接配置
     */
    public Optional<LlmProviderConnection> findEnabledById(Long id) {
        return Optional.ofNullable(llmProviderConnectionMapper.findEnabledById(id));
    }

    /**
     * 删除连接配置。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        llmProviderConnectionMapper.deleteById(id);
    }

    private LlmProviderConnection insert(LlmProviderConnection connection) {
        Long id = llmProviderConnectionMapper.insert(connection);
        if (id == null) {
            throw new IllegalStateException("Failed to insert llm_provider_connections");
        }
        return findById(id).orElseThrow();
    }

    private void update(LlmProviderConnection connection) {
        llmProviderConnectionMapper.update(connection);
    }
}
