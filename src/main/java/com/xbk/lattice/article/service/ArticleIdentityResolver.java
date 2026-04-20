package com.xbk.lattice.article.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 文章身份解析服务
 *
 * 职责：统一按 articleKey、sourceId + conceptId、conceptId 解析文章身份，避免各入口重复实现兼容逻辑
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ArticleIdentityResolver {

    private final ArticleJdbcRepository articleJdbcRepository;

    /**
     * 创建文章身份解析服务。
     *
     * @param articleJdbcRepository 文章仓储
     */
    public ArticleIdentityResolver(ArticleJdbcRepository articleJdbcRepository) {
        this.articleJdbcRepository = articleJdbcRepository;
    }

    /**
     * 解析文章身份。
     *
     * @param articleId 文章唯一键或概念标识
     * @return 文章记录
     */
    public Optional<ArticleRecord> resolve(String articleId) {
        return resolve(articleId, null);
    }

    /**
     * 解析文章身份。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @return 文章记录
     */
    public Optional<ArticleRecord> resolve(String articleId, Long sourceId) {
        if (articleId == null) {
            return Optional.empty();
        }
        String normalizedArticleId = articleId.trim();
        if (normalizedArticleId.isEmpty()) {
            return Optional.empty();
        }

        Optional<ArticleRecord> articleRecord = tryFindByArticleKey(normalizedArticleId);
        if (articleRecord.isPresent()) {
            return articleRecord;
        }
        if (sourceId != null) {
            articleRecord = tryFindBySourceIdAndConceptId(sourceId, normalizedArticleId);
            if (articleRecord.isPresent()) {
                return articleRecord;
            }
        }
        return articleJdbcRepository.findByConceptId(normalizedArticleId);
    }

    /**
     * 强制解析文章身份。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @return 文章记录
     */
    public ArticleRecord require(String articleId, Long sourceId) {
        return resolve(articleId, sourceId)
                .orElseThrow(() -> new IllegalArgumentException(buildNotFoundMessage(articleId, sourceId)));
    }

    /**
     * 强制解析文章身份。
     *
     * @param articleId 文章唯一键或概念标识
     * @return 文章记录
     */
    public ArticleRecord require(String articleId) {
        return require(articleId, null);
    }

    /**
     * 构造未找到异常消息。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @return 异常消息
     */
    private String buildNotFoundMessage(String articleId, Long sourceId) {
        if (sourceId == null) {
            return "article not found: " + articleId;
        }
        return "article not found: " + articleId + ", sourceId=" + sourceId;
    }

    /**
     * 尝试按文章唯一键查询。
     *
     * @param articleKey 文章唯一键
     * @return 文章记录
     */
    private Optional<ArticleRecord> tryFindByArticleKey(String articleKey) {
        try {
            return articleJdbcRepository.findByArticleKey(articleKey);
        }
        catch (IllegalStateException ex) {
            if (isMissingDataSource(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    /**
     * 尝试按资料源和概念标识查询。
     *
     * @param sourceId 资料源主键
     * @param conceptId 概念标识
     * @return 文章记录
     */
    private Optional<ArticleRecord> tryFindBySourceIdAndConceptId(Long sourceId, String conceptId) {
        try {
            return articleJdbcRepository.findBySourceIdAndConceptId(sourceId, conceptId);
        }
        catch (IllegalStateException ex) {
            if (isMissingDataSource(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    /**
     * 判断是否为测试替身缺少 DataSource 的异常。
     *
     * @param ex 异常
     * @return 是否缺少 DataSource
     */
    private boolean isMissingDataSource(IllegalStateException ex) {
        return ex.getMessage() != null && ex.getMessage().contains("No DataSource set");
    }
}
