package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.DeepResearchTaskRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Deep Research 任务 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 deep_research_tasks 表
 *
 * @author xiexu
 */
@Mapper
public interface DeepResearchTaskMapper {

    /**
     * 写入任务记录。
     *
     * @param record 任务记录
     * @return 影响行数
     */
    int insert(@Param("record") DeepResearchTaskRecord record);
}
