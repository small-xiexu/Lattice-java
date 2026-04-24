package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.compiler.ast.domain.AstEntityType;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图谱实体 JDBC 仓储
 *
 * 职责：负责 graph_entities 表的清理、写入与查询
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class GraphEntityJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建图谱实体 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public GraphEntityJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 删除指定源文件下的全部实体。
     *
     * @param sourceFileId 源文件主键
     */
    public void deleteBySourceFileId(Long sourceFileId) {
        jdbcTemplate.update("delete from graph_entities where source_file_id = ?", sourceFileId);
    }

    /**
     * 保存或更新图谱实体。
     *
     * @param entity 图谱实体
     */
    public void upsert(AstEntity entity) {
        jdbcTemplate.update(
                """
                        insert into graph_entities (
                            id, canonical_name, simple_name, entity_type, system_label,
                            source_file_id, anchor_ref, resolution_status, metadata_json
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        on conflict (id) do update
                        set canonical_name = excluded.canonical_name,
                            simple_name = excluded.simple_name,
                            entity_type = excluded.entity_type,
                            system_label = excluded.system_label,
                            source_file_id = excluded.source_file_id,
                            anchor_ref = excluded.anchor_ref,
                            resolution_status = excluded.resolution_status,
                            metadata_json = excluded.metadata_json
                        """,
                entity.getId(),
                entity.getCanonicalName(),
                entity.getSimpleName(),
                entity.getEntityType().name(),
                entity.getSystemLabel(),
                entity.getSourceFileId(),
                entity.getAnchorRef(),
                entity.getResolutionStatus(),
                entity.getMetadataJson()
        );
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
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                select id, canonical_name, simple_name, entity_type, system_label,
                       source_file_id, anchor_ref, resolution_status, metadata_json::text as metadata_json
                from graph_entities
                where
                """);
        List<Object> parameters = new ArrayList<Object>();
        for (int index = 0; index < mentions.size(); index++) {
            if (index > 0) {
                sqlBuilder.append(" or ");
            }
            sqlBuilder.append("(lower(simple_name) like ? or lower(canonical_name) like ?)");
            String likePattern = "%" + mentions.get(index).toLowerCase() + "%";
            parameters.add(likePattern);
            parameters.add(likePattern);
        }
        sqlBuilder.append(" order by simple_name asc limit ?");
        parameters.add(Integer.valueOf(limit));
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapEntity, parameters.toArray());
    }

    private AstEntity mapEntity(ResultSet resultSet, int rowNum) throws SQLException {
        AstEntity entity = new AstEntity();
        entity.setId(resultSet.getString("id"));
        entity.setCanonicalName(resultSet.getString("canonical_name"));
        entity.setSimpleName(resultSet.getString("simple_name"));
        entity.setEntityType(AstEntityType.valueOf(resultSet.getString("entity_type")));
        entity.setSystemLabel(resultSet.getString("system_label"));
        entity.setSourceFileId(resultSet.getLong("source_file_id"));
        entity.setAnchorRef(resultSet.getString("anchor_ref"));
        entity.setResolutionStatus(resultSet.getString("resolution_status"));
        entity.setMetadataJson(resultSet.getString("metadata_json"));
        return entity;
    }
}
