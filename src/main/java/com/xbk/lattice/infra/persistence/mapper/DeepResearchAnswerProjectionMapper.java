package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.DeepResearchAnswerProjectionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Deep Research 答案投影 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 deep_research_answer_projections 表
 *
 * @author xiexu
 */
@Mapper
public interface DeepResearchAnswerProjectionMapper {

    /**
     * 写入答案投影记录。
     *
     * @param record 答案投影记录
     * @return 影响行数
     */
    int insert(@Param("record") DeepResearchAnswerProjectionRecord record);
}
