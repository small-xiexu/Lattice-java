package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.compiler.ast.domain.AstRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图谱关系 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 graph_relations 表
 *
 * @author xiexu
 */
@Mapper
public interface GraphRelationMapper {

    /**
     * 删除指定源引用下的全部关系。
     *
     * @param sourceRef 源引用
     * @return 影响行数
     */
    int deleteBySourceRef(@Param("sourceRef") String sourceRef);

    /**
     * 写入图谱关系。
     *
     * @param relation 图谱关系
     * @return 影响行数
     */
    int insert(@Param("relation") AstRelation relation);

    /**
     * 按实体查询高置信关系。
     *
     * @param entityIds 实体 ID 列表
     * @param limit 返回数量
     * @return 关系列表
     */
    List<AstRelation> findActiveRelationsByEntityIds(
            @Param("entityIds") List<String> entityIds,
            @Param("limit") int limit
    );
}
