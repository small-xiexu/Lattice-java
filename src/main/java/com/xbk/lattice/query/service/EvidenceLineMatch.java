package com.xbk.lattice.query.service;

/**
 * 聚合候选证据行。
 *
 * 职责：承载 deterministic fallback 聚合阶段的一条候选事实及其来源
 *
 * @author xiexu
 */
final class EvidenceLineMatch {

    final QueryArticleHit queryArticleHit;

    final String line;

    final int score;

    /**
     * 创建候选证据行。
     *
     * @param queryArticleHit 来源命中
     * @param line 候选事实行
     * @param score 相关性分值
     */
    EvidenceLineMatch(QueryArticleHit queryArticleHit, String line, int score) {
        this.queryArticleHit = queryArticleHit;
        this.line = line;
        this.score = score;
    }

    /**
     * 获取来源命中。
     *
     * @return 来源命中
     */
    QueryArticleHit getQueryArticleHit() {
        return queryArticleHit;
    }

    /**
     * 获取候选事实行。
     *
     * @return 候选事实行
     */
    String getLine() {
        return line;
    }

    /**
     * 获取相关性分值。
     *
     * @return 相关性分值
     */
    int getScore() {
        return score;
    }
}
