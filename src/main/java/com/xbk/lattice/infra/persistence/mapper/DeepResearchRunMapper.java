package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.DeepResearchRunRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Deep Research 运行 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 deep_research_runs 表
 *
 * @author xiexu
 */
@Mapper
public interface DeepResearchRunMapper {

    /**
     * 写入运行主记录。
     *
     * @param record 运行记录
     * @return 运行主键
     */
    Long insert(@Param("record") DeepResearchRunRecord record);

    /**
     * 绑定最终答案审计主键。
     *
     * @param runId run 主键
     * @param finalAnswerAuditId 最终答案审计主键
     * @return 影响行数
     */
    int bindFinalAnswerAudit(
            @Param("runId") Long runId,
            @Param("finalAnswerAuditId") Long finalAnswerAuditId
    );
}
