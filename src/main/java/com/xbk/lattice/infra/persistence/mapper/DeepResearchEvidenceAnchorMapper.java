package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.DeepResearchEvidenceAnchorRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Deep Research 证据锚点 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 deep_research_evidence_anchors 表
 *
 * @author xiexu
 */
@Mapper
public interface DeepResearchEvidenceAnchorMapper {

    /**
     * 写入证据锚点记录。
     *
     * @param record 证据锚点记录
     * @return 影响行数
     */
    int insert(@Param("record") DeepResearchEvidenceAnchorRecord record);
}
