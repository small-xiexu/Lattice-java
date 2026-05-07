package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.compiler.ast.domain.AstRelation;
import com.xbk.lattice.infra.persistence.mapper.GraphRelationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图谱关系 JDBC 仓储
 *
 * 职责：负责 graph_relations 表的清理、写入与查询
 *
 * @author xiexu
 */
@Repository
public class GraphRelationJdbcRepository {

    private final GraphRelationMapper graphRelationMapper;

    /**
     * 创建图谱关系 JDBC 仓储。
     *
     * @param graphRelationMapper 图谱关系 Mapper
     */
    public GraphRelationJdbcRepository(GraphRelationMapper graphRelationMapper) {
        this.graphRelationMapper = graphRelationMapper;
    }

    /**
     * 删除指定源引用下的全部关系。
     *
     * @param sourceRef 源引用
     */
    public void deleteBySourceRef(String sourceRef) {
        graphRelationMapper.deleteBySourceRef(sourceRef);
    }

    /**
     * 写入图谱关系。
     *
     * @param relation 图谱关系
     */
    public void insert(AstRelation relation) {
        graphRelationMapper.insert(relation);
    }

    /**
     * 按实体查询高置信关系。
     *
     * @param entityIds 实体 ID 列表
     * @param limit 返回数量
     * @return 关系列表
     */
    public List<AstRelation> findActiveRelationsByEntityIds(List<String> entityIds, int limit) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        return graphRelationMapper.findActiveRelationsByEntityIds(entityIds, limit);
    }
}
