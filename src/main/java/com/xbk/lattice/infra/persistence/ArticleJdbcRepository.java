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
                insert into articles (
                    concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json,
                    summary, referential_keywords, depends_on, related, confidence, review_status
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (concept_id) do update
                set title = excluded.title,
                    content = excluded.content,
                    lifecycle = excluded.lifecycle,
                    compiled_at = excluded.compiled_at,
                    source_paths = excluded.source_paths,
                    metadata_json = excluded.metadata_json,
                    summary = excluded.summary,
                    referential_keywords = excluded.referential_keywords,
                    depends_on = excluded.depends_on,
                    related = excluded.related,
                    confidence = excluded.confidence,
                    review_status = excluded.review_status
                """;
        jdbcTemplate.update(connection -> {
            Array sourcePathsArray = connection.createArrayOf(
                    "text",
                    articleRecord.getSourcePaths().toArray(new String[0])
            );
            Array referentialKeywordsArray = connection.createArrayOf(
                    "text",
                    articleRecord.getReferentialKeywords().toArray(new String[0])
            );
            Array dependsOnArray = connection.createArrayOf(
                    "text",
                    articleRecord.getDependsOn().toArray(new String[0])
            );
            Array relatedArray = connection.createArrayOf(
                    "text",
                    articleRecord.getRelated().toArray(new String[0])
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
            preparedStatement.setString(8, articleRecord.getSummary());
            preparedStatement.setArray(9, referentialKeywordsArray);
            preparedStatement.setArray(10, dependsOnArray);
            preparedStatement.setArray(11, relatedArray);
            preparedStatement.setString(12, articleRecord.getConfidence());
            preparedStatement.setString(13, articleRecord.getReviewStatus());
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
                select concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json,
                       summary, referential_keywords, depends_on, related, confidence, review_status
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
     * 追加一条上游纠错标记。
     *
     * @param conceptId 下游概念标识
     * @param fromConceptId 上游概念标识
     * @param correctionSummary 纠错摘要
     */
    public void appendUpstreamCorrection(String conceptId, String fromConceptId, String correctionSummary) {
        String sql = """
                update articles
                set metadata_json = jsonb_set(
                    coalesce(metadata_json::jsonb, '{}'::jsonb),
                    '{upstream_corrections}',
                    coalesce(metadata_json::jsonb->'upstream_corrections', '[]'::jsonb)
                        || jsonb_build_array(jsonb_build_object(
                            'from', ?,
                            'summary', ?,
                            'marked_at', to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
                        ))
                )
                where concept_id = ?
                """;
        jdbcTemplate.update(sql, fromConceptId, correctionSummary, conceptId);
    }

    /**
     * 查询带有指定上游纠错标记的下游文章。
     *
     * @param fromConceptId 上游概念标识
     * @return 下游文章列表
     */
    public List<ArticleRecord> findWithUpstreamCorrections(String fromConceptId) {
        String sql = """
                select concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json,
                       summary, referential_keywords, depends_on, related, confidence, review_status
                from articles
                where exists (
                    select 1
                    from jsonb_array_elements(coalesce(metadata_json::jsonb->'upstream_corrections', '[]'::jsonb)) as elem
                    where elem->>'from' = ?
                )
                order by concept_id asc
                """;
        return jdbcTemplate.query(sql, this::mapArticleRecord, fromConceptId);
    }

    /**
     * 清理指定下游文章中来自特定上游的纠错标记。
     *
     * @param downstreamConceptId 下游概念标识
     * @param fromConceptId 上游概念标识
     */
    public void clearUpstreamCorrection(String downstreamConceptId, String fromConceptId) {
        String sql = """
                update articles
                set metadata_json = jsonb_set(
                    coalesce(metadata_json::jsonb, '{}'::jsonb),
                    '{upstream_corrections}',
                    (
                        select coalesce(jsonb_agg(elem), '[]'::jsonb)
                        from jsonb_array_elements(coalesce(metadata_json::jsonb->'upstream_corrections', '[]'::jsonb)) as elem
                        where elem->>'from' <> ?
                    )
                )
                where concept_id = ?
                """;
        jdbcTemplate.update(sql, fromConceptId, downstreamConceptId);
    }

    /**
     * 查询全部文章。
     *
     * @return 文章记录列表
     */
    public List<ArticleRecord> findAll() {
        String sql = """
                select concept_id, title, content, lifecycle, compiled_at, source_paths, metadata_json,
                       summary, referential_keywords, depends_on, related, confidence, review_status
                from articles
                order by compiled_at desc, concept_id asc
                """;
        return jdbcTemplate.query(sql, this::mapArticleRecord);
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
                resultSet.getString("metadata_json"),
                resultSet.getString("summary"),
                readTextArray(resultSet, "referential_keywords"),
                readTextArray(resultSet, "depends_on"),
                readTextArray(resultSet, "related"),
                resultSet.getString("confidence"),
                resultSet.getString("review_status")
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
        return readTextArray(resultSet, "source_paths");
    }

    /**
     * 读取文本数组字段。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 文本数组
     * @throws SQLException SQL 异常
     */
    private List<String> readTextArray(ResultSet resultSet, String columnName) throws SQLException {
        Array array = resultSet.getArray(columnName);
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
