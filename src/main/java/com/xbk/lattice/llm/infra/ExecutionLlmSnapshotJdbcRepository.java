package com.xbk.lattice.llm.infra;

import com.xbk.lattice.llm.domain.ExecutionLlmSnapshot;
import com.xbk.lattice.llm.infra.mapper.ExecutionLlmSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 运行时快照 JDBC 仓储
 *
 * 职责：提供 execution_llm_snapshots 表的写入与查询能力
 *
 * @author xiexu
 */
@Repository
public class ExecutionLlmSnapshotJdbcRepository {

    private final ExecutionLlmSnapshotMapper executionLlmSnapshotMapper;

    /**
     * 创建运行时快照 JDBC 仓储。
     *
     * @param executionLlmSnapshotMapper 运行时快照 Mapper
     */
    public ExecutionLlmSnapshotJdbcRepository(ExecutionLlmSnapshotMapper executionLlmSnapshotMapper) {
        this.executionLlmSnapshotMapper = executionLlmSnapshotMapper;
    }

    /**
     * 批量保存快照。
     *
     * @param snapshots 快照列表
     */
    public void saveAll(List<ExecutionLlmSnapshot> snapshots) {
        for (ExecutionLlmSnapshot snapshot : snapshots) {
            save(snapshot);
        }
    }

    /**
     * 按作用域查询快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 快照列表
     */
    public List<ExecutionLlmSnapshot> findByScope(String scopeType, String scopeId, String scene) {
        return executionLlmSnapshotMapper.findByScope(scopeType, scopeId, scene);
    }

    /**
     * 按作用域和角色查询单条快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 快照
     */
    public Optional<ExecutionLlmSnapshot> findByScopeAndRole(
            String scopeType,
            String scopeId,
            String scene,
            String agentRole
    ) {
        return Optional.ofNullable(
                executionLlmSnapshotMapper.findByScopeAndRole(scopeType, scopeId, scene, agentRole)
        );
    }

    /**
     * 删除某个作用域下的全部快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     */
    public void deleteByScope(String scopeType, String scopeId, String scene) {
        executionLlmSnapshotMapper.deleteByScope(scopeType, scopeId, scene);
    }

    private void save(ExecutionLlmSnapshot snapshot) {
        executionLlmSnapshotMapper.save(snapshot);
    }
}
