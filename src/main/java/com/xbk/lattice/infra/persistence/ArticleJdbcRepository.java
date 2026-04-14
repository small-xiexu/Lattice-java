package com.xbk.lattice.infra.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Article JDBC 仓储
 *
 * 职责：提供最小文章落盘与读取能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ArticleJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Article JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ArticleJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存或更新文章。
     *
     * @param articleRecord 文章记录
     */
    public void upsert(ArticleRecord articleRecord) {
        String sql = """
                insert into articles (concept_id, title, content, lifecycle, compiled_at)
                values (?, ?, ?, ?, ?)
                on conflict (concept_id) do update
                set title = excluded.title,
                    content = excluded.content,
                    lifecycle = excluded.lifecycle,
                    compiled_at = excluded.compiled_at
                """;
        jdbcTemplate.update(
                sql,
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                articleRecord.getContent(),
                articleRecord.getLifecycle(),
                articleRecord.getCompiledAt()
        );
    }

    /**
     * 按概念标识查询文章。
     *
     * @param conceptId 概念标识
     * @return 文章记录
     */
    public Optional<ArticleRecord> findByConceptId(String conceptId) {
        String sql = """
                select concept_id, title, content, lifecycle, compiled_at
                from articles
                where concept_id = ?
                """;
        List<ArticleRecord> articleRecords = jdbcTemplate.query(sql, this::mapArticleRecord, conceptId);
        if (articleRecords.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(articleRecords.get(0));
    }

    /**
     * 映射单行文章记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 文章记录
     * @throws SQLException SQL 异常
     */
    private ArticleRecord mapArticleRecord(ResultSet resultSet, int rowNum) throws SQLException {
        OffsetDateTime compiledAt = resultSet.getObject("compiled_at", OffsetDateTime.class);
        return new ArticleRecord(
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("lifecycle"),
                compiledAt
        );
    }
}
