package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.compiler.ast.domain.AstFact;
import com.xbk.lattice.infra.persistence.mapper.GraphFactMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图谱事实 JDBC 仓储
 *
 * 职责：负责 graph_facts 表的清理、写入与查询
 *
 * @author xiexu
 */
@Repository
public class GraphFactJdbcRepository {

    private final GraphFactMapper graphFactMapper;

    /**
     * 创建图谱事实 JDBC 仓储。
     *
     * @param graphFactMapper 图谱事实 Mapper
     */
    public GraphFactJdbcRepository(GraphFactMapper graphFactMapper) {
        this.graphFactMapper = graphFactMapper;
    }

    /**
     * 删除指定源引用下的全部事实。
     *
     * @param sourceRef 源引用
     */
    public void deleteBySourceRef(String sourceRef) {
        graphFactMapper.deleteBySourceRef(sourceRef);
    }

    /**
     * 写入图谱事实。
     *
     * @param fact 图谱事实
     */
    public void insert(AstFact fact) {
        graphFactMapper.insert(fact);
    }

    /**
     * 按实体查询高置信事实。
     *
     * @param entityIds 实体 ID 列表
     * @param limit 返回数量
     * @return 事实列表
     */
    public List<AstFact> findActiveFactsByEntityIds(List<String> entityIds, int limit) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        return graphFactMapper.findActiveFactsByEntityIds(entityIds, limit);
    }
}
