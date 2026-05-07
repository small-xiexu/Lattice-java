package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.DeepResearchAnswerProjectionMapper;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 答案投影 JDBC 仓储
 *
 * 职责：负责 deep_research_answer_projections 表的写入
 *
 * @author xiexu
 */
@Repository
public class DeepResearchAnswerProjectionJdbcRepository {

    private final DeepResearchAnswerProjectionMapper deepResearchAnswerProjectionMapper;

    /**
     * 创建 Deep Research 答案投影 JDBC 仓储。
     *
     * @param deepResearchAnswerProjectionMapper Deep Research 答案投影 Mapper
     */
    public DeepResearchAnswerProjectionJdbcRepository(
            DeepResearchAnswerProjectionMapper deepResearchAnswerProjectionMapper
    ) {
        this.deepResearchAnswerProjectionMapper = deepResearchAnswerProjectionMapper;
    }

    /**
     * 写入答案投影记录。
     *
     * @param record 答案投影记录
     */
    public void insert(DeepResearchAnswerProjectionRecord record) {
        deepResearchAnswerProjectionMapper.insert(record);
    }
}
