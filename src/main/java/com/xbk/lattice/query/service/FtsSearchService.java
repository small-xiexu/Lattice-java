package com.xbk.lattice.query.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * FTS 检索服务
 *
 * 职责：提供最小全文检索能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class FtsSearchService {

    private final JdbcTemplate jdbcTemplate;

    private final FtsConfigResolver ftsConfigResolver;

    /**
     * 创建 FTS 检索服务。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public FtsSearchService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new FtsConfigResolver());
    }

    /**
     * 创建 FTS 检索服务。
     *
     * @param jdbcTemplate JDBC 模板
     * @param ftsConfigResolver FTS 配置解析器
     */
    @Autowired
    public FtsSearchService(JdbcTemplate jdbcTemplate, FtsConfigResolver ftsConfigResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.ftsConfigResolver = ftsConfigResolver;
    }

    /**
     * 执行全文检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中文章
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (jdbcTemplate == null) {
            return List.of();
        }

        String tsConfig = ftsConfigResolver.resolveArticleTsConfig();
        String sql = """
                select source_id,
                       article_key,
                       concept_id,
                       title,
                       content,
                       metadata_json::text as metadata_json,
                       source_paths,
                       ts_rank_cd(
                           to_tsvector(cast(? as regconfig),
                               coalesce(title, '') || ' ' ||
                               coalesce(content, '') || ' ' ||
                               coalesce(metadata_json->>'description', '')
                           ),
                           plainto_tsquery(cast(? as regconfig), ?)
                       ) as score
                from articles
                where to_tsvector(cast(? as regconfig),
                          coalesce(title, '') || ' ' ||
                          coalesce(content, '') || ' ' ||
                          coalesce(metadata_json->>'description', '')
                      ) @@ plainto_tsquery(cast(? as regconfig), ?)
                order by score desc, compiled_at desc
                limit ?
                """;
        return jdbcTemplate.query(
                sql,
                this::mapQueryArticleHit,
                tsConfig,
                tsConfig,
                question,
                tsConfig,
                tsConfig,
                question,
                limit
        );
    }

    /**
     * 映射查询命中。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 查询命中
     * @throws SQLException SQL 异常
     */
    private QueryArticleHit mapQueryArticleHit(ResultSet resultSet, int rowNum) throws SQLException {
        return new QueryArticleHit(
                readLong(resultSet, "source_id"),
                resultSet.getString("article_key"),
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("metadata_json"),
                readSourcePaths(resultSet),
                resultSet.getDouble("score")
        );
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
