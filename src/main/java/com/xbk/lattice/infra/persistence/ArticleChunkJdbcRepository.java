package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 ArticleChunk JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ArticleChunkJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按 conceptId 替换文章 chunk。
     *
     * @param conceptId 概念标识
     * @param chunkTexts chunk 文本集合
     */
    public void replaceChunks(String conceptId, List<String> chunkTexts) {
        String deleteSql = """
                delete from article_chunks
                where article_id = (select id from articles where concept_id = ?)
                """;
        jdbcTemplate.update(deleteSql, conceptId);

        String insertSql = """
                insert into article_chunks (article_id, chunk_text, chunk_index)
                values ((select id from articles where concept_id = ?), ?, ?)
                """;
        for (int index = 0; index < chunkTexts.size(); index++) {
            jdbcTemplate.update(insertSql, conceptId, chunkTexts.get(index), index);
        }
    }

    /**
     * 按 conceptId 查询 chunk 文本。
     *
     * @param conceptId 概念标识
     * @return chunk 文本集合
     */
    public List<String> findChunkTexts(String conceptId) {
        String sql = """
                select ac.chunk_text
                from article_chunks ac
                join articles a on a.id = ac.article_id
                where a.concept_id = ?
                order by ac.chunk_index
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> resultSet.getString("chunk_text"), conceptId);
    }
}
