package com.xbk.lattice.admin.service;

import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 管理侧文章查询服务
 *
 * 职责：提供管理侧文章列表筛选与详情读取能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class AdminArticleQueryService {

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    /**
     * 创建管理侧文章查询服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param articleIdentityResolver 文章身份解析服务
     */
    @Autowired
    public AdminArticleQueryService(
            ArticleJdbcRepository articleJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
    }

    /**
     * 创建兼容旧构造方式的管理侧文章查询服务。
     *
     * @param articleJdbcRepository 文章仓储
     */
    public AdminArticleQueryService(ArticleJdbcRepository articleJdbcRepository) {
        this(articleJdbcRepository, new ArticleIdentityResolver(articleJdbcRepository));
    }

    /**
     * 查询管理侧文章列表。
     *
     * @param query 关键字
     * @param lifecycle 生命周期
     * @return 匹配文章
     */
    public List<ArticleRecord> list(String query, String lifecycle) {
        return list(query, lifecycle, null);
    }

    /**
     * 查询管理侧文章列表。
     *
     * @param query 关键字
     * @param lifecycle 生命周期
     * @param sourceId 资料源主键
     * @return 匹配文章
     */
    public List<ArticleRecord> list(String query, String lifecycle, Long sourceId) {
        List<ArticleRecord> allArticles = articleJdbcRepository.findAll();
        List<ArticleRecord> matchedArticles = new ArrayList<ArticleRecord>();
        String normalizedQuery = normalize(query);
        String normalizedLifecycle = normalize(lifecycle);

        for (ArticleRecord articleRecord : allArticles) {
            if (!matchesSourceId(articleRecord, sourceId)) {
                continue;
            }
            if (!matchesLifecycle(articleRecord, normalizedLifecycle)) {
                continue;
            }
            if (!matchesQuery(articleRecord, normalizedQuery)) {
                continue;
            }
            matchedArticles.add(articleRecord);
        }
        matchedArticles.sort(Comparator
                .comparing(ArticleRecord::getCompiledAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ArticleRecord::getConceptId, Comparator.nullsLast(String::compareToIgnoreCase)));
        return matchedArticles;
    }

    /**
     * 读取单篇文章详情。
     *
     * @param conceptId 概念标识
     * @return 文章详情
     */
    public ArticleRecord get(String conceptId) {
        return get(conceptId, null);
    }

    /**
     * 读取单篇文章详情。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @return 文章详情
     */
    public ArticleRecord get(String articleId, Long sourceId) {
        Optional<ArticleRecord> articleRecord = articleIdentityResolver.resolve(articleId, sourceId);
        if (articleRecord.isEmpty()) {
            throw new IllegalArgumentException("article not found: " + articleId);
        }
        return articleRecord.orElseThrow();
    }

    /**
     * 判断是否匹配资料源筛选。
     *
     * @param articleRecord 文章记录
     * @param sourceId 资料源主键
     * @return 是否匹配
     */
    private boolean matchesSourceId(ArticleRecord articleRecord, Long sourceId) {
        if (sourceId == null) {
            return true;
        }
        return sourceId.equals(articleRecord.getSourceId());
    }

    /**
     * 判断是否匹配生命周期筛选。
     *
     * @param articleRecord 文章记录
     * @param normalizedLifecycle 规范化生命周期
     * @return 是否匹配
     */
    private boolean matchesLifecycle(ArticleRecord articleRecord, String normalizedLifecycle) {
        if (normalizedLifecycle == null) {
            return true;
        }
        return articleRecord.getLifecycle() != null
                && articleRecord.getLifecycle().toLowerCase(Locale.ROOT).equals(normalizedLifecycle);
    }

    /**
     * 判断是否匹配关键字。
     *
     * @param articleRecord 文章记录
     * @param normalizedQuery 规范化关键字
     * @return 是否匹配
     */
    private boolean matchesQuery(ArticleRecord articleRecord, String normalizedQuery) {
        if (normalizedQuery == null) {
            return true;
        }
        return contains(articleRecord.getArticleKey(), normalizedQuery)
                || contains(articleRecord.getConceptId(), normalizedQuery)
                || contains(articleRecord.getTitle(), normalizedQuery)
                || contains(articleRecord.getSummary(), normalizedQuery)
                || contains(articleRecord.getContent(), normalizedQuery);
    }

    /**
     * 判断文本是否包含关键字。
     *
     * @param value 原始文本
     * @param normalizedQuery 规范化关键字
     * @return 是否包含
     */
    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    /**
     * 规范化筛选文本。
     *
     * @param value 原始文本
     * @return 规范化结果
     */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        return trimmedValue.toLowerCase(Locale.ROOT);
    }
}
