package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.chunking.SemanticChunker;
import com.xbk.lattice.infra.chunking.TextChunk;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                insert into article_chunks (article_id, chunk_text, chunk_index)
                values ((
                    select id
                    from articles
                    where article_key = ?
                    order by compiled_at desc, id desc
                    limit 1
                ), ?, ?)
                """
                : """
                insert into article_chunks (article_id, chunk_text, chunk_index)
                values ((
                    select id
                    from articles
                    where concept_id = ?
                    order by compiled_at desc, id desc
                    limit 1
                ), ?, ?)
                """;
        for (int index = 0; index < chunkTexts.size(); index++) {
            jdbcTemplate.update(insertSql, useArticleKey ? articleKey : conceptId, chunkTexts.get(index), index);
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
     * 映射 chunk 记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return chunk 记录
     */
    private ArticleChunkRecord mapArticleChunkRecord(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
        return new ArticleChunkRecord(
                resultSet.getLong("id"),
                resultSet.getLong("article_id"),
                resultSet.getString("concept_id"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text")
        );
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
