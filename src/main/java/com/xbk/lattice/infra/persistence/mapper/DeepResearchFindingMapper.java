package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.DeepResearchFindingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Deep Research finding MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 deep_research_findings 表
 *
 * @author xiexu
 */
@Mapper
public interface DeepResearchFindingMapper {

    /**
     * 写入 finding 记录。
     *
     * @param record finding 记录
     * @return 影响行数
     */
    int insert(@Param("record") DeepResearchFindingRecord record);
}
