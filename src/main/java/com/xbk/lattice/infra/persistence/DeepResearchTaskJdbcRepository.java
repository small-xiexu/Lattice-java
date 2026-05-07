package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.DeepResearchTaskMapper;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 任务 JDBC 仓储
 *
 * 职责：负责 deep_research_tasks 表的写入
 *
 * @author xiexu
 */
@Repository
public class DeepResearchTaskJdbcRepository {

    private final DeepResearchTaskMapper deepResearchTaskMapper;

    /**
     * 创建 Deep Research 任务 JDBC 仓储。
     *
     * @param deepResearchTaskMapper Deep Research 任务 Mapper
     */
    public DeepResearchTaskJdbcRepository(DeepResearchTaskMapper deepResearchTaskMapper) {
        this.deepResearchTaskMapper = deepResearchTaskMapper;
    }

    /**
     * 写入任务记录。
     *
     * @param record 任务记录
     */
    public void insert(DeepResearchTaskRecord record) {
        deepResearchTaskMapper.insert(record);
    }
}
