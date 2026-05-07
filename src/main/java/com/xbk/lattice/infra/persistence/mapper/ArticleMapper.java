package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 articles 表
 *
 * @author xiexu
 */
@Mapper
public interface ArticleMapper {

    /**
     * 保存或更新文章。
     *
     * @param articleRecord 文章记录
     * @param searchText 检索文本
     * @param refkeyText 明确性关键词检索文本
     * @return 影响行数
     */
    int upsert(
            @Param("record") ArticleRecord articleRecord,
            @Param("searchText") String searchText,
            @Param("refkeyText") String refkeyText
    );

    /**
     * 按概念标识查询文章。
     *
     * @param conceptId 概念标识
     * @return 文章记录
     */
    ArticleRecord findByConceptId(@Param("conceptId") String conceptId);

    /**
     * 按文章唯一键查询文章。
     *
     * @param articleKey 文章唯一键
     * @return 文章记录
     */
    ArticleRecord findByArticleKey(@Param("articleKey") String articleKey);

    /**
     * 按资料源与概念标识查询文章。
     *
     * @param sourceId 资料源主键
     * @param conceptId 概念标识
     * @return 文章记录
     */
    ArticleRecord findBySourceIdAndConceptId(@Param("sourceId") Long sourceId, @Param("conceptId") String conceptId);

    /**
     * 追加上游纠错标记。
     *
     * @param conceptId 下游概念标识
     * @param fromConceptId 上游概念标识
     * @param correctionSummary 纠错摘要
     * @return 影响行数
     */
    int appendUpstreamCorrection(
            @Param("conceptId") String conceptId,
            @Param("fromConceptId") String fromConceptId,
            @Param("correctionSummary") String correctionSummary
    );

    /**
     * 查询带有指定上游纠错标记的下游文章。
     *
     * @param fromConceptId 上游概念标识
     * @return 文章列表
     */
    List<ArticleRecord> findWithUpstreamCorrections(@Param("fromConceptId") String fromConceptId);

    /**
     * 查询所有带上游纠错标记的候选文章。
     *
     * @return 候选文章列表
     */
    List<ArticleRecord> findUpstreamCorrectionCandidates();

    /**
     * 清理指定下游文章中来自特定上游的纠错标记。
     *
     * @param downstreamConceptId 下游概念标识
     * @param fromConceptId 上游概念标识
     * @return 影响行数
     */
    int clearUpstreamCorrection(
            @Param("downstreamConceptId") String downstreamConceptId,
            @Param("fromConceptId") String fromConceptId
    );

    /**
     * 查询全部文章。
     *
     * @return 文章列表
     */
    List<ArticleRecord> findAll();

    /**
     * 批量标记热点待抽检文章。
     *
     * @param articleKeys 文章唯一键
     * @param riskReason 风险原因
     * @return 影响行数
     */
    int markHotspotPendingVerification(
            @Param("articleKeys") List<String> articleKeys,
            @Param("riskReason") String riskReason
    );

    /**
     * 清空全部文章。
     *
     * @return 影响行数
     */
    int deleteAll();
}
