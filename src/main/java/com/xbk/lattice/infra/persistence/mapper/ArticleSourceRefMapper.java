package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ArticleSourceFileArticleKeyRow;
import com.xbk.lattice.infra.persistence.ArticleSourceRefRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章来源关联 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 article_source_refs 表
 *
 * @author xiexu
 */
@Mapper
public interface ArticleSourceRefMapper {

    /**
     * 删除指定文章的来源关联。
     *
     * @param articleKey 文章唯一键
     * @return 影响行数
     */
    int deleteByArticleKey(@Param("articleKey") String articleKey);

    /**
     * 写入文章来源关联。
     *
     * @param record 关联记录
     * @return 影响行数
     */
    int insert(@Param("record") ArticleSourceRefRecord record);

    /**
     * 按文章唯一键查询来源关联。
     *
     * @param articleKey 文章唯一键
     * @return 来源关联列表
     */
    List<ArticleSourceRefRecord> findByArticleKey(@Param("articleKey") String articleKey);

    /**
     * 按源文件主键批量查询文章键。
     *
     * @param sourceFileIds 源文件主键列表
     * @return 源文件关联文章键查询行
     */
    List<ArticleSourceFileArticleKeyRow> findArticleKeysBySourceFileIds(
            @Param("sourceFileIds") List<Long> sourceFileIds
    );
}
