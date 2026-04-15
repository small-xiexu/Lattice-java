package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 关键引用词检索服务
 *
 * 职责：提供最小引用词/业务码检索能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class RefKeySearchService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建关键引用词检索服务。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public RefKeySearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行关键引用词检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中文章
     */
    public List<QueryArticleHit> search(String question, int limit) {
        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String sql = """
                select concept_id, title, content, metadata_json::text as metadata_json, source_paths
                from articles
                """;
        List<QueryArticleHit> articleHits = jdbcTemplate.query(sql, this::mapArticleWithoutScore);
        List<QueryArticleHit> matchedHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit articleHit : articleHits) {
            double score = scoreArticleHit(articleHit, queryTokens);
            if (score > 0) {
                matchedHits.add(new QueryArticleHit(
                        articleHit.getConceptId(),
                        articleHit.getTitle(),
                        articleHit.getContent(),
                        articleHit.getMetadataJson(),
                        articleHit.getSourcePaths(),
                        score
                ));
            }
        }
        matchedHits.sort(Comparator.comparing(QueryArticleHit::getScore).reversed()
                .thenComparing(QueryArticleHit::getConceptId));
        if (matchedHits.size() <= limit) {
            return matchedHits;
        }
        return matchedHits.subList(0, limit);
    }

    /**
     * 计算文章命中分数。
     *
     * @param articleHit 文章命中
     * @param queryTokens 查询 token
     * @return 分数
     */
    private double scoreArticleHit(QueryArticleHit articleHit, List<String> queryTokens) {
        String conceptId = articleHit.getConceptId().toLowerCase(Locale.ROOT);
        String title = articleHit.getTitle().toLowerCase(Locale.ROOT);
        String content = articleHit.getContent().toLowerCase(Locale.ROOT);
        String metadata = articleHit.getMetadataJson().toLowerCase(Locale.ROOT);

        double score = 0;
        for (String queryToken : queryTokens) {
            if (conceptId.contains(queryToken)) {
                score += 3.0;
            }
            if (title.contains(queryToken)) {
                score += 2.0;
            }
            if (metadata.contains(queryToken)) {
                score += 1.5;
            }
            if (content.contains(queryToken)) {
                score += 1.0;
            }
        }
        return score;
    }

    /**
     * 映射无分数字段的文章命中。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 文章命中
     * @throws SQLException SQL 异常
     */
    private QueryArticleHit mapArticleWithoutScore(ResultSet resultSet, int rowNum) throws SQLException {
        return new QueryArticleHit(
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("metadata_json"),
                readSourcePaths(resultSet),
                0
        );
    }

    /**
     * 读取来源路径数组。
     *
     * @param resultSet 结果集
     * @return 来源路径
     * @throws SQLException SQL 异常
     */
    private List<String> readSourcePaths(ResultSet resultSet) throws SQLException {
        Array sourcePathsArray = resultSet.getArray("source_paths");
        if (sourcePathsArray == null) {
            return List.of();
        }

        Object[] values = (Object[]) sourcePathsArray.getArray();
        List<String> sourcePaths = new ArrayList<String>();
        for (Object value : values) {
            sourcePaths.add(String.valueOf(value));
        }
        return sourcePaths;
    }
}
