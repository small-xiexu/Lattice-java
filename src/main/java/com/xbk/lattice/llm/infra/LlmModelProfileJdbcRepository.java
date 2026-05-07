package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.infra.mapper.LlmModelProfileMapper;
import org.springframework.stereotype.Repository;

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
public class LlmModelProfileJdbcRepository {

    private final LlmModelProfileMapper llmModelProfileMapper;

    /**
     * 创建模型配置 JDBC 仓储。
     *
     * @param llmModelProfileMapper 模型配置 Mapper
     */
    public LlmModelProfileJdbcRepository(LlmModelProfileMapper llmModelProfileMapper) {
        this.llmModelProfileMapper = llmModelProfileMapper;
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
        return llmModelProfileMapper.findAll();
    }

    /**
     * 按主键查询模型配置。
     *
     * @param id 主键
     * @return 模型配置
     */
    public Optional<LlmModelProfile> findById(Long id) {
        return Optional.ofNullable(llmModelProfileMapper.findById(id));
    }

    /**
     * 按主键查询启用中的模型配置。
     *
     * @param id 主键
     * @return 启用中的模型配置
     */
    public Optional<LlmModelProfile> findEnabledById(Long id) {
        return Optional.ofNullable(llmModelProfileMapper.findEnabledById(id));
    }

    /**
     * 删除模型配置。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        llmModelProfileMapper.deleteById(id);
    }

    private LlmModelProfile insert(LlmModelProfile modelProfile) {
        Long id = llmModelProfileMapper.insert(modelProfile);
        if (id == null) {
            throw new IllegalStateException("Failed to insert llm_model_profiles");
        }
        return findById(id).orElseThrow();
    }

    private void update(LlmModelProfile modelProfile) {
        llmModelProfileMapper.update(modelProfile);
    }
}
