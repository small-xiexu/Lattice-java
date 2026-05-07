package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.ArticleReviewAuditMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章人工复核审计 JDBC 仓储
 *
 * 职责：保存并查询文章人工复核审计记录
 *
 * @author xiexu
 */
@Repository
public class ArticleReviewAuditJdbcRepository {

    private final ArticleReviewAuditMapper articleReviewAuditMapper;

    /**
     * 创建文章人工复核审计 JDBC 仓储。
     *
     * @param articleReviewAuditMapper 文章人工复核审计 Mapper
     */
    public ArticleReviewAuditJdbcRepository(ArticleReviewAuditMapper articleReviewAuditMapper) {
        this.articleReviewAuditMapper = articleReviewAuditMapper;
    }

    /**
     * 保存审计记录。
     *
     * @param articleReviewAuditRecord 审计记录
     * @return 保存后的审计记录
     */
    public ArticleReviewAuditRecord save(ArticleReviewAuditRecord articleReviewAuditRecord) {
        ArticleReviewAuditRecord normalizedRecord = new ArticleReviewAuditRecord(
                articleReviewAuditRecord.getId(),
                articleReviewAuditRecord.getSourceId(),
                articleReviewAuditRecord.getArticleKey(),
                articleReviewAuditRecord.getConceptId(),
                articleReviewAuditRecord.getAction(),
                articleReviewAuditRecord.getPreviousReviewStatus(),
                articleReviewAuditRecord.getNextReviewStatus(),
                articleReviewAuditRecord.getComment(),
                articleReviewAuditRecord.getReviewedBy(),
                articleReviewAuditRecord.getReviewedAt(),
                safeMetadataJson(articleReviewAuditRecord.getMetadataJson())
        );
        Long generatedId = articleReviewAuditMapper.insert(normalizedRecord);
        long id = generatedId == null ? 0L : generatedId.longValue();
        return new ArticleReviewAuditRecord(
                id,
                articleReviewAuditRecord.getSourceId(),
                articleReviewAuditRecord.getArticleKey(),
                articleReviewAuditRecord.getConceptId(),
                articleReviewAuditRecord.getAction(),
                articleReviewAuditRecord.getPreviousReviewStatus(),
                articleReviewAuditRecord.getNextReviewStatus(),
                articleReviewAuditRecord.getComment(),
                articleReviewAuditRecord.getReviewedBy(),
                articleReviewAuditRecord.getReviewedAt(),
                safeMetadataJson(articleReviewAuditRecord.getMetadataJson())
        );
    }

    /**
     * 按文章身份查询审计历史。
     *
     * @param articleRecord 文章记录
     * @return 审计记录列表
     */
    public List<ArticleReviewAuditRecord> findByArticle(ArticleRecord articleRecord) {
        if (articleRecord == null) {
            return List.of();
        }
        if (articleRecord.getArticleKey() != null && !articleRecord.getArticleKey().isBlank()) {
            return findByArticleKey(articleRecord.getArticleKey());
        }
        return findByConceptIdAndSourceId(articleRecord.getConceptId(), articleRecord.getSourceId());
    }

    /**
     * 按文章唯一键查询审计历史。
     *
     * @param articleKey 文章唯一键
     * @return 审计记录列表
     */
    public List<ArticleReviewAuditRecord> findByArticleKey(String articleKey) {
        return articleReviewAuditMapper.findByArticleKey(articleKey);
    }

    /**
     * 按概念标识与资料源查询审计历史。
     *
     * @param conceptId 概念标识
     * @param sourceId 资料源主键
     * @return 审计记录列表
     */
    public List<ArticleReviewAuditRecord> findByConceptIdAndSourceId(String conceptId, Long sourceId) {
        return articleReviewAuditMapper.findByConceptIdAndSourceId(conceptId, sourceId);
    }

    /**
     * 返回安全元数据 JSON。
     *
     * @param metadataJson 原始元数据 JSON
     * @return 非空 JSON
     */
    private String safeMetadataJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "{}";
        }
        return metadataJson;
    }
}
