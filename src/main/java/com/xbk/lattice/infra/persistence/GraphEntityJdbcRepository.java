package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.infra.persistence.mapper.GraphEntityMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图谱实体 JDBC 仓储
 *
 * 职责：负责 graph_entities 表的清理、写入与查询
 *
 * @author xiexu
 */
@Repository
public class GraphEntityJdbcRepository {

    private final GraphEntityMapper graphEntityMapper;

    /**
     * 创建图谱实体 JDBC 仓储。
     *
     * @param graphEntityMapper 图谱实体 Mapper
     */
    public GraphEntityJdbcRepository(GraphEntityMapper graphEntityMapper) {
        this.graphEntityMapper = graphEntityMapper;
    }

    /**
     * 删除指定源文件下的全部实体。
     *
     * @param sourceFileId 源文件主键
     */
    public void deleteBySourceFileId(Long sourceFileId) {
        graphEntityMapper.deleteBySourceFileId(sourceFileId);
    }

    /**
     * 保存或更新图谱实体。
     *
     * @param entity 图谱实体
     */
    public void upsert(AstEntity entity) {
        graphEntityMapper.upsert(entity);
    }

    /**
     * 按提及词查询实体。
     *
     * @param mentions 提及词
     * @param limit 返回数量
     * @return 实体列表
     */
    public List<AstEntity> searchByMentions(List<String> mentions, int limit) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        return graphEntityMapper.searchByMentions(mentions, limit);
    }
}
