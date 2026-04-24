package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章来源关联 JDBC 仓储
 *
 * 职责：维护 article_source_refs 表中的来源溯源映射
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ArticleSourceRefJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文章来源关联 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ArticleSourceRefJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 替换文章的全部来源关联。
     *
     * @param articleKey 文章唯一键
     * @param refRecords 关联记录列表
     */
    public void replaceRefs(String articleKey, List<ArticleSourceRefRecord> refRecords) {
        if (jdbcTemplate == null || articleKey == null || articleKey.isBlank()) {
            return;
        }
        jdbcTemplate.update("delete from article_source_refs where article_key = ?", articleKey);
        if (refRecords == null || refRecords.isEmpty()) {
            return;
        }
        String sql = """
                insert into article_source_refs (
                    article_key, source_id, source_file_id, ref_type, ref_label
                )
                values (?, ?, ?, ?, ?)
                """;
        for (ArticleSourceRefRecord refRecord : refRecords) {
            jdbcTemplate.update(
                    sql,
                    refRecord.getArticleKey(),
                    refRecord.getSourceId(),
                    refRecord.getSourceFileId(),
                    refRecord.getRefType(),
                    refRecord.getRefLabel()
            );
        }
    }

    /**
     * 查询文章当前已有的全部来源关联。
     *
     * @param articleKey 文章唯一键
     * @return 来源关联列表
     */
    public List<ArticleSourceRefRecord> findByArticleKey(String articleKey) {
        if (jdbcTemplate == null || articleKey == null || articleKey.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select article_key, source_id, source_file_id, ref_type, ref_label
                        from article_source_refs
                        where article_key = ?
                        order by created_at asc, id asc
                        """,
                this::mapRecord,
                articleKey
        );
    }

    /**
     * 按源文件主键批量查询关联的文章键。
     *
     * @param sourceFileIds 源文件主键列表
     * @return sourceFileId -> articleKey 列表
     */
    public Map<Long, List<String>> findArticleKeysBySourceFileIds(List<Long> sourceFileIds) {
        Map<Long, List<String>> articleKeysBySourceFileId = new LinkedHashMap<Long, List<String>>();
        if (jdbcTemplate == null || sourceFileIds == null || sourceFileIds.isEmpty()) {
            return articleKeysBySourceFileId;
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                select source_file_id, article_key
                from article_source_refs
                where source_file_id in (
                """);
        for (int index = 0; index < sourceFileIds.size(); index++) {
            if (index > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append("?");
        }
        sqlBuilder.append(") order by created_at asc, id asc");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlBuilder.toString(), sourceFileIds.toArray());
        for (Map<String, Object> row : rows) {
            Number sourceFileId = (Number) row.get("source_file_id");
            if (sourceFileId == null) {
                continue;
            }
            Long sourceFileKey = Long.valueOf(sourceFileId.longValue());
            List<String> articleKeys = articleKeysBySourceFileId.computeIfAbsent(
                    sourceFileKey,
                    ignored -> new ArrayList<String>()
            );
            Object articleKey = row.get("article_key");
            if (articleKey != null) {
                articleKeys.add(String.valueOf(articleKey));
            }
        }
        return articleKeysBySourceFileId;
    }

    private ArticleSourceRefRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ArticleSourceRefRecord(
                resultSet.getString("article_key"),
                resultSet.getLong("source_id"),
                resultSet.getLong("source_file_id"),
                resultSet.getString("ref_type"),
                resultSet.getString("ref_label")
        );
    }
}
