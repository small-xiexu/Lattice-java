package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.DeepResearchTaskHitMapper;
import org.springframework.stereotype.Repository;

/**
 * Deep Research 任务命中 JDBC 仓储
 *
 * 职责：负责 deep_research_task_hits 表的写入
 *
 * @author xiexu
 */
@Repository
public class DeepResearchTaskHitJdbcRepository {

    private final DeepResearchTaskHitMapper deepResearchTaskHitMapper;

    /**
     * 创建 Deep Research 任务命中 JDBC 仓储。
     *
     * @param deepResearchTaskHitMapper Deep Research 任务命中 Mapper
     */
    public DeepResearchTaskHitJdbcRepository(DeepResearchTaskHitMapper deepResearchTaskHitMapper) {
        this.deepResearchTaskHitMapper = deepResearchTaskHitMapper;
    }

    /**
     * 写入任务命中记录。
     *
     * @param record 命中记录
     */
    public void insert(DeepResearchTaskHitRecord record) {
        deepResearchTaskHitMapper.insert(record);
    }
}
