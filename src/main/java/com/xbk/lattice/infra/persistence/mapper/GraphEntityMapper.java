package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.compiler.ast.domain.AstEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图谱实体 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 graph_entities 表
 *
 * @author xiexu
 */
@Mapper
public interface GraphEntityMapper {

    /**
     * 删除指定源文件下的全部实体。
     *
     * @param sourceFileId 源文件主键
     * @return 影响行数
     */
    int deleteBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 保存或更新图谱实体。
     *
     * @param entity 图谱实体
     * @return 影响行数
     */
    int upsert(@Param("entity") AstEntity entity);

    /**
     * 按提及词查询实体。
     *
     * @param mentions 提及词
     * @param limit 返回数量
     * @return 实体列表
     */
    List<AstEntity> searchByMentions(@Param("mentions") List<String> mentions, @Param("limit") int limit);
}
