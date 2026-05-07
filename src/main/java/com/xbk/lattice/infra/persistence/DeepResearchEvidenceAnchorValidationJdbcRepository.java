package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.DeepResearchEvidenceAnchorValidationMapper;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 证据锚点校验 JDBC 仓储
 *
 * 职责：负责 deep_research_evidence_anchor_validations 表的写入
 *
 * @author xiexu
 */
@Repository
public class DeepResearchEvidenceAnchorValidationJdbcRepository {

    private final DeepResearchEvidenceAnchorValidationMapper deepResearchEvidenceAnchorValidationMapper;

    /**
     * 创建 Deep Research 证据锚点校验 JDBC 仓储。
     *
     * @param deepResearchEvidenceAnchorValidationMapper Deep Research 证据锚点校验 Mapper
     */
    public DeepResearchEvidenceAnchorValidationJdbcRepository(
            DeepResearchEvidenceAnchorValidationMapper deepResearchEvidenceAnchorValidationMapper
    ) {
        this.deepResearchEvidenceAnchorValidationMapper = deepResearchEvidenceAnchorValidationMapper;
    }

    /**
     * 写入证据锚点校验记录。
     *
     * @param record 证据锚点校验记录
     */
    public void insert(DeepResearchEvidenceAnchorValidationRecord record) {
        deepResearchEvidenceAnchorValidationMapper.insert(record);
    }
}
