package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.QueryAnswerAuditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询答案审计 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_answer_audits 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryAnswerAuditMapper {

    /**
     * 写入审计主记录。
     *
     * @param record 审计记录
     * @return 审计主键
     */
    Long insert(@Param("record") QueryAnswerAuditRecord record);
}
