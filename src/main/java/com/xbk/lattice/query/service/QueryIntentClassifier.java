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
        return QueryIntent.GENERAL;
    }
}
