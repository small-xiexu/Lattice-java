package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.AgentModelBinding;
import com.xbk.lattice.llm.infra.mapper.AgentModelBindingMapper;
import org.springframework.stereotype.Repository;

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
public class AgentModelBindingJdbcRepository {

    private final AgentModelBindingMapper agentModelBindingMapper;

    /**
     * 创建 Agent 绑定 JDBC 仓储。
     *
     * @param agentModelBindingMapper Agent 绑定 Mapper
     */
    public AgentModelBindingJdbcRepository(AgentModelBindingMapper agentModelBindingMapper) {
        this.agentModelBindingMapper = agentModelBindingMapper;
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
        return agentModelBindingMapper.findAll();
    }

    /**
     * 查询某个场景下启用中的 Agent 绑定。
     *
     * @param scene 场景
     * @return Agent 绑定列表
     */
    public List<AgentModelBinding> findEnabledByScene(String scene) {
        return agentModelBindingMapper.findEnabledByScene(scene);
    }

    /**
     * 按主键查询 Agent 绑定。
     *
     * @param id 主键
     * @return Agent 绑定
     */
    public Optional<AgentModelBinding> findById(Long id) {
        return Optional.ofNullable(agentModelBindingMapper.findById(id));
    }

    /**
     * 删除 Agent 绑定。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        agentModelBindingMapper.deleteById(id);
    }

    private AgentModelBinding insert(AgentModelBinding binding) {
        Long id = agentModelBindingMapper.insert(binding);
        if (id == null) {
            throw new IllegalStateException("Failed to insert agent_model_bindings");
        }
        return findById(id).orElseThrow();
    }

    private void update(AgentModelBinding binding) {
        agentModelBindingMapper.update(binding);
    }
}
