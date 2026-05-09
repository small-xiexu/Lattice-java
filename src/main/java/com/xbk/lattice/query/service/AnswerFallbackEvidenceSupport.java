package com.xbk.lattice.query.service;

import java.util.Locale;

/**
 * fallback 证据身份支持
 *
 * 职责：统一 fallback 证据的去重键、细粒度身份与展示优先级
 *
 * 不属于本类的事：不选择证据、不打分候选句、不生成最终 Markdown
 *
 * @author xiexu
 */
final class AnswerFallbackEvidenceSupport {

    private final AnswerGenerationService support;

    /**
     * 创建 fallback 证据身份支持。
     *
     * @param support 答案生成支撑逻辑
     */
    AnswerFallbackEvidenceSupport(AnswerGenerationService support) {
        this.support = support;
    }

    /**
     * 计算 fallback 证据的基础去重键。
     *
     * @param queryArticleHit 查询命中
     * @return 去重键
     */
    String canonicalKey(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            if (queryArticleHit.getArticleKey() != null
                    && queryArticleHit.getArticleKey().contains("#")) {
                return queryArticleHit.getArticleKey();
            }
            if (queryArticleHit.getConceptId() != null
                    && queryArticleHit.getConceptId().contains("#")) {
                return queryArticleHit.getConceptId();
            }
        }
        if (queryArticleHit.getSourcePaths() != null && !queryArticleHit.getSourcePaths().isEmpty()) {
            return queryArticleHit.getSourcePaths().get(0);
        }
        if (queryArticleHit.getArticleKey() != null && !queryArticleHit.getArticleKey().isBlank()) {
            return queryArticleHit.getArticleKey();
        }
        if (queryArticleHit.getConceptId() != null && !queryArticleHit.getConceptId().isBlank()) {
            return queryArticleHit.getConceptId();
        }
        return queryArticleHit.getTitle() == null ? "" : queryArticleHit.getTitle();
    }

    /**
     * 针对精确查值题保留同源的 ARTICLE / SOURCE 互补证据，避免 source 里的精确值被 article 摘要吞掉。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 问题感知后的去重键
     */
    String canonicalKey(String question, QueryArticleHit queryArticleHit) {
        String canonicalKey = canonicalKey(queryArticleHit);
        if (!support.looksLikeExactLookupQuestion(question) || queryArticleHit == null) {
            return canonicalKey;
        }
        QueryEvidenceType evidenceType = queryArticleHit.getEvidenceType();
        if (evidenceType == QueryEvidenceType.ARTICLE
                || evidenceType == QueryEvidenceType.CONTRIBUTION
                || evidenceType == QueryEvidenceType.FACT_CARD
                || evidenceType == QueryEvidenceType.SOURCE) {
            String identityKey = identityKey(queryArticleHit);
            if (!identityKey.isBlank()) {
                return canonicalKey + "#" + evidenceType.name() + "#" + identityKey;
            }
            return canonicalKey + "#" + evidenceType.name();
        }
        return canonicalKey;
    }

    /**
     * 计算同源文档内可区分章节、条目或卡片的细粒度身份。
     *
     * @param queryArticleHit 查询命中
     * @return 细粒度身份键
     */
    private String identityKey(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getArticleKey() != null && !queryArticleHit.getArticleKey().isBlank()) {
            return queryArticleHit.getArticleKey();
        }
        if (queryArticleHit.getConceptId() != null && !queryArticleHit.getConceptId().isBlank()) {
            return queryArticleHit.getConceptId();
        }
        return queryArticleHit.getTitle() == null ? "" : queryArticleHit.getTitle();
    }

    /**
     * 计算 fallback 证据的展示优先级。
     *
     * @param queryArticleHit 查询命中
     * @return 优先级
     */
    int priority(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || queryArticleHit.getEvidenceType() == null) {
            return Integer.MIN_VALUE;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION) {
            return 120;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            return 115;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            return 100;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            return 60;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.GRAPH) {
            return 40;
        }
        return 20;
    }

    /**
     * 把文本转成小写字符串，便于 fallback 相关性判断。
     *
     * @param value 原始文本
     * @return 小写文本
     */
    String lowerCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
