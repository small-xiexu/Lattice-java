package com.xbk.lattice.llm.infra.mapper;

import com.xbk.lattice.llm.domain.AgentModelBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 绑定 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 agent_model_bindings 表
 *
 * @author xiexu
 */
@Mapper
public interface AgentModelBindingMapper {

    /**
     * 插入 Agent 绑定。
     *
     * @param binding Agent 绑定
     * @return 主键
     */
    Long insert(@Param("binding") AgentModelBinding binding);

    /**
     * 更新 Agent 绑定。
     *
     * @param binding Agent 绑定
     * @return 影响行数
     */
    int update(@Param("binding") AgentModelBinding binding);

    /**
     * 查询全部 Agent 绑定。
     *
     * @return Agent 绑定列表
     */
    List<AgentModelBinding> findAll();

    /**
     * 查询某个场景下启用中的 Agent 绑定。
     *
     * @param scene 场景
     * @return Agent 绑定列表
     */
    List<AgentModelBinding> findEnabledByScene(@Param("scene") String scene);

    /**
     * 按主键查询 Agent 绑定。
     *
     * @param id 主键
     * @return Agent 绑定
     */
    AgentModelBinding findById(@Param("id") Long id);

    /**
     * 删除 Agent 绑定。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
