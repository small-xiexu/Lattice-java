package com.xbk.lattice.query.service;

import org.springframework.stereotype.Service;

/**
 * 查询意图分类器
 *
 * 职责：基于结构化精确标识识别配置查值意图，避免 query 主链关键词旁路
 *
 * @author xiexu
 */
@Service
public class QueryIntentClassifier {

    private final QuerySemanticRules querySemanticRules;

    /**
     * 创建查询意图分类器（无参回退构造器）。
     */
    public QueryIntentClassifier() {
        this(null);
    }

    /**
     * 创建查询意图分类器。
     *
     * @param querySemanticRules 查询语义规则
     */
    public QueryIntentClassifier(QuerySemanticRules querySemanticRules) {
        this.querySemanticRules = querySemanticRules == null ? new QuerySemanticRules() : querySemanticRules;
    }

    /**
     * 识别查询意图。
     *
     * @param question 查询问题
     * @return 查询意图
     */
    public QueryIntent classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryIntent.GENERAL;
        }
        if (!QueryTokenExtractor.extractExactIdentifierTokens(question).isEmpty()) {
            return QueryIntent.CONFIGURATION;
        }
        if (querySemanticRules.containsAnyArchitectureSignal(question)) {
            return QueryIntent.ARCHITECTURE;
        }
        if (querySemanticRules.containsAnyConfigIdentifierSignal(question)) {
            return QueryIntent.CONFIGURATION;
        }
        return QueryIntent.GENERAL;
    }
}
