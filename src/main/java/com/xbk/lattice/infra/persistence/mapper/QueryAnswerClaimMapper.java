package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.QueryAnswerClaimRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询答案 claim MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_answer_claims 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryAnswerClaimMapper {

    /**
     * 写入 claim 记录。
     *
     * @param record claim 记录
     * @return claim 主键
     */
    Long insert(@Param("record") QueryAnswerClaimRecord record);
}
