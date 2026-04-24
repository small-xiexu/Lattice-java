package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.compiler.ast.domain.AstFact;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图谱事实 JDBC 仓储
 *
 * 职责：负责 graph_facts 表的清理、写入与查询
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class GraphFactJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建图谱事实 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public GraphFactJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 删除指定源引用下的全部事实。
     *
     * @param sourceRef 源引用
     */
    public void deleteBySourceRef(String sourceRef) {
        jdbcTemplate.update("delete from graph_facts where source_ref = ?", sourceRef);
    }

    /**
     * 写入图谱事实。
     *
     * @param fact 图谱事实
     */
    public void insert(AstFact fact) {
        jdbcTemplate.update(
                """
                        insert into graph_facts (
                            entity_id, predicate, value, source_ref, source_start_line, source_end_line,
                            evidence_excerpt, confidence, extractor, asserted_at, superseded_by, tombstoned
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, null, false)
                        """,
                fact.getEntityId(),
                fact.getPredicate(),
                fact.getValue(),
                fact.getSourceRef(),
                Integer.valueOf(fact.getSourceStartLine()),
                Integer.valueOf(fact.getSourceEndLine()),
                fact.getEvidenceExcerpt(),
                Double.valueOf(fact.getConfidence()),
                fact.getExtractor()
        );
    }

    /**
     * 按实体查询高置信事实。
     *
     * @param entityIds 实体 ID 列表
     * @param limit 返回数量
     * @return 事实列表
     */
    public List<AstFact> findActiveFactsByEntityIds(List<String> entityIds, int limit) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                select entity_id, predicate, value, source_ref, source_start_line, source_end_line,
                       evidence_excerpt, confidence, extractor
                from graph_facts
                where tombstoned = false
                  and superseded_by is null
                  and entity_id in (
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
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapFact, parameters.toArray());
    }

    private AstFact mapFact(ResultSet resultSet, int rowNum) throws SQLException {
        AstFact fact = new AstFact();
        fact.setEntityId(resultSet.getString("entity_id"));
        fact.setPredicate(resultSet.getString("predicate"));
        fact.setValue(resultSet.getString("value"));
        fact.setSourceRef(resultSet.getString("source_ref"));
        fact.setSourceStartLine(resultSet.getInt("source_start_line"));
        fact.setSourceEndLine(resultSet.getInt("source_end_line"));
        fact.setEvidenceExcerpt(resultSet.getString("evidence_excerpt"));
        fact.setConfidence(resultSet.getDouble("confidence"));
        fact.setExtractor(resultSet.getString("extractor"));
        return fact;
    }
}
