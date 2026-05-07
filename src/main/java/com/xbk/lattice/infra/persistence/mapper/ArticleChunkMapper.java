package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ArticleChunkRecord;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章分块 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 article_chunks 表
 *
 * @author xiexu
 */
@Mapper
public interface ArticleChunkMapper {

    /**
     * 按文章唯一键删除分块。
     *
     * @param articleKey 文章唯一键
     * @return 影响行数
     */
    int deleteByArticleKey(@Param("articleKey") String articleKey);

    /**
     * 按概念标识删除分块。
     *
     * @param conceptId 概念标识
     * @return 影响行数
     */
    int deleteByConceptId(@Param("conceptId") String conceptId);

    /**
     * 按文章唯一键写入分块。
     *
     * @param articleKey 文章唯一键
     * @param chunkText 分块文本
     * @param chunkIndex 分块序号
     * @return 影响行数
     */
    int insertByArticleKey(
            @Param("articleKey") String articleKey,
            @Param("chunkText") String chunkText,
            @Param("chunkIndex") int chunkIndex
    );

    /**
     * 按概念标识写入分块。
     *
     * @param conceptId 概念标识
     * @param chunkText 分块文本
     * @param chunkIndex 分块序号
     * @return 影响行数
     */
    int insertByConceptId(
            @Param("conceptId") String conceptId,
            @Param("chunkText") String chunkText,
            @Param("chunkIndex") int chunkIndex
    );

    /**
     * 清空全部文章分块。
     */
    void truncateAll();

    /**
     * 统计全部文章分块。
     *
     * @return 分块数量
     */
    int countAll();

    /**
     * 按概念标识查询分块文本。
     *
     * @param conceptId 概念标识
     * @return 分块文本列表
     */
    List<String> findChunkTexts(@Param("conceptId") String conceptId);

    /**
     * 按概念标识查询分块记录。
     *
     * @param conceptId 概念标识
     * @return 分块记录列表
     */
    List<ArticleChunkRecord> findByConceptId(@Param("conceptId") String conceptId);

    /**
     * 按文章唯一键查询分块记录。
     *
     * @param articleKey 文章唯一键
     * @return 分块记录列表
     */
    List<ArticleChunkRecord> findByArticleKey(@Param("articleKey") String articleKey);

    /**
     * 查询全部分块记录。
     *
     * @return 分块记录列表
     */
    List<ArticleChunkRecord> findAllRecords();

    /**
     * 执行文章分块 lexical 检索。
     *
     * @param tsConfig FTS 配置
     * @param question 查询问题
     * @param likeTokens LIKE 模式列表
     * @param limit 返回上限
     * @return lexical 命中列表
     */
    List<LexicalSearchRecord> searchLexical(
            @Param("tsConfig") String tsConfig,
            @Param("question") String question,
            @Param("likeTokens") List<String> likeTokens,
            @Param("limit") int limit
    );
}
