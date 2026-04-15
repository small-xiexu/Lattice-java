package com.xbk.lattice.infra.persistence;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
                insert into articles (concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (concept_id) do update
                set title = excluded.title,
                    content = excluded.content,
                    lifecycle = excluded.lifecycle,
                    compiled_at = excluded.compiled_at,
                    source_paths = excluded.source_paths,
                    metadata_json = excluded.metadata_json
                """;
        jdbcTemplate.update(connection -> {
            Array sourcePathsArray = connection.createArrayOf(
                    "text",
                    articleRecord.getSourcePaths().toArray(new String[0])
            );
            PGobject metadataJsonObject = new PGobject();
            metadataJsonObject.setType("jsonb");
            metadataJsonObject.setValue(articleRecord.getMetadataJson());

            java.sql.PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, articleRecord.getConceptId());
            preparedStatement.setString(2, articleRecord.getTitle());
            preparedStatement.setString(3, articleRecord.getContent());
            preparedStatement.setString(4, articleRecord.getLifecycle());
            preparedStatement.setObject(5, articleRecord.getCompiledAt());
            preparedStatement.setArray(6, sourcePathsArray);
            preparedStatement.setObject(7, metadataJsonObject);
            return preparedStatement;
        });
    }

    /**
     * 按概念标识查询文章。
     *
     * @param conceptId 概念标识
     * @return 文章记录
     */
    public Optional<ArticleRecord> findByConceptId(String conceptId) {
        String sql = """
                select concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json
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
        List<String> sourcePaths = readSourcePaths(resultSet);
        return new ArticleRecord(
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("lifecycle"),
                compiledAt,
                sourcePaths,
                resultSet.getString("metadata_json")
        );
    }

    /**
     * 读取来源路径数组。
     *
     * @param resultSet 结果集
     * @return 来源路径列表
     * @throws SQLException SQL 异常
     */
    private List<String> readSourcePaths(ResultSet resultSet) throws SQLException {
        Array array = resultSet.getArray("source_paths");
        if (array == null) {
            return List.of();
        }

        Object[] values = (Object[]) array.getArray();
        List<String> sourcePaths = new ArrayList<String>();
        for (Object value : values) {
            sourcePaths.add(String.valueOf(value));
        }
        return sourcePaths;
    }
}
