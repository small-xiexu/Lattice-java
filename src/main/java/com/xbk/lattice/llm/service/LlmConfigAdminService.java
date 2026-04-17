package com.xbk.lattice.llm.service;

import com.xbk.lattice.llm.domain.AgentModelBinding;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.infra.AgentModelBindingJdbcRepository;
import com.xbk.lattice.llm.infra.LlmModelProfileJdbcRepository;
import com.xbk.lattice.llm.infra.LlmProviderConnectionJdbcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * LLM 配置中心后台服务
 *
 * 职责：封装连接、模型、绑定的后台管理操作
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LlmConfigAdminService {

    private final LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository;

    private final LlmModelProfileJdbcRepository llmModelProfileJdbcRepository;

    private final AgentModelBindingJdbcRepository agentModelBindingJdbcRepository;

    /**
     * 创建 LLM 配置中心后台服务。
     *
     * @param llmProviderConnectionJdbcRepository 连接仓储
     * @param llmModelProfileJdbcRepository 模型仓储
     * @param agentModelBindingJdbcRepository 绑定仓储
     */
    public LlmConfigAdminService(
            LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository,
            LlmModelProfileJdbcRepository llmModelProfileJdbcRepository,
            AgentModelBindingJdbcRepository agentModelBindingJdbcRepository
    ) {
        this.llmProviderConnectionJdbcRepository = llmProviderConnectionJdbcRepository;
        this.llmModelProfileJdbcRepository = llmModelProfileJdbcRepository;
        this.agentModelBindingJdbcRepository = agentModelBindingJdbcRepository;
    }

    /**
     * 返回全部连接配置。
     *
     * @return 连接配置列表
     */
    public List<LlmProviderConnection> listConnections() {
        return llmProviderConnectionJdbcRepository.findAll();
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    @Transactional(rollbackFor = Exception.class)
    public LlmProviderConnection saveConnection(LlmProviderConnection connection) {
        return llmProviderConnectionJdbcRepository.save(connection);
    }

    /**
     * 删除连接配置。
     *
     * @param id 主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConnection(Long id) {
        llmProviderConnectionJdbcRepository.deleteById(id);
    }

    /**
     * 返回全部模型配置。
     *
     * @return 模型配置列表
     */
    public List<LlmModelProfile> listModelProfiles() {
        return llmModelProfileJdbcRepository.findAll();
    }

    /**
     * 保存模型配置。
     *
     * @param modelProfile 模型配置
     * @return 保存后的模型配置
     */
    @Transactional(rollbackFor = Exception.class)
    public LlmModelProfile saveModelProfile(LlmModelProfile modelProfile) {
        return llmModelProfileJdbcRepository.save(modelProfile);
    }

    /**
     * 删除模型配置。
     *
     * @param id 主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteModelProfile(Long id) {
        llmModelProfileJdbcRepository.deleteById(id);
    }

    /**
     * 返回全部 Agent 绑定。
     *
     * @return Agent 绑定列表
     */
    public List<AgentModelBinding> listBindings() {
        return agentModelBindingJdbcRepository.findAll();
    }

    /**
     * 保存 Agent 绑定。
     *
     * @param binding Agent 绑定
     * @return 保存后的 Agent 绑定
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentModelBinding saveBinding(AgentModelBinding binding) {
        return agentModelBindingJdbcRepository.save(binding);
    }

    /**
     * 删除 Agent 绑定。
     *
     * @param id 主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBinding(Long id) {
        agentModelBindingJdbcRepository.deleteById(id);
    }

    /**
     * 查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    public Optional<LlmProviderConnection> findConnection(Long id) {
        return llmProviderConnectionJdbcRepository.findById(id);
    }

    /**
     * 查询模型配置。
     *
     * @param id 主键
     * @return 模型配置
     */
    public Optional<LlmModelProfile> findModelProfile(Long id) {
        return llmModelProfileJdbcRepository.findById(id);
    }

    /**
     * 查询 Agent 绑定。
     *
     * @param id 主键
     * @return Agent 绑定
     */
    public Optional<AgentModelBinding> findBinding(Long id) {
        return agentModelBindingJdbcRepository.findById(id);
    }
}
