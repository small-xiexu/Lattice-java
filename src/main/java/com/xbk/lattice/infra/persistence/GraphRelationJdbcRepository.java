package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.compiler.ast.domain.AstRelation;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图谱关系 JDBC 仓储
 *
 * 职责：负责 graph_relations 表的清理、写入与查询
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class GraphRelationJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建图谱关系 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public GraphRelationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 删除指定源引用下的全部关系。
     *
     * @param sourceRef 源引用
     */
    public void deleteBySourceRef(String sourceRef) {
        jdbcTemplate.update("delete from graph_relations where source_ref = ?", sourceRef);
    }

    /**
     * 写入图谱关系。
     *
     * @param relation 图谱关系
     */
    public void insert(AstRelation relation) {
        jdbcTemplate.update(
                """
                        insert into graph_relations (
                            src_id, edge_type, dst_id, source_ref, source_start_line, source_end_line,
                            confidence, extractor, asserted_at, superseded_by, tombstoned
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, null, false)
                        """,
                relation.getSrcId(),
                relation.getEdgeType(),
                relation.getDstId(),
                relation.getSourceRef(),
                Integer.valueOf(relation.getSourceStartLine()),
                Integer.valueOf(relation.getSourceEndLine()),
                Double.valueOf(relation.getConfidence()),
                relation.getExtractor()
        );
    }

    /**
     * 按实体查询高置信关系。
     *
     * @param entityIds 实体 ID 列表
     * @param limit 返回数量
     * @return 关系列表
     */
    public List<AstRelation> findActiveRelationsByEntityIds(List<String> entityIds, int limit) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                select src_id, edge_type, dst_id, source_ref, source_start_line, source_end_line,
                       confidence, extractor
                from graph_relations
                where tombstoned = false
                  and superseded_by is null
                  and src_id in (
                """);
        List<Object> parameters = new ArrayList<Object>();
        for (int index = 0; index < entityIds.size(); index++) {
            if (index > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append("?");
            parameters.add(entityIds.get(index));
        }
        sqlBuilder.append(") order by confidence desc, id asc limit ?");
        parameters.add(Integer.valueOf(limit));
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapRelation, parameters.toArray());
    }

    private AstRelation mapRelation(ResultSet resultSet, int rowNum) throws SQLException {
        AstRelation relation = new AstRelation();
        relation.setSrcId(resultSet.getString("src_id"));
        relation.setEdgeType(resultSet.getString("edge_type"));
        relation.setDstId(resultSet.getString("dst_id"));
        relation.setSourceRef(resultSet.getString("source_ref"));
        relation.setSourceStartLine(resultSet.getInt("source_start_line"));
        relation.setSourceEndLine(resultSet.getInt("source_end_line"));
        relation.setConfidence(resultSet.getDouble("confidence"));
        relation.setExtractor(resultSet.getString("extractor"));
        return relation;
    }
}
