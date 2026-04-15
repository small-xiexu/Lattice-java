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

    /**
     * 创建 Lint 问题。
     *
     * @param dimension 维度
     * @param targetId 目标标识
     * @param message 问题描述
     */
    public LintIssue(String dimension, String targetId, String message) {
        this.dimension = dimension;
        this.targetId = targetId;
        this.message = message;
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
}
