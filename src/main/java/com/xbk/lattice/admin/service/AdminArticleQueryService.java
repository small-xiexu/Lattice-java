package com.xbk.lattice.admin.service;

import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
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
        return list(query, lifecycle, sourceId, null);
    }

    /**
     * 查询管理侧文章列表。
     *
     * @param query 关键字
     * @param lifecycle 生命周期
     * @param sourceId 资料源主键
     * @param reviewStatus 复核状态
     * @return 匹配文章
     */
    public List<ArticleRecord> list(String query, String lifecycle, Long sourceId, String reviewStatus) {
        return list(query, lifecycle, sourceId, reviewStatus, null, null, null, null);
    }

    /**
     * 查询管理侧文章列表。
     *
     * @param query 关键字
     * @param lifecycle 生命周期
     * @param sourceId 资料源主键
     * @param reviewStatus 复核状态
     * @param riskLevel 风险等级
     * @param riskReason 风险原因
     * @param hotspot 是否热点
     * @param requiresResultVerification 是否需要结果抽检
     * @return 匹配文章
     */
    public List<ArticleRecord> list(
            String query,
            String lifecycle,
            Long sourceId,
            String reviewStatus,
            String riskLevel,
            String riskReason,
            Boolean hotspot,
            Boolean requiresResultVerification
    ) {
        List<ArticleRecord> allArticles = articleJdbcRepository.findAll();
        List<ArticleRecord> matchedArticles = new ArrayList<ArticleRecord>();
        String normalizedQuery = normalize(query);
        String normalizedLifecycle = normalize(lifecycle);
        String normalizedReviewStatus = normalize(reviewStatus);
        String normalizedRiskLevel = normalize(riskLevel);
        String normalizedRiskReason = normalize(riskReason);

        for (ArticleRecord articleRecord : allArticles) {
            if (!matchesSourceId(articleRecord, sourceId)) {
                continue;
            }
            if (!matchesLifecycle(articleRecord, normalizedLifecycle)) {
                continue;
            }
            if (!matchesReviewStatus(articleRecord, normalizedReviewStatus)) {
                continue;
            }
            if (!matchesRiskLevel(articleRecord, normalizedRiskLevel)) {
                continue;
            }
            if (!matchesRiskReason(articleRecord, normalizedRiskReason)) {
                continue;
            }
            if (!matchesBoolean(articleRecord.isHotspot(), hotspot)) {
                continue;
            }
            if (!matchesBoolean(articleRecord.isRequiresResultVerification(), requiresResultVerification)) {
                continue;
            }
            if (!matchesQuery(articleRecord, normalizedQuery)) {
                continue;
            }
            matchedArticles.add(articleRecord);
        }
        matchedArticles.sort(Comparator
                .comparing(AdminArticleQueryService::resolveStoredAt, Comparator.nullsLast(Comparator.reverseOrder()))
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
     * 判断是否匹配复核状态筛选。
     *
     * @param articleRecord 文章记录
     * @param normalizedReviewStatus 规范化复核状态
     * @return 是否匹配
     */
    private boolean matchesReviewStatus(ArticleRecord articleRecord, String normalizedReviewStatus) {
        if (normalizedReviewStatus == null) {
            return true;
        }
        return articleRecord.getReviewStatus() != null
                && articleRecord.getReviewStatus().toLowerCase(Locale.ROOT).equals(normalizedReviewStatus);
    }

    /**
     * 判断是否匹配风险等级筛选。
     *
     * @param articleRecord 文章记录
     * @param normalizedRiskLevel 规范化风险等级
     * @return 是否匹配
     */
    private boolean matchesRiskLevel(ArticleRecord articleRecord, String normalizedRiskLevel) {
        if (normalizedRiskLevel == null) {
            return true;
        }
        return articleRecord.getRiskLevel() != null
                && articleRecord.getRiskLevel().toLowerCase(Locale.ROOT).equals(normalizedRiskLevel);
    }

    /**
     * 判断是否匹配风险原因筛选。
     *
     * @param articleRecord 文章记录
     * @param normalizedRiskReason 规范化风险原因
     * @return 是否匹配
     */
    private boolean matchesRiskReason(ArticleRecord articleRecord, String normalizedRiskReason) {
        if (normalizedRiskReason == null) {
            return true;
        }
        List<String> riskReasons = articleRecord.getRiskReasons();
        if (riskReasons == null || riskReasons.isEmpty()) {
            return false;
        }
        for (String riskReason : riskReasons) {
            if (riskReason != null && riskReason.toLowerCase(Locale.ROOT).equals(normalizedRiskReason)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否匹配布尔筛选。
     *
     * @param actual 实际值
     * @param expected 期望值
     * @return 是否匹配
     */
    private boolean matchesBoolean(boolean actual, Boolean expected) {
        return expected == null || actual == expected.booleanValue();
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
                || contains(articleRecord.getContent(), normalizedQuery)
                || containsAny(articleRecord.getSourcePaths(), normalizedQuery);
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
     * 判断任一来源路径是否包含关键字。
     *
     * @param values 来源路径列表
     * @param normalizedQuery 规范化关键字
     * @return 是否包含
     */
    private boolean containsAny(List<String> values, String normalizedQuery) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (contains(value, normalizedQuery)) {
                return true;
            }
        }
        return false;
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

    /**
     * 解析文章最近存表时间。
     *
     * @param articleRecord 文章记录
     * @return 最近存表时间
     */
    private static OffsetDateTime resolveStoredAt(ArticleRecord articleRecord) {
        if (articleRecord.getUpdatedAt() != null) {
            return articleRecord.getUpdatedAt();
        }
        return articleRecord.getCompiledAt();
    }
}
