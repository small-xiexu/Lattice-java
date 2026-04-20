package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
