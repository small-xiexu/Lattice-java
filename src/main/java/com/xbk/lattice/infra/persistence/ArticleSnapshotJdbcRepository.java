package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.ArticleSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文章快照 JDBC 仓储
 *
 * 职责：提供文章快照历史查询能力
 *
 * @author xiexu
 */
@Repository
public class ArticleSnapshotJdbcRepository {

    private final ArticleSnapshotMapper articleSnapshotMapper;

    /**
     * 创建文章快照 JDBC 仓储。
     *
     * @param articleSnapshotMapper 文章快照 Mapper
     */
    public ArticleSnapshotJdbcRepository(ArticleSnapshotMapper articleSnapshotMapper) {
        this.articleSnapshotMapper = articleSnapshotMapper;
    }

    /**
     * 保存一条文章快照。
     *
     * @param articleSnapshotRecord 文章快照
     */
    public void save(ArticleSnapshotRecord articleSnapshotRecord) {
        articleSnapshotMapper.insert(articleSnapshotRecord);
    }

    /**
     * 查询最近文章快照。
     *
     * @param limit 返回数量
     * @return 快照列表
     */
    public List<ArticleSnapshotRecord> findRecent(int limit) {
        return articleSnapshotMapper.findRecent(Math.max(limit, 0));
    }

    /**
     * 查询指定概念的历史快照。
     *
     * @param conceptId 概念标识
     * @param limit 返回数量
     * @return 历史快照列表
     */
    public List<ArticleSnapshotRecord> findByConceptId(String conceptId, int limit) {
        return articleSnapshotMapper.findByConceptId(conceptId, Math.max(limit, 0));
    }

    /**
     * 查询指定文章唯一键的历史快照。
     *
     * @param articleKey 文章唯一键
     * @param limit 返回数量
     * @return 历史快照列表
     */
    public List<ArticleSnapshotRecord> findByArticleKey(String articleKey, int limit) {
        return articleSnapshotMapper.findByArticleKey(articleKey, Math.max(limit, 0));
    }

    /**
     * 按快照标识查询单条快照。
     *
     * @param snapshotId 快照标识
     * @return 快照记录
     */
    public Optional<ArticleSnapshotRecord> findBySnapshotId(long snapshotId) {
        return Optional.ofNullable(articleSnapshotMapper.findBySnapshotId(snapshotId));
    }
}
