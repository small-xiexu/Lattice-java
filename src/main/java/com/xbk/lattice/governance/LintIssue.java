package com.xbk.lattice.governance;

/**
 * Lint 问题
 *
 * 职责：表示单条治理检查发现的问题
 *
 * @author xiexu
 */
public class LintIssue {

    private final String dimension;

    private final String targetId;

    private final String message;

    private final boolean fixable;

    private final String fixSuggestion;

    /**
     * 创建 Lint 问题。
     *
     * @param dimension 维度
     * @param targetId 目标标识
     * @param message 问题描述
     */
    public LintIssue(String dimension, String targetId, String message) {
        this(dimension, targetId, message, false, null);
    }

    /**
     * 创建 Lint 问题。
     *
     * @param dimension 维度
     * @param targetId 目标标识
     * @param message 问题描述
     * @param fixable 是否可自动修复
     * @param fixSuggestion 自动修复建议
     */
    public LintIssue(String dimension, String targetId, String message, boolean fixable, String fixSuggestion) {
        this.dimension = dimension;
        this.targetId = targetId;
        this.message = message;
        this.fixable = fixable;
        this.fixSuggestion = fixSuggestion;
    }

    /**
     * 获取维度。
     *
     * @return 维度
     */
    public String getDimension() {
        return dimension;
    }

    /**
     * 获取目标标识。
     *
     * @return 目标标识
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * 获取问题描述。
     *
     * @return 问题描述
     */
    public String getMessage() {
        return message;
    }

    /**
     * 是否可自动修复。
     *
     * @return 是否可自动修复
     */
    public boolean isFixable() {
        return fixable;
    }

    /**
     * 获取自动修复建议。
     *
     * @return 自动修复建议
     */
    public String getFixSuggestion() {
        return fixSuggestion;
    }
}
