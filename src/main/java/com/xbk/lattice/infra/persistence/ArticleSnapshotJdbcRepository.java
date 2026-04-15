package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章快照 JDBC 仓储
 *
 * 职责：提供文章快照历史查询能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ArticleSnapshotJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文章快照 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ArticleSnapshotJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询最近文章快照。
     *
     * @param limit 返回数量
     * @return 快照列表
     */
    public List<ArticleSnapshotRecord> findRecent(int limit) {
        String sql = """
                select snapshot_id, concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json,
                       summary, referential_keywords, depends_on, related, confidence, review_status,
                       snapshot_reason, captured_at
                from article_snapshots
                order by captured_at desc, snapshot_id desc
                limit ?
                """;
        return jdbcTemplate.query(sql, this::mapArticleSnapshotRecord, Math.max(limit, 0));
    }

    /**
     * 查询指定概念的历史快照。
     *
     * @param conceptId 概念标识
     * @param limit 返回数量
     * @return 历史快照列表
     */
    public List<ArticleSnapshotRecord> findByConceptId(String conceptId, int limit) {
        String sql = """
                select snapshot_id, concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json,
                       summary, referential_keywords, depends_on, related, confidence, review_status,
                       snapshot_reason, captured_at
                from article_snapshots
                where concept_id = ?
                order by captured_at desc, snapshot_id desc
                limit ?
                """;
        return jdbcTemplate.query(sql, this::mapArticleSnapshotRecord, conceptId, Math.max(limit, 0));
    }

    /**
     * 映射单行文章快照记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 文章快照记录
     * @throws SQLException SQL 异常
     */
    private ArticleSnapshotRecord mapArticleSnapshotRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ArticleSnapshotRecord(
                resultSet.getLong("snapshot_id"),
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("lifecycle"),
                resultSet.getObject("compiled_at", OffsetDateTime.class),
                readTextArray(resultSet, "source_paths"),
                resultSet.getString("metadata_json"),
                resultSet.getString("summary"),
                readTextArray(resultSet, "referential_keywords"),
                readTextArray(resultSet, "depends_on"),
                readTextArray(resultSet, "related"),
                resultSet.getString("confidence"),
                resultSet.getString("review_status"),
                resultSet.getString("snapshot_reason"),
                resultSet.getObject("captured_at", OffsetDateTime.class)
        );
    }

    /**
     * 读取文本数组列。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 文本列表
     * @throws SQLException SQL 异常
     */
    private List<String> readTextArray(ResultSet resultSet, String columnName) throws SQLException {
        Array array = resultSet.getArray(columnName);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<String> items = new ArrayList<String>();
        for (Object value : values) {
            items.add(String.valueOf(value));
        }
        return items;
    }
}
