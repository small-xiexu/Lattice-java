package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.DeepResearchEvidenceAnchorMapper;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 证据锚点 JDBC 仓储
 *
 * 职责：负责 deep_research_evidence_anchors 表的写入
 *
 * @author xiexu
 */
@Repository
public class DeepResearchEvidenceAnchorJdbcRepository {

    private final DeepResearchEvidenceAnchorMapper deepResearchEvidenceAnchorMapper;

    /**
     * 创建 Deep Research 证据锚点 JDBC 仓储。
     *
     * @param deepResearchEvidenceAnchorMapper Deep Research 证据锚点 Mapper
     */
    public DeepResearchEvidenceAnchorJdbcRepository(DeepResearchEvidenceAnchorMapper deepResearchEvidenceAnchorMapper) {
        this.deepResearchEvidenceAnchorMapper = deepResearchEvidenceAnchorMapper;
    }

    /**
     * 写入证据锚点记录。
     *
     * @param record 证据锚点记录
     */
    public void insert(DeepResearchEvidenceAnchorRecord record) {
        deepResearchEvidenceAnchorMapper.insert(record);
    }
}
