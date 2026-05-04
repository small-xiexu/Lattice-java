package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.chunking.SemanticChunker;
import com.xbk.lattice.infra.chunking.TextChunk;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ArticleChunk JDBC 仓储
 *
 * 职责：提供最小 article chunk 替换写入与读取能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ArticleChunkJdbcRepository {

    private static final int DEFAULT_MAX_CHARS = 3600;

    private static final float DEFAULT_OVERLAP_RATIO = 0.15f;

    private final JdbcTemplate jdbcTemplate;

    private final SemanticChunker semanticChunker;

    /**
     * 创建 ArticleChunk JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ArticleChunkJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.semanticChunker = new SemanticChunker();
    }

    /**
     * 按 conceptId 替换文章 chunk。
     *
     * @param conceptId 概念标识
     * @param chunkTexts chunk 文本集合
     */
    public void replaceChunks(String conceptId, List<String> chunkTexts) {
        replaceChunks(null, conceptId, chunkTexts);
    }

    /**
     * 按文章唯一键或 conceptId 替换文章 chunk。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param chunkTexts chunk 文本集合
     */
    public void replaceChunks(String articleKey, String conceptId, List<String> chunkTexts) {
        if (jdbcTemplate == null) {
            return;
        }
        boolean useArticleKey = hasText(articleKey);
        String deleteSql = useArticleKey
                ? """
                delete from article_chunks
                where article_id = (
                    select id
                    from articles
                    where article_key = ?
                    order by compiled_at desc, id desc
                    limit 1
                )
                """
                : """
                delete from article_chunks
                where article_id = (
                    select id
                    from articles
                    where concept_id = ?
                    order by compiled_at desc, id desc
                    limit 1
                )
                """;
        jdbcTemplate.update(deleteSql, useArticleKey ? articleKey : conceptId);

        String insertSql = useArticleKey
                ? """
                insert into article_chunks (article_id, chunk_text, chunk_index, search_tsv)
                values ((
                    select id
                    from articles
                    where article_key = ?
                    order by compiled_at desc, id desc
                    limit 1
                ), ?, ?, to_tsvector('simple'::regconfig, ?))
                """
                : """
                insert into article_chunks (article_id, chunk_text, chunk_index, search_tsv)
                values ((
                    select id
                    from articles
                    where concept_id = ?
                    order by compiled_at desc, id desc
                    limit 1
                ), ?, ?, to_tsvector('simple'::regconfig, ?))
                """;
        for (int index = 0; index < chunkTexts.size(); index++) {
            String chunkText = chunkTexts.get(index);
            jdbcTemplate.update(insertSql, useArticleKey ? articleKey : conceptId, chunkText, index, chunkText);
        }
    }

    /**
     * 按文章正文替换语义分块。
     *
     * @param conceptId 概念标识
     * @param content 文章正文
     */
    public void replaceChunksFromContent(String conceptId, String content) {
        replaceChunksFromContent(null, conceptId, content);
    }

    /**
     * 按文章正文替换语义分块。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param content 文章正文
     */
    public void replaceChunksFromContent(String articleKey, String conceptId, String content) {
        if (jdbcTemplate == null) {
            return;
        }
        List<TextChunk> textChunks = semanticChunker.chunk(content, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_RATIO);
        List<String> chunkTexts = new ArrayList<String>();
        for (TextChunk textChunk : textChunks) {
            chunkTexts.add(textChunk.getText());
        }
        replaceChunks(articleKey, conceptId, chunkTexts);
    }

    /**
     * 按当前文章正文全量重建全部 article chunks。
     *
     * @param articleRecords 文章记录列表
     * @return 重建的文章数量
     */
    public int rebuildAll(List<ArticleRecord> articleRecords) {
        if (jdbcTemplate == null) {
            return 0;
        }

        jdbcTemplate.execute("TRUNCATE TABLE article_chunks RESTART IDENTITY CASCADE");
        int rebuiltCount = 0;
        for (ArticleRecord articleRecord : articleRecords) {
            replaceChunksFromContent(articleRecord.getArticleKey(), articleRecord.getConceptId(), articleRecord.getContent());
            rebuiltCount++;
        }
        return rebuiltCount;
    }

    /**
     * 统计全部 article chunk 数量。
     *
     * @return chunk 数量
     */
    public int countAll() {
        if (jdbcTemplate == null) {
            return 0;
        }

        Integer count = jdbcTemplate.queryForObject("select count(*) from article_chunks", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 按 conceptId 查询 chunk 文本。
     *
     * @param conceptId 概念标识
     * @return chunk 文本集合
     */
    public List<String> findChunkTexts(String conceptId) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        String sql = """
                select ac.chunk_text
                from article_chunks ac
                join articles a on a.id = ac.article_id
                where a.concept_id = ?
                order by ac.chunk_index
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> resultSet.getString("chunk_text"), conceptId);
    }

    /**
     * 按 conceptId 查询完整 chunk 记录。
     *
     * @param conceptId 概念标识
     * @return chunk 记录列表
     */
    public List<ArticleChunkRecord> findByConceptId(String conceptId) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        String sql = """
                select ac.id, ac.article_id, a.concept_id, ac.chunk_index, ac.chunk_text
                from article_chunks ac
                join articles a on a.id = ac.article_id
                where a.concept_id = ?
                order by ac.chunk_index
                """;
        return jdbcTemplate.query(sql, this::mapArticleChunkRecord, conceptId);
    }

    /**
     * 按文章唯一键查询完整 chunk 记录。
     *
     * @param articleKey 文章唯一键
     * @return chunk 记录列表
     */
    public List<ArticleChunkRecord> findByArticleKey(String articleKey) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        String sql = """
                select ac.id, ac.article_id, a.concept_id, ac.chunk_index, ac.chunk_text
                from article_chunks ac
                join articles a on a.id = ac.article_id
                where a.article_key = ?
                order by ac.chunk_index
                """;
        return jdbcTemplate.query(sql, this::mapArticleChunkRecord, articleKey);
    }

    /**
     * 查询全部 chunk 记录。
     *
     * @return 全部 chunk 记录
     */
    public List<ArticleChunkRecord> findAllRecords() {
        if (jdbcTemplate == null) {
            return List.of();
        }
        String sql = """
                select ac.id, ac.article_id, a.concept_id, ac.chunk_index, ac.chunk_text
                from article_chunks ac
                join articles a on a.id = ac.article_id
                order by ac.article_id asc, ac.chunk_index asc
                """;
        return jdbcTemplate.query(sql, this::mapArticleChunkRecord);
    }

    /**
     * 执行 article chunk 数据库侧 lexical 检索。
     *
     * @param question 查询问题
     * @param queryTokens 查询 token
     * @param limit 返回数量
     * @param tsConfig FTS 配置
     * @return lexical 命中记录
     */
    public List<LexicalSearchRecord> searchLexical(
            String question,
            List<String> queryTokens,
            int limit,
            String tsConfig
    ) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        List<String> normalizedTokens = normalizeTokens(queryTokens);
        if (!hasText(question) && normalizedTokens.isEmpty()) {
            return List.of();
        }

        List<Object> parameters = new ArrayList<Object>();
        parameters.add(normalizeTsConfig(tsConfig));
        parameters.add(question == null ? "" : question);
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                with query as (
                    select plainto_tsquery(cast(? as regconfig), ?) as tsq
                )
                select a.source_id,
                       a.article_key,
                       a.concept_id,
                       a.title,
                       ac.chunk_index,
                       ac.chunk_text,
                       a.metadata_json::text as metadata_json,
                       a.review_status,
                       a.source_paths,
                       ts_rank_cd(ac.search_tsv, query.tsq)
                """);
        appendTokenScore(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("lower(ac.chunk_text)", "lower(a.title)", "lower(a.concept_id)"),
                List.of(Double.valueOf(3.0D), Double.valueOf(1.5D), Double.valueOf(1.0D))
        );
        sqlBuilder.append("""
                       as score
                from article_chunks ac
                join articles a on a.id = ac.article_id
                cross join query
                where ac.search_tsv @@ query.tsq
                """);
        appendTokenWhere(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("lower(ac.chunk_text)", "lower(a.title)", "lower(a.concept_id)")
        );
        sqlBuilder.append("""
                order by score desc, a.compiled_at desc, a.article_key asc, ac.chunk_index asc
                limit ?
                """);
        parameters.add(Integer.valueOf(safeLimit(limit)));
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapLexicalSearchRecord, parameters.toArray());
    }

    /**
     * 映射 chunk 记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return chunk 记录
     */
    private ArticleChunkRecord mapArticleChunkRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ArticleChunkRecord(
                resultSet.getLong("id"),
                resultSet.getLong("article_id"),
                resultSet.getString("concept_id"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text")
        );
    }

    /**
     * 映射 lexical 搜索记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return lexical 搜索记录
     * @throws SQLException SQL 异常
     */
    private LexicalSearchRecord mapLexicalSearchRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new LexicalSearchRecord(
                readLong(resultSet, "source_id"),
                resultSet.getString("article_key"),
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getString("chunk_text"),
                resultSet.getString("metadata_json"),
                resultSet.getString("review_status"),
                readSourcePaths(resultSet),
                Integer.valueOf(resultSet.getInt("chunk_index")),
                null,
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
                sqlBuilder.append("                   or ").append(column).append(" like ?\n");
                parameters.add(pattern);
            }
        }
    }

    /**
     * 规范化查询 token。
     *
     * @param queryTokens 原始 token
     * @return 规范化 token
     */
    private List<String> normalizeTokens(List<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return List.of();
        }
        List<String> normalizedTokens = new ArrayList<String>();
        for (String queryToken : queryTokens) {
            if (hasText(queryToken)) {
                normalizedTokens.add(queryToken.toLowerCase());
            }
        }
        return normalizedTokens;
    }

    /**
     * 规范化 FTS 配置。
     *
     * @param tsConfig FTS 配置
     * @return 规范化配置
     */
    private String normalizeTsConfig(String tsConfig) {
        return hasText(tsConfig) ? tsConfig.trim() : "simple";
    }

    /**
     * 计算安全返回数量。
     *
     * @param limit 原始数量
     * @return 安全数量
     */
    private int safeLimit(int limit) {
        return limit <= 0 ? 5 : limit;
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

    /**
     * 判断文本是否有值。
     *
     * @param value 文本
     * @return 是否有值
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
