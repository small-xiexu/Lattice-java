package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.DeepResearchRunMapper;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 运行 JDBC 仓储
 *
 * 职责：负责 deep_research_runs 表的写入
 *
 * @author xiexu
 */
@Repository
public class DeepResearchRunJdbcRepository {

    private final DeepResearchRunMapper deepResearchRunMapper;

    /**
     * 创建 Deep Research 运行 JDBC 仓储。
     *
     * @param deepResearchRunMapper Deep Research 运行 Mapper
     */
    public DeepResearchRunJdbcRepository(DeepResearchRunMapper deepResearchRunMapper) {
        this.deepResearchRunMapper = deepResearchRunMapper;
    }

    /**
     * 写入运行主记录。
     *
     * @param record 运行记录
     * @return 运行主键
     */
    public Long insert(DeepResearchRunRecord record) {
        return deepResearchRunMapper.insert(record);
    }

    /**
     * 绑定最终答案审计主键。
     *
     * @param runId run 主键
     * @param finalAnswerAuditId 最终答案审计主键
     */
    public void bindFinalAnswerAudit(Long runId, Long finalAnswerAuditId) {
        deepResearchRunMapper.bindFinalAnswerAudit(runId, finalAnswerAuditId);
    }
}
