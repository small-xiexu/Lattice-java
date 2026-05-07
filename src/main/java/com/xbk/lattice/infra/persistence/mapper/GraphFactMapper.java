package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.compiler.ast.domain.AstFact;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图谱事实 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 graph_facts 表
 *
 * @author xiexu
 */
@Mapper
public interface GraphFactMapper {

    /**
     * 删除指定源引用下的全部事实。
     *
     * @param sourceRef 源引用
     * @return 影响行数
     */
    int deleteBySourceRef(@Param("sourceRef") String sourceRef);

    /**
     * 写入图谱事实。
     *
     * @param fact 图谱事实
     * @return 影响行数
     */
    int insert(@Param("fact") AstFact fact);

    /**
     * 按实体查询高置信事实。
     *
     * @param entityIds 实体 ID 列表
     * @param limit 返回数量
     * @return 事实列表
     */
    List<AstFact> findActiveFactsByEntityIds(@Param("entityIds") List<String> entityIds, @Param("limit") int limit);
}
