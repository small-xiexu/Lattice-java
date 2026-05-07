package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.QueryAnswerCitationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询答案引用 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_answer_citations 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryAnswerCitationMapper {

    /**
     * 写入引用记录。
     *
     * @param record 引用记录
     * @return 影响行数
     */
    int insert(@Param("record") QueryAnswerCitationRecord record);
}
