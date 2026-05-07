package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.DeepResearchTaskHitRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Deep Research 任务命中 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 deep_research_task_hits 表
 *
 * @author xiexu
 */
@Mapper
public interface DeepResearchTaskHitMapper {

    /**
     * 写入任务命中记录。
     *
     * @param record 命中记录
     * @return 影响行数
     */
    int insert(@Param("record") DeepResearchTaskHitRecord record);
}
