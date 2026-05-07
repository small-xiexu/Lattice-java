package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.QueryRewriteAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryRewriteRuleJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryRewriteRuleRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 查询改写服务
 *
 * 职责：基于规则表把别名、缩写与业务代号扩展为更利于召回的检索问题
 *
 * @author xiexu
 */
@Slf4j
@Service
public class QueryRewriteService {

    private final QueryRewriteRuleJdbcRepository queryRewriteRuleJdbcRepository;

    private final QueryRewriteAuditJdbcRepository queryRewriteAuditJdbcRepository;

    /**
     * 创建默认查询改写服务。
     */
    public QueryRewriteService() {
        this(null, null);
    }

    /**
     * 创建查询改写服务。
     *
     * @param queryRewriteRuleJdbcRepository 改写规则仓储
     * @param queryRewriteAuditJdbcRepository 改写审计仓储
     */
    public QueryRewriteService(
            QueryRewriteRuleJdbcRepository queryRewriteRuleJdbcRepository,
            QueryRewriteAuditJdbcRepository queryRewriteAuditJdbcRepository
    ) {
        this.queryRewriteRuleJdbcRepository = queryRewriteRuleJdbcRepository;
        this.queryRewriteAuditJdbcRepository = queryRewriteAuditJdbcRepository;
    }

    /**
     * 执行查询改写。
     *
     * @param queryId 查询标识
     * @param question 查询问题
     * @return 改写结果
     */
    public QueryRewriteResult rewrite(String queryId, String question) {
        String normalizedQuestion = question == null ? "" : question.trim();
        if (normalizedQuestion.isBlank()) {
            return QueryRewriteResult.unchanged(normalizedQuestion);
        }
        QueryRewriteResult result = rewriteByRules(normalizedQuestion);
        if (queryRewriteAuditJdbcRepository == null || queryId == null || queryId.isBlank()) {
            return result;
        }
        try {
            String auditRef = queryRewriteAuditJdbcRepository.save(queryId, result);
            return result.withAuditRef(auditRef);
        }
        catch (RuntimeException exception) {
            log.warn("Query rewrite audit skipped. queryId: {}", queryId, exception);
            return result;
        }
    }

    /**
     * 基于规则执行改写。
     *
     * @param normalizedQuestion 归一化问题
     * @return 改写结果
     */
    private QueryRewriteResult rewriteByRules(String normalizedQuestion) {
        List<QueryRewriteRuleRecord> rules = activeRules();
        if (rules.isEmpty()) {
            return QueryRewriteResult.unchanged(normalizedQuestion);
        }
        List<String> matchedRuleCodes = new ArrayList<String>();
        String rewrittenQuestion = normalizedQuestion;
        for (QueryRewriteRuleRecord rule : rules) {
            if (!matchesRule(normalizedQuestion, rewrittenQuestion, rule)) {
                continue;
            }
            String rewriteText = rule.getRewriteText() == null ? "" : rule.getRewriteText().trim();
            if (rewriteText.isBlank() || containsIgnoreCase(rewrittenQuestion, rewriteText)) {
                continue;
            }
            rewrittenQuestion = rewrittenQuestion + " " + rewriteText;
            matchedRuleCodes.add(rule.getRuleCode());
        }
        boolean rewriteApplied = !matchedRuleCodes.isEmpty() && !normalizedQuestion.equals(rewrittenQuestion);
        return new QueryRewriteResult(
                normalizedQuestion,
                rewrittenQuestion,
                matchedRuleCodes,
                rewriteApplied,
                null
        );
    }

    /**
     * 查询启用规则。
     *
     * @return 启用规则
     */
    private List<QueryRewriteRuleRecord> activeRules() {
        if (queryRewriteRuleJdbcRepository == null) {
            return List.of();
        }
        return queryRewriteRuleJdbcRepository.findActiveRules();
    }

    /**
     * 判断规则是否命中。
     *
     * @param originalQuestion 原始问题
     * @param rewrittenQuestion 当前改写问题
     * @param rule 改写规则
     * @return 是否命中
     */
    private boolean matchesRule(String originalQuestion, String rewrittenQuestion, QueryRewriteRuleRecord rule) {
        if (rule == null || rule.getSourcePattern() == null || rule.getSourcePattern().isBlank()) {
            return false;
        }
        String sourcePattern = rule.getSourcePattern().trim();
        return containsIgnoreCase(originalQuestion, sourcePattern)
                || containsIgnoreCase(rewrittenQuestion, sourcePattern);
    }

    /**
     * 忽略大小写判断包含关系。
     *
     * @param text 文本
     * @param token token
     * @return 是否包含
     */
    private boolean containsIgnoreCase(String text, String token) {
        if (text == null || token == null || token.isBlank()) {
            return false;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        return normalizedText.contains(normalizedToken);
    }
}
