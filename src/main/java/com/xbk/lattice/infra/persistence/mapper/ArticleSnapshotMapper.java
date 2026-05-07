package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章快照 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 article_snapshots 表
 *
 * @author xiexu
 */
@Mapper
public interface ArticleSnapshotMapper {

    /**
     * 写入文章快照。
     *
     * @param record 文章快照记录
     * @return 影响行数
     */
    int insert(@Param("record") ArticleSnapshotRecord record);

    /**
     * 查询最近文章快照。
     *
     * @param limit 返回上限
     * @return 快照列表
     */
    List<ArticleSnapshotRecord> findRecent(@Param("limit") int limit);

    /**
     * 按概念标识查询文章快照。
     *
     * @param conceptId 概念标识
     * @param limit 返回上限
     * @return 快照列表
     */
    List<ArticleSnapshotRecord> findByConceptId(@Param("conceptId") String conceptId, @Param("limit") int limit);

    /**
     * 按文章唯一键查询文章快照。
     *
     * @param articleKey 文章唯一键
     * @param limit 返回上限
     * @return 快照列表
     */
    List<ArticleSnapshotRecord> findByArticleKey(@Param("articleKey") String articleKey, @Param("limit") int limit);

    /**
     * 按快照标识查询文章快照。
     *
     * @param snapshotId 快照标识
     * @return 快照记录
     */
    ArticleSnapshotRecord findBySnapshotId(@Param("snapshotId") long snapshotId);
}
