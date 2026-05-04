package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
        if (jdbcTemplate == null) {
            return List.of();
        }
        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<Object> parameters = new ArrayList<Object>();
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                select a.source_id,
                       a.article_key,
                       a.concept_id,
                       a.title,
                       a.content,
                       a.metadata_json::text as metadata_json,
                       a.review_status,
                       a.source_paths,
                       0
                """);
        appendTokenScore(
                sqlBuilder,
                parameters,
                queryTokens,
                List.of("lower(a.refkey_text)", "lower(a.concept_id)", "lower(a.title)", "lower(a.metadata_json::text)"),
                List.of(Double.valueOf(5.0D), Double.valueOf(4.0D), Double.valueOf(2.0D), Double.valueOf(1.0D))
        );
        sqlBuilder.append("""
                       as score
                from articles a
                where false
                """);
        appendTokenWhere(
                sqlBuilder,
                parameters,
                queryTokens,
                List.of("lower(a.refkey_text)", "lower(a.concept_id)", "lower(a.title)", "lower(a.metadata_json::text)")
        );
        sqlBuilder.append("""
                order by score desc, a.compiled_at desc, a.article_key asc
                limit ?
                """);
        parameters.add(Integer.valueOf(limit <= 0 ? 5 : limit));
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapArticleWithScore, parameters.toArray());
    }

    /**
     * 映射带分数字段的文章命中。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 文章命中
     * @throws SQLException SQL 异常
     */
    private QueryArticleHit mapArticleWithScore(ResultSet resultSet, int rowNum) throws SQLException {
        return new QueryArticleHit(
                readLong(resultSet, "source_id"),
                resultSet.getString("article_key"),
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("metadata_json"),
                resultSet.getString("review_status"),
                readSourcePaths(resultSet),
                resultSet.getDouble("score")
        );
    }

    /**
     * 拼接 token 评分表达式。
     *
     * @param sqlBuilder SQL 构造器
     * @param parameters SQL 参数
     * @param queryTokens 查询 token
     * @param columns 参与匹配的列
     * @param weights 列对应权重
     */
    private void appendTokenScore(
            StringBuilder sqlBuilder,
            List<Object> parameters,
            List<String> queryTokens,
            List<String> columns,
            List<Double> weights
    ) {
        for (String queryToken : queryTokens) {
            String pattern = likePattern(queryToken);
            for (int index = 0; index < columns.size(); index++) {
                sqlBuilder.append(" + case when ")
                        .append(columns.get(index))
                        .append(" like ? then ")
                        .append(weights.get(index).doubleValue())
                        .append(" else 0 end\n");
                parameters.add(pattern);
            }
        }
    }

    /**
     * 拼接 token 过滤条件。
     *
     * @param sqlBuilder SQL 构造器
     * @param parameters SQL 参数
     * @param queryTokens 查询 token
     * @param columns 参与匹配的列
     */
    private void appendTokenWhere(
            StringBuilder sqlBuilder,
            List<Object> parameters,
            List<String> queryTokens,
            List<String> columns
    ) {
        for (String queryToken : queryTokens) {
            String pattern = likePattern(queryToken);
            for (String column : columns) {
                sqlBuilder.append("                  or ").append(column).append(" like ?\n");
                parameters.add(pattern);
            }
        }
    }

    /**
     * 构造 LIKE 匹配模式。
     *
     * @param queryToken 查询 token
     * @return LIKE 模式
     */
    private String likePattern(String queryToken) {
        return "%" + queryToken + "%";
    }

    /**
     * 读取可空长整型列。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 长整型值
     * @throws SQLException SQL 异常
     */
    private Long readLong(ResultSet resultSet, String columnName) throws SQLException {
        Object value = resultSet.getObject(columnName);
        return value == null ? null : resultSet.getLong(columnName);
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
