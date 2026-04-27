package com.xbk.lattice.infra.persistence;

/**
 * Query Rewrite 规则记录
 *
 * 职责：承载 query_rewrite_rules 表中的规则定义
 *
 * @author xiexu
 */
public class QueryRewriteRuleRecord {

    private final long id;

    private final String ruleCode;

    private final String sourcePattern;

    private final String rewriteText;

    private final String scope;

    private final int priority;

    /**
     * 创建 Query Rewrite 规则记录。
     *
     * @param id 规则主键
     * @param ruleCode 规则编码
     * @param sourcePattern 命中表达式
     * @param rewriteText 改写文本
     * @param scope 作用域
     * @param priority 优先级
     */
    public QueryRewriteRuleRecord(
            long id,
            String ruleCode,
            String sourcePattern,
            String rewriteText,
            String scope,
            int priority
    ) {
        this.id = id;
        this.ruleCode = ruleCode;
        this.sourcePattern = sourcePattern;
        this.rewriteText = rewriteText;
        this.scope = scope;
        this.priority = priority;
    }

    /**
     * 返回规则主键。
     *
     * @return 规则主键
     */
    public long getId() {
        return id;
    }

    /**
     * 返回规则编码。
     *
     * @return 规则编码
     */
    public String getRuleCode() {
        return ruleCode;
    }

    /**
     * 返回命中表达式。
     *
     * @return 命中表达式
     */
    public String getSourcePattern() {
        return sourcePattern;
    }

    /**
     * 返回改写文本。
     *
     * @return 改写文本
     */
    public String getRewriteText() {
        return rewriteText;
    }

    /**
     * 返回作用域。
     *
     * @return 作用域
     */
    public String getScope() {
        return scope;
    }

    /**
     * 返回优先级。
     *
     * @return 优先级
     */
    public int getPriority() {
        return priority;
    }
}
