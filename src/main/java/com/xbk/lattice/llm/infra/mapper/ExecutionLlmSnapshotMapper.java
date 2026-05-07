package com.xbk.lattice.llm.infra.mapper;

import com.xbk.lattice.llm.domain.ExecutionLlmSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 运行时快照 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 execution_llm_snapshots 表
 *
 * @author xiexu
 */
@Mapper
public interface ExecutionLlmSnapshotMapper {

    /**
     * 保存快照。
     *
     * @param snapshot 快照
     * @return 影响行数
     */
    int save(@Param("snapshot") ExecutionLlmSnapshot snapshot);

    /**
     * 按作用域查询快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 快照列表
     */
    List<ExecutionLlmSnapshot> findByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId,
            @Param("scene") String scene
    );

    /**
     * 按作用域和角色查询单条快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 快照
     */
    ExecutionLlmSnapshot findByScopeAndRole(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId,
            @Param("scene") String scene,
            @Param("agentRole") String agentRole
    );

    /**
     * 删除某个作用域下的全部快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 影响行数
     */
    int deleteByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId,
            @Param("scene") String scene
    );
}
