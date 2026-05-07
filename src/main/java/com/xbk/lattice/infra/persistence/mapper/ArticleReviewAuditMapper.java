package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ArticleReviewAuditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章人工复核审计 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 article_review_audits 表
 *
 * @author xiexu
 */
@Mapper
public interface ArticleReviewAuditMapper {

    /**
     * 保存审计记录。
     *
     * @param record 审计记录
     * @return 审计主键
     */
    Long insert(@Param("record") ArticleReviewAuditRecord record);

    /**
     * 按文章唯一键查询审计历史。
     *
     * @param articleKey 文章唯一键
     * @return 审计记录列表
     */
    List<ArticleReviewAuditRecord> findByArticleKey(@Param("articleKey") String articleKey);

    /**
     * 按概念标识与资料源查询审计历史。
     *
     * @param conceptId 概念标识
     * @param sourceId 资料源主键
     * @return 审计记录列表
     */
    List<ArticleReviewAuditRecord> findByConceptIdAndSourceId(
            @Param("conceptId") String conceptId,
            @Param("sourceId") Long sourceId
    );
}
