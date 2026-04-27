package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

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
        ArticleRecord normalizedArticleRecord = ArticleMarkdownSupport.synchronizeArticleRecord(
                articleRecord,
                articleRecord.getContent(),
                articleRecord.getReviewStatus()
        );
        String searchText = buildArticleSearchText(normalizedArticleRecord);
        String refkeyText = buildRefkeyText(normalizedArticleRecord);
        String sql = """
                insert into articles (
                    source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                    source_paths, metadata_json, summary, referential_keywords, depends_on,
                    related, confidence, review_status, search_text, search_tsv, refkey_text
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('simple'::regconfig, ?), ?)
                on conflict (article_key) do update
                set source_id = excluded.source_id,
                    concept_id = excluded.concept_id,
                    title = excluded.title,
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
                    review_status = excluded.review_status,
                    search_text = excluded.search_text,
                    search_tsv = excluded.search_tsv,
                    refkey_text = excluded.refkey_text
                """;
        jdbcTemplate.update(connection -> {
            Array sourcePathsArray = connection.createArrayOf(
                    "text",
                    normalizedArticleRecord.getSourcePaths().toArray(new String[0])
            );
            Array referentialKeywordsArray = connection.createArrayOf(
                    "text",
                    normalizedArticleRecord.getReferentialKeywords().toArray(new String[0])
            );
            Array dependsOnArray = connection.createArrayOf(
                    "text",
                    normalizedArticleRecord.getDependsOn().toArray(new String[0])
            );
            Array relatedArray = connection.createArrayOf(
                    "text",
                    normalizedArticleRecord.getRelated().toArray(new String[0])
            );
            PGobject metadataJsonObject = new PGobject();
            metadataJsonObject.setType("jsonb");
            metadataJsonObject.setValue(normalizedArticleRecord.getMetadataJson());

            java.sql.PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setObject(1, normalizedArticleRecord.getSourceId());
            preparedStatement.setString(2, normalizedArticleRecord.getArticleKey());
            preparedStatement.setString(3, normalizedArticleRecord.getConceptId());
            preparedStatement.setString(4, normalizedArticleRecord.getTitle());
            preparedStatement.setString(5, normalizedArticleRecord.getContent());
            preparedStatement.setString(6, normalizedArticleRecord.getLifecycle());
            preparedStatement.setObject(7, normalizedArticleRecord.getCompiledAt());
            preparedStatement.setArray(8, sourcePathsArray);
            preparedStatement.setObject(9, metadataJsonObject);
            preparedStatement.setString(10, normalizedArticleRecord.getSummary());
            preparedStatement.setArray(11, referentialKeywordsArray);
            preparedStatement.setArray(12, dependsOnArray);
            preparedStatement.setArray(13, relatedArray);
            preparedStatement.setString(14, normalizedArticleRecord.getConfidence());
            preparedStatement.setString(15, normalizedArticleRecord.getReviewStatus());
            preparedStatement.setString(16, searchText);
            preparedStatement.setString(17, searchText);
            preparedStatement.setString(18, refkeyText);
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
                select source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                       source_paths, metadata_json, summary, referential_keywords, depends_on,
                       related, confidence, review_status
                from articles
                where concept_id = ?
                order by compiled_at desc, article_key asc
                limit 1
                """;
        List<ArticleRecord> articleRecords = jdbcTemplate.query(sql, this::mapArticleRecord, conceptId);
        if (articleRecords.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(articleRecords.get(0));
    }

    /**
     * 按文章唯一键查询文章。
     *
     * @param articleKey 文章唯一键
     * @return 文章记录
     */
    public Optional<ArticleRecord> findByArticleKey(String articleKey) {
        String sql = """
                select source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                       source_paths, metadata_json, summary, referential_keywords, depends_on,
                       related, confidence, review_status
                from articles
                where article_key = ?
                """;
        List<ArticleRecord> articleRecords = jdbcTemplate.query(sql, this::mapArticleRecord, articleKey);
        if (articleRecords.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(articleRecords.get(0));
    }

    /**
     * 按资料源与概念标识查询文章。
     *
     * @param sourceId 资料源主键
     * @param conceptId 概念标识
     * @return 文章记录
     */
    public Optional<ArticleRecord> findBySourceIdAndConceptId(Long sourceId, String conceptId) {
        String sql = """
                select source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                       source_paths, metadata_json, summary, referential_keywords, depends_on,
                       related, confidence, review_status
                from articles
                where source_id = ?
                  and concept_id = ?
                """;
        List<ArticleRecord> articleRecords = jdbcTemplate.query(sql, this::mapArticleRecord, sourceId, conceptId);
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
     * 追加 source-aware 上游纠错标记。
     *
     * @param downstreamArticle 下游文章
     * @param upstreamArticle 上游文章
     * @param correctionSummary 纠错摘要
     */
    public void appendUpstreamCorrection(
            ArticleRecord downstreamArticle,
            ArticleRecord upstreamArticle,
            String correctionSummary
    ) {
        if (downstreamArticle == null || upstreamArticle == null) {
            return;
        }
        ObjectNode metadataNode = readMetadata(downstreamArticle.getMetadataJson());
        ArrayNode correctionsNode = ensureCorrectionsNode(metadataNode);
        ObjectNode correctionNode = OBJECT_MAPPER.createObjectNode();
        correctionNode.put("from", upstreamArticle.getConceptId());
        correctionNode.put("summary", correctionSummary);
        correctionNode.put("marked_at", OffsetDateTime.now().toString());
        if (upstreamArticle.getArticleKey() != null && !upstreamArticle.getArticleKey().isBlank()) {
            correctionNode.put("fromArticleKey", upstreamArticle.getArticleKey());
        }
        if (upstreamArticle.getSourceId() != null) {
            correctionNode.put("fromSourceId", upstreamArticle.getSourceId().longValue());
        }
        correctionsNode.add(correctionNode);
        upsert(downstreamArticle.copy(
                downstreamArticle.getTitle(),
                downstreamArticle.getContent(),
                downstreamArticle.getLifecycle(),
                downstreamArticle.getCompiledAt(),
                downstreamArticle.getSourcePaths(),
                metadataNode.toString(),
                downstreamArticle.getSummary(),
                downstreamArticle.getReferentialKeywords(),
                downstreamArticle.getDependsOn(),
                downstreamArticle.getRelated(),
                downstreamArticle.getConfidence(),
                downstreamArticle.getReviewStatus()
        ));
    }

    /**
     * 查询带有指定上游纠错标记的下游文章。
     *
     * @param fromConceptId 上游概念标识
     * @return 下游文章列表
     */
    public List<ArticleRecord> findWithUpstreamCorrections(String fromConceptId) {
        String sql = """
                select source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                       source_paths, metadata_json, summary, referential_keywords, depends_on,
                       related, confidence, review_status
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
     * 查询带有指定上游纠错标记的下游文章。
     *
     * @param upstreamArticle 上游文章
     * @return 下游文章列表
     */
    public List<ArticleRecord> findWithUpstreamCorrections(ArticleRecord upstreamArticle) {
        if (upstreamArticle == null) {
            return List.of();
        }
        String sql = """
                select source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                       source_paths, metadata_json, summary, referential_keywords, depends_on,
                       related, confidence, review_status
                from articles
                where metadata_json::text like '%upstream_corrections%'
                order by source_id asc nulls first, article_key asc
                """;
        List<ArticleRecord> candidates = jdbcTemplate.query(sql, this::mapArticleRecord);
        List<ArticleRecord> matchedRecords = new ArrayList<ArticleRecord>();
        for (ArticleRecord candidate : candidates) {
            if (containsUpstreamCorrection(candidate.getMetadataJson(), upstreamArticle)) {
                matchedRecords.add(candidate);
            }
        }
        return matchedRecords;
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
     * 清理指定下游文章中来自特定上游的 source-aware 纠错标记。
     *
     * @param downstreamArticle 下游文章
     * @param upstreamArticle 上游文章
     */
    public void clearUpstreamCorrection(ArticleRecord downstreamArticle, ArticleRecord upstreamArticle) {
        if (downstreamArticle == null || upstreamArticle == null) {
            return;
        }
        ObjectNode metadataNode = readMetadata(downstreamArticle.getMetadataJson());
        ArrayNode correctionsNode = ensureCorrectionsNode(metadataNode);
        ArrayNode filteredNode = OBJECT_MAPPER.createArrayNode();
        for (JsonNode correctionNode : correctionsNode) {
            if (matchesUpstreamCorrection(correctionNode, upstreamArticle)) {
                continue;
            }
            filteredNode.add(correctionNode);
        }
        metadataNode.set("upstream_corrections", filteredNode);
        upsert(downstreamArticle.copy(
                downstreamArticle.getTitle(),
                downstreamArticle.getContent(),
                downstreamArticle.getLifecycle(),
                downstreamArticle.getCompiledAt(),
                downstreamArticle.getSourcePaths(),
                metadataNode.toString(),
                downstreamArticle.getSummary(),
                downstreamArticle.getReferentialKeywords(),
                downstreamArticle.getDependsOn(),
                downstreamArticle.getRelated(),
                downstreamArticle.getConfidence(),
                downstreamArticle.getReviewStatus()
        ));
    }

    /**
     * 查询全部文章。
     *
     * @return 文章记录列表
     */
    public List<ArticleRecord> findAll() {
        String sql = """
                select source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                       source_paths, metadata_json, summary, referential_keywords, depends_on,
                       related, confidence, review_status
                from articles
                order by source_id asc nulls first, compiled_at desc, article_key asc
                """;
        return jdbcTemplate.query(sql, this::mapArticleRecord);
    }

    /**
     * 清空全部文章与级联受管数据。
     */
    public void deleteAll() {
        jdbcTemplate.execute("TRUNCATE TABLE articles CASCADE");
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
        Object sourceId = resultSet.getObject("source_id");
        return new ArticleRecord(
                sourceId == null ? null : resultSet.getLong("source_id"),
                resultSet.getString("article_key"),
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

    /**
     * 构建文章检索文本。
     *
     * @param articleRecord 文章记录
     * @return 检索文本
     */
    private String buildArticleSearchText(ArticleRecord articleRecord) {
        return String.join(
                " ",
                safeText(articleRecord.getTitle()),
                safeText(articleRecord.getSummary()),
                safeText(articleRecord.getContent()),
                safeText(articleRecord.getMetadataJson())
        ).trim();
    }

    /**
     * 构建明确性关键词检索文本。
     *
     * @param articleRecord 文章记录
     * @return 明确性关键词检索文本
     */
    private String buildRefkeyText(ArticleRecord articleRecord) {
        return String.join(
                " ",
                safeText(articleRecord.getConceptId()),
                safeText(articleRecord.getTitle()),
                String.join(" ", articleRecord.getReferentialKeywords())
        ).trim();
    }

    /**
     * 返回非空文本。
     *
     * @param value 原始文本
     * @return 非空文本
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private ObjectNode readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(metadataJson);
            if (rootNode instanceof ObjectNode objectNode) {
                return objectNode;
            }
        }
        catch (Exception ignored) {
            // 回退为空对象
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private ArrayNode ensureCorrectionsNode(ObjectNode metadataNode) {
        JsonNode correctionsNode = metadataNode.path("upstream_corrections");
        if (correctionsNode instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        metadataNode.set("upstream_corrections", arrayNode);
        return arrayNode;
    }

    private boolean containsUpstreamCorrection(String metadataJson, ArticleRecord upstreamArticle) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return false;
        }
        try {
            JsonNode correctionsNode = OBJECT_MAPPER.readTree(metadataJson).path("upstream_corrections");
            if (!correctionsNode.isArray()) {
                return false;
            }
            for (JsonNode correctionNode : correctionsNode) {
                if (matchesUpstreamCorrection(correctionNode, upstreamArticle)) {
                    return true;
                }
            }
            return false;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesUpstreamCorrection(JsonNode correctionNode, ArticleRecord upstreamArticle) {
        String fromArticleKey = correctionNode.path("fromArticleKey").asText("");
        if (!fromArticleKey.isBlank()
                && upstreamArticle.getArticleKey() != null
                && upstreamArticle.getArticleKey().equals(fromArticleKey)) {
            return true;
        }
        String fromConceptId = correctionNode.path("from").asText("");
        if (!upstreamArticle.getConceptId().equals(fromConceptId)) {
            return false;
        }
        JsonNode fromSourceIdNode = correctionNode.get("fromSourceId");
        if (fromSourceIdNode == null || fromSourceIdNode.isNull() || upstreamArticle.getSourceId() == null) {
            return true;
        }
        return fromSourceIdNode.asLong() == upstreamArticle.getSourceId().longValue();
    }
}
