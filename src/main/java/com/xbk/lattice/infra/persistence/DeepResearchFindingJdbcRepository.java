package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.DeepResearchFindingMapper;
import org.springframework.stereotype.Repository;

/**
 * Deep Research finding JDBC 仓储
 *
 * 职责：负责 deep_research_findings 表的写入
 *
 * @author xiexu
 */
@Repository
public class DeepResearchFindingJdbcRepository {

    private final DeepResearchFindingMapper deepResearchFindingMapper;

    /**
     * 创建 Deep Research finding JDBC 仓储。
     *
     * @param deepResearchFindingMapper Deep Research finding Mapper
     */
    public DeepResearchFindingJdbcRepository(DeepResearchFindingMapper deepResearchFindingMapper) {
        this.deepResearchFindingMapper = deepResearchFindingMapper;
    }

    /**
     * 写入 finding 记录。
     *
     * @param record finding 记录
     */
    public void insert(DeepResearchFindingRecord record) {
        deepResearchFindingMapper.insert(record);
    }
}
