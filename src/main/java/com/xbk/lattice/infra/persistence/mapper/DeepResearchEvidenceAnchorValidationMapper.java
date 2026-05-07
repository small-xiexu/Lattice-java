package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.DeepResearchEvidenceAnchorValidationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Deep Research 证据锚点校验 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 deep_research_evidence_anchor_validations 表
 *
 * @author xiexu
 */
@Mapper
public interface DeepResearchEvidenceAnchorValidationMapper {

    /**
     * 写入证据锚点校验记录。
     *
     * @param record 证据锚点校验记录
     * @return 影响行数
     */
    int insert(@Param("record") DeepResearchEvidenceAnchorValidationRecord record);
}
